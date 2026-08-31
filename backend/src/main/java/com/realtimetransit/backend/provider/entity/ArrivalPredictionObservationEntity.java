package com.realtimetransit.backend.provider.entity;

import java.time.Instant;
import java.util.UUID;

public record ArrivalPredictionObservationEntity(
		Long id,
		Long rawObservationId,
		Long vehicleRunObservationId,
		UUID boardingStopId,
		Instant expectedAt,
		Instant minExpectedAt,
		Instant maxExpectedAt,
		Integer remainingStops,
		String source,
		String confidence,
		Instant observedAt,
		Instant receivedAt) {
}
