package com.realtimetransit.backend.provider.dto.response;

import java.time.Instant;

import com.realtimetransit.backend.provider.entity.RawObservationEntity;

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
public class RawObservationStatusResponse {
	private Long id;
	private Long providerId;
	private String endpoint;
	private String requestKey;
	private Instant receivedAt;
	private Instant providerObservedAt;
	private Integer responseStatus;
	private Instant expiresAt;

	public static RawObservationStatusResponse from(RawObservationEntity entity) {
		return RawObservationStatusResponse.builder()
				.id(entity.getId())
				.providerId(entity.getProviderId())
				.endpoint(entity.getEndpoint())
				.requestKey(entity.getRequestKey())
				.receivedAt(entity.getReceivedAt())
				.providerObservedAt(entity.getProviderObservedAt())
				.responseStatus(entity.getResponseStatus())
				.expiresAt(entity.getExpiresAt())
				.build();
	}
}
