package com.realtimetransit.backend.transit.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.transit.dto.response.UpcomingArrivalResponse;
import com.realtimetransit.backend.transit.repository.ArrivalQueryMapper;
import com.realtimetransit.backend.transit.service.ArrivalService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ArrivalServiceImpl implements ArrivalService {

	private static final Duration OBSERVATION_FRESHNESS = Duration.ofMinutes(2);
	private static final int UPCOMING_ARRIVAL_LIMIT = 2;

	private final ArrivalQueryMapper arrivalQueryMapper;
	private final Clock clock;

	@Override
	public List<UpcomingArrivalResponse> findUpcomingArrivals(
			UUID lineId,
			UUID boardingStopId,
			UUID alightingStopId) {
		Objects.requireNonNull(lineId, "lineId must not be null");
		Objects.requireNonNull(boardingStopId, "boardingStopId must not be null");
		Objects.requireNonNull(alightingStopId, "alightingStopId must not be null");

		Instant asOf = clock.instant();
		Instant observedAfter = asOf.minus(OBSERVATION_FRESHNESS);

		return arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId,
				boardingStopId,
				alightingStopId,
				asOf,
				observedAfter,
				UPCOMING_ARRIVAL_LIMIT).stream()
				.map(UpcomingArrivalResponse::from)
				.toList();
	}

}
