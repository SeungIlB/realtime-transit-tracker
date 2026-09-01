package com.realtimetransit.backend.transit.entity;

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
public class TransitLineEntity {
	private UUID id;
	private Long providerId;
	private String providerLineId;
	private String publicName;
	private String operatorName;
	private String routeType;
	private Boolean active;
	private Instant sourceUpdatedAt;
	private Instant createdAt;
	private Instant updatedAt;
}

