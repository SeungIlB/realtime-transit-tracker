package com.realtimetransit.backend.provider.seoulsubway.client;

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
public class SeoulSubwayReferenceClient extends ProviderClientSupport {

	private final RestClient client;
	private final String serviceKey;

	public SeoulSubwayReferenceClient(
			RestClient.Builder builder,
			ExternalApiQuotaService quotaService,
			TransitProviderProperties properties) {
		super(quotaService);
		var config = properties.getSeoulSubway();
		this.client = builder.clone().baseUrl(config.getReferenceBaseUrl()).build();
		this.serviceKey = config.getReferenceServiceKey();
	}

	@Cacheable(
			cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA,
			key = "'SEOUL:reference-stations:v1'",
			unless = "#result.isEmpty()")
	public List<JsonNode> findAllLineStations() {
		return call("SearchSTNBySubwayLineInfo", "SEOUL_SUBWAY reference stations");
	}

	@Cacheable(
			cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA,
			key = "'SEOUL:station-master:v1'",
			unless = "#result.isEmpty()")
	public List<JsonNode> findAllMasterStations() {
		return call("subwayStationMaster", "SEOUL_SUBWAY station master");
	}

	private List<JsonNode> call(String serviceName, String errorContext) {
		requireKey(serviceKey, ExternalApiProvider.SEOUL_SUBWAY);
		acquireQuota(ExternalApiProvider.SEOUL_SUBWAY);
		try {
			JsonNode response = client.get()
					.uri("/{key}/json/{service}/1/1000/", serviceKey, serviceName)
					.retrieve().body(JsonNode.class);
			JsonNode service = response == null ? null : response.path(serviceName);
			if (service == null || !"INFO-000".equals(text(service.path("RESULT"), "CODE"))) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, errorContext);
			}
			List<JsonNode> rows = new ArrayList<>();
			service.path("row").forEach(rows::add);
			return rows;
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, errorContext);
		}
	}

}
