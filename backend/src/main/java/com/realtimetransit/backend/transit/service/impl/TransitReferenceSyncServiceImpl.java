package com.realtimetransit.backend.transit.service.impl;

import java.util.ArrayList;
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
import com.realtimetransit.backend.transit.dto.request.StopPatternSyncRequest;
import com.realtimetransit.backend.transit.dto.response.StopPatternSyncKey;
import com.realtimetransit.backend.transit.entity.DirectedStopAssignmentEntity;
import com.realtimetransit.backend.transit.entity.RouteDirectionEntity;
import com.realtimetransit.backend.transit.entity.StopPatternEntity;
import com.realtimetransit.backend.transit.entity.StopPatternStopEntity;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;
import com.realtimetransit.backend.transit.repository.RouteDirectionMapper;
import com.realtimetransit.backend.transit.repository.DirectedStopMapper;
import com.realtimetransit.backend.transit.repository.StopPatternMapper;
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
	private final StopPatternMapper stopPatternMapper;
	private final TransitReferenceSyncValidator referenceSyncValidator;

	@Override
	public Map<String, UUID> synchronizeTransitLines(
			long providerId,
			List<TransitLineSyncRequest> lines) {
		Map<String, UUID> result = upsertTransitLines(providerId, lines);
		transitLineMapper.deactivateTransitLinesNotInProviderLineIds(
				providerId,
				lines.stream().map(TransitLineSyncRequest::getProviderLineId).toList());
		return result;
	}

	@Override
	public Map<String, UUID> upsertTransitLines(
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

	@Override
	public Map<StopPatternSyncKey, UUID> synchronizeStopPatterns(
			UUID lineId,
			Map<String, UUID> stopIdsByProviderStopId,
			List<StopPatternSyncRequest> patterns) {
		referenceSyncValidator.validateStopPatterns(lineId, stopIdsByProviderStopId, patterns);

		Map<StopPatternSyncKey, UUID> patternIdsByBusinessKey = new LinkedHashMap<>();
		List<StopPatternEntity> retainedPatterns = new ArrayList<>();
		for (StopPatternSyncRequest pattern : patterns) {
			StopPatternEntity patternEntity = createStopPatternEntity(lineId, pattern);
			UUID actualPatternId = synchronizeStopPattern(
					patternEntity, pattern, stopIdsByProviderStopId);

			patternIdsByBusinessKey.put(createStopPatternKey(pattern), actualPatternId);
			retainedPatterns.add(patternEntity);
		}

		deactivateMissingStopPatterns(lineId, retainedPatterns);
		return Map.copyOf(patternIdsByBusinessKey);
	}

	private UUID synchronizeStopPattern(
			StopPatternEntity patternEntity,
			StopPatternSyncRequest pattern,
			Map<String, UUID> stopIdsByProviderStopId) {
		stopPatternMapper.upsertStopPattern(patternEntity);
		UUID actualPatternId = findActualStopPatternId(patternEntity);
		synchronizeStopPatternStops(actualPatternId, pattern, stopIdsByProviderStopId);
		return actualPatternId;
	}

	private StopPatternEntity createStopPatternEntity(
			UUID lineId,
			StopPatternSyncRequest pattern) {
		return StopPatternEntity.builder()
				.id(UUID.randomUUID())
				.lineId(lineId)
				.providerPatternId(pattern.getProviderPatternId())
				.serviceType(pattern.getServiceType())
				.validFrom(pattern.getValidFrom())
				.validTo(pattern.getValidTo())
				.active(pattern.getActive())
				.build();
	}

	private UUID findActualStopPatternId(StopPatternEntity pattern) {
		return stopPatternMapper.findStopPatternByBusinessKey(
				pattern.getLineId(),
				pattern.getProviderPatternId(),
				pattern.getValidFrom())
				.orElseThrow(() -> new BusinessException(
						ErrorCode.SYNC_RESULT_NOT_FOUND,
						pattern.getProviderPatternId() + ":" + pattern.getValidFrom()))
				.getId();
	}

	private void synchronizeStopPatternStops(
			UUID stopPatternId,
			StopPatternSyncRequest pattern,
			Map<String, UUID> stopIdsByProviderStopId) {
		List<StopPatternStopEntity> patternStops = pattern.getPatternStops().stream()
				.map(stop -> StopPatternStopEntity.builder()
						.stopPatternId(stopPatternId)
						.stopSequence(stop.getStopSequence())
						.stopId(stopIdsByProviderStopId.get(stop.getProviderStopId()))
						.pickupAllowed(stop.getPickupAllowed())
						.dropoffAllowed(stop.getDropoffAllowed())
						.build())
				.toList();

		if (!patternStops.isEmpty()) {
			stopPatternMapper.upsertStopPatternStops(patternStops);
		}
		stopPatternMapper.deleteStopPatternStopsNotInSequences(
				stopPatternId,
				patternStops.stream().map(StopPatternStopEntity::getStopSequence).toList());
	}

	private StopPatternSyncKey createStopPatternKey(StopPatternSyncRequest pattern) {
		return StopPatternSyncKey.builder()
				.providerPatternId(pattern.getProviderPatternId())
				.validFrom(pattern.getValidFrom())
				.build();
	}

	private void deactivateMissingStopPatterns(
			UUID lineId,
			List<StopPatternEntity> retainedPatterns) {
		stopPatternMapper.deactivateStopPatternsNotInBusinessKeys(lineId, retainedPatterns);
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
