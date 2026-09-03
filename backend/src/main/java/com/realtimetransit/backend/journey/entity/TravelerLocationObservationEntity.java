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
public class TravelerLocationObservationEntity {
	private Long id;
	private UUID journeyId;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private BigDecimal accuracyM;
	private BigDecimal speedMps;
	private Instant observedAt;
	private Instant receivedAt;
	private Instant expiresAt;
}
