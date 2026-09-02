package com.realtimetransit.backend.provider.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
public class VehicleRunObservationSaveRequest {
	private Long rawObservationId;
	@NotNull(message = "{validation.line-id.required}")
	private UUID lineId;
	private UUID directionId;
	private UUID stopPatternId;
	@NotBlank(message = "{validation.provider-vehicle-id.required}")
	private String providerVehicleId;
	private String providerRunId;
	private UUID destinationStopId;
	private UUID currentStopId;
	@PositiveOrZero(message = "{validation.current-sequence.positive-or-zero}")
	private Integer currentSequence;
	@NotBlank(message = "{validation.service-type.required}")
	private String serviceType;
	@NotBlank(message = "{validation.movement-status.required}")
	private String movementStatus;
	private BigDecimal latitude;
	private BigDecimal longitude;
	@PositiveOrZero(message = "{validation.speed.positive-or-zero}")
	private BigDecimal speedKph;
	private BigDecimal bearingDegrees;
	@NotBlank(message = "{validation.position-source.required}")
	private String positionSource;
	@NotNull(message = "{validation.observed-at.required}")
	private Instant observedAt;
}
