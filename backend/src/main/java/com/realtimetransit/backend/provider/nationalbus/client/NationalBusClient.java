package com.realtimetransit.backend.provider.nationalbus.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.cache.TransitCacheNames;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.common.quota.ExternalApiQuotaService;
import com.realtimetransit.backend.provider.client.ProviderClientSupport;
import com.realtimetransit.backend.provider.client.TransitProviderClient;
import com.realtimetransit.backend.provider.client.TransitProviderProperties;
import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.client.dto.ExternalDirection;
import com.realtimetransit.backend.provider.client.dto.ExternalRouteReference;
import com.realtimetransit.backend.provider.client.dto.ExternalStop;
import com.realtimetransit.backend.provider.client.dto.ExternalTransitLine;

@Component
@EnableConfigurationProperties(TransitProviderProperties.class)
public class NationalBusClient extends ProviderClientSupport implements TransitProviderClient {
	private static final int PAGE_SIZE = 1000;
	private final RestClient restClient;
	private final String serviceKey;

	public NationalBusClient(RestClient.Builder builder, ExternalApiQuotaService quotaService,
			TransitProviderProperties properties) {
		super(quotaService);
		var config = properties.getNationalPrecisionBus();
		this.restClient = builder.baseUrl(config.getBaseUrl()).build();
		this.serviceKey = decodeServiceKey(config.getServiceKey());
	}

	@Override
	public ExternalApiProvider provider() {
		return ExternalApiProvider.NATIONAL_PRECISION_BUS;
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'NATIONAL:lines:' + #query + ':' + #limit")
	public List<ExternalTransitLine> searchLines(String query, int limit) {
		String normalized = query.strip().toLowerCase(Locale.ROOT);
		return fetchAll("/mst_info", null).stream()
				.filter(item -> safe(text(item, "rteNo")).toLowerCase(Locale.ROOT).contains(normalized))
				.sorted(Comparator.comparing(item -> safe(text(item, "rteNo"))))
				.limit(limit)
				.map(this::toLine)
				.toList();
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'NATIONAL:route:' + #providerLineId")
	public ExternalRouteReference fetchRoute(String providerLineId) {
		ProviderLineKey key = ProviderLineKey.parse(providerLineId);
		List<JsonNode> routeStops = fetchAll("/ps_info", key.getMunicipalityCode()).stream()
				.filter(item -> key.getRouteId().equals(text(item, "rteId")))
				.toList();
		List<ExternalDirection> directions = routeStops.stream()
				.collect(java.util.stream.Collectors.groupingBy(item -> fallback(text(item, "drcGbnCd"), "0")))
				.entrySet().stream()
				.sorted(java.util.Map.Entry.comparingByKey())
				.map(entry -> toDirection(entry.getKey(), entry.getValue()))
				.toList();
		return ExternalRouteReference.builder()
				.providerLineId(providerLineId)
				.sourceUpdatedAt(latestInstant(routeStops, "totDt", "yyyyMMddHHmmss"))
				.directions(directions)
				.build();
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.NATIONAL_BUS_LOCATIONS, key = "#providerLineId")
	public List<ExternalArrival> fetchArrivals(String providerLineId, String providerStopId) {
		ProviderLineKey key = ProviderLineKey.parse(providerLineId);
		return fetchAll("/rtm_loc_info", key.getMunicipalityCode()).stream()
				.filter(item -> key.getRouteId().equals(text(item, "rteId")))
				.map(item -> ExternalArrival.builder()
						.providerVehicleId(text(item, "vhclNo"))
						.providerRunId(key.getRouteId())
						.movementStatus("UNKNOWN")
						.latitude(decimal(item, "lat"))
						.longitude(decimal(item, "lot"))
						.speedKph(decimal(item, "oprSpd"))
						.bearingDegrees(decimal(item, "oprDrct"))
						.positionSource(positionSource(text(item, "evtType")))
						.observedAt(firstInstant(item, "gthrDt", "totDt"))
						.build())
				.toList();
	}

	private List<JsonNode> fetchAll(String path, String municipalityCode) {
		List<JsonNode> result = new ArrayList<>();
		int page = 1;
		int total;
		do {
			JsonNode response = call(path, municipalityCode, page++);
			List<JsonNode> pageItems = items(response);
			if (pageItems.isEmpty()) break;
			result.addAll(pageItems);
			total = integer(response.path("body"), "totalCount") == null ? result.size() : integer(response.path("body"), "totalCount");
		} while (result.size() < total);
		return result;
	}

	private JsonNode call(String path, String municipalityCode, int page) {
		requireKey(serviceKey, provider());
		acquireQuota(provider());
		try {
			JsonNode response = restClient.get().uri(uriBuilder -> {
				uriBuilder.path(path).queryParam("serviceKey", "{serviceKey}").queryParam("pageNo", page)
						.queryParam("numOfRows", PAGE_SIZE).queryParam("type", "json");
				if (municipalityCode != null) {
					uriBuilder.queryParam("stdgCd", municipalityCode);
				}
				return uriBuilder.build(serviceKey);
			}).retrieve().body(JsonNode.class);
			validate(response, path);
			return response;
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, provider() + " " + path);
		}
	}

	private void validate(JsonNode response, String path) {
		if (response == null || !"K0".equals(text(response.path("header"), "resultCode"))) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, provider() + " " + path);
		}
	}

	private ExternalTransitLine toLine(JsonNode item) {
		return ExternalTransitLine.builder()
				.providerLineId(text(item, "stdgCd") + ":" + text(item, "rteId"))
				.publicName(text(item, "rteNo"))
				.operatorName(text(item, "lclgvNm"))
				.routeType(text(item, "rteType"))
				.sourceUpdatedAt(koreaInstant(text(item, "totDt"), "yyyyMMddHHmmss"))
				.build();
	}

	private ExternalDirection toDirection(String directionCode, List<JsonNode> items) {
		List<ExternalStop> stops = items.stream()
				.sorted(Comparator.comparingInt(item -> integer(item, "bstaSn") == null ? Integer.MAX_VALUE : integer(item, "bstaSn")))
				.map(item -> ExternalStop.builder()
						.providerStopId(text(item, "stdgCd") + ":" + text(item, "bstaId"))
						.publicName(text(item, "bstaNm"))
						.platformId(text(item, "bstaNo"))
						.sequence(integer(item, "bstaSn"))
						.latitude(decimal(item, "bstaLat"))
						.longitude(decimal(item, "bstaLot"))
						.build())
				.toList();
		return ExternalDirection.builder().providerDirectionId(directionCode)
				.displayName("0".equals(directionCode) ? "상행" : "하행").stops(stops).build();
	}

	private static List<JsonNode> items(JsonNode response) {
		JsonNode item = response.path("body").path("items").path("item");
		List<JsonNode> result = new ArrayList<>();
		if (item.isArray()) item.forEach(result::add);
		else if (item.isObject()) result.add(item);
		return result;
	}

	private static Instant latestInstant(List<JsonNode> items, String field, String pattern) {
		return items.stream().map(item -> koreaInstant(text(item, field), pattern))
				.filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
	}

	private static Instant firstInstant(JsonNode item, String first, String second) {
		Instant instant = koreaInstant(text(item, first), "yyyy-MM-dd HH:mm:ss");
		return instant != null ? instant : koreaInstant(text(item, second), "yyyyMMddHHmmss");
	}

	private static String safe(String value) { return value == null ? "" : value; }
	private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
	private static String positionSource(String value) {
		return "GNSS".equalsIgnoreCase(value) ? "GNSS" : "GPS";
	}

	private static final class ProviderLineKey {
		private final String municipalityCode;
		private final String routeId;

		private ProviderLineKey(String municipalityCode, String routeId) {
			this.municipalityCode = municipalityCode;
			this.routeId = routeId;
		}

		private String getMunicipalityCode() {
			return municipalityCode;
		}

		private String getRouteId() {
			return routeId;
		}

		private static ProviderLineKey parse(String value) {
			String[] parts = value.split(":", 2);
			if (parts.length != 2) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid national route ID");
			return new ProviderLineKey(parts[0], parts[1]);
		}
	}
}
