package com.realtimetransit.backend.provider.client;

import java.math.BigDecimal;

import com.realtimetransit.backend.provider.client.dto.ExternalRouteReference;

public final class TransitRouteProximity {

	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	private TransitRouteProximity() {
	}

	public static boolean servesLocation(
			ExternalRouteReference route,
			BigDecimal latitude,
			BigDecimal longitude,
			double radiusMeters) {
		return route.getDirections().stream()
				.flatMap(direction -> direction.getStops().stream())
				.filter(stop -> stop.getLatitude() != null && stop.getLongitude() != null)
				.anyMatch(stop -> distanceMeters(
						latitude, longitude, stop.getLatitude(), stop.getLongitude()) <= radiusMeters);
	}

	private static double distanceMeters(
			BigDecimal originLatitude,
			BigDecimal originLongitude,
			BigDecimal destinationLatitude,
			BigDecimal destinationLongitude) {
		double originLatitudeRadians = Math.toRadians(originLatitude.doubleValue());
		double destinationLatitudeRadians = Math.toRadians(destinationLatitude.doubleValue());
		double latitudeDelta = destinationLatitudeRadians - originLatitudeRadians;
		double longitudeDelta = Math.toRadians(destinationLongitude.subtract(originLongitude).doubleValue());
		double latitudeHaversine = Math.sin(latitudeDelta / 2.0);
		double longitudeHaversine = Math.sin(longitudeDelta / 2.0);
		double haversine = latitudeHaversine * latitudeHaversine
				+ Math.cos(originLatitudeRadians) * Math.cos(destinationLatitudeRadians)
				* longitudeHaversine * longitudeHaversine;
		double boundedHaversine = Math.max(0.0, Math.min(1.0, haversine));
		return EARTH_RADIUS_METERS * 2.0 * Math.atan2(
				Math.sqrt(boundedHaversine), Math.sqrt(1.0 - boundedHaversine));
	}
}
