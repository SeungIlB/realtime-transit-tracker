package com.realtimetransit.backend.provider.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

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
public class ExternalArrival {
	private String providerVehicleId;
	private String providerRunId;
	private String providerDirectionId;
	private String serviceType;
	private String destinationProviderStopId;
	private String currentProviderStopId;
	private Integer currentSequence;
	private Instant expectedAt;
	private Integer remainingStops;
	private String movementStatus;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private BigDecimal speedKph;
	private BigDecimal bearingDegrees;
	private String positionSource;
	private String confidence;
	private Instant observedAt;
}
