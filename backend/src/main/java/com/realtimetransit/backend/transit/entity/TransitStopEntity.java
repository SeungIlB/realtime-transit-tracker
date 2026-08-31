package com.realtimetransit.backend.transit.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransitStopEntity(
		UUID id,
		Long providerId,
		String providerStopId,
		UUID parentStationId,
		String publicName,
		BigDecimal latitude,
		BigDecimal longitude,
		Instant sourceUpdatedAt,
		Instant createdAt,
		Instant updatedAt) {
}
