package com.realtimetransit.backend.provider.client.dto;

import java.math.BigDecimal;

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
public class ExternalStop {
	private String providerStopId;
	private String publicName;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private Integer sequence;
	private String platformId;
}
