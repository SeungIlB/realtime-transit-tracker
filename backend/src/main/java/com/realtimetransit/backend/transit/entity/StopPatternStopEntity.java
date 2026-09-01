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
public class StopPatternStopEntity {
	private UUID stopPatternId;
	private Integer stopSequence;
	private UUID stopId;
	private Boolean pickupAllowed;
	private Boolean dropoffAllowed;
}

