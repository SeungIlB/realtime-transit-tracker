package com.realtimetransit.backend.transit.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.realtimetransit.backend.transit.entity.DirectedStopEntity;

public record DirectedStopResponse(
		UUID directionId,
		String directionName,
		UUID stopId,
		String stopName,
		Integer stopSequence,
		BigDecimal latitude,
		BigDecimal longitude,
		UUID nextStopId,
		String displayDirection) {

	public static DirectedStopResponse from(DirectedStopEntity entity) {
		return new DirectedStopResponse(
				entity.directionId(),
				entity.directionName(),
				entity.stopId(),
				entity.stopName(),
				entity.stopSequence(),
				entity.latitude(),
				entity.longitude(),
				entity.nextStopId(),
				entity.displayDirection());
	}
}
