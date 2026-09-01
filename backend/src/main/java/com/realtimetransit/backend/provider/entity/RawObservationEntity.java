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
public class RawObservationEntity {
	private Long id;
	private Long providerId;
	private String endpoint;
	private String requestKey;
	private Instant receivedAt;
	private Instant providerObservedAt;
	private Integer responseStatus;
	private String payload;
	private Instant expiresAt;
}

