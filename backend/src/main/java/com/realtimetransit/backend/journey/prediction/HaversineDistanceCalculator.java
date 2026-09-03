package com.realtimetransit.backend.journey.prediction;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

@Component
public class HaversineDistanceCalculator {

	private static final double EARTH_RADIUS_M = 6_371_008.8;

	public BigDecimal calculateMeters(
			BigDecimal originLatitude,
			BigDecimal originLongitude,
			BigDecimal destinationLatitude,
			BigDecimal destinationLongitude) {
		double originLatitudeRadians = Math.toRadians(originLatitude.doubleValue());
		double destinationLatitudeRadians = Math.toRadians(destinationLatitude.doubleValue());
		double latitudeDelta = destinationLatitudeRadians - originLatitudeRadians;
		double longitudeDelta = Math.toRadians(
				destinationLongitude.subtract(originLongitude).doubleValue());

		double latitudeHaversine = Math.sin(latitudeDelta / 2.0);
		double longitudeHaversine = Math.sin(longitudeDelta / 2.0);
		double haversine = latitudeHaversine * latitudeHaversine
				+ Math.cos(originLatitudeRadians)
				* Math.cos(destinationLatitudeRadians)
				* longitudeHaversine
				* longitudeHaversine;

		// Floating-point rounding can move the theoretical [0, 1] value slightly
		// outside its domain, especially around identical or antipodal points.
		double boundedHaversine = Math.max(0.0, Math.min(1.0, haversine));
		double centralAngle = 2.0 * Math.atan2(
				Math.sqrt(boundedHaversine),
				Math.sqrt(1.0 - boundedHaversine));

		return BigDecimal.valueOf(EARTH_RADIUS_M * centralAngle)
				.setScale(2, RoundingMode.HALF_UP);
	}
}
