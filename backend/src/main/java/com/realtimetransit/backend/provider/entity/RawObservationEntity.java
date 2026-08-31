package com.realtimetransit.backend.provider.entity;

import java.time.Instant;

public record RawObservationEntity(
		Long id,
		Long providerId,
		String endpoint,
		String requestKey,
		Instant receivedAt,
		Instant providerObservedAt,
		Integer responseStatus,
		String payload,
		Instant expiresAt) {
}
