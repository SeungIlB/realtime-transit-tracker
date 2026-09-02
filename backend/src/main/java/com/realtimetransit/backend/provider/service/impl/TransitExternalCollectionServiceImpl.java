package com.realtimetransit.backend.provider.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.TransitProviderClient;
import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.client.dto.ExternalDirection;
import com.realtimetransit.backend.provider.client.dto.ExternalStop;
import com.realtimetransit.backend.provider.dto.request.ArrivalPredictionObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.VehicleRunObservationSaveRequest;
import com.realtimetransit.backend.provider.entity.TransitProviderEntity;
import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.provider.service.ObservationService;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.provider.service.TransitProviderService;
import com.realtimetransit.backend.transit.dto.request.DirectedStopSyncRequest;
import com.realtimetransit.backend.transit.dto.request.RouteDirectionSyncRequest;
import com.realtimetransit.backend.transit.dto.request.StopPatternStopSyncRequest;
import com.realtimetransit.backend.transit.dto.request.StopPatternSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitLineSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitStopSyncRequest;
import com.realtimetransit.backend.transit.entity.RouteDirectionEntity;
import com.realtimetransit.backend.transit.entity.StopPatternEntity;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.repository.RouteDirectionMapper;
import com.realtimetransit.backend.transit.repository.StopPatternMapper;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;
import com.realtimetransit.backend.transit.service.TransitReferenceSyncService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransitExternalCollectionServiceImpl implements TransitExternalCollectionService {
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private final TransitProviderService transitProviderService;
	private final TransitProviderMapper transitProviderMapper;
	private final TransitLineMapper transitLineMapper;
	private final TransitStopMapper transitStopMapper;
	private final RouteDirectionMapper routeDirectionMapper;
	private final StopPatternMapper stopPatternMapper;
	private final TransitReferenceSyncService referenceSyncService;
	private final ObservationService observationService;
	private final Clock clock;

	@Override
	public void searchAndSynchronizeLines(String providerCode, String query, int limit) {
		TransitProviderEntity provider = provider(providerCode);
		TransitProviderClient client = client(providerCode);
		List<TransitLineSyncRequest> requests = client.searchLines(query, limit).stream()
				.map(line -> TransitLineSyncRequest.builder().providerLineId(line.getProviderLineId())
						.publicName(line.getPublicName()).operatorName(line.getOperatorName())
						.routeType(line.getRouteType()).active(true).sourceUpdatedAt(line.getSourceUpdatedAt()).build())
				.toList();
		if (!requests.isEmpty()) referenceSyncService.upsertTransitLines(provider.getId(), requests);
	}

	@Override
	public void synchronizeRoute(UUID lineId) {
		TransitLineEntity line = line(lineId);
		TransitProviderEntity provider = provider(line.getProviderId());
		var route = client(provider.getCode()).fetchRoute(line.getProviderLineId());
		Map<String, ExternalStop> uniqueStops = new LinkedHashMap<>();
		route.getDirections().stream().flatMap(direction -> direction.getStops().stream())
				.forEach(stop -> uniqueStops.putIfAbsent(stop.getProviderStopId(), stop));
		List<TransitStopSyncRequest> stops = uniqueStops.values().stream().map(stop -> TransitStopSyncRequest.builder()
				.providerStopId(stop.getProviderStopId()).publicName(stop.getPublicName()).latitude(stop.getLatitude())
				.longitude(stop.getLongitude()).sourceUpdatedAt(route.getSourceUpdatedAt()).build()).toList();
		Map<String, UUID> stopIds = referenceSyncService.synchronizeTransitStops(provider.getId(), stops);
		List<RouteDirectionSyncRequest> directions = route.getDirections().stream().map(this::directionRequest).toList();
		referenceSyncService.synchronizeRouteDirections(lineId, stopIds, directions);
		List<StopPatternSyncRequest> patterns = route.getDirections().stream().map(this::patternRequest).toList();
		referenceSyncService.synchronizeStopPatterns(lineId, stopIds, patterns);
	}

	@Override
	public void collectArrivals(UUID lineId, UUID boardingStopId) {
		TransitLineEntity line = line(lineId);
		TransitProviderEntity provider = provider(line.getProviderId());
		TransitStopEntity boardingStop = transitStopMapper.findById(boardingStopId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Stop: " + boardingStopId));
		List<StopPatternEntity> patterns = stopPatternMapper.findActiveStopPatternsByLineIdAndServiceDate(
				lineId, LocalDate.now(clock.withZone(KOREA_ZONE)));
		if (patterns.isEmpty()) {
			synchronizeRoute(lineId);
			patterns = stopPatternMapper.findActiveStopPatternsByLineIdAndServiceDate(
					lineId, LocalDate.now(clock.withZone(KOREA_ZONE)));
		}
		List<ExternalArrival> arrivals = client(provider.getCode())
				.fetchArrivals(line.getProviderLineId(), boardingStop.getProviderStopId());
		for (ExternalArrival arrival : arrivals) saveArrival(line, boardingStopId, patterns, arrival);
	}

	private void saveArrival(TransitLineEntity line, UUID boardingStopId,
			List<StopPatternEntity> patterns, ExternalArrival arrival) {
		StopPatternEntity pattern = selectPattern(patterns, arrival.getProviderDirectionId());
		RouteDirectionEntity direction = arrival.getProviderDirectionId() == null ? null
				: routeDirectionMapper.findRouteDirectionByBusinessKey(line.getId(), arrival.getProviderDirectionId()).orElse(null);
		UUID currentStopId = resolveStopId(line.getProviderId(), arrival.getCurrentProviderStopId());
		UUID destinationStopId = resolveStopId(line.getProviderId(), arrival.getDestinationProviderStopId());
		long vehicleObservationId = observationService.saveVehicleRunObservation(
				VehicleRunObservationSaveRequest.builder().lineId(line.getId())
						.directionId(direction == null ? null : direction.getId()).stopPatternId(pattern == null ? null : pattern.getId())
						.providerVehicleId(arrival.getProviderVehicleId()).providerRunId(arrival.getProviderRunId())
						.destinationStopId(destinationStopId).currentStopId(currentStopId).currentSequence(arrival.getCurrentSequence())
						.serviceType("UNKNOWN").movementStatus(arrival.getMovementStatus())
						.latitude(arrival.getLatitude()).longitude(arrival.getLongitude()).speedKph(arrival.getSpeedKph())
						.bearingDegrees(arrival.getBearingDegrees()).positionSource(arrival.getPositionSource())
						.observedAt(arrival.getObservedAt() == null ? clock.instant() : arrival.getObservedAt()).build());
		if (arrival.getExpectedAt() != null && pattern != null) {
			observationService.saveArrivalPredictionObservation(ArrivalPredictionObservationSaveRequest.builder()
					.vehicleRunObservationId(vehicleObservationId).boardingStopId(boardingStopId)
					.expectedAt(arrival.getExpectedAt()).remainingStops(arrival.getRemainingStops())
					.source("PROVIDER").confidence("HIGH")
					.observedAt(arrival.getObservedAt() == null ? clock.instant() : arrival.getObservedAt()).build());
		}
	}

	private RouteDirectionSyncRequest directionRequest(ExternalDirection direction) {
		List<DirectedStopSyncRequest> stops = new ArrayList<>();
		for (int index = 0; index < direction.getStops().size(); index++) {
			ExternalStop stop = direction.getStops().get(index);
			String next = index + 1 < direction.getStops().size() ? direction.getStops().get(index + 1).getProviderStopId() : null;
			stops.add(DirectedStopSyncRequest.builder().providerStopId(stop.getProviderStopId()).stopSequence(index + 1)
					.nextProviderStopId(next).platformId(stop.getPlatformId()).displayDirection(direction.getDisplayName()).build());
		}
		String origin = stops.isEmpty() ? null : stops.getFirst().getProviderStopId();
		String terminal = stops.isEmpty() ? null : stops.getLast().getProviderStopId();
		String next = stops.size() < 2 ? null : stops.get(1).getProviderStopId();
		return RouteDirectionSyncRequest.builder().providerDirectionId(direction.getProviderDirectionId())
				.originProviderStopId(origin).terminalProviderStopId(terminal).representativeNextProviderStopId(next)
				.displayName(direction.getDisplayName()).active(true).directedStops(stops).build();
	}

	private StopPatternSyncRequest patternRequest(ExternalDirection direction) {
		List<StopPatternStopSyncRequest> stops = new ArrayList<>();
		for (int index = 0; index < direction.getStops().size(); index++) {
			stops.add(StopPatternStopSyncRequest.builder().providerStopId(direction.getStops().get(index).getProviderStopId())
					.stopSequence(index + 1).pickupAllowed(true).dropoffAllowed(true).build());
		}
		return StopPatternSyncRequest.builder().providerPatternId(direction.getProviderDirectionId())
				.serviceType("UNKNOWN").validFrom(LocalDate.now(clock.withZone(KOREA_ZONE)))
				.active(true).patternStops(stops).build();
	}

	private TransitProviderClient client(String providerCode) {
		try {
			return transitProviderService.getClient(ExternalApiProvider.valueOf(providerCode));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER, providerCode);
		}
	}

	private TransitProviderEntity provider(String code) {
		return transitProviderMapper.findByCode(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Provider: " + code));
	}

	private TransitProviderEntity provider(long id) {
		return transitProviderMapper.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Provider: " + id));
	}

	private TransitLineEntity line(UUID id) {
		return transitLineMapper.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Line: " + id));
	}

	private UUID resolveStopId(long providerId, String providerStopId) {
		if (providerStopId == null) return null;
		return transitStopMapper.findByProviderResourceId(providerId, providerStopId).map(TransitStopEntity::getId).orElse(null);
	}

	private static StopPatternEntity selectPattern(List<StopPatternEntity> patterns, String directionId) {
		if (directionId != null) {
			for (StopPatternEntity pattern : patterns) if (directionId.equals(pattern.getProviderPatternId())) return pattern;
		}
		return patterns.isEmpty() ? null : patterns.getFirst();
	}
}
