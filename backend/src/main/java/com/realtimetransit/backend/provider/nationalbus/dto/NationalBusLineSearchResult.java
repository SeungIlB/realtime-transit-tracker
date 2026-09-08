package com.realtimetransit.backend.provider.nationalbus.dto;

import java.util.List;

import com.realtimetransit.backend.provider.client.dto.ExternalTransitLine;

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
public class NationalBusLineSearchResult {
	private List<ExternalTransitLine> lines;
	private List<String> nearbyGyeonggiRegionNames;
}
