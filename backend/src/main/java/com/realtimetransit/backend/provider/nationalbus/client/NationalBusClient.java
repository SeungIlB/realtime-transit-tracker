package com.realtimetransit.backend.provider.nationalbus.client;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.realtimetransit.backend.common.cache.TransitCacheNames;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.common.quota.ExternalApiQuotaService;
import com.realtimetransit.backend.provider.client.ProviderClientSupport;
import com.realtimetransit.backend.provider.client.TransitProviderClient;
import com.realtimetransit.backend.provider.client.TransitProviderProperties;
import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.client.dto.ExternalDirection;
import com.realtimetransit.backend.provider.client.dto.ExternalRouteReference;
import com.realtimetransit.backend.provider.client.dto.ExternalStop;
import com.realtimetransit.backend.provider.client.dto.ExternalTransitLine;
import com.realtimetransit.backend.provider.nationalbus.dto.NationalBusLineSearchResult;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

@Component
@EnableConfigurationProperties(TransitProviderProperties.class)
public class NationalBusClient extends ProviderClientSupport implements TransitProviderClient {

	private static final String ID_PREFIX = "TAGO:";
	private static final int MAX_NEARBY_CITIES = 3;
	private static final Duration ROUTE_CACHE_TTL = Duration.ofHours(24);
	private static final Duration ARRIVAL_CACHE_TTL = Duration.ofSeconds(15);

	private final NationalBusReferenceClient referenceClient;
	private final Clock clock;
	private final Map<String, CachedValue<ExternalRouteReference>> routeCache = new ConcurrentHashMap<>();
	private final Map<String, CachedValue<List<ExternalArrival>>> arrivalCache = new ConcurrentHashMap<>();

	public NationalBusClient(
			ExternalApiQuotaService quotaService,
			NationalBusReferenceClient referenceClient,
			Clock clock) {
		super(quotaService);
		this.referenceClient = referenceClient;
		this.clock = clock;
	}

	@Override
	public ExternalApiProvider provider() {
		return ExternalApiProvider.NATIONAL_PRECISION_BUS;
	}

	@Override
	public List<ExternalTransitLine> searchLines(String query, int limit) {
		throw new BusinessException(
				ErrorCode.INVALID_REQUEST,
				"latitude and longitude are required for nationwide bus search");
	}

	@Override
	public List<ExternalTransitLine> searchLines(
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude) {
		return searchNearbyLines(query, limit, latitude, longitude).getLines();
	}

	@Cacheable(
			cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA,
			key = "'TAGO:nearby-lines:v3:' + #query + ':' + #limit + ':'"
					+ " + #latitude.setScale(3, T(java.math.RoundingMode).HALF_UP).toPlainString() + ':'"
					+ " + #longitude.setScale(3, T(java.math.RoundingMode).HALF_UP).toPlainString()")
	public NationalBusLineSearchResult searchNearbyLines(
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude) {
		if (latitude == null || longitude == null) {
			throw new BusinessException(
					ErrorCode.INVALID_REQUEST,
					"latitude and longitude are required for nationwide bus search");
		}
		String normalizedQuery = query.strip().toLowerCase(Locale.ROOT);
		List<String> cityCodes = referenceClient.findNearbyCityCodes(latitude, longitude).stream()
				.limit(MAX_NEARBY_CITIES)
				.toList();
		Map<String, ExternalTransitLine> lines = new LinkedHashMap<>();
		for (String cityCode : cityCodes) {
			for (JsonNode item : referenceClient.findRoutes(cityCode, query)) {
				String routeNo = text(item, "routeno");
				if (routeNo == null || !routeNo.toLowerCase(Locale.ROOT).contains(normalizedQuery)) continue;
				ExternalTransitLine line = toLine(cityCode, item);
				lines.putIfAbsent(line.getProviderLineId(), line);
				if (lines.size() >= limit) return nationalSearchResult(lines, cityCodes);
			}
		}
		return nationalSearchResult(lines, cityCodes);
	}

	private NationalBusLineSearchResult nationalSearchResult(
			Map<String, ExternalTransitLine> lines,
			List<String> cityCodes) {
		List<String> nearbyGyeonggiRegionNames = cityCodes.stream().noneMatch(cityCode -> cityCode.startsWith("31"))
				? List.of()
				: cityCodes.stream()
						.map(referenceClient.findCityNamesByCode()::get)
						.filter(Objects::nonNull)
						.toList();
		return NationalBusLineSearchResult.builder()
				.lines(List.copyOf(lines.values()))
				.nearbyGyeonggiRegionNames(nearbyGyeonggiRegionNames)
				.build();
	}

	@Override
	public ExternalRouteReference fetchRoute(String providerLineId) {
		Instant now = clock.instant();
		CachedValue<ExternalRouteReference> cached = routeCache.get(providerLineId);
		if (cached != null && now.isBefore(cached.getExpiresAt())) return cached.getValue();
		ProviderLineKey key = ProviderLineKey.parse(providerLineId);
		List<JsonNode> routeStops = referenceClient.findRouteStops(key.getCityCode(), key.getRouteId());
		Map<String, List<JsonNode>> groupedStops = new LinkedHashMap<>();
		routeStops.stream()
				.sorted(Comparator.comparingInt(NationalBusClient::nodeOrder))
				.forEach(item -> groupedStops.computeIfAbsent(
						fallback(text(item, "updowncd"), "2"), ignored -> new ArrayList<>()).add(item));
		List<ExternalDirection> directions = groupedStops.entrySet().stream()
				.map(entry -> toDirection(key, entry.getKey(), entry.getValue()))
				.filter(direction -> !direction.getStops().isEmpty())
				.toList();
		ExternalRouteReference route = ExternalRouteReference.builder()
				.providerLineId(providerLineId)
				.sourceUpdatedAt(now)
				.directions(directions)
				.build();
		routeCache.put(providerLineId, new CachedValue<>(route, now.plus(ROUTE_CACHE_TTL)));
		return route;
	}

	@Override
	public List<ExternalArrival> fetchArrivals(String providerLineId, String providerStopId) {
		Instant now = clock.instant();
		String cacheKey = providerLineId + ':' + providerStopId;
		CachedValue<List<ExternalArrival>> cached = arrivalCache.get(cacheKey);
		if (cached != null && now.isBefore(cached.getExpiresAt())) return cached.getValue();
		ProviderLineKey lineKey = ProviderLineKey.parse(providerLineId);
		ProviderStopKey stopKey = ProviderStopKey.parse(providerStopId);
		if (!lineKey.getCityCode().equals(stopKey.getCityCode())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "The route and stop city codes do not match");
		}
		List<JsonNode> arrivals = referenceClient.findArrivals(
				lineKey.getCityCode(), lineKey.getRouteId(), stopKey.getNodeId());
		if (arrivals.isEmpty()) {
			List<ExternalArrival> empty = List.of();
			arrivalCache.put(cacheKey, new CachedValue<>(empty, now.plus(ARRIVAL_CACHE_TTL)));
			return empty;
		}
		List<JsonNode> locations = referenceClient.findLocations(lineKey.getCityCode(), lineKey.getRouteId());
		List<ExternalDirection> directions = fetchRoute(providerLineId).getDirections();
		Instant observedAt = now;
		List<ExternalArrival> result = arrivals.stream()
				.map(item -> toArrival(item, lineKey, stopKey, directions, locations, observedAt))
				.filter(Objects::nonNull)
				.toList();
		arrivalCache.put(cacheKey, new CachedValue<>(result, now.plus(ARRIVAL_CACHE_TTL)));
		return result;
	}

	private ExternalArrival toArrival(
			JsonNode arrival,
			ProviderLineKey lineKey,
			ProviderStopKey stopKey,
			List<ExternalDirection> directions,
			List<JsonNode> locations,
			Instant observedAt) {
		Integer arrivalSeconds = integer(arrival, "arrtime");
		Integer remainingStops = integer(arrival, "arrprevstationcnt");
		if (arrivalSeconds == null || arrivalSeconds < 0) return null;
		ArrivalPosition position = resolvePosition(
				directions, locations, stopKey.getNodeId(), remainingStops == null ? 0 : remainingStops);
		if (position == null) return null;
		JsonNode location = position.getLocation();
		String vehicleId = location == null ? null : text(location, "vehicleno");
		if (vehicleId == null) {
			vehicleId = lineKey.getRouteId() + ":" + stopKey.getNodeId() + ":" + observedAt.plusSeconds(arrivalSeconds).getEpochSecond();
		}
		return ExternalArrival.builder()
				.providerVehicleId(vehicleId)
				.providerRunId(lineKey.getRouteId())
				.providerDirectionId(position.getDirection().getProviderDirectionId())
				.serviceType(serviceType(text(arrival, "routetp")))
				.currentProviderStopId(position.getCurrentStop().getProviderStopId())
				.currentSequence(position.getCurrentIndex() + 1)
				.expectedAt(observedAt.plusSeconds(arrivalSeconds))
				.remainingStops(remainingStops)
				.movementStatus("BETWEEN")
				.latitude(location == null ? null : decimal(location, "gpslati"))
				.longitude(location == null ? null : decimal(location, "gpslong"))
				.positionSource(location == null ? "PROVIDER_ESTIMATE" : "GPS")
				.confidence("HIGH")
				.observedAt(observedAt)
				.build();
	}

	static ArrivalPosition resolvePosition(
			List<ExternalDirection> directions,
			List<JsonNode> locations,
			String targetNodeId,
			int remainingStops) {
		ArrivalPosition best = null;
		for (ExternalDirection direction : directions) {
			List<ExternalStop> stops = direction.getStops();
			for (int targetIndex = 0; targetIndex < stops.size(); targetIndex++) {
				if (!nodeId(stops.get(targetIndex).getProviderStopId()).equals(targetNodeId)) continue;
				for (JsonNode location : locations) {
					String currentNodeId = text(location, "nodeid");
					for (int currentIndex = 0; currentIndex <= targetIndex; currentIndex++) {
						if (!nodeId(stops.get(currentIndex).getProviderStopId()).equals(currentNodeId)) continue;
						int score = Math.abs((targetIndex - currentIndex) - remainingStops);
						if (best == null || score < best.getScore()) {
							best = new ArrivalPosition(
									direction, stops.get(currentIndex), currentIndex, location, score);
						}
					}
				}
				if (best == null) {
					int inferredIndex = Math.max(0, targetIndex - remainingStops);
					best = new ArrivalPosition(
							direction, stops.get(inferredIndex), inferredIndex, null, Integer.MAX_VALUE);
				}
			}
		}
		return best;
	}

	private ExternalTransitLine toLine(String cityCode, JsonNode item) {
		String start = text(item, "startnodenm");
		String end = text(item, "endnodenm");
		String operatorName = start == null || end == null
				? null
				: Objects.equals(start, end) ? start + " 순환" : start + " → " + end;
		return ExternalTransitLine.builder()
				.providerLineId(ID_PREFIX + cityCode + ":" + text(item, "routeid"))
				.publicName(text(item, "routeno"))
				.operatorName(operatorName)
				.routeType(text(item, "routetp"))
				.sourceUpdatedAt(clock.instant())
				.build();
	}

	private static ExternalDirection toDirection(
			ProviderLineKey key,
			String directionCode,
			List<JsonNode> items) {
		List<ExternalStop> stops = items.stream()
				.sorted(Comparator.comparingInt(NationalBusClient::nodeOrder))
				.map(item -> ExternalStop.builder()
						.providerStopId(ID_PREFIX + key.getCityCode() + ":" + text(item, "nodeid"))
						.publicName(text(item, "nodenm"))
						.sequence(integer(item, "nodeord"))
						.latitude(decimal(item, "gpslati"))
						.longitude(decimal(item, "gpslong"))
						.build())
				.toList();
		String displayName = "운행 방향";
		if (!stops.isEmpty()) {
			String first = stops.getFirst().getPublicName();
			String last = stops.getLast().getPublicName();
			displayName = Objects.equals(first, last) ? first + " 순환" : first + " → " + last;
		}
		return ExternalDirection.builder()
				.providerDirectionId(ID_PREFIX + directionCode)
				.displayName(displayName)
				.stops(stops)
				.build();
	}

	private static int nodeOrder(JsonNode item) {
		Integer order = integer(item, "nodeord");
		return order == null ? Integer.MAX_VALUE : order;
	}

	private static String nodeId(String providerStopId) {
		String[] parts = providerStopId.split(":", 3);
		return parts.length == 3 ? parts[2] : providerStopId;
	}

	private static String serviceType(String routeType) {
		if (routeType == null) return "UNKNOWN";
		if (routeType.contains("급행")) return "EXPRESS";
		if (routeType.contains("순환")) return "CIRCULAR";
		return "LOCAL";
	}

	private static String fallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	@Getter
	@AllArgsConstructor
	private static final class CachedValue<T> {
		private final T value;
		private final Instant expiresAt;
	}

	@Getter
	@AllArgsConstructor
	static final class ArrivalPosition {
		private final ExternalDirection direction;
		private final ExternalStop currentStop;
		private final int currentIndex;
		private final JsonNode location;
		private final int score;
	}

	@Getter
	@AllArgsConstructor
	private static final class ProviderLineKey {
		private final String cityCode;
		private final String routeId;

		private static ProviderLineKey parse(String value) {
			String[] parts = value == null ? new String[0] : value.split(":", 3);
			if (parts.length != 3 || !"TAGO".equals(parts[0])) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid TAGO route ID");
			}
			return new ProviderLineKey(parts[1], parts[2]);
		}
	}

	@Getter
	@AllArgsConstructor
	private static final class ProviderStopKey {
		private final String cityCode;
		private final String nodeId;

		private static ProviderStopKey parse(String value) {
			String[] parts = value == null ? new String[0] : value.split(":", 3);
			if (parts.length != 3 || !"TAGO".equals(parts[0])) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid TAGO stop ID");
			}
			return new ProviderStopKey(parts[1], parts[2]);
		}
	}
}
