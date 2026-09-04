package com.realtimetransit.backend.transit.service;

import java.util.List;
import java.util.UUID;

import com.realtimetransit.backend.transit.dto.response.UpcomingArrivalResponse;

public interface ArrivalService {

	List<UpcomingArrivalResponse> findUpcomingArrivals(
			UUID lineId,
			UUID directionId,
			UUID boardingStopId,
			UUID alightingStopId);
}
