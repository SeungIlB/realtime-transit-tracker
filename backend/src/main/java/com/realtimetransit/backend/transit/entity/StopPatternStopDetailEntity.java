package com.realtimetransit.backend.transit.entity;

import java.util.UUID;

public record StopPatternStopDetailEntity(
		UUID stopPatternId,
		Integer stopSequence,
		UUID stopId,
		String stopName,
		Boolean pickupAllowed,
		Boolean dropoffAllowed) {
}
