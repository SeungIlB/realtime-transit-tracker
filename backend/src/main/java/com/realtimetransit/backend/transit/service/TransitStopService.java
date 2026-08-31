package com.realtimetransit.backend.transit.service;

import java.util.List;
import java.util.UUID;

import com.realtimetransit.backend.transit.dto.response.DirectedStopResponse;

public interface TransitStopService {

	List<DirectedStopResponse> findActiveStopsByLineId(UUID lineId);
}
