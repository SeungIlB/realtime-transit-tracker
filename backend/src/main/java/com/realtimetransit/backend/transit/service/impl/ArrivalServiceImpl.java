package com.realtimetransit.backend.transit.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.dto.response.UpcomingArrivalResponse;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
import com.realtimetransit.backend.transit.repository.ArrivalQueryMapper;
import com.realtimetransit.backend.transit.service.ArrivalService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ArrivalServiceImpl implements ArrivalService {

	private static final Duration OBSERVATION_FRESHNESS = Duration.ofSeconds(30);
	private static final int UPCOMING_ARRIVAL_LIMIT = 2;

	private final ArrivalQueryMapper arrivalQueryMapper;
	private final Clock clock;
	private final TransitExternalCollectionService externalCollectionService;

	@Override
	@Transactional
	public List<UpcomingArrivalResponse> findUpcomingArrivals(
			UUID lineId,
			UUID boardingStopId,
			UUID alightingStopId) {
		validateRequiredId(lineId, "lineId");
		validateRequiredId(boardingStopId, "boardingStopId");
		validateRequiredId(alightingStopId, "alightingStopId");
		Instant asOf = clock.instant();
		Instant observedAfter = asOf.minus(OBSERVATION_FRESHNESS);
		var arrivals = findArrivals(lineId, boardingStopId, alightingStopId, asOf, observedAfter);
		if (arrivals.isEmpty()) {
			externalCollectionService.collectArrivals(lineId, boardingStopId);
			arrivals = findArrivals(lineId, boardingStopId, alightingStopId, asOf, observedAfter);
		}
		return arrivals.stream()
				.map(UpcomingArrivalResponse::from)
				.toList();
	}

	private List<UpcomingArrivalEntity> findArrivals(
			UUID lineId,
			UUID boardingStopId,
			UUID alightingStopId,
			Instant asOf,
			Instant observedAfter) {
		return arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId, boardingStopId, alightingStopId, asOf, observedAfter, UPCOMING_ARRIVAL_LIMIT);
	}

	private static void validateRequiredId(UUID value, String fieldName) {
		if (value == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, fieldName + " is required");
		}
	}

}
