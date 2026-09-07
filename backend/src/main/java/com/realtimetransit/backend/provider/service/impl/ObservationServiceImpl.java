package com.realtimetransit.backend.provider.service.impl;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.common.maintenance.TransitMaintenanceProperties;
import com.realtimetransit.backend.provider.dto.request.RawObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.VehicleRunObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.ArrivalPredictionObservationSaveRequest;
import com.realtimetransit.backend.provider.entity.RawObservationEntity;
import com.realtimetransit.backend.provider.entity.VehicleRunObservationEntity;
import com.realtimetransit.backend.provider.entity.ArrivalPredictionObservationEntity;
import com.realtimetransit.backend.provider.repository.RawObservationMapper;
import com.realtimetransit.backend.provider.repository.VehicleRunObservationMapper;
import com.realtimetransit.backend.provider.repository.ArrivalPredictionObservationMapper;
import com.realtimetransit.backend.provider.service.ObservationService;
import com.realtimetransit.backend.provider.service.validation.ObservationValidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
@EnableConfigurationProperties(TransitMaintenanceProperties.class)
public class ObservationServiceImpl implements ObservationService {

	private final RawObservationMapper rawObservationMapper;
	private final VehicleRunObservationMapper vehicleRunObservationMapper;
	private final ArrivalPredictionObservationMapper arrivalPredictionObservationMapper;
	private final Clock clock;
	private final ObservationValidator observationValidator;
	private final TransitMaintenanceProperties maintenanceProperties;

	@Override
	public long saveRawObservation(RawObservationSaveRequest request) {
		Instant receivedAt = Instant.now(clock);
        observationValidator.validateRawObservation(request, receivedAt);   
        RawObservationEntity rawObservation = createRawObservationEntity(request, receivedAt);

        return rawObservationMapper.insertRawObservation(rawObservation);
	}

	@Override
	public long saveVehicleRunObservation(VehicleRunObservationSaveRequest request) {
        Instant receivedAt = Instant.now(clock);
        observationValidator.validateVehicleRunObservation(
                request,
                receivedAt);
        VehicleRunObservationEntity entity = createVehicleRunObservationEntity(request, receivedAt);

        return  vehicleRunObservationMapper.insertVehicleRunObservation(entity);
	}

	@Override
	public long saveArrivalPredictionObservation(
			ArrivalPredictionObservationSaveRequest request) {
		Instant receivedAt =  Instant.now(clock);
        observationValidator.validateArrivalPredictionObservation(request, receivedAt);

        ArrivalPredictionObservationEntity entity = createArrivalPredictionObservationEntity(request, receivedAt);
        return  arrivalPredictionObservationMapper.insertArrivalPredictionObservation(entity);
	}

	private ArrivalPredictionObservationEntity createArrivalPredictionObservationEntity(
			ArrivalPredictionObservationSaveRequest request,
			Instant receivedAt) {

		ArrivalPredictionObservationEntity entity = ArrivalPredictionObservationEntity.builder()
                .id(null)
                .rawObservationId(request.getRawObservationId())
                .vehicleRunObservationId(request.getVehicleRunObservationId())
                .boardingStopId(request.getBoardingStopId())
                .requestedAlightingStopId(request.getRequestedAlightingStopId())
                .alightingStopConfirmed(request.isAlightingStopConfirmed())
                .alightingStopStatus(request.getAlightingStopStatus())
                .expectedAt(request.getExpectedAt())
                .minExpectedAt(request.getMinExpectedAt())
                .maxExpectedAt(request.getMaxExpectedAt())
                .remainingStops(request.getRemainingStops())
                .source(request.getSource())
                .confidence(request.getConfidence())
                .observedAt(request.getObservedAt())
                .receivedAt(receivedAt)
                .build();

        return entity;
	}

	private VehicleRunObservationEntity createVehicleRunObservationEntity(
			VehicleRunObservationSaveRequest request,
			Instant receivedAt) {
        VehicleRunObservationEntity vehicleRunObservationEntity = VehicleRunObservationEntity.builder()
                .id(null)
                .rawObservationId(request.getRawObservationId())
                .lineId(request.getLineId())
                .directionId(request.getDirectionId())
                .stopPatternId(request.getStopPatternId())
                .providerVehicleId(request.getProviderVehicleId())
                .providerRunId(request.getProviderRunId())
                .destinationStopId(request.getDestinationStopId())
                .currentStopId(request.getCurrentStopId())
                .currentSequence(request.getCurrentSequence())
                .serviceType(request.getServiceType())
                .movementStatus(request.getMovementStatus())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .speedKph(request.getSpeedKph())
                .bearingDegrees(request.getBearingDegrees())
                .positionSource(request.getPositionSource())
                .observedAt(request.getObservedAt())
                .receivedAt(receivedAt)
                .build();

        return  vehicleRunObservationEntity;
    }

	private RawObservationEntity createRawObservationEntity(
			RawObservationSaveRequest request,
			Instant receivedAt) {
		Instant retentionExpiresAt = receivedAt.plus(maintenanceProperties.getObservationRetention());
		Instant requestedExpiresAt = request.getExpiresAt();
		Instant expiresAt = requestedExpiresAt == null || requestedExpiresAt.isAfter(retentionExpiresAt)
				? retentionExpiresAt
				: requestedExpiresAt;

        RawObservationEntity entity = RawObservationEntity.builder()
                .id(null)
                .providerId(request.getProviderId())
                .endpoint(request.getEndpoint())
                .requestKey(request.getRequestKey())
                .receivedAt(receivedAt)
                .providerObservedAt(request.getProviderObservedAt())
                .responseStatus(request.getResponseStatus())
                .payload(request.getPayload())
                .expiresAt(expiresAt)
                .build();
        return entity;
    }
}
