package com.realtimetransit.backend.transit.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StopPatternEntity(
		UUID id,
		UUID lineId,
		String providerPatternId,
		String serviceType,
		LocalDate validFrom,
		LocalDate validTo,
		Boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
