package com.realtimetransit.backend.provider.entity;

import java.time.Instant;

public record ProviderCollectionStatusEntity(
		Long providerId,
		String providerCode,
		String endpoint,
		Long totalCalls,
		Long successfulCalls,
		Long failedCalls,
		Instant latestReceivedAt) {
}
