package com.realtimetransit.backend.transit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectedStopEntity {
	private UUID directionId;
	private String directionName;
	private UUID stopId;
	private String stopName;
	private Integer stopSequence;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private UUID nextStopId;
	private String displayDirection;
}

