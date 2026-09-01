package com.realtimetransit.backend.transit.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingArrivalResponse {
	private Long arrivalPredictionId;
	private Long vehicleRunObservationId;
	private String providerVehicleId;
	private UUID lineId;
	private UUID boardingStopId;
	private Instant expectedAt;
	private Instant minExpectedAt;
	private Instant maxExpectedAt;
	private Integer remainingStops;
	private String source;
	private String confidence;
	private String movementStatus;
	private UUID currentStopId;
	private Integer currentSequence;
	private Instant observedAt;
	private Instant receivedAt;

	public static UpcomingArrivalResponse from(UpcomingArrivalEntity entity) {
		return UpcomingArrivalResponse.builder()
				.arrivalPredictionId(entity.getArrivalPredictionId())
				.vehicleRunObservationId(entity.getVehicleRunObservationId())
				.providerVehicleId(entity.getProviderVehicleId())
				.lineId(entity.getLineId())
				.boardingStopId(entity.getBoardingStopId())
				.expectedAt(entity.getExpectedAt())
				.minExpectedAt(entity.getMinExpectedAt())
				.maxExpectedAt(entity.getMaxExpectedAt())
				.remainingStops(entity.getRemainingStops())
				.source(entity.getSource())
				.confidence(entity.getConfidence())
				.movementStatus(entity.getMovementStatus())
				.currentStopId(entity.getCurrentStopId())
				.currentSequence(entity.getCurrentSequence())
				.observedAt(entity.getObservedAt())
				.receivedAt(entity.getReceivedAt())
				.build();
	}
}
