package com.realtimetransit.backend.journey.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.realtimetransit.backend.journey.entity.JourneySessionEntity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.dto.request.JourneyLocationCreateRequest;
import com.realtimetransit.backend.journey.dto.response.JourneyLocationResponse;
import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;
import com.realtimetransit.backend.journey.repository.JourneyLocationMapper;
import com.realtimetransit.backend.journey.service.JourneyLocationService;
import com.realtimetransit.backend.journey.service.validation.JourneySessionValidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@EnableConfigurationProperties(JourneyProperties.class)
public class JourneyLocationServiceImpl implements JourneyLocationService {

	private final JourneyLocationMapper journeyLocationMapper;
	private final Clock clock;
	private final JourneyProperties properties;
	private final JourneySessionValidator journeySessionValidator;

	@Override
	@Transactional
	public JourneyLocationResponse addLocation(
			UUID journeyId,
			JourneyLocationCreateRequest request) {
		Instant now = clock.instant();
		validateLocationRequest(journeyId, request, now);
		journeySessionValidator.findActiveJourney(journeyId, now);
		TravelerLocationObservationEntity observation = buildLocationObservation(journeyId, request, now);
		TravelerLocationObservationEntity savedObservation = saveLocationObservation(observation);

		return JourneyLocationResponse.from(savedObservation);
	}

	private void validateLocationRequest(
			UUID journeyId,
			JourneyLocationCreateRequest request,
			Instant now) {
		if (journeyId == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "journeyId is required");
		}
		if (request == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "request is required");
		}
		if (request.getLatitude() == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "latitude is required");
		}
		if (request.getLatitude().compareTo(new BigDecimal("-90")) < 0
				|| request.getLatitude().compareTo(new BigDecimal("90")) > 0) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "latitude must be between -90 and 90");
		}
		if (request.getLongitude() == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "longitude is required");
		}
		if (request.getLongitude().compareTo(new BigDecimal("-180")) < 0
				|| request.getLongitude().compareTo(new BigDecimal("180")) > 0) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "longitude must be between -180 and 180");
		}
		if (request.getAccuracyM() == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "accuracyM is required");
		}
		if (request.getAccuracyM().compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "accuracyM must be greater than or equal to 0");
		}
		if (request.getSpeedMps() != null
				&& request.getSpeedMps().compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "speedMps must be greater than or equal to 0");
		}
		if (request.getObservedAt() == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "observedAt is required");
		}
		if (request.getObservedAt().isAfter(now.plus(properties.getMaxLocationFutureSkew()))) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_LOCATION, "observedAt is too far in the future");
		}
	}

	private TravelerLocationObservationEntity buildLocationObservation(
			UUID journeyId,
			JourneyLocationCreateRequest request,
			Instant now) {
		return TravelerLocationObservationEntity.builder()
				.journeyId(journeyId)
				.latitude(request.getLatitude())
				.longitude(request.getLongitude())
                .accuracyM(request.getAccuracyM())
				.speedMps(request.getSpeedMps())
				.observedAt(request.getObservedAt())
				.receivedAt(now)
				.expiresAt(now.plus(properties.getLocationTtl()))
				.build();
	}

	private TravelerLocationObservationEntity saveLocationObservation(
			TravelerLocationObservationEntity observation) {
		long observationId = journeyLocationMapper.insertTravelerLocationObservation(observation);
		observation.setId(observationId);
		return observation;
	}
}
