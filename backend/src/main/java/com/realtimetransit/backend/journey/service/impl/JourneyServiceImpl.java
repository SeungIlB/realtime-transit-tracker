package com.realtimetransit.backend.journey.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.dto.request.JourneyCreateRequest;
import com.realtimetransit.backend.journey.dto.response.JourneySessionResponse;
import com.realtimetransit.backend.journey.entity.JourneySessionEntity;
import com.realtimetransit.backend.journey.entity.JourneyStopValidationEntity;
import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;
import com.realtimetransit.backend.journey.repository.JourneyMapper;
import com.realtimetransit.backend.journey.repository.TravelerProfileMapper;
import com.realtimetransit.backend.journey.service.JourneyService;
import com.realtimetransit.backend.journey.service.validation.JourneySessionValidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@EnableConfigurationProperties(JourneyProperties.class)
public class JourneyServiceImpl implements JourneyService {

	private final JourneyMapper journeyMapper;
	private final TravelerProfileMapper travelerProfileMapper;
	private final Clock clock;
	private final JourneyProperties properties;
	private final JourneySessionValidator journeySessionValidator;

	@Override
	@Transactional
	public JourneySessionResponse createJourney(JourneyCreateRequest request) {
		Instant now = clock.instant();
		validateCreateRequest(request, now);
		JourneyStopValidationEntity validatedStops = validateJourneyStops(request);
		TravelerProfileEntity profile = resolveTravelerProfile(request.getAnonymousKey());
		JourneySessionEntity journey = buildJourneySession(request, profile, validatedStops, now);
		JourneySessionEntity savedJourney = saveAndReadJourneySession(journey);

		return JourneySessionResponse.from(savedJourney);
	}

	@Override
	@Transactional
	public JourneySessionResponse cancelJourney(UUID journeyId) {
		Instant now = clock.instant();
		JourneySessionEntity activeJourney = journeySessionValidator.findActiveJourney(journeyId, now);
		JourneySessionEntity cancelledJourney = cancelAndReadJourney(activeJourney, now);
		return JourneySessionResponse.from(cancelledJourney);
	}

	private static void validateCreateRequest(JourneyCreateRequest request, Instant now) {
		if (request == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_REQUEST, "request is required");
		}
		if (request.getAnonymousKey() == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_REQUEST, "anonymousKey is required");
		}
		if (request.getLineId() == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_REQUEST, "lineId is required");
		}
		if (request.getDirectionId() == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_REQUEST, "directionId is required");
		}
		if (request.getBoardingStopId() == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_REQUEST, "boardingStopId is required");
		}
		if (request.getAlightingStopId() != null
				&& request.getBoardingStopId().equals(request.getAlightingStopId())) {
			throw new BusinessException(
					ErrorCode.INVALID_JOURNEY_REQUEST,
					"alightingStopId must differ from boardingStopId");
		}
		var targetProbability = request.getTargetProbability();
		if (targetProbability != null
				&& (targetProbability.compareTo(BigDecimal.ZERO) <= 0
						|| targetProbability.compareTo(BigDecimal.ONE) > 0)) {
			throw new BusinessException(
					ErrorCode.INVALID_JOURNEY_REQUEST,
					"targetProbability must be greater than 0 and less than or equal to 1");
		}
		if (request.getDesiredArrivalAt() != null
				&& !request.getDesiredArrivalAt().isAfter(now)) {
			throw new BusinessException(
					ErrorCode.INVALID_JOURNEY_REQUEST,
					"desiredArrivalAt must be in the future");
		}
	}

	private JourneyStopValidationEntity validateJourneyStops(JourneyCreateRequest request) {
		return journeyMapper.validateJourneyStopsOnSameDirection(
				request.getLineId(),
				request.getDirectionId(),
				request.getBoardingStopId(),
				request.getAlightingStopId())
				.orElseThrow(() -> new BusinessException(
						ErrorCode.INVALID_JOURNEY_STOPS,
						"line, direction, boarding stop, and alighting stop do not form a valid journey"));
	}

	private TravelerProfileEntity resolveTravelerProfile(UUID anonymousKey) {
		var existingProfile = travelerProfileMapper.findByAnonymousKey(anonymousKey);
		if (existingProfile.isPresent()) {
			return existingProfile.get();
		}

		var profile = TravelerProfileEntity.builder()
				.id(UUID.randomUUID())
				.anonymousKey(anonymousKey)
				.slowWalkSpeedMps(properties.getDefaultSlowWalkSpeedMps())
				.walkSpeedMps(properties.getDefaultWalkSpeedMps())
				.fastWalkSpeedMps(properties.getDefaultFastWalkSpeedMps())
				.runSpeedMps(properties.getDefaultRunSpeedMps())
				.sampleCount(0)
				.build();
		travelerProfileMapper.upsertTravelerProfile(profile);

		return travelerProfileMapper.findByAnonymousKey(anonymousKey)
				.orElseThrow(() -> new BusinessException(
						ErrorCode.SYNC_RESULT_NOT_FOUND,
						"traveler profile could not be read after upsert"));
	}

	private JourneySessionEntity buildJourneySession(
			JourneyCreateRequest request,
			TravelerProfileEntity profile,
			JourneyStopValidationEntity validatedStops,
			Instant now) {
		return JourneySessionEntity.builder()
				.id(UUID.randomUUID())
				.travelerProfileId(profile.getId())
				.lineId(validatedStops.getLineId())
				.directionId(validatedStops.getDirectionId())
				.boardingStopId(validatedStops.getBoardingStopId())
				.alightingStopId(validatedStops.getAlightingStopId())
				.targetProbability(request.getTargetProbability() != null
						? request.getTargetProbability()
						: properties.getDefaultTargetProbability())
				.desiredArrivalAt(request.getDesiredArrivalAt())
				.status("ACTIVE")
				.expiresAt(now.plus(properties.getSessionTtl()))
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	private JourneySessionEntity saveAndReadJourneySession(JourneySessionEntity journey) {
		journeyMapper.insertJourneySession(journey);
		return journeyMapper.findJourneySessionById(journey.getId())
				.orElseThrow(() -> new BusinessException(
						ErrorCode.SYNC_RESULT_NOT_FOUND,
						"journey session could not be read after insert"));

	}

	private JourneySessionEntity cancelAndReadJourney(JourneySessionEntity journey, Instant now) {
		int updatedCount = journeyMapper.updateJourneySessionStatus(
				journey.getId(),
				"CANCELLED",
				now);
		if (updatedCount != 1) {
			throw new BusinessException(
					ErrorCode.JOURNEY_STATUS_UPDATE_FAILED,
					"journeyId=" + journey.getId());
		}

		return journeyMapper.findJourneySessionById(journey.getId())
				.orElseThrow(() -> new BusinessException(
						ErrorCode.SYNC_RESULT_NOT_FOUND,
						"cancelled journey session could not be read"));
	}
}
