package com.realtimetransit.backend.provider.dto.request;

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
public class ArrivalPredictionObservationSaveRequest {
	private Long rawObservationId;
	private Long vehicleRunObservationId;
	@NotNull(message = "{validation.boarding-stop-id.required}")
	private UUID boardingStopId;
	private UUID requestedAlightingStopId;
	private boolean alightingStopConfirmed;
	@NotBlank(message = "{validation.alighting-stop-status.required}")
	private String alightingStopStatus;
	private Instant expectedAt;
	private Instant minExpectedAt;
	private Instant maxExpectedAt;
	@PositiveOrZero(message = "{validation.remaining-stops.positive-or-zero}")
	private Integer remainingStops;
	@NotBlank(message = "{validation.arrival-source.required}")
	private String source;
	@NotBlank(message = "{validation.confidence.required}")
	private String confidence;
	@NotNull(message = "{validation.observed-at.required}")
	private Instant observedAt;
}
