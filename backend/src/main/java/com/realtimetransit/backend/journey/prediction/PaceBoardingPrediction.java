package com.realtimetransit.backend.journey.prediction;

import java.math.BigDecimal;

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
public class PaceBoardingPrediction {
	private PaceEtaEstimate travelerEta;
	private BigDecimal probability;
}
