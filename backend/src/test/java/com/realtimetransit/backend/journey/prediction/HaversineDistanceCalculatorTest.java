package com.realtimetransit.backend.journey.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class HaversineDistanceCalculatorTest {

	private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

	@Test
	void returnsZeroForIdenticalCoordinates() {
		assertThat(calculator.calculateMeters(
				decimal("37.500000"), decimal("127.000000"),
				decimal("37.500000"), decimal("127.000000")))
				.isEqualByComparingTo("0.00");
	}

	@Test
	void calculatesKnownOneDegreeEquatorialDistance() {
		assertThat(calculator.calculateMeters(
				decimal("0"), decimal("0"), decimal("0"), decimal("1")))
				.isEqualByComparingTo("111195.08");
	}

	@Test
	void calculatesNearbyDistanceSymmetrically() {
		BigDecimal forward = calculator.calculateMeters(
				decimal("37.500000"), decimal("127.000000"),
				decimal("37.500500"), decimal("127.000500"));
		BigDecimal reverse = calculator.calculateMeters(
				decimal("37.500500"), decimal("127.000500"),
				decimal("37.500000"), decimal("127.000000"));

		assertThat(forward).isBetween(decimal("70.90"), decimal("71.10"));
		assertThat(reverse).isEqualByComparingTo(forward);
	}

	@Test
	void remainsFiniteForAntipodalCoordinates() {
		assertThat(calculator.calculateMeters(
				decimal("0"), decimal("0"), decimal("0"), decimal("180")))
				.isEqualByComparingTo("20015114.44");
	}

	private BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
