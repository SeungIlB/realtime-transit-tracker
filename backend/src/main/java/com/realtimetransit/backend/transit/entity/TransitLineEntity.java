package com.realtimetransit.backend.transit.entity;

import java.time.Instant;
import java.util.UUID;

public record TransitLineEntity(
		UUID id,
		Long providerId,
		String providerLineId,
		String publicName,
		String operatorName,
		String routeType,
		Boolean active,
		Instant sourceUpdatedAt,
		Instant createdAt,
		Instant updatedAt) {
}
