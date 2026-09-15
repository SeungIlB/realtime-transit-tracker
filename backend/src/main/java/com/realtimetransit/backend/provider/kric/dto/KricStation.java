package com.realtimetransit.backend.provider.kric.dto;

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
public class KricStation {
	private String operatorCode;
	private String lineCode;
	private String stationCode;
	private String stationName;
	private BigDecimal latitude;
	private BigDecimal longitude;
}
