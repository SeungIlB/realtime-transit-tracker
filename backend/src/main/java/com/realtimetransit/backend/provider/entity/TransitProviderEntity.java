package com.realtimetransit.backend.provider.entity;

import java.time.Instant;

public record TransitProviderEntity(
		Long id,
		String code,
		String displayName,
		String transportType,
		Boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
