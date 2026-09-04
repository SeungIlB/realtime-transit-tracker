package com.realtimetransit.backend.transit.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
public class UpcomingArrivalEntity {
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
	private Integer currentSequence;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private Instant observedAt;
	private Instant receivedAt;
}

