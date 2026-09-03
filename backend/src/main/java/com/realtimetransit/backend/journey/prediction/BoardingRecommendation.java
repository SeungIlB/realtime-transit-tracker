package com.realtimetransit.backend.journey.prediction;

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
public class BoardingRecommendation {
	private BoardingDecision decision;
	private PaceType recommendedPace;
}
