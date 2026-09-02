package com.realtimetransit.backend.provider.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class RawObservationSaveRequest {
	@Positive(message = "{validation.provider-id.positive}")
	private long providerId;
	@NotBlank(message = "{validation.endpoint.required}")
	private String endpoint;
	@NotBlank(message = "{validation.request-key.required}")
	private String requestKey;
	private Instant providerObservedAt;
	@Min(value = 100, message = "{validation.response-status.range}")
	@Max(value = 599, message = "{validation.response-status.range}")
	private int responseStatus;
	private String payload;
	private Instant expiresAt;
}
