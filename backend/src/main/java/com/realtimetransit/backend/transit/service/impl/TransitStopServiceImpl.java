package com.realtimetransit.backend.transit.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.dto.response.DestinationStopResponse;
import com.realtimetransit.backend.transit.dto.response.DirectedStopResponse;
import com.realtimetransit.backend.transit.entity.DirectedStopEntity;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;
import com.realtimetransit.backend.transit.service.TransitStopService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransitStopServiceImpl implements TransitStopService {

	private final TransitStopMapper transitStopMapper;
	private final TransitExternalCollectionService externalCollectionService;

	@Override
	public List<DirectedStopResponse> findActiveStopsByLineId(UUID lineId) {
		validateRequiredId(lineId, "lineId");
		var stops = transitStopMapper.findActiveStopsByLineId(lineId);
		if (stops.isEmpty() || stops.stream().noneMatch(TransitStopServiceImpl::hasCoordinates)) {
			externalCollectionService.synchronizeRoute(lineId);
			stops = transitStopMapper.findActiveStopsByLineId(lineId);
		}
		return stops.stream()
				.map(DirectedStopResponse::from)
				.toList();
	}

	@Override
	public List<DestinationStopResponse> findDestinationsAfterBoardingStop(
			UUID lineId,
			UUID directionId,
			UUID boardingStopId) {
		validateRequiredId(lineId, "lineId");
		validateRequiredId(directionId, "directionId");
		validateRequiredId(boardingStopId, "boardingStopId");

		return transitStopMapper.findDestinationsAfterBoardingStop(lineId, directionId, boardingStopId).stream()
				.map(DestinationStopResponse::from)
				.toList();
	}

	private static void validateRequiredId(UUID value, String fieldName) {
		if (value == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, fieldName + " is required");
		}
	}

	private static boolean hasCoordinates(DirectedStopEntity stop) {
		return stop.getLatitude() != null && stop.getLongitude() != null;
	}
}
