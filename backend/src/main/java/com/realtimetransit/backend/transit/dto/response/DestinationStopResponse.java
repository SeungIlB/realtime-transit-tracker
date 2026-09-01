package com.realtimetransit.backend.transit.dto.response;

import java.util.UUID;

import com.realtimetransit.backend.transit.entity.DestinationStopEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DestinationStopResponse {
	private UUID directionId;
	private String directionName;
	private UUID stopId;
	private String stopName;
	private Integer stopSequence;

	public static DestinationStopResponse from(DestinationStopEntity entity) {
		return DestinationStopResponse.builder()
				.directionId(entity.getDirectionId())
				.directionName(entity.getDirectionName())
				.stopId(entity.getStopId())
				.stopName(entity.getStopName())
				.stopSequence(entity.getStopSequence())
				.build();
	}
}
