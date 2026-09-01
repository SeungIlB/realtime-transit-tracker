package com.realtimetransit.backend.transit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import com.realtimetransit.backend.transit.entity.TransitLineEntity;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitLineResponse {
	private UUID id;
	private String providerLineId;
	private String publicName;
	private String operatorName;
	private String routeType;

	public static TransitLineResponse from(TransitLineEntity line) {
		return new TransitLineResponse(
				line.getId(),
				line.getProviderLineId(),
				line.getPublicName(),
				line.getOperatorName(),
				line.getRouteType());
	}
}


