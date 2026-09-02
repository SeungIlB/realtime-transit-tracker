package com.realtimetransit.backend.provider.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.provider.dto.response.VehicleRunObservationResponse;
import com.realtimetransit.backend.provider.repository.VehicleRunObservationMapper;
import com.realtimetransit.backend.provider.service.VehicleHistoryService;
import com.realtimetransit.backend.provider.service.validation.ObservationValidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VehicleHistoryServiceImpl implements VehicleHistoryService {

	private final VehicleRunObservationMapper vehicleRunObservationMapper;
	private final ObservationValidator observationValidator;
	private final Clock clock;

	@Override
	public List<VehicleRunObservationResponse> findRecentHistory(
			UUID lineId,
			String providerVehicleId,
			Duration lookback,
			int limit) {
		observationValidator.validateVehicleHistoryQuery(lineId, providerVehicleId, lookback, limit);
        Instant observedAfter = clock.instant().minus(lookback);
        return vehicleRunObservationMapper.findRecentVehicleRunObservations(lineId, providerVehicleId, observedAfter, limit)
                .stream()
                .map(VehicleRunObservationResponse::from)
                .toList();

	}
}
