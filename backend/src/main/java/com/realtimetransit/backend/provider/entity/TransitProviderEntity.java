package com.realtimetransit.backend.provider.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitProviderEntity {
	private Long id;
	private String code;
	private String displayName;
	private String transportType;
	private Boolean active;
	private Instant createdAt;
	private Instant updatedAt;
}

