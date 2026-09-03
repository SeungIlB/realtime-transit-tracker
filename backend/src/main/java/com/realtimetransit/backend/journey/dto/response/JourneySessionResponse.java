package com.realtimetransit.backend.journey.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.realtimetransit.backend.journey.entity.JourneySessionEntity;

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
public class JourneySessionResponse {
	private UUID journeyId;
	private UUID lineId;
	private UUID directionId;
	private UUID boardingStopId;
	private UUID alightingStopId;
	private BigDecimal targetProbability;
	private Instant desiredArrivalAt;
	private String status;
	private Instant expiresAt;
	private Instant createdAt;

	public static JourneySessionResponse from(JourneySessionEntity entity) {
		return JourneySessionResponse.builder()
				.journeyId(entity.getId())
				.lineId(entity.getLineId())
				.directionId(entity.getDirectionId())
				.boardingStopId(entity.getBoardingStopId())
				.alightingStopId(entity.getAlightingStopId())
				.targetProbability(entity.getTargetProbability())
				.desiredArrivalAt(entity.getDesiredArrivalAt())
				.status(entity.getStatus())
				.expiresAt(entity.getExpiresAt())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}
