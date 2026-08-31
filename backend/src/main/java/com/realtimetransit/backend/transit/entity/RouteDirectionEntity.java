package com.realtimetransit.backend.transit.entity;

import java.time.Instant;
import java.util.UUID;

public record RouteDirectionEntity(
		UUID id,
		UUID lineId,
		String providerDirectionId,
		UUID originStopId,
		UUID terminalStopId,
		UUID representativeNextStopId,
		String displayName,
		Boolean active,
		Instant createdAt,
		Instant updatedAt) {
}
