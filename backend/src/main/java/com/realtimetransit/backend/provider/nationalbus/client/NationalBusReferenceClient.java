package com.realtimetransit.backend.provider.nationalbus.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.realtimetransit.backend.common.cache.TransitCacheNames;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.common.quota.ExternalApiQuotaService;
import com.realtimetransit.backend.provider.client.ProviderClientSupport;
import com.realtimetransit.backend.provider.client.TransitProviderProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

@Component
public class NationalBusReferenceClient extends ProviderClientSupport {

	private static final int PAGE_SIZE = 1000;
	private static final String STOP_SERVICE = "/BusSttnInfoInqireService";
	private static final String ROUTE_SERVICE = "/BusRouteInfoInqireService";
	private static final String LOCATION_SERVICE = "/BusLcInfoInqireService";
	private static final String ARRIVAL_SERVICE = "/ArvlInfoInqireService";
	private static final int MAX_ATTEMPTS = 3;

	private final RestClient client;
	private final String serviceKey;

	public NationalBusReferenceClient(
			RestClient.Builder builder,
			ExternalApiQuotaService quotaService,
			TransitProviderProperties properties) {
		super(quotaService);
		var config = properties.getNationalPrecisionBus();
		this.client = builder.clone().baseUrl(config.getBaseUrl()).build();
		this.serviceKey = decodeServiceKey(config.getServiceKey());
	}

	@Cacheable(
			cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA,
			key = "'TAGO:nearby-cities:v2:' + #latitude.toPlainString() + ':' + #longitude.toPlainString()")
	public List<String> findNearbyCityCodes(BigDecimal latitude, BigDecimal longitude) {
		return fetchAll(
				STOP_SERVICE,
				"/getCrdntPrxmtSttnList",
				List.of(parameter("gpsLati", latitude), parameter("gpsLong", longitude))).stream()
				.map(item -> text(item, "citycode"))
				.filter(Objects::nonNull)
				.distinct()
				.toList();
	}

	public List<JsonNode> findRoutes(String cityCode, String routeNo) {
		return fetchAll(
				ROUTE_SERVICE,
				"/getRouteNoList",
				List.of(parameter("cityCode", cityCode), parameter("routeNo", routeNo)));
	}

	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'TAGO:city-names:v2'")
	public Map<String, String> findCityNamesByCode() {
		Map<String, String> namesByCode = new LinkedHashMap<>();
		for (JsonNode item : fetchAll(ROUTE_SERVICE, "/getCtyCodeList", List.of())) {
			String cityCode = text(item, "citycode");
			String cityName = text(item, "cityname");
			if (cityCode != null && cityName != null) namesByCode.put(cityCode, cityName);
		}
		return Map.copyOf(namesByCode);
	}

	public List<JsonNode> findRouteStops(String cityCode, String routeId) {
		return fetchAll(
				ROUTE_SERVICE,
				"/getRouteAcctoThrghSttnList",
				List.of(parameter("cityCode", cityCode), parameter("routeId", routeId)));
	}

	public List<JsonNode> findArrivals(String cityCode, String routeId, String nodeId) {
		return fetchAll(
				ARRIVAL_SERVICE,
				"/getSttnAcctoSpcifyRouteBusArvlPrearngeInfoList",
				List.of(
						parameter("cityCode", cityCode),
						parameter("routeId", routeId),
						parameter("nodeId", nodeId)));
	}

	public List<JsonNode> findLocations(String cityCode, String routeId) {
		return fetchAll(
				LOCATION_SERVICE,
				"/getRouteAcctoBusLcList",
				List.of(parameter("cityCode", cityCode), parameter("routeId", routeId)));
	}

	private List<JsonNode> fetchAll(String servicePath, String operationPath, List<QueryParameter> parameters) {
		List<JsonNode> result = new ArrayList<>();
		int page = 1;
		int totalCount;
		do {
			JsonNode response = call(servicePath, operationPath, parameters, page++);
			List<JsonNode> pageItems = items(response);
			result.addAll(pageItems);
			Integer reportedTotal = integer(response.path("response").path("body"), "totalCount");
			totalCount = reportedTotal == null ? result.size() : reportedTotal;
			if (pageItems.isEmpty()) break;
		} while (result.size() < totalCount);
		return List.copyOf(result);
	}

	private JsonNode call(
			String servicePath,
			String operationPath,
			List<QueryParameter> parameters,
			int page) {
		requireKey(serviceKey, ExternalApiProvider.NATIONAL_PRECISION_BUS);
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			acquireQuota(ExternalApiProvider.NATIONAL_PRECISION_BUS);
			try {
				JsonNode response = client.get().uri(uriBuilder -> {
					uriBuilder.path(servicePath).path(operationPath)
							.queryParam("serviceKey", "{serviceKey}")
							.queryParam("_type", "json")
							.queryParam("pageNo", page)
							.queryParam("numOfRows", PAGE_SIZE);
					for (QueryParameter parameter : parameters) {
						uriBuilder.queryParam(parameter.getName(), parameter.getValue());
					}
					return uriBuilder.build(serviceKey);
				}).retrieve().body(JsonNode.class);
				if (isSuccessful(response)) return response;
				if (!isTemporarilyBusy(response) || attempt == MAX_ATTEMPTS) {
					validate(response, operationPath);
				}
			} catch (RestClientException exception) {
				if (attempt == MAX_ATTEMPTS) {
					throw new BusinessException(
							ErrorCode.EXTERNAL_API_ERROR,
							ExternalApiProvider.NATIONAL_PRECISION_BUS + " " + operationPath);
				}
			}
		}
		throw new BusinessException(
				ErrorCode.EXTERNAL_API_ERROR,
				ExternalApiProvider.NATIONAL_PRECISION_BUS + " " + operationPath);
	}

	private static boolean isSuccessful(JsonNode response) {
		return response != null
				&& "00".equals(text(response.path("response").path("header"), "resultCode"));
	}

	private static boolean isTemporarilyBusy(JsonNode response) {
		JsonNode header = response == null ? null : response.path("response").path("header");
		String code = text(header, "resultCode");
		String message = text(header, "resultMsg");
		return "22".equals(code)
				|| message != null && message.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS");
	}

	private static void validate(JsonNode response, String operationPath) {
		JsonNode header = response == null ? null : response.path("response").path("header");
		if (header == null || !"00".equals(text(header, "resultCode"))) {
			throw new BusinessException(
					ErrorCode.EXTERNAL_API_ERROR,
					ExternalApiProvider.NATIONAL_PRECISION_BUS + " " + operationPath);
		}
	}

	private static List<JsonNode> items(JsonNode response) {
		JsonNode item = response.path("response").path("body").path("items").path("item");
		List<JsonNode> result = new ArrayList<>();
		if (item.isArray()) item.forEach(result::add);
		else if (item.isObject()) result.add(item);
		return result;
	}

	private static QueryParameter parameter(String name, Object value) {
		return new QueryParameter(name, value);
	}

	@Getter
	@AllArgsConstructor
	private static final class QueryParameter {
		private final String name;
		private final Object value;
	}
}
