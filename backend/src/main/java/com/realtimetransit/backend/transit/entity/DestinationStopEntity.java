package com.realtimetransit.backend.transit.entity;

import java.util.UUID;

public record DestinationStopEntity(
		UUID directionId,
		String directionName,
		UUID stopId,
		String stopName,
		Integer stopSequence) {
}
