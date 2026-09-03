package com.realtimetransit.backend.journey.entity;

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
public class BoardingPredictionSnapshotEntity {
	private Long id;
	private UUID calculationId;
	private UUID journeyId;
	private Long locationObservationId;
	private Long vehicleRunObservationId;
	private Long arrivalPredictionObservationId;
	private String providerVehicleId;
	private String paceType;
	private BigDecimal paceSpeedMps;
	private BigDecimal distanceM;
	private Instant userExpectedAt;
	private Instant userMinExpectedAt;
	private Instant userMaxExpectedAt;
	private Instant vehicleExpectedAt;
	private Instant vehicleMinExpectedAt;
	private Instant vehicleMaxExpectedAt;
	private BigDecimal boardingProbability;
	private String decision;
	private Boolean recommended;
	private String confidence;
	private String modelVersion;
	private String factorsJson;
	private Instant calculatedAt;
	private Instant expiresAt;
}
