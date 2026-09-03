package com.realtimetransit.backend.journey.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
public class BoardingDecisionResponse {
	private UUID journeyId;
	private String decision;
	private String recommendedVehicleId;
	private String recommendedPace;
	private BigDecimal targetProbability;
	private String confidence;
	private List<String> reasons;
	private List<VehicleBoardingPredictionResponse> vehicles;
	private Instant calculatedAt;
	private Instant nextRefreshAt;
}
