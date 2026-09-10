package com.realtimetransit.backend.journey.prediction;

import java.time.Instant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(JourneyProperties.class)
public class VehicleEtaEstimator {

	private final JourneyProperties properties;

	public VehicleEtaEstimate estimate(UpcomingArrivalEntity arrival, Instant calculatedAt) {
		Instant expectedAt = expectedAt(arrival, calculatedAt);
		Instant minExpectedAt = arrival.getMinExpectedAt() != null
				? arrival.getMinExpectedAt()
				: expectedAt.minus(properties.getDefaultVehicleEtaUncertainty());
		Instant maxExpectedAt = arrival.getMaxExpectedAt() != null
				? arrival.getMaxExpectedAt()
				: expectedAt.plus(properties.getDefaultVehicleEtaUncertainty());

		minExpectedAt = latest(calculatedAt, earliest(minExpectedAt, expectedAt));
		maxExpectedAt = latest(maxExpectedAt, expectedAt);

		return VehicleEtaEstimate.builder()
				.sourceArrival(arrival)
				.minExpectedAt(minExpectedAt)
				.expectedAt(expectedAt)
				.maxExpectedAt(maxExpectedAt)
				.build();
	}

	private Instant expectedAt(UpcomingArrivalEntity arrival, Instant calculatedAt) {
		Instant providerExpectedAt = arrival.getExpectedAt();
		if (!providerExpectedAt.isBefore(calculatedAt)) {
			return providerExpectedAt;
		}
		Integer remainingStops = arrival.getRemainingStops();
		if (remainingStops != null && remainingStops > 0) {
			return calculatedAt.plus(properties.getDefaultVehicleStopTravelTime().multipliedBy(remainingStops));
		}
		return calculatedAt;
	}

	private Instant earliest(Instant first, Instant second) {
		return first.isBefore(second) ? first : second;
	}

	private Instant latest(Instant first, Instant second) {
		return first.isAfter(second) ? first : second;
	}
}
