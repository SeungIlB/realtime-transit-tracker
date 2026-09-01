package com.realtimetransit.backend.transit.service;

import java.util.List;
import java.util.UUID;

import com.realtimetransit.backend.transit.dto.response.DirectedStopResponse;
import com.realtimetransit.backend.transit.dto.response.DestinationStopResponse;

public interface TransitStopService {

	List<DirectedStopResponse> findActiveStopsByLineId(UUID lineId);

	List<DestinationStopResponse> findDestinationsAfterBoardingStop(
			UUID lineId,
			UUID boardingStopId);
}
