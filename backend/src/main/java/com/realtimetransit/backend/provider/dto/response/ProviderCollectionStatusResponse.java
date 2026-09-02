package com.realtimetransit.backend.provider.dto.response;

import java.time.Instant;

import com.realtimetransit.backend.provider.entity.ProviderCollectionStatusEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCollectionStatusResponse {
	private Long providerId;
	private String providerCode;
	private String endpoint;
	private Long totalCalls;
	private Long successfulCalls;
	private Long failedCalls;
	private Instant latestReceivedAt;

	public static ProviderCollectionStatusResponse from(ProviderCollectionStatusEntity entity) {
		return ProviderCollectionStatusResponse.builder()
				.providerId(entity.getProviderId())
				.providerCode(entity.getProviderCode())
				.endpoint(entity.getEndpoint())
				.totalCalls(entity.getTotalCalls())
				.successfulCalls(entity.getSuccessfulCalls())
				.failedCalls(entity.getFailedCalls())
				.latestReceivedAt(entity.getLatestReceivedAt())
				.build();
	}
}
