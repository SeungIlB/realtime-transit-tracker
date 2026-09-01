package com.realtimetransit.backend.transit.dto.request;

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
public class RouteDirectionSyncRequest {
	@NotBlank(message = "{validation.provider-direction-id.required}")
	private String providerDirectionId;
	private String originProviderStopId;
	private String terminalProviderStopId;
	private String representativeNextProviderStopId;
	private String displayName;
	@NotNull(message = "{validation.active.required}")
	private Boolean active;
	@Valid
	@NotNull(message = "{validation.directed-stops.required}")
	private List<DirectedStopSyncRequest> directedStops;
}
