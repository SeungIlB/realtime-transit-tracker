package com.realtimetransit.backend.journey.dto.request;

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
public class RouteDecisionRequest {
	private BigDecimal latitude;
	private BigDecimal longitude;
	private BigDecimal accuracyM;
	private Instant observedAt;
	private BigDecimal targetProbability;
	private List<RouteTransitLegRequest> legs;
}
