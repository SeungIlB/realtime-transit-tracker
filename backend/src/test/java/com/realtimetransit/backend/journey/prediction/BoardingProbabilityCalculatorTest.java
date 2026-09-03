package com.realtimetransit.backend.journey.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.realtimetransit.backend.journey.config.JourneyProperties;

class BoardingProbabilityCalculatorTest {

	private static final Instant BASE = Instant.parse("2026-09-03T01:00:00Z");

	@Test
	void returnsOneWhenTravelerAlwaysArrivesBeforeSafeBoardingTime() {
		assertThat(calculator(15).calculate(
				traveler(0, 10, 20), vehicle(60, 70, 80)))
				.isEqualByComparingTo("1.00000");
	}

	@Test
	void returnsZeroWhenTravelerAlwaysArrivesTooLate() {
		assertThat(calculator(15).calculate(
				traveler(80, 90, 100), vehicle(20, 30, 40)))
				.isEqualByComparingTo("0.00000");
	}

	@Test
	void givesHalfProbabilityForIdenticalSymmetricRangesWithoutBuffer() {
		assertThat(calculator(0).calculate(
				traveler(0, 30, 60), vehicle(0, 30, 60)))
				.isEqualByComparingTo("0.50000");
	}

	@Test
	void boardingBufferStrictlyReducesProbability() {
		var traveler = traveler(0, 30, 60);
		var vehicle = vehicle(30, 60, 90);

		assertThat(calculator(15).calculate(traveler, vehicle))
				.isLessThan(calculator(0).calculate(traveler, vehicle));
	}

	private BoardingProbabilityCalculator calculator(long bufferSeconds) {
		JourneyProperties properties = new JourneyProperties();
		properties.setBoardingBuffer(Duration.ofSeconds(bufferSeconds));
		return new BoardingProbabilityCalculator(properties);
	}

	private PaceEtaEstimate traveler(long min, long mode, long max) {
		return PaceEtaEstimate.builder()
				.minExpectedAt(BASE.plusSeconds(min))
				.expectedAt(BASE.plusSeconds(mode))
				.maxExpectedAt(BASE.plusSeconds(max))
				.build();
	}

	private VehicleEtaEstimate vehicle(long min, long mode, long max) {
		return VehicleEtaEstimate.builder()
				.minExpectedAt(BASE.plusSeconds(min))
				.expectedAt(BASE.plusSeconds(mode))
				.maxExpectedAt(BASE.plusSeconds(max))
				.build();
	}
}
