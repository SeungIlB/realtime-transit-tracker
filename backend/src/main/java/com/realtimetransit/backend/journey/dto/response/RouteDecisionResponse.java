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
public class RouteDecisionResponse {
	private String decision;
	private String recommendedPace;
	private BigDecimal overallProbability;
	private BigDecimal targetProbability;
	private String confidence;
	private List<String> reasons;
	private List<RouteLegDecisionResponse> legs;
	private Instant expectedArrivalAt;
	private Instant calculatedAt;
	private Instant nextRefreshAt;
}
