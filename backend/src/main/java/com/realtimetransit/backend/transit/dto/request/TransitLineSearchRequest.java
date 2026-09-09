package com.realtimetransit.backend.transit.dto.request;

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
public class TransitLineSearchRequest {
	private String provider;
	private String query;
	private Integer limit;
	private BigDecimal latitude;
	private BigDecimal longitude;
}
