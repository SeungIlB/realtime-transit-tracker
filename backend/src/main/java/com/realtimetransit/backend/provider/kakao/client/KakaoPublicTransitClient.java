package com.realtimetransit.backend.provider.kakao.client;

import tools.jackson.databind.JsonNode;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.common.cache.TransitCacheNames;
import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.common.quota.ExternalApiQuotaService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoPublicTransitClient {

	private final RestClient client;
	private final String restApiKey;
	private final ExternalApiQuotaService quotaService;

	public KakaoPublicTransitClient(RestClient.Builder builder,
			@Value("${transit.routing.kakao.base-url:https://dapi.kakao.com}") String baseUrl,
			@Value("${transit.routing.kakao.rest-api-key:}") String restApiKey,
			ExternalApiQuotaService quotaService) {
		this.client = builder.baseUrl(baseUrl).build();
		this.restApiKey = restApiKey;
		this.quotaService = quotaService;
	}

	@Cacheable(cacheNames = TransitCacheNames.KAKAO_PUBLIC_TRANSIT, key = "#root.target.cacheKey(#startLongitude, #startLatitude, #endLongitude, #endLatitude)")
	public JsonNode search(double startLongitude, double startLatitude,
			double endLongitude, double endLatitude) {
		if (restApiKey == null || restApiKey.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION, "Kakao REST API key is not configured");
		}
		if (!quotaService.tryAcquire(ExternalApiProvider.KAKAO_PUBLIC_TRANSIT).isAllowed()) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_QUOTA_EXCEEDED, "Kakao public transit daily quota exceeded");
		}
		try {
			return client.get().uri(uriBuilder -> uriBuilder.path("/v2/routing/publictraffic")
					.queryParam("start_x", startLongitude).queryParam("start_y", startLatitude)
					.queryParam("end_x", endLongitude).queryParam("end_y", endLatitude)
					.build())
				.header("Authorization", "KakaoAK " + restApiKey)
				.retrieve().body(JsonNode.class);
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Kakao public transit route request failed");
		}
	}

	public String cacheKey(double startLongitude, double startLatitude, double endLongitude, double endLatitude) {
		return String.format("%.3f:%.3f:%.3f:%.3f", startLongitude, startLatitude, endLongitude, endLatitude);
	}
}
