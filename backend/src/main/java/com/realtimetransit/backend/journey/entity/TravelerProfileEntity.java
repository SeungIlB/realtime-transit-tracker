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
public class TravelerProfileEntity {
	private UUID id;
	private UUID anonymousKey;
	private BigDecimal slowWalkSpeedMps;
	private BigDecimal walkSpeedMps;
	private BigDecimal fastWalkSpeedMps;
	private BigDecimal runSpeedMps;
	private Integer sampleCount;
	private Instant createdAt;
	private Instant updatedAt;
}
