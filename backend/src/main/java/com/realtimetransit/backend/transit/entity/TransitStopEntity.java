package com.realtimetransit.backend.transit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitStopEntity {
	private UUID id;
	private Long providerId;
	private String providerStopId;
	private UUID parentStationId;
	private String publicName;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private Instant sourceUpdatedAt;
	private Instant createdAt;
	private Instant updatedAt;
}

