package com.realtimetransit.backend.journey.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
public class VehicleBoardingPredictionResponse {
	private Long arrivalPredictionId;
	private Long vehicleRunObservationId;
	private String providerVehicleId;
	private String serviceType;
	private String alightingStopStatus;
	private String movementStatus;
	private String currentStopName;
	private String destinationStopName;
	private Integer currentSequence;
	private Integer remainingStops;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private Instant observedAt;
	private Instant vehicleMinExpectedAt;
	private Instant vehicleExpectedAt;
	private Instant vehicleMaxExpectedAt;
	private String decision;
	private String recommendedPace;
	private String confidence;
	private List<PacePredictionResponse> pacePredictions;
}
