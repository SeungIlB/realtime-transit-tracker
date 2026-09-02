package com.realtimetransit.backend.provider.client.dto;

import java.time.Instant;

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
public class ExternalTransitLine {
	private String providerLineId;
	private String publicName;
	private String operatorName;
	private String routeType;
	private Instant sourceUpdatedAt;
}
