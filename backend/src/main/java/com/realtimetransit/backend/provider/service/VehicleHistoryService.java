package com.realtimetransit.backend.provider.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.realtimetransit.backend.provider.dto.response.VehicleRunObservationResponse;

public interface VehicleHistoryService {

	List<VehicleRunObservationResponse> findRecentHistory(
			UUID lineId,
			String providerVehicleId,
			Duration lookback,
			int limit);
}
