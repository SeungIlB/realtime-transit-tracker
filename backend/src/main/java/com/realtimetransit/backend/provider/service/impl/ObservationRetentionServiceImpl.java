package com.realtimetransit.backend.provider.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.provider.repository.RawObservationMapper;
import com.realtimetransit.backend.provider.repository.VehicleRunObservationMapper;
import com.realtimetransit.backend.provider.repository.ArrivalPredictionObservationMapper;
import com.realtimetransit.backend.provider.service.ObservationRetentionService;
import com.realtimetransit.backend.provider.service.validation.ObservationValidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ObservationRetentionServiceImpl implements ObservationRetentionService {

	private final RawObservationMapper rawObservationMapper;
	private final VehicleRunObservationMapper vehicleRunObservationMapper;
	private final ArrivalPredictionObservationMapper arrivalPredictionObservationMapper;
	private final ObservationValidator observationValidator;
	private final Clock clock;

	@Override
	public int deleteExpiredRawObservations(int limit) {
        observationValidator.validateCleanupLimit(limit);

        Instant expiresAtOrBefore = Instant.now(clock);

        return rawObservationMapper.deleteExpiredRawObservations(expiresAtOrBefore, limit);
	}

	@Override
	public int deleteOldVehicleRunObservations(Duration retention, int limit) {
		observationValidator.validateRetention(retention);
        observationValidator.validateCleanupLimit(limit);

        Instant receivedBefore = Instant.now(clock).minus(retention);

        return vehicleRunObservationMapper.deleteVehicleRunObservationsReceivedBefore(
                receivedBefore, limit
        );
	}

	@Override
	public int deleteOldArrivalPredictions(Duration retention, int limit) {
        observationValidator.validateRetention(retention);
        observationValidator.validateCleanupLimit(limit);

        Instant receivedBefore = Instant.now(clock).minus(retention);

        return arrivalPredictionObservationMapper.deleteArrivalPredictionsReceivedBefore(
                receivedBefore, limit
        );
	}
}
