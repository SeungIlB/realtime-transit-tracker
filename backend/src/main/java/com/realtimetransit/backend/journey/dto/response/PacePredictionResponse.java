package com.realtimetransit.backend.journey.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

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
public class PacePredictionResponse {
	private String paceType;
	private BigDecimal speedMps;
	private BigDecimal distanceM;
	private Instant minExpectedAt;
	private Instant expectedAt;
	private Instant maxExpectedAt;
	private BigDecimal boardingProbability;
	private Boolean recommended;
}
