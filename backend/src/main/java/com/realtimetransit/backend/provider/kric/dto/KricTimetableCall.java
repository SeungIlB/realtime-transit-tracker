package com.realtimetransit.backend.provider.kric.dto;

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
public class KricTimetableCall {
	private String operatorCode;
	private String lineCode;
	private String stationCode;
	private String trainNumber;
	private String arrivalTime;
	private String departureTime;
	private String expressCode;
	private Integer dayCode;
}
