package com.realtimetransit.backend.provider.entity;

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
public class VehicleRunObservationEntity {
	private Long id;
	private Long rawObservationId;
	private UUID lineId;
	private UUID directionId;
	private UUID stopPatternId;
	private String providerVehicleId;
	private String providerRunId;
	private UUID destinationStopId;
	private UUID currentStopId;
	private Integer currentSequence;
	private String serviceType;
	private String movementStatus;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private BigDecimal speedKph;
	private BigDecimal bearingDegrees;
	private String positionSource;
	private Instant observedAt;
	private Instant receivedAt;
}

