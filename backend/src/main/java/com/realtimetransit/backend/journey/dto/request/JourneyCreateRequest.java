package com.realtimetransit.backend.journey.dto.request;

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
public class JourneyCreateRequest {
	private UUID anonymousKey;
	private UUID lineId;
	private UUID directionId;
	private UUID boardingStopId;
	private UUID alightingStopId;
	private BigDecimal targetProbability;
	private Instant desiredArrivalAt;
}
