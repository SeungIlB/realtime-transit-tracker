package com.realtimetransit.backend.journey.prediction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(JourneyProperties.class)
public class TravelerEtaEstimator {

	private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");

	private final JourneyProperties properties;

	public List<PaceEtaEstimate> estimateAll(
			BigDecimal straightDistanceM,
			BigDecimal gpsAccuracyM,
			TravelerProfileEntity profile,
			Instant calculatedAt) {
		BigDecimal detourFactor = properties.getWalkingDetourFactor();
		BigDecimal minDistance = straightDistanceM.subtract(gpsAccuracyM)
				.max(BigDecimal.ZERO)
				.multiply(detourFactor);
		BigDecimal expectedDistance = straightDistanceM.multiply(detourFactor);
		BigDecimal maxDistance = straightDistanceM.add(gpsAccuracyM).multiply(detourFactor);
		Duration preparation = properties.getDeparturePreparationTime();

		return List.of(
				estimate(PaceType.SLOW_WALK, profile.getSlowWalkSpeedMps(),
						minDistance, expectedDistance, maxDistance, calculatedAt, preparation),
				estimate(PaceType.WALK, profile.getWalkSpeedMps(),
						minDistance, expectedDistance, maxDistance, calculatedAt, preparation),
				estimate(PaceType.FAST_WALK, profile.getFastWalkSpeedMps(),
						minDistance, expectedDistance, maxDistance, calculatedAt, preparation),
				estimate(PaceType.RUN, profile.getRunSpeedMps(),
						minDistance, expectedDistance, maxDistance, calculatedAt, preparation));
	}

	private PaceEtaEstimate estimate(
			PaceType paceType,
			BigDecimal speedMps,
			BigDecimal minDistance,
			BigDecimal expectedDistance,
			BigDecimal maxDistance,
			Instant calculatedAt,
			Duration preparation) {
		return PaceEtaEstimate.builder()
				.paceType(paceType)
				.speedMps(speedMps)
				.distanceM(expectedDistance.setScale(2, RoundingMode.HALF_UP))
				.minExpectedAt(addTravelTime(calculatedAt, minDistance, speedMps, Duration.ZERO))
				.expectedAt(addTravelTime(calculatedAt, expectedDistance, speedMps, preparation))
				.maxExpectedAt(addTravelTime(
						calculatedAt, maxDistance, speedMps, preparation.multipliedBy(2)))
				.build();
	}

	private Instant addTravelTime(
			Instant base,
			BigDecimal distanceM,
			BigDecimal speedMps,
			Duration preparation) {
		long travelMillis = distanceM.divide(speedMps, 9, RoundingMode.HALF_UP)
				.multiply(ONE_THOUSAND)
				.setScale(0, RoundingMode.CEILING)
				.longValueExact();
		return base.plusMillis(travelMillis).plus(preparation);
	}
}
