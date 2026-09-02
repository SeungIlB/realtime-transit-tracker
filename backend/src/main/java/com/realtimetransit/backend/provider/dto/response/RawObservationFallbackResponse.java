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
public class RawObservationFallbackResponse {
	private Long id;
	private String payload;
	private Instant providerObservedAt;
	private Instant receivedAt;
	private Instant expiresAt;

	public static RawObservationFallbackResponse from(RawObservationEntity entity) {
		return RawObservationFallbackResponse.builder()
				.id(entity.getId())
				.payload(entity.getPayload())
				.providerObservedAt(entity.getProviderObservedAt())
				.receivedAt(entity.getReceivedAt())
				.expiresAt(entity.getExpiresAt())
				.build();
	}
}
