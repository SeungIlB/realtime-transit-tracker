package com.realtimetransit.backend.journey.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;

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
public class JourneyLocationResponse {
	private Long locationObservationId;
	private UUID journeyId;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private BigDecimal accuracyM;
	private BigDecimal speedMps;
	private Instant observedAt;
	private Instant receivedAt;
	private Instant expiresAt;

	public static JourneyLocationResponse from(TravelerLocationObservationEntity entity) {
		return JourneyLocationResponse.builder()
				.locationObservationId(entity.getId())
				.journeyId(entity.getJourneyId())
				.latitude(entity.getLatitude())
				.longitude(entity.getLongitude())
				.accuracyM(entity.getAccuracyM())
				.speedMps(entity.getSpeedMps())
				.observedAt(entity.getObservedAt())
				.receivedAt(entity.getReceivedAt())
				.expiresAt(entity.getExpiresAt())
				.build();
	}
}
