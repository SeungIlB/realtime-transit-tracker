package com.realtimetransit.backend.provider.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.realtimetransit.backend.provider.entity.VehicleRunObservationEntity;

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
public class VehicleRunObservationResponse {
	private Long id;
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

	public static VehicleRunObservationResponse from(VehicleRunObservationEntity entity) {
		return VehicleRunObservationResponse.builder()
				.id(entity.getId())
				.lineId(entity.getLineId())
				.directionId(entity.getDirectionId())
				.stopPatternId(entity.getStopPatternId())
				.providerVehicleId(entity.getProviderVehicleId())
				.providerRunId(entity.getProviderRunId())
				.destinationStopId(entity.getDestinationStopId())
				.currentStopId(entity.getCurrentStopId())
				.currentSequence(entity.getCurrentSequence())
				.serviceType(entity.getServiceType())
				.movementStatus(entity.getMovementStatus())
				.latitude(entity.getLatitude())
				.longitude(entity.getLongitude())
				.speedKph(entity.getSpeedKph())
				.bearingDegrees(entity.getBearingDegrees())
				.positionSource(entity.getPositionSource())
				.observedAt(entity.getObservedAt())
				.receivedAt(entity.getReceivedAt())
				.build();
	}
}
