package com.realtimetransit.backend.transit.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.transit.dto.request.TransitLineSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitStopSyncRequest;
import com.realtimetransit.backend.transit.dto.request.RouteDirectionSyncRequest;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.entity.RouteDirectionEntity;
import com.realtimetransit.backend.transit.entity.DirectedStopAssignmentEntity;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;
import com.realtimetransit.backend.transit.repository.RouteDirectionMapper;
import com.realtimetransit.backend.transit.repository.DirectedStopMapper;
import com.realtimetransit.backend.transit.service.TransitReferenceSyncService;
import com.realtimetransit.backend.transit.service.validation.TransitReferenceSyncValidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class TransitReferenceSyncServiceImpl implements TransitReferenceSyncService {

	private final TransitLineMapper transitLineMapper;
	private final TransitStopMapper transitStopMapper;
	private final RouteDirectionMapper routeDirectionMapper;
	private final DirectedStopMapper directedStopMapper;
	private final TransitReferenceSyncValidator referenceSyncValidator;

	@Override
	public Map<String, UUID> synchronizeTransitLines(
			long providerId,
			List<TransitLineSyncRequest> lines) {
		referenceSyncValidator.validateLines(providerId, lines);

		Map<String, UUID> lineIdsByProviderLineId = new LinkedHashMap<>();
		for (TransitLineSyncRequest line : lines) {
			TransitLineEntity entity = TransitLineEntity.builder()
					.id(UUID.randomUUID())
					.providerId(providerId)
					.providerLineId(line.getProviderLineId())
					.publicName(line.getPublicName())
					.operatorName(line.getOperatorName())
					.routeType(line.getRouteType())
					.active(line.getActive())
					.sourceUpdatedAt(line.getSourceUpdatedAt())
					.build();
			transitLineMapper.upsertTransitLine(entity);

			UUID actualLineId = transitLineMapper
					.findByProviderResourceId(providerId, line.getProviderLineId())
					.orElseThrow(() -> new BusinessException(
							ErrorCode.SYNC_RESULT_NOT_FOUND,
							line.getProviderLineId()))
					.getId();
			lineIdsByProviderLineId.put(line.getProviderLineId(), actualLineId);
		}

		transitLineMapper.deactivateTransitLinesNotInProviderLineIds(
				providerId,
				lines.stream().map(TransitLineSyncRequest::getProviderLineId).toList());
		return Map.copyOf(lineIdsByProviderLineId);
	}

	@Override
	public Map<String, UUID> synchronizeTransitStops(
			long providerId,
			List<TransitStopSyncRequest> stops) {
		referenceSyncValidator.validateStops(providerId, stops);

		Map<String, UUID> stopIdsByProviderStopId = new LinkedHashMap<>();
		for (TransitStopSyncRequest stop : stops) {
			upsertTransitStop(providerId, stop, UUID.randomUUID(), null);
			UUID actualStopId = transitStopMapper
					.findByProviderResourceId(providerId, stop.getProviderStopId())
					.orElseThrow(() -> new BusinessException(
							ErrorCode.SYNC_RESULT_NOT_FOUND,
							stop.getProviderStopId()))
					.getId();
			stopIdsByProviderStopId.put(stop.getProviderStopId(), actualStopId);
		}

		for (TransitStopSyncRequest stop : stops) {
			if (stop.getParentProviderStopId() == null) {
				continue;
			}
			UUID stopId = stopIdsByProviderStopId.get(stop.getProviderStopId());
			UUID parentStationId = stopIdsByProviderStopId.get(stop.getParentProviderStopId());
			upsertTransitStop(providerId, stop, stopId, parentStationId);
		}

		return Map.copyOf(stopIdsByProviderStopId);
	}

	@Override
	public Map<String, UUID> synchronizeRouteDirections(
			UUID lineId,
			Map<String, UUID> stopIdsByProviderStopId,
			List<RouteDirectionSyncRequest> directions) {
		referenceSyncValidator.validateDirections(lineId, stopIdsByProviderStopId, directions);

		Map<String, UUID> directionIdsByProviderDirectionId = new LinkedHashMap<>();
		for (RouteDirectionSyncRequest direction : directions) {
			routeDirectionMapper.upsertRouteDirection(RouteDirectionEntity.builder()
					.id(UUID.randomUUID())
					.lineId(lineId)
					.providerDirectionId(direction.getProviderDirectionId())
					.originStopId(resolveStopId(stopIdsByProviderStopId, direction.getOriginProviderStopId()))
					.terminalStopId(resolveStopId(stopIdsByProviderStopId, direction.getTerminalProviderStopId()))
					.representativeNextStopId(resolveStopId(stopIdsByProviderStopId, direction.getRepresentativeNextProviderStopId()))
					.displayName(direction.getDisplayName())
					.active(direction.getActive())
					.build());

			UUID directionId = routeDirectionMapper
					.findRouteDirectionByBusinessKey(lineId, direction.getProviderDirectionId())
					.orElseThrow(() -> new BusinessException(
							ErrorCode.SYNC_RESULT_NOT_FOUND,
							direction.getProviderDirectionId()))
					.getId();
			directionIdsByProviderDirectionId.put(direction.getProviderDirectionId(), directionId);

			for (var stop : direction.getDirectedStops()) {
				directedStopMapper.upsertDirectedStop(DirectedStopAssignmentEntity.builder()
						.id(UUID.randomUUID())
						.lineId(lineId)
						.directionId(directionId)
						.stopId(stopIdsByProviderStopId.get(stop.getProviderStopId()))
						.stopSequence(stop.getStopSequence())
						.nextStopId(resolveStopId(stopIdsByProviderStopId, stop.getNextProviderStopId()))
						.platformId(stop.getPlatformId())
						.displayDirection(stop.getDisplayDirection())
						.segmentId(stop.getSegmentId())
						.build());
			}
			directedStopMapper.deleteDirectedStopsNotInSequences(
					directionId,
					direction.getDirectedStops().stream().map(stop -> stop.getStopSequence()).toList());
		}

		routeDirectionMapper.deactivateRouteDirectionsNotInProviderIds(
				lineId,
				directions.stream().map(RouteDirectionSyncRequest::getProviderDirectionId).toList());
		return Map.copyOf(directionIdsByProviderDirectionId);
	}

	private static UUID resolveStopId(Map<String, UUID> stopIds, String providerStopId) {
		return providerStopId == null ? null : stopIds.get(providerStopId);
	}

	private void upsertTransitStop(
			long providerId,
			TransitStopSyncRequest stop,
			UUID stopId,
			UUID parentStationId) {
		TransitStopEntity entity = TransitStopEntity.builder()
				.id(stopId)
				.providerId(providerId)
				.providerStopId(stop.getProviderStopId())
				.parentStationId(parentStationId)
				.publicName(stop.getPublicName())
				.latitude(stop.getLatitude())
				.longitude(stop.getLongitude())
				.sourceUpdatedAt(stop.getSourceUpdatedAt())
				.build();
		transitStopMapper.upsertTransitStop(entity);
	}

}

