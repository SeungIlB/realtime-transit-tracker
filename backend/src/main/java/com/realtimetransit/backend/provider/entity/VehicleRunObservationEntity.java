package com.realtimetransit.backend.provider.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VehicleRunObservationEntity(
		Long id,
		Long rawObservationId,
		UUID lineId,
		UUID directionId,
		UUID stopPatternId,
		String providerVehicleId,
		String providerRunId,
		UUID destinationStopId,
		UUID currentStopId,
		Integer currentSequence,
		String serviceType,
		String movementStatus,
		BigDecimal latitude,
		BigDecimal longitude,
		BigDecimal speedKph,
		BigDecimal bearingDegrees,
		String positionSource,
		Instant observedAt,
		Instant receivedAt) {
}
