package com.realtimetransit.backend.journey.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;

class VehicleEtaEstimatorTest {

	private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");

	@Test
	void preservesProviderRangeWhenPresent() {
		VehicleEtaEstimator estimator = estimator();
		UpcomingArrivalEntity arrival = UpcomingArrivalEntity.builder()
				.expectedAt(NOW.plusSeconds(120))
				.minExpectedAt(NOW.plusSeconds(90))
				.maxExpectedAt(NOW.plusSeconds(180))
				.build();

		VehicleEtaEstimate estimate = estimator.estimate(arrival, NOW);

		assertThat(estimate.getMinExpectedAt()).isEqualTo(NOW.plusSeconds(90));
		assertThat(estimate.getExpectedAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(estimate.getMaxExpectedAt()).isEqualTo(NOW.plusSeconds(180));
	}

	@Test
	void suppliesFallbackRangeAndDoesNotPredictBeforeCalculationTime() {
		VehicleEtaEstimator estimator = estimator();
		UpcomingArrivalEntity arrival = UpcomingArrivalEntity.builder()
				.expectedAt(NOW.plusSeconds(20))
				.build();

		VehicleEtaEstimate estimate = estimator.estimate(arrival, NOW);

		assertThat(estimate.getMinExpectedAt()).isEqualTo(NOW);
		assertThat(estimate.getExpectedAt()).isEqualTo(NOW.plusSeconds(20));
		assertThat(estimate.getMaxExpectedAt()).isEqualTo(NOW.plusSeconds(65));
	}

	private VehicleEtaEstimator estimator() {
		JourneyProperties properties = new JourneyProperties();
		properties.setDefaultVehicleEtaUncertainty(Duration.ofSeconds(45));
		return new VehicleEtaEstimator(properties);
	}
}
