package com.realtimetransit.backend.transit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopPatternEntity {
	private UUID id;
	private UUID lineId;
	private String providerPatternId;
	private String serviceType;
	private LocalDate validFrom;
	private LocalDate validTo;
	private Boolean active;
	private Instant createdAt;
	private Instant updatedAt;
}

