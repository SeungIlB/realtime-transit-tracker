package com.realtimetransit.backend.journey.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;

class PredictionConfidenceEvaluatorTest {

	private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");

	@Test
	void usesWorstOfLocationAccuracyFreshnessAndProviderConfidence() {
		PredictionConfidenceEvaluator evaluator = evaluator();
		TravelerLocationObservationEntity location = TravelerLocationObservationEntity.builder()
				.accuracyM(new BigDecimal("35"))
				.observedAt(NOW.minusSeconds(10))
				.build();
		UpcomingArrivalEntity arrival = UpcomingArrivalEntity.builder()
				.confidence("HIGH")
				.observedAt(NOW.minusSeconds(10))
				.build();

		assertThat(evaluator.evaluate(location, arrival, NOW))
				.isEqualTo(PredictionConfidence.MEDIUM);
	}

	@Test
	void rejectsLocationOlderThanUsableThreshold() {
		PredictionConfidenceEvaluator evaluator = evaluator();
		TravelerLocationObservationEntity location = TravelerLocationObservationEntity.builder()
				.accuracyM(new BigDecimal("10"))
				.observedAt(NOW.minusSeconds(121))
				.build();

		assertThat(evaluator.isLocationUsable(location, NOW)).isFalse();
	}

	private PredictionConfidenceEvaluator evaluator() {
		JourneyProperties properties = new JourneyProperties();
		properties.setFreshLocationThreshold(Duration.ofSeconds(30));
		properties.setUsableLocationThreshold(Duration.ofMinutes(2));
		properties.setHighAccuracyThresholdM(new BigDecimal("20"));
		properties.setMediumAccuracyThresholdM(new BigDecimal("50"));
		return new PredictionConfidenceEvaluator(properties);
	}
}
