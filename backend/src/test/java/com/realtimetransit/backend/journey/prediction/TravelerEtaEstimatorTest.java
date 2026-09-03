package com.realtimetransit.backend.journey.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;

class TravelerEtaEstimatorTest {

	private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");

	@Test
	void estimatesOrderedRangesForEveryPace() {
		JourneyProperties properties = new JourneyProperties();
		properties.setWalkingDetourFactor(decimal("1.20"));
		properties.setDeparturePreparationTime(Duration.ofSeconds(10));
		TravelerEtaEstimator estimator = new TravelerEtaEstimator(properties);
		TravelerProfileEntity profile = TravelerProfileEntity.builder()
				.slowWalkSpeedMps(decimal("0.90"))
				.walkSpeedMps(decimal("1.30"))
				.fastWalkSpeedMps(decimal("1.70"))
				.runSpeedMps(decimal("2.50"))
				.build();

		var estimates = estimator.estimateAll(decimal("100"), decimal("10"), profile, NOW);

		assertThat(estimates).extracting(PaceEtaEstimate::getPaceType)
				.containsExactly(PaceType.SLOW_WALK, PaceType.WALK, PaceType.FAST_WALK, PaceType.RUN);
		assertThat(estimates).allSatisfy(estimate -> {
			assertThat(estimate.getDistanceM()).isEqualByComparingTo("120.00");
			assertThat(estimate.getMinExpectedAt()).isBeforeOrEqualTo(estimate.getExpectedAt());
			assertThat(estimate.getExpectedAt()).isBeforeOrEqualTo(estimate.getMaxExpectedAt());
		});
		assertThat(estimates.get(3).getExpectedAt()).isBefore(estimates.get(0).getExpectedAt());
	}

	@Test
	void neverProducesNegativeMinimumDistanceWhenGpsAccuracyExceedsDistance() {
		JourneyProperties properties = new JourneyProperties();
		properties.setWalkingDetourFactor(decimal("1.20"));
		properties.setDeparturePreparationTime(Duration.ofSeconds(10));
		TravelerEtaEstimator estimator = new TravelerEtaEstimator(properties);
		TravelerProfileEntity profile = TravelerProfileEntity.builder()
				.slowWalkSpeedMps(decimal("1.00"))
				.walkSpeedMps(decimal("1.00"))
				.fastWalkSpeedMps(decimal("1.00"))
				.runSpeedMps(decimal("1.00"))
				.build();

		assertThat(estimator.estimateAll(decimal("5"), decimal("20"), profile, NOW).get(0)
				.getMinExpectedAt()).isEqualTo(NOW);
	}

	private BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
