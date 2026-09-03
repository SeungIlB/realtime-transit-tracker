package com.realtimetransit.backend.journey.dto.response;

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
	private Instant vehicleMinExpectedAt;
	private Instant vehicleExpectedAt;
	private Instant vehicleMaxExpectedAt;
	private String decision;
	private String recommendedPace;
	private String confidence;
	private List<PacePredictionResponse> pacePredictions;
}
