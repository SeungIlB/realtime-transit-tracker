package com.realtimetransit.backend.transit.entity;

import java.util.UUID;

public record StopPatternStopEntity(
		UUID stopPatternId,
		Integer stopSequence,
		UUID stopId,
		Boolean pickupAllowed,
		Boolean dropoffAllowed) {
}
