package com.realtimetransit.backend.provider.client.dto;

import java.time.Instant;
import java.util.List;

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
public class ExternalRouteReference {
	private String providerLineId;
	private Instant sourceUpdatedAt;
	private List<ExternalDirection> directions;
}
