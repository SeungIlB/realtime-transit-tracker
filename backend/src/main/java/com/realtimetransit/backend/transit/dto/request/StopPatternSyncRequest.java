package com.realtimetransit.backend.transit.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class StopPatternSyncRequest {
	@NotBlank(message = "{validation.provider-pattern-id.required}")
	private String providerPatternId;
	private String serviceType;
	@NotNull(message = "{validation.valid-from.required}")
	private LocalDate validFrom;
	private LocalDate validTo;
	@NotNull(message = "{validation.active.required}")
	private Boolean active;
	@Valid
	@NotNull(message = "{validation.pattern-stops.required}")
	private List<StopPatternStopSyncRequest> patternStops;
}
