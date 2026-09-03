package com.realtimetransit.backend.journey.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class BoardingRecommendationPolicyTest {

	private final BoardingRecommendationPolicy policy = new BoardingRecommendationPolicy();

	@Test
	void selectsLeastDemandingPaceMeetingTarget() {
		BoardingRecommendation recommendation = policy.recommend(List.of(
				prediction(PaceType.RUN, "0.99"),
				prediction(PaceType.WALK, "0.85"),
				prediction(PaceType.SLOW_WALK, "0.70"),
				prediction(PaceType.FAST_WALK, "0.95")), new BigDecimal("0.80"));

		assertThat(recommendation.getRecommendedPace()).isEqualTo(PaceType.WALK);
		assertThat(recommendation.getDecision()).isEqualTo(BoardingDecision.LEAVE_NOW);
	}

	@Test
	void returnsUnlikelyWhenNoPaceMeetsTarget() {
		BoardingRecommendation recommendation = policy.recommend(List.of(
				prediction(PaceType.RUN, "0.79")), new BigDecimal("0.80"));

		assertThat(recommendation.getRecommendedPace()).isNull();
		assertThat(recommendation.getDecision()).isEqualTo(BoardingDecision.UNLIKELY);
	}

	private PaceBoardingPrediction prediction(PaceType paceType, String probability) {
		return PaceBoardingPrediction.builder()
				.travelerEta(PaceEtaEstimate.builder().paceType(paceType).build())
				.probability(new BigDecimal(probability))
				.build();
	}
}
