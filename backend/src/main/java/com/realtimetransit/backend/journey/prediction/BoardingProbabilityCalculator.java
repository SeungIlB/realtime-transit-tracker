package com.realtimetransit.backend.journey.prediction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.realtimetransit.backend.journey.config.JourneyProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(JourneyProperties.class)
public class BoardingProbabilityCalculator {

	private static final int INTEGRATION_INTERVALS = 2048;

	private final JourneyProperties properties;

	public BigDecimal calculate(PaceEtaEstimate traveler, VehicleEtaEstimate vehicle) {
		double travelerMin = seconds(traveler.getMinExpectedAt());
		double travelerMode = seconds(traveler.getExpectedAt());
		double travelerMax = seconds(traveler.getMaxExpectedAt());
		double vehicleMin = seconds(vehicle.getMinExpectedAt());
		double vehicleMode = seconds(vehicle.getExpectedAt());
		double vehicleMax = seconds(vehicle.getMaxExpectedAt());
		double boardingBufferSeconds = properties.getBoardingBuffer().toMillis() / 1000.0;

		double probability;
		if (vehicleMax == vehicleMin) {
			probability = triangularCdf(
					vehicleMin - boardingBufferSeconds,
					travelerMin, travelerMode, travelerMax);
		} else {
			probability = integrateSuccessProbability(
					travelerMin, travelerMode, travelerMax,
					vehicleMin, vehicleMode, vehicleMax,
					boardingBufferSeconds);
		}

		return BigDecimal.valueOf(clamp(probability))
				.setScale(5, RoundingMode.HALF_UP);
	}

	private double integrateSuccessProbability(
			double travelerMin,
			double travelerMode,
			double travelerMax,
			double vehicleMin,
			double vehicleMode,
			double vehicleMax,
			double boardingBufferSeconds) {
		double intervalWidth = (vehicleMax - vehicleMin) / INTEGRATION_INTERVALS;
		double weightedSum = integrand(
				vehicleMin, travelerMin, travelerMode, travelerMax,
				vehicleMin, vehicleMode, vehicleMax, boardingBufferSeconds)
				+ integrand(
						vehicleMax, travelerMin, travelerMode, travelerMax,
						vehicleMin, vehicleMode, vehicleMax, boardingBufferSeconds);

		for (int index = 1; index < INTEGRATION_INTERVALS; index++) {
			double vehicleArrival = vehicleMin + intervalWidth * index;
			double weight = index % 2 == 0 ? 2.0 : 4.0;
			weightedSum += weight * integrand(
					vehicleArrival, travelerMin, travelerMode, travelerMax,
					vehicleMin, vehicleMode, vehicleMax, boardingBufferSeconds);
		}
		return weightedSum * intervalWidth / 3.0;
	}

	private double integrand(
			double vehicleArrival,
			double travelerMin,
			double travelerMode,
			double travelerMax,
			double vehicleMin,
			double vehicleMode,
			double vehicleMax,
			double boardingBufferSeconds) {
		double safeBoardingTime = vehicleArrival - boardingBufferSeconds;
		return triangularCdf(safeBoardingTime, travelerMin, travelerMode, travelerMax)
				* triangularDensity(vehicleArrival, vehicleMin, vehicleMode, vehicleMax);
	}

	private double triangularCdf(double value, double min, double mode, double max) {
		if (max <= min) {
			return value < min ? 0.0 : 1.0;
		}
		if (value <= min) {
			return 0.0;
		}
		if (value >= max) {
			return 1.0;
		}
		if (mode <= min) {
			double remainingRatio = (max - value) / (max - min);
			return 1.0 - remainingRatio * remainingRatio;
		}
		if (mode >= max) {
			double elapsedRatio = (value - min) / (max - min);
			return elapsedRatio * elapsedRatio;
		}
		if (value <= mode) {
			return square(value - min) / ((max - min) * (mode - min));
		}
		return 1.0 - square(max - value) / ((max - min) * (max - mode));
	}

	private double triangularDensity(double value, double min, double mode, double max) {
		if (max <= min || value < min || value > max) {
			return 0.0;
		}
		if (mode <= min) {
			return 2.0 * (max - value) / square(max - min);
		}
		if (mode >= max) {
			return 2.0 * (value - min) / square(max - min);
		}
		if (value <= mode) {
			return 2.0 * (value - min) / ((max - min) * (mode - min));
		}
		return 2.0 * (max - value) / ((max - min) * (max - mode));
	}

	private double seconds(Instant instant) {
		return instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;
	}

	private double square(double value) {
		return value * value;
	}

	private double clamp(double probability) {
		return Math.max(0.0, Math.min(1.0, probability));
	}
}
