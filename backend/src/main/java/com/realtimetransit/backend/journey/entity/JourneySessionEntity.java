package com.realtimetransit.backend.journey.entity;

import java.math.BigDecimal;
import java.time.Instant;
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
public class JourneySessionEntity {
	private UUID id;
	private UUID travelerProfileId;
	private UUID lineId;
	private UUID directionId;
	private UUID boardingStopId;
	private UUID alightingStopId;
	private BigDecimal targetProbability;
	private Instant desiredArrivalAt;
	private String status;
	private Instant expiresAt;
	private Instant createdAt;
	private Instant updatedAt;
}
