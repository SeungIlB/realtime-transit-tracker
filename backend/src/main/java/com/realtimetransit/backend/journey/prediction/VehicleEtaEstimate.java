package com.realtimetransit.backend.journey.prediction;

import java.time.Instant;

import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;

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
public class VehicleEtaEstimate {
	private UpcomingArrivalEntity sourceArrival;
	private Instant minExpectedAt;
	private Instant expectedAt;
	private Instant maxExpectedAt;
}
