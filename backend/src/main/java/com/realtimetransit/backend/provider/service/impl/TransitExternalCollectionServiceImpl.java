package com.realtimetransit.backend.provider.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.TransitProviderClient;
import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.client.dto.ExternalDirection;
import com.realtimetransit.backend.provider.client.dto.ExternalStop;
import com.realtimetransit.backend.provider.client.dto.ExternalTransitLine;
import com.realtimetransit.backend.provider.dto.request.ArrivalPredictionObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.VehicleRunObservationSaveRequest;
import com.realtimetransit.backend.provider.entity.TransitProviderEntity;
import com.realtimetransit.backend.provider.nationalbus.client.NationalBusClient;
import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.provider.service.ObservationService;
import com.realtimetransit.backend.provider.service.SubwayStopConfirmationService;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.provider.service.TransitProviderService;
import com.realtimetransit.backend.transit.dto.request.DirectedStopSyncRequest;
import com.realtimetransit.backend.transit.dto.request.RouteDirectionSyncRequest;
import com.realtimetransit.backend.transit.dto.request.StopPatternStopSyncRequest;
import com.realtimetransit.backend.transit.dto.request.StopPatternSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitLineSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitStopSyncRequest;
import com.realtimetransit.backend.transit.entity.RouteDirectionEntity;
import com.realtimetransit.backend.transit.entity.AlightingStopStatus;
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
	private static final Duration ARRIVAL_CLOCK_SKEW_TOLERANCE = Duration.ofSeconds(5);
	private final TransitProviderService transitProviderService;
	private final NationalBusClient nationalBusClient;
	private final TransitProviderMapper transitProviderMapper;
	private final TransitLineMapper transitLineMapper;
	private final TransitStopMapper transitStopMapper;
	private final RouteDirectionMapper routeDirectionMapper;
	private final StopPatternMapper stopPatternMapper;
	private final TransitReferenceSyncService referenceSyncService;
	private final ObservationService observationService;
	private final SubwayStopConfirmationService subwayStopConfirmationService;
	private final Clock clock;

	@Override
	public List<String> searchAndSynchronizeLines(
			String providerCode,
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude) {
		TransitProviderEntity provider = provider(providerCode);
		TransitProviderClient client = client(providerCode);
		var externalLines = client.searchLines(query, limit, latitude, longitude);
		List<TransitLineSyncRequest> requests = externalLines.stream()
				.map(line -> TransitLineSyncRequest.builder().providerLineId(line.getProviderLineId())
						.publicName(line.getPublicName()).operatorName(line.getOperatorName())
						.routeType(line.getRouteType()).active(true).sourceUpdatedAt(line.getSourceUpdatedAt()).build())
				.toList();
		if (!requests.isEmpty()) referenceSyncService.upsertTransitLines(provider.getId(), requests);
		return externalLines.stream().map(line -> line.getProviderLineId()).toList();
	}

	@Override
	public List<TransitLineEntity> searchAndSynchronizeNearbyBusLines(
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude) {
		var nationalResult = nationalBusClient.searchNearbyLines(query, limit, latitude, longitude);
		List<TransitLineEntity> nationalLines = synchronizeAndLoadLines(
				"NATIONAL_PRECISION_BUS", nationalResult.getLines());
		if (nationalResult.getNearbyGyeonggiRegionNames().isEmpty()) return nationalLines;

		List<ExternalTransitLine> nearbyGbisExternalLines = client("GBIS").searchLines(query, limit).stream()
				.filter(line -> servesAnyRegion(line, nationalResult.getNearbyGyeonggiRegionNames()))
				.toList();
		List<TransitLineEntity> gbisLines = synchronizeAndLoadLines("GBIS", nearbyGbisExternalLines);
		Set<String> gbisRouteNames = gbisLines.stream()
				.map(line -> line.getPublicName().strip().toLowerCase(Locale.ROOT))
				.collect(Collectors.toSet());
		return java.util.stream.Stream.concat(
				gbisLines.stream(),
				nationalLines.stream().filter(line -> !gbisRouteNames.contains(
						line.getPublicName().strip().toLowerCase(Locale.ROOT))))
				.limit(limit)
				.toList();
	}

	private List<TransitLineEntity> synchronizeAndLoadLines(
			String providerCode,
			List<ExternalTransitLine> externalLines) {
		if (externalLines.isEmpty()) return List.of();
		TransitProviderEntity provider = provider(providerCode);
		List<String> providerLineIds = externalLines.stream()
				.map(ExternalTransitLine::getProviderLineId)
				.distinct()
				.toList();
		List<TransitLineSyncRequest> requests = externalLines.stream()
				.map(line -> TransitLineSyncRequest.builder().providerLineId(line.getProviderLineId())
						.publicName(line.getPublicName()).operatorName(line.getOperatorName())
						.routeType(line.getRouteType()).active(true).sourceUpdatedAt(line.getSourceUpdatedAt()).build())
				.toList();
		referenceSyncService.upsertTransitLines(provider.getId(), requests);
		return transitLineMapper.findActiveLinesByProviderLineIds(provider.getId(), providerLineIds);
	}

	private static boolean servesAnyRegion(ExternalTransitLine line, List<String> regionNames) {
		if (line.getOperatorName() == null) return false;
		String operatorRegion = line.getOperatorName().replace(" ", "").toLowerCase(Locale.ROOT);
		return regionNames.stream()
				.map(TransitExternalCollectionServiceImpl::normalizeRegionName)
				.anyMatch(operatorRegion::contains);
	}

	private static String normalizeRegionName(String regionName) {
		String normalized = regionName.replace(" ", "").toLowerCase(Locale.ROOT);
		return normalized.replaceFirst("[시군]$", "");
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
	public void collectArrivals(UUID lineId, UUID boardingStopId, UUID alightingStopId) {
		TransitLineEntity line = line(lineId);
		TransitProviderEntity provider = provider(line.getProviderId());
		TransitStopEntity boardingStop = transitStopMapper.findById(boardingStopId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Stop: " + boardingStopId));
		TransitStopEntity alightingStop = alightingStopId == null ? null : transitStopMapper.findById(alightingStopId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Stop: " + alightingStopId));
		List<StopPatternEntity> patterns = stopPatternMapper.findActiveStopPatternsByLineIdAndServiceDate(
				lineId, LocalDate.now(clock.withZone(KOREA_ZONE)));
		if (patterns.isEmpty()) {
			synchronizeRoute(lineId);
			patterns = stopPatternMapper.findActiveStopPatternsByLineIdAndServiceDate(
					lineId, LocalDate.now(clock.withZone(KOREA_ZONE)));
		}
		TransitProviderClient providerClient = client(provider.getCode());
		List<ExternalArrival> arrivals = providerClient
				.fetchArrivals(line.getProviderLineId(), boardingStop.getProviderStopId());
		Instant receivedAt = clock.instant();
		for (ExternalArrival arrival : arrivals) {
			AlightingStopStatus alightingStopStatus = alightingStopStatus(
					provider, line, boardingStop, alightingStop, arrival);
			saveArrival(line, boardingStopId, alightingStopId, alightingStopStatus, patterns, arrival, receivedAt);
		}
	}

	private AlightingStopStatus alightingStopStatus(
			TransitProviderEntity provider,
			TransitLineEntity line,
			TransitStopEntity boardingStop,
			TransitStopEntity alightingStop,
			ExternalArrival arrival) {
		if (alightingStop == null) return AlightingStopStatus.NOT_REQUESTED;
		if (!"SUBWAY".equals(provider.getTransportType())) return AlightingStopStatus.STOPS;
		return subwayStopConfirmationService.confirmAlightingStop(
				line, boardingStop, alightingStop, arrival);
	}

	private void saveArrival(TransitLineEntity line, UUID boardingStopId, UUID alightingStopId,
			AlightingStopStatus alightingStopStatus, List<StopPatternEntity> patterns,
			ExternalArrival arrival, Instant receivedAt) {
		UUID currentStopId = resolveStopId(line.getProviderId(), arrival.getCurrentProviderStopId());
		UUID destinationStopId = resolveStopId(line.getProviderId(), arrival.getDestinationProviderStopId());
		List<StopPatternEntity> matchingPatterns = selectPatterns(
				patterns, arrival.getProviderDirectionId(), boardingStopId, destinationStopId);
		if (matchingPatterns.isEmpty()) {
			saveArrivalForPattern(line, boardingStopId, alightingStopId, alightingStopStatus,
					arrival, receivedAt, currentStopId, destinationStopId, null);
			return;
		}
		for (StopPatternEntity pattern : matchingPatterns) {
			saveArrivalForPattern(line, boardingStopId, alightingStopId, alightingStopStatus,
					arrival, receivedAt, currentStopId, destinationStopId, pattern);
		}
	}

	private void saveArrivalForPattern(
			TransitLineEntity line,
			UUID boardingStopId,
			UUID alightingStopId,
			AlightingStopStatus alightingStopStatus,
			ExternalArrival arrival,
			Instant receivedAt,
			UUID currentStopId,
			UUID destinationStopId,
			StopPatternEntity pattern) {
		RouteDirectionEntity direction = pattern == null ? null
				: routeDirectionMapper.findRouteDirectionByBusinessKey(
						line.getId(), pattern.getProviderPatternId()).orElse(null);
		long vehicleObservationId = observationService.saveVehicleRunObservation(
				VehicleRunObservationSaveRequest.builder().lineId(line.getId())
						.directionId(direction == null ? null : direction.getId()).stopPatternId(pattern == null ? null : pattern.getId())
						.providerVehicleId(arrival.getProviderVehicleId()).providerRunId(arrival.getProviderRunId())
						.destinationStopId(destinationStopId).currentStopId(currentStopId).currentSequence(arrival.getCurrentSequence())
					.serviceType(normalizeServiceType(arrival.getServiceType())).movementStatus(arrival.getMovementStatus())
						.latitude(arrival.getLatitude()).longitude(arrival.getLongitude()).speedKph(arrival.getSpeedKph())
						.bearingDegrees(arrival.getBearingDegrees()).positionSource(arrival.getPositionSource())
						.observedAt(arrival.getObservedAt() == null ? clock.instant() : arrival.getObservedAt()).build());
		Instant expectedAt = normalizedExpectedAt(arrival.getExpectedAt(), receivedAt);
		if (expectedAt != null && pattern != null) {
			observationService.saveArrivalPredictionObservation(ArrivalPredictionObservationSaveRequest.builder()
					.vehicleRunObservationId(vehicleObservationId).boardingStopId(boardingStopId)
					.requestedAlightingStopId(alightingStopId)
					.alightingStopConfirmed(alightingStopStatus == AlightingStopStatus.STOPS)
					.alightingStopStatus(alightingStopStatus.name())
					.expectedAt(expectedAt).remainingStops(arrival.getRemainingStops())
					.source("PROVIDER").confidence(normalizeConfidence(arrival.getConfidence()))
					.observedAt(arrival.getObservedAt() == null ? clock.instant() : arrival.getObservedAt()).build());
		}
	}

	private static Instant normalizedExpectedAt(Instant expectedAt, Instant receivedAt) {
		if (expectedAt == null || !expectedAt.isBefore(receivedAt)) return expectedAt;
		Duration lag = Duration.between(expectedAt, receivedAt);
		return lag.compareTo(ARRIVAL_CLOCK_SKEW_TOLERANCE) <= 0 ? receivedAt : null;
	}

	private static String normalizeServiceType(String serviceType) {
		return switch (serviceType == null ? "" : serviceType) {
			case "LOCAL", "EXPRESS", "RAPID", "CIRCULAR", "SHUTTLE" -> serviceType;
			default -> "UNKNOWN";
		};
	}

	private static String normalizeConfidence(String confidence) {
		return switch (confidence == null ? "" : confidence) {
			case "HIGH", "MEDIUM", "LOW", "UNKNOWN" -> confidence;
			default -> "HIGH";
		};
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
		String terminal = stops.isEmpty() || stops.getLast().getProviderStopId().equals(origin)
				? null
				: stops.getLast().getProviderStopId();
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

	private List<StopPatternEntity> selectPatterns(
			List<StopPatternEntity> patterns,
			String directionId,
			UUID boardingStopId,
			UUID destinationStopId) {
		if (directionId == null) return List.of();
		List<StopPatternEntity> directionPatterns = patterns.stream()
				.filter(pattern -> pattern.getProviderPatternId().equals(directionId)
						|| pattern.getProviderPatternId().startsWith(directionId + ":"))
				.toList();
		if (destinationStopId == null) {
			return directionPatterns.stream()
					.filter(pattern -> pattern.getProviderPatternId().equals(directionId))
					.toList();
		}
		List<StopPatternEntity> selected = new ArrayList<>();
		for (StopPatternEntity pattern : directionPatterns) {
			var stops = stopPatternMapper.findStopsByStopPatternId(pattern.getId());
			Integer boardingSequence = stops.stream()
					.filter(stop -> boardingStopId.equals(stop.getStopId()))
					.map(stop -> stop.getStopSequence()).min(Integer::compareTo).orElse(null);
			Integer destinationSequence = stops.stream()
					.filter(stop -> destinationStopId.equals(stop.getStopId()))
					.map(stop -> stop.getStopSequence())
					.filter(sequence -> boardingSequence != null && sequence > boardingSequence)
					.min(Integer::compareTo).orElse(null);
			if (destinationSequence != null) selected.add(pattern);
		}
		return List.copyOf(selected);
	}
}
