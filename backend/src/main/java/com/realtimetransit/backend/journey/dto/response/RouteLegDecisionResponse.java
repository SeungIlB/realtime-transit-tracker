package com.realtimetransit.backend.journey.dto.response;

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
public class RouteLegDecisionResponse {
	private int legIndex;
	private UUID lineId;
	private String lineName;
	private String boardingStopName;
	private String alightingStopName;
	private String providerVehicleId;
	private String serviceType;
	private BigDecimal connectionProbability;
	private BigDecimal cumulativeProbability;
	private Instant readyExpectedAt;
	private Instant vehicleExpectedAt;
	private Instant alightingExpectedAt;
	private String confidence;
}
