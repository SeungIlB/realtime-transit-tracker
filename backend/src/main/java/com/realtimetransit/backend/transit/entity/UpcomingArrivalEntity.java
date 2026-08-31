package com.realtimetransit.backend.transit.entity;

import java.time.Instant;
import java.util.UUID;

public record UpcomingArrivalEntity(
		Long arrivalPredictionId,
		Long vehicleRunObservationId,
		String providerVehicleId,
		UUID lineId,
		UUID boardingStopId,
		Instant expectedAt,
		Instant minExpectedAt,
		Instant maxExpectedAt,
		Integer remainingStops,
		String source,
		String confidence,
		String movementStatus,
		UUID currentStopId,
		Integer currentSequence,
		Instant observedAt,
		Instant receivedAt) {
}
