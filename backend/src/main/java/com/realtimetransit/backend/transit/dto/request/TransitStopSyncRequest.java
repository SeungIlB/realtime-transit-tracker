package com.realtimetransit.backend.transit.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class TransitStopSyncRequest {
	@NotBlank(message = "{validation.provider-stop-id.required}")
	private String providerStopId;
	private String parentProviderStopId;
	@NotBlank(message = "{validation.public-name.required}")
	private String publicName;
	@DecimalMin(value = "-90", message = "{validation.latitude.range}")
	@DecimalMax(value = "90", message = "{validation.latitude.range}")
	private BigDecimal latitude;
	@DecimalMin(value = "-180", message = "{validation.longitude.range}")
	@DecimalMax(value = "180", message = "{validation.longitude.range}")
	private BigDecimal longitude;
	private Instant sourceUpdatedAt;
}
