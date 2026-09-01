package com.realtimetransit.backend.transit.dto.request;

import java.time.Instant;
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
public class TransitLineSyncRequest {
	@NotBlank(message = "{validation.provider-line-id.required}")
	private String providerLineId;
	@NotBlank(message = "{validation.public-name.required}")
	private String publicName;
	private String operatorName;
	private String routeType;
	@NotNull(message = "{validation.active.required}")
	private Boolean active;
	private Instant sourceUpdatedAt;
}
