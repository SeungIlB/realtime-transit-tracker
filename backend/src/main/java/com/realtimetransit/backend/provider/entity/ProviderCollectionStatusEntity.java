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
public class ProviderCollectionStatusEntity {
	private Long providerId;
	private String providerCode;
	private String endpoint;
	private Long totalCalls;
	private Long successfulCalls;
	private Long failedCalls;
	private Instant latestReceivedAt;
}

