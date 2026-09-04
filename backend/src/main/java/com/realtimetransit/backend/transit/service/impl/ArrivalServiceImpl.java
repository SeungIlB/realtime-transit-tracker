package com.realtimetransit.backend.transit.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.config.TransitArrivalProperties;
import com.realtimetransit.backend.transit.dto.response.UpcomingArrivalResponse;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
import com.realtimetransit.backend.transit.repository.ArrivalQueryMapper;
import com.realtimetransit.backend.transit.service.ArrivalService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(TransitArrivalProperties.class)
public class ArrivalServiceImpl implements ArrivalService {

	private static final int UPCOMING_ARRIVAL_LIMIT = 2;

	private final ArrivalQueryMapper arrivalQueryMapper;
	private final Clock clock;
	private final TransitExternalCollectionService externalCollectionService;
	private final TransitArrivalProperties arrivalProperties;

	@Override
	public List<UpcomingArrivalResponse> findUpcomingArrivals(
			UUID lineId,
			UUID directionId,
			UUID boardingStopId,
			UUID alightingStopId) {
		validateRequiredId(lineId, "lineId");
		validateRequiredId(directionId, "directionId");
		validateRequiredId(boardingStopId, "boardingStopId");
		validateRequiredId(alightingStopId, "alightingStopId");
		Instant asOf = clock.instant();
		Instant observedAfter = asOf.minus(arrivalProperties.getObservationFreshness());
		var storedArrivals = findArrivals(lineId, directionId, boardingStopId, alightingStopId, asOf, observedAfter);
		try {
			externalCollectionService.collectArrivals(lineId, boardingStopId, alightingStopId);
		} catch (BusinessException exception) {
			if (!storedArrivals.isEmpty()) {
				return storedArrivals.stream().map(UpcomingArrivalResponse::from).toList();
			}
			throw exception;
		}
		var refreshedArrivals = findArrivals(lineId, directionId, boardingStopId, alightingStopId, asOf, observedAfter);
		var arrivals = refreshedArrivals.isEmpty() ? storedArrivals : refreshedArrivals;
		return arrivals.stream()
				.map(UpcomingArrivalResponse::from)
				.toList();
	}

	private List<UpcomingArrivalEntity> findArrivals(
			UUID lineId,
			UUID directionId,
			UUID boardingStopId,
			UUID alightingStopId,
			Instant asOf,
			Instant observedAfter) {
		return arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId, directionId, boardingStopId, alightingStopId, asOf, observedAfter, UPCOMING_ARRIVAL_LIMIT);
	}

	private static void validateRequiredId(UUID value, String fieldName) {
		if (value == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, fieldName + " is required");
		}
	}

}
