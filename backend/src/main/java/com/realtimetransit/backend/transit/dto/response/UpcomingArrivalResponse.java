package com.realtimetransit.backend.transit.dto.response;

import java.math.BigDecimal;
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
	private String serviceType;
	private String alightingStopStatus;
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
	private String currentStopName;
	private String destinationStopName;
	private Integer currentSequence;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private Instant observedAt;
	private Instant receivedAt;

	public static UpcomingArrivalResponse from(UpcomingArrivalEntity entity) {
		return UpcomingArrivalResponse.builder()
				.arrivalPredictionId(entity.getArrivalPredictionId())
				.vehicleRunObservationId(entity.getVehicleRunObservationId())
				.providerVehicleId(entity.getProviderVehicleId())
				.serviceType(entity.getServiceType())
				.alightingStopStatus(entity.getAlightingStopStatus())
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
				.currentStopName(entity.getCurrentStopName())
				.destinationStopName(entity.getDestinationStopName())
				.currentSequence(entity.getCurrentSequence())
				.latitude(entity.getLatitude())
				.longitude(entity.getLongitude())
				.observedAt(entity.getObservedAt())
				.receivedAt(entity.getReceivedAt())
				.build();
	}
}
