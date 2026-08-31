package com.realtimetransit.backend.transit.dto.response;

import java.util.UUID;

import com.realtimetransit.backend.transit.entity.TransitLineEntity;

public record TransitLineResponse(
		UUID id,
		String providerLineId,
		String publicName,
		String operatorName,
		String routeType) {

	public static TransitLineResponse from(TransitLineEntity line) {
		return new TransitLineResponse(
				line.id(),
				line.providerLineId(),
				line.publicName(),
				line.operatorName(),
				line.routeType());
	}
}
