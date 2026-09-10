package com.realtimetransit.backend.journey.dto.request;

import java.util.UUID;

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
public class RouteTransitLegRequest {
	private UUID lineId;
	private UUID directionId;
	private UUID boardingStopId;
	private UUID alightingStopId;
	private String lineName;
	private String boardingStopName;
	private String alightingStopName;
	private Integer sectionTimeMinutes;
	private Integer transferWalkTimeMinutes;
}
