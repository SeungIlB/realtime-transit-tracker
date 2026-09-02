package com.realtimetransit.backend.transit.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.dto.response.DestinationStopResponse;
import com.realtimetransit.backend.transit.dto.response.DirectedStopResponse;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;
import com.realtimetransit.backend.transit.service.TransitStopService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransitStopServiceImpl implements TransitStopService {

	private final TransitStopMapper transitStopMapper;
	private final TransitExternalCollectionService externalCollectionService;

	@Override
	@Transactional
	public List<DirectedStopResponse> findActiveStopsByLineId(UUID lineId) {
		Objects.requireNonNull(lineId, "lineId must not be null");
		var stops = transitStopMapper.findActiveStopsByLineId(lineId);
		if (stops.isEmpty()) {
			externalCollectionService.synchronizeRoute(lineId);
			stops = transitStopMapper.findActiveStopsByLineId(lineId);
		}
		return stops.stream()
				.map(DirectedStopResponse::from)
				.toList();
	}

	@Override
	@Transactional
	public List<DestinationStopResponse> findDestinationsAfterBoardingStop(
			UUID lineId,
			UUID boardingStopId) {
		Objects.requireNonNull(lineId, "lineId must not be null");
		Objects.requireNonNull(boardingStopId, "boardingStopId must not be null");

		return transitStopMapper.findDestinationsAfterBoardingStop(lineId, boardingStopId).stream()
				.map(DestinationStopResponse::from)
				.toList();
	}


}
