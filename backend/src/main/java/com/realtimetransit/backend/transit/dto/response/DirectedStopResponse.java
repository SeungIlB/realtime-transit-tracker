package com.realtimetransit.backend.transit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

import com.realtimetransit.backend.transit.entity.DirectedStopEntity;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectedStopResponse {
	private UUID directionId;
	private String directionName;
	private UUID stopId;
	private String stopName;
	private Integer stopSequence;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private UUID nextStopId;
	private String displayDirection;

	public static DirectedStopResponse from(DirectedStopEntity entity) {
		return new DirectedStopResponse(
				entity.getDirectionId(),
				entity.getDirectionName(),
				entity.getStopId(),
				entity.getStopName(),
				entity.getStopSequence(),
				entity.getLatitude(),
				entity.getLongitude(),
				entity.getNextStopId(),
				entity.getDisplayDirection());
	}
}


