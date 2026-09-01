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
public class RouteDirectionEntity {
	private UUID id;
	private UUID lineId;
	private String providerDirectionId;
	private UUID originStopId;
	private UUID terminalStopId;
	private UUID representativeNextStopId;
	private String displayName;
	private Boolean active;
	private Instant createdAt;
	private Instant updatedAt;
}

