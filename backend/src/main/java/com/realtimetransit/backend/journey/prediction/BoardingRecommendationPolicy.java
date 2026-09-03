package com.realtimetransit.backend.journey.prediction;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class BoardingRecommendationPolicy {

	public BoardingRecommendation recommend(
			List<PaceBoardingPrediction> predictions,
			BigDecimal targetProbability) {
		return predictions.stream()
				.filter(prediction -> prediction.getProbability().compareTo(targetProbability) >= 0)
				.min(Comparator.comparing(prediction -> prediction.getTravelerEta().getPaceType()))
				.map(this::successfulRecommendation)
				.orElseGet(() -> BoardingRecommendation.builder()
						.decision(BoardingDecision.UNLIKELY)
						.build());
	}

	private BoardingRecommendation successfulRecommendation(PaceBoardingPrediction prediction) {
		PaceType pace = prediction.getTravelerEta().getPaceType();
		BoardingDecision decision = switch (pace) {
			case SLOW_WALK -> BoardingDecision.COMFORTABLE;
			case WALK -> BoardingDecision.LEAVE_NOW;
			case FAST_WALK, RUN -> BoardingDecision.HURRY;
		};
		return BoardingRecommendation.builder()
				.decision(decision)
				.recommendedPace(pace)
				.build();
	}
}
