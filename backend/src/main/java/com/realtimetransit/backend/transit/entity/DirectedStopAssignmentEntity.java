package com.realtimetransit.backend.transit.entity;

import java.util.UUID;

public record DirectedStopAssignmentEntity(
		UUID id,
		UUID lineId,
		UUID directionId,
		UUID stopId,
		Integer stopSequence,
		UUID nextStopId,
		String platformId,
		String displayDirection,
		String segmentId) {
}
