package com.realtimetransit.backend.transit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
public class DirectedStopSyncRequest {
	@NotBlank(message = "{validation.provider-stop-id.required}")
	private String providerStopId;
	@NotNull(message = "{validation.stop-sequence.required}")
	@Positive(message = "{validation.stop-sequence.positive}")
	private Integer stopSequence;
	private String nextProviderStopId;
	private String platformId;
	private String displayDirection;
	private String segmentId;
}
