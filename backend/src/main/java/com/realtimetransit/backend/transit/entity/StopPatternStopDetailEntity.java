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
public class StopPatternStopDetailEntity {
	private UUID stopPatternId;
	private Integer stopSequence;
	private UUID stopId;
	private String stopName;
	private Boolean pickupAllowed;
	private Boolean dropoffAllowed;
}

