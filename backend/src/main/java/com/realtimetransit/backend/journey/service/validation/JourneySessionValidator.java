package com.realtimetransit.backend.journey.service.validation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.entity.JourneySessionEntity;
import com.realtimetransit.backend.journey.repository.JourneyMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JourneySessionValidator {

	private final JourneyMapper journeyMapper;

	public JourneySessionEntity findActiveJourney(UUID journeyId, UUID anonymousKey, Instant now) {
		if (journeyId == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_REQUEST, "journeyId is required");
		}
		if (anonymousKey == null) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_REQUEST, "anonymousKey is required");
		}
		JourneySessionEntity journey = journeyMapper.findJourneySessionByIdAndAnonymousKey(journeyId, anonymousKey)
				.orElseThrow(() -> new BusinessException(
						ErrorCode.JOURNEY_NOT_FOUND,
						"journey session was not found"));
		if (!"ACTIVE".equals(journey.getStatus())) {
			throw new BusinessException(
					ErrorCode.JOURNEY_NOT_ACTIVE,
					"journey status is " + journey.getStatus());
		}
		if (!journey.getExpiresAt().isAfter(now)) {
			throw new BusinessException(
					ErrorCode.JOURNEY_NOT_ACTIVE,
					"journey session has expired");
		}
		return journey;
	}
}
