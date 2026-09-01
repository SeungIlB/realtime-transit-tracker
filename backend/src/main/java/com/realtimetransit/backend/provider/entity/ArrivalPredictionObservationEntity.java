package com.realtimetransit.backend.provider.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArrivalPredictionObservationEntity {
	private Long id;
	private Long rawObservationId;
	private Long vehicleRunObservationId;
	private UUID boardingStopId;
	private Instant expectedAt;
	private Instant minExpectedAt;
	private Instant maxExpectedAt;
	private Integer remainingStops;
	private String source;
	private String confidence;
	private Instant observedAt;
	private Instant receivedAt;
}

