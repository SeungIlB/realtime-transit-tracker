package com.realtimetransit.backend.transit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectedStopAssignmentEntity {
	private UUID id;
	private UUID lineId;
	private UUID directionId;
	private UUID stopId;
	private Integer stopSequence;
	private UUID nextStopId;
	private String platformId;
	private String displayDirection;
	private String segmentId;
}

