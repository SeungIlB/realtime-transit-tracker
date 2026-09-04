package com.realtimetransit.backend.provider.nationalbus.client;

import java.util.ArrayList;
import java.util.List;

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

import tools.jackson.databind.JsonNode;

@Component
public class NationalBusReferenceClient extends ProviderClientSupport {

	private static final int PAGE_SIZE = 1000;
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
			key = "'NATIONAL:all-routes:v1'",
			unless = "#result.isEmpty()")
	public List<JsonNode> findAllRoutes() {
		return fetchAll("/mst_info", null);
	}

	@Cacheable(
			cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA,
			key = "'NATIONAL:route-stops:v1:' + #municipalityCode",
			unless = "#result.isEmpty()")
	public List<JsonNode> findStops(String municipalityCode) {
		return fetchAll("/ps_info", municipalityCode);
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
			Integer totalCount = integer(response.path("body"), "totalCount");
			total = totalCount == null ? result.size() : totalCount;
		} while (result.size() < total);
		return result;
	}

	private JsonNode call(String path, String municipalityCode, int page) {
		requireKey(serviceKey, ExternalApiProvider.NATIONAL_PRECISION_BUS);
		acquireQuota(ExternalApiProvider.NATIONAL_PRECISION_BUS);
		try {
			JsonNode response = client.get().uri(uriBuilder -> {
				uriBuilder.path(path).queryParam("serviceKey", "{serviceKey}")
						.queryParam("pageNo", page).queryParam("numOfRows", PAGE_SIZE)
						.queryParam("type", "json");
				if (municipalityCode != null) uriBuilder.queryParam("stdgCd", municipalityCode);
				return uriBuilder.build(serviceKey);
			}).retrieve().body(JsonNode.class);
			if (response == null || !"K0".equals(text(response.path("header"), "resultCode"))) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "NATIONAL_PRECISION_BUS " + path);
			}
			return response;
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "NATIONAL_PRECISION_BUS " + path);
		}
	}

	private static List<JsonNode> items(JsonNode response) {
		JsonNode item = response.path("body").path("items").path("item");
		List<JsonNode> result = new ArrayList<>();
		if (item.isArray()) item.forEach(result::add);
		else if (item.isObject()) result.add(item);
		return result;
	}
}
