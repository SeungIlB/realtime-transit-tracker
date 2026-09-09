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
public class SeoulSubwayPositionClient extends ProviderClientSupport {

	private final RestClient client;
	private final String serviceKey;

	public SeoulSubwayPositionClient(
			RestClient.Builder builder,
			ExternalApiQuotaService quotaService,
			TransitProviderProperties properties) {
		super(quotaService);
		var config = properties.getSeoulSubway();
		this.client = builder.clone().baseUrl(config.getBaseUrl()).build();
		this.serviceKey = config.getServiceKey();
	}

	@Cacheable(
			cacheNames = TransitCacheNames.SEOUL_SUBWAY_ARRIVALS,
			key = "'positions:' + #lineName",
			unless = "#result.isEmpty()")
	public List<JsonNode> findPositions(String lineName) {
		requireKey(serviceKey, ExternalApiProvider.SEOUL_SUBWAY);
		acquireQuota(ExternalApiProvider.SEOUL_SUBWAY);
		try {
			JsonNode response = client.get()
					.uri("/{key}/json/realtimePosition/0/200/{line}", serviceKey, lineName)
					.retrieve().body(JsonNode.class);
			if (response == null
					|| !"INFO-000".equals(text(response.path("errorMessage"), "code"))) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "SEOUL_SUBWAY realtime position");
			}
			List<JsonNode> positions = new ArrayList<>();
			response.path("realtimePositionList").forEach(positions::add);
			return positions;
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "SEOUL_SUBWAY realtime position");
		}
	}
}
