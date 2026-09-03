package com.realtimetransit.backend.journey.entity;

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
public class JourneyStopValidationEntity {
	private UUID lineId;
	private UUID directionId;
	private UUID boardingStopId;
	private Integer boardingSequence;
	private UUID alightingStopId;
	private Integer alightingSequence;
}
