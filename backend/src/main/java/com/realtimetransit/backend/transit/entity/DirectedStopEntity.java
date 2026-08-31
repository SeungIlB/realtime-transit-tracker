package com.realtimetransit.backend.transit.entity;

import java.math.BigDecimal;
import java.util.UUID;

public record DirectedStopEntity(
		UUID directionId,
		String directionName,
		UUID stopId,
		String stopName,
		Integer stopSequence,
		BigDecimal latitude,
		BigDecimal longitude,
		UUID nextStopId,
		String displayDirection) {
}
