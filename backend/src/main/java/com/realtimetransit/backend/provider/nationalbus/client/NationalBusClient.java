package com.realtimetransit.backend.provider.nationalbus.client;

import java.time.Instant;
import java.time.Clock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.cache.TransitCacheNames;
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

@Component
@EnableConfigurationProperties(TransitProviderProperties.class)
public class NationalBusClient extends ProviderClientSupport implements TransitProviderClient {
	private static final int PAGE_SIZE = 1000;
	private final RestClient restClient;
	private final String serviceKey;
	private final NationalBusReferenceClient referenceClient;
	private final Clock clock;

	public NationalBusClient(RestClient.Builder builder, ExternalApiQuotaService quotaService,
			TransitProviderProperties properties, NationalBusReferenceClient referenceClient, Clock clock) {
		super(quotaService);
		var config = properties.getNationalPrecisionBus();
		this.restClient = builder.baseUrl(config.getBaseUrl()).build();
		this.serviceKey = decodeServiceKey(config.getServiceKey());
		this.referenceClient = referenceClient;
		this.clock = clock;
	}

	@Override
	public ExternalApiProvider provider() {
		return ExternalApiProvider.NATIONAL_PRECISION_BUS;
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'NATIONAL:lines:' + #query + ':' + #limit")
	public List<ExternalTransitLine> searchLines(String query, int limit) {
		String normalized = query.strip().toLowerCase(Locale.ROOT);
		return referenceClient.findAllRoutes().stream()
				.filter(item -> safe(text(item, "rteNo")).toLowerCase(Locale.ROOT).contains(normalized))
				.sorted(Comparator.comparing(item -> safe(text(item, "rteNo"))))
				.limit(limit)
				.map(this::toLine)
				.toList();
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'NATIONAL:route:' + #providerLineId")
	public ExternalRouteReference fetchRoute(String providerLineId) {
		ProviderLineKey key = ProviderLineKey.parse(providerLineId);
		List<JsonNode> routeStops = referenceClient.findStops(key.getMunicipalityCode()).stream()
				.filter(item -> key.getRouteId().equals(text(item, "rteId")))
				.toList();
		List<ExternalDirection> directions = routeStops.stream()
				.collect(java.util.stream.Collectors.groupingBy(item -> fallback(text(item, "drcGbnCd"), "0")))
				.entrySet().stream()
				.sorted(java.util.Map.Entry.comparingByKey())
				.map(entry -> toDirection(entry.getKey(), entry.getValue()))
				.toList();
		return ExternalRouteReference.builder()
				.providerLineId(providerLineId)
				.sourceUpdatedAt(latestInstant(routeStops, "totDt", "yyyyMMddHHmmss"))
				.directions(directions)
				.build();
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.NATIONAL_BUS_LOCATIONS, key = "#providerLineId")
	public List<ExternalArrival> fetchArrivals(String providerLineId, String providerStopId) {
		ProviderLineKey key = ProviderLineKey.parse(providerLineId);
		List<ExternalDirection> directions = fetchRoute(providerLineId).getDirections();
		return fetchAll("/rtm_loc_info", key.getMunicipalityCode()).stream()
				.filter(item -> key.getRouteId().equals(text(item, "rteId")))
				.map(item -> toEstimatedArrival(item, key, providerStopId, directions))
				.filter(java.util.Objects::nonNull)
				.toList();
	}

	private ExternalArrival toEstimatedArrival(
			JsonNode item,
			ProviderLineKey key,
			String providerStopId,
			List<ExternalDirection> directions) {
		BigDecimal latitude = decimal(item, "lat");
		BigDecimal longitude = decimal(item, "lot");
		if (latitude == null || longitude == null) return null;
		BigDecimal bearingDegrees = decimal(item, "oprDrct");
		RouteProgress progress = directions.stream()
				.map(direction -> progress(direction, providerStopId, latitude, longitude, bearingDegrees))
				.filter(java.util.Objects::nonNull)
				.min(Comparator.comparingDouble(RouteProgress::getProjectionDistanceM))
				.orElse(null);
		if (progress == null) return null;
		Instant observedAt = firstInstant(item, "gthrDt", "totDt");
		if (observedAt == null) observedAt = clock.instant();
		BigDecimal reportedSpeed = decimal(item, "oprSpd");
		double speedKph = reportedSpeed == null ? 0 : reportedSpeed.doubleValue();
		double effectiveSpeedMps = Math.max(speedKph, 15.0) / 3.6;
		long remainingSeconds = Math.max(30L, Math.round(progress.getRemainingDistanceM() / effectiveSpeedMps));
		return ExternalArrival.builder()
				.providerVehicleId(text(item, "vhclNo"))
				.providerRunId(key.getRouteId())
				.providerDirectionId(progress.getDirection().getProviderDirectionId())
				.currentProviderStopId(progress.getCurrentStop().getProviderStopId())
				.currentSequence(progress.getCurrentIndex() + 1)
				.expectedAt(observedAt.plusSeconds(remainingSeconds))
				.remainingStops(progress.getTargetIndex() - progress.getCurrentIndex())
				.movementStatus("BETWEEN")
				.latitude(latitude)
				.longitude(longitude)
				.speedKph(reportedSpeed)
				.bearingDegrees(bearingDegrees)
				.positionSource(positionSource(text(item, "evtType")))
				.confidence("LOW")
				.observedAt(observedAt)
				.build();
	}

	static RouteProgress progress(
			ExternalDirection direction,
			String providerStopId,
			BigDecimal latitude,
			BigDecimal longitude) {
		return progress(direction, providerStopId, latitude, longitude, null);
	}

	static RouteProgress progress(
			ExternalDirection direction,
			String providerStopId,
			BigDecimal latitude,
			BigDecimal longitude,
			BigDecimal vehicleBearingDegrees) {
		List<ExternalStop> stops = direction.getStops();
		if (stops.isEmpty()) return null;
		RouteProjection projection = closestProjection(
				stops, latitude, longitude, vehicleBearingDegrees);
		if (projection == null && vehicleBearingDegrees != null) {
			projection = closestProjection(stops, latitude, longitude, null);
		}
		if (projection == null) return null;

		double routePosition = projection.getSegmentIndex() + projection.getFraction();
		int targetIndex = -1;
		for (int index = 0; index < stops.size(); index++) {
			if (providerStopId.equals(stops.get(index).getProviderStopId())
					&& index + 1.0e-6 >= routePosition) {
				targetIndex = index;
				break;
			}
		}
		if (targetIndex < 0) return null;

		int projectedStopIndex = projection.getFraction() >= 1 - 1.0e-6
				? projection.getSegmentIndex() + 1
				: projection.getSegmentIndex();
		int currentIndex = Math.min(projectedStopIndex, targetIndex);
		double remainingDistance = 0;
		int nextSegmentIndex = projection.getSegmentIndex();
		if (projection.getSegmentIndex() < targetIndex) {
			ExternalStop from = stops.get(projection.getSegmentIndex());
			ExternalStop to = stops.get(projection.getSegmentIndex() + 1);
			remainingDistance = distanceMeters(
					from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude())
					* (1 - projection.getFraction());
			nextSegmentIndex++;
		}
		for (int index = nextSegmentIndex; index < targetIndex; index++) {
			ExternalStop from = stops.get(index);
			ExternalStop to = stops.get(index + 1);
			if (from.getLatitude() == null || from.getLongitude() == null
					|| to.getLatitude() == null || to.getLongitude() == null) return null;
			remainingDistance += distanceMeters(
					from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude());
		}
		return new RouteProgress(direction, stops.get(currentIndex), currentIndex, targetIndex,
				projection.getDistanceM(), remainingDistance);
	}

	private static RouteProjection closestProjection(
			List<ExternalStop> stops,
			BigDecimal latitude,
			BigDecimal longitude,
			BigDecimal vehicleBearingDegrees) {
		if (stops.size() == 1) {
			ExternalStop stop = stops.getFirst();
			if (stop.getLatitude() == null || stop.getLongitude() == null) return null;
			return new RouteProjection(0, 0,
					distanceMeters(latitude, longitude, stop.getLatitude(), stop.getLongitude()));
		}
		RouteProjection closest = null;
		for (int index = 0; index < stops.size() - 1; index++) {
			ExternalStop from = stops.get(index);
			ExternalStop to = stops.get(index + 1);
			if (from.getLatitude() == null || from.getLongitude() == null
					|| to.getLatitude() == null || to.getLongitude() == null) continue;
			if (vehicleBearingDegrees != null && angularDifference(
					vehicleBearingDegrees.doubleValue(), bearing(from, to)) > 100) continue;
			double fraction = projectionFraction(latitude, longitude, from, to);
			BigDecimal projectedLatitude = interpolate(from.getLatitude(), to.getLatitude(), fraction);
			BigDecimal projectedLongitude = interpolate(from.getLongitude(), to.getLongitude(), fraction);
			double distance = distanceMeters(latitude, longitude, projectedLatitude, projectedLongitude);
			if (closest == null || distance < closest.getDistanceM()) {
				closest = new RouteProjection(index, fraction, distance);
			}
		}
		return closest;
	}

	private static double projectionFraction(
			BigDecimal latitude,
			BigDecimal longitude,
			ExternalStop from,
			ExternalStop to) {
		double referenceLatitude = Math.toRadians(
				(from.getLatitude().doubleValue() + to.getLatitude().doubleValue()) / 2);
		double segmentX = (to.getLongitude().doubleValue() - from.getLongitude().doubleValue())
				* Math.cos(referenceLatitude);
		double segmentY = to.getLatitude().doubleValue() - from.getLatitude().doubleValue();
		double pointX = (longitude.doubleValue() - from.getLongitude().doubleValue())
				* Math.cos(referenceLatitude);
		double pointY = latitude.doubleValue() - from.getLatitude().doubleValue();
		double denominator = segmentX * segmentX + segmentY * segmentY;
		if (denominator == 0) return 0;
		return Math.clamp((pointX * segmentX + pointY * segmentY) / denominator, 0, 1);
	}

	private static BigDecimal interpolate(BigDecimal from, BigDecimal to, double fraction) {
		return BigDecimal.valueOf(from.doubleValue() + (to.doubleValue() - from.doubleValue()) * fraction);
	}

	private static double bearing(ExternalStop from, ExternalStop to) {
		double lat1 = Math.toRadians(from.getLatitude().doubleValue());
		double lat2 = Math.toRadians(to.getLatitude().doubleValue());
		double longitudeDelta = Math.toRadians(to.getLongitude().doubleValue() - from.getLongitude().doubleValue());
		double y = Math.sin(longitudeDelta) * Math.cos(lat2);
		double x = Math.cos(lat1) * Math.sin(lat2)
				- Math.sin(lat1) * Math.cos(lat2) * Math.cos(longitudeDelta);
		return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
	}

	private static double angularDifference(double first, double second) {
		double difference = Math.abs((first - second) % 360);
		return difference > 180 ? 360 - difference : difference;
	}

	private static double distanceMeters(
			BigDecimal firstLatitude,
			BigDecimal firstLongitude,
			BigDecimal secondLatitude,
			BigDecimal secondLongitude) {
		double lat1 = Math.toRadians(firstLatitude.doubleValue());
		double lat2 = Math.toRadians(secondLatitude.doubleValue());
		double deltaLat = lat2 - lat1;
		double deltaLon = Math.toRadians(secondLongitude.doubleValue() - firstLongitude.doubleValue());
		double haversine = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
		return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
	}

	private List<JsonNode> fetchAll(String path, String municipalityCode) {
		List<JsonNode> result = new ArrayList<>();
		int page = 1;
		int total;
		do {
			JsonNode response = call(path, municipalityCode, page++);
			List<JsonNode> pageItems = items(response);
			if (pageItems.isEmpty()) break;
			result.addAll(pageItems);
			total = integer(response.path("body"), "totalCount") == null ? result.size() : integer(response.path("body"), "totalCount");
		} while (result.size() < total);
		return result;
	}

	private JsonNode call(String path, String municipalityCode, int page) {
		requireKey(serviceKey, provider());
		acquireQuota(provider());
		try {
			JsonNode response = restClient.get().uri(uriBuilder -> {
				uriBuilder.path(path).queryParam("serviceKey", "{serviceKey}").queryParam("pageNo", page)
						.queryParam("numOfRows", PAGE_SIZE).queryParam("type", "json");
				if (municipalityCode != null) {
					uriBuilder.queryParam("stdgCd", municipalityCode);
				}
				return uriBuilder.build(serviceKey);
			}).retrieve().body(JsonNode.class);
			validate(response, path);
			return response;
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, provider() + " " + path);
		}
	}

	private void validate(JsonNode response, String path) {
		if (response == null || !"K0".equals(text(response.path("header"), "resultCode"))) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, provider() + " " + path);
		}
	}

	private ExternalTransitLine toLine(JsonNode item) {
		return ExternalTransitLine.builder()
				.providerLineId(text(item, "stdgCd") + ":" + text(item, "rteId"))
				.publicName(text(item, "rteNo"))
				.operatorName(text(item, "lclgvNm"))
				.routeType(text(item, "rteType"))
				.sourceUpdatedAt(koreaInstant(text(item, "totDt"), "yyyyMMddHHmmss"))
				.build();
	}

	private ExternalDirection toDirection(String directionCode, List<JsonNode> items) {
		List<ExternalStop> stops = items.stream()
				.sorted(Comparator.comparingInt(item -> integer(item, "bstaSn") == null ? Integer.MAX_VALUE : integer(item, "bstaSn")))
				.map(item -> ExternalStop.builder()
						.providerStopId(text(item, "stdgCd") + ":" + text(item, "bstaId"))
						.publicName(text(item, "bstaNm"))
						.platformId(text(item, "bstaNo"))
						.sequence(integer(item, "bstaSn"))
						.latitude(decimal(item, "bstaLat"))
						.longitude(decimal(item, "bstaLot"))
						.build())
				.toList();
		return ExternalDirection.builder().providerDirectionId(directionCode)
				.displayName("0".equals(directionCode) ? "상행" : "하행").stops(stops).build();
	}

	private static List<JsonNode> items(JsonNode response) {
		JsonNode item = response.path("body").path("items").path("item");
		List<JsonNode> result = new ArrayList<>();
		if (item.isArray()) item.forEach(result::add);
		else if (item.isObject()) result.add(item);
		return result;
	}

	private static Instant latestInstant(List<JsonNode> items, String field, String pattern) {
		return items.stream().map(item -> koreaInstant(text(item, field), pattern))
				.filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
	}

	private static Instant firstInstant(JsonNode item, String first, String second) {
		Instant instant = koreaInstant(text(item, first), "yyyy-MM-dd HH:mm:ss");
		return instant != null ? instant : koreaInstant(text(item, second), "yyyyMMddHHmmss");
	}

	private static String safe(String value) { return value == null ? "" : value; }
	private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
	private static String positionSource(String value) {
		return "GNSS".equalsIgnoreCase(value) ? "GNSS" : "GPS";
	}

	private static final class ProviderLineKey {
		private final String municipalityCode;
		private final String routeId;

		private ProviderLineKey(String municipalityCode, String routeId) {
			this.municipalityCode = municipalityCode;
			this.routeId = routeId;
		}

		private String getMunicipalityCode() {
			return municipalityCode;
		}

		private String getRouteId() {
			return routeId;
		}

		private static ProviderLineKey parse(String value) {
			String[] parts = value.split(":", 2);
			if (parts.length != 2) throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid national route ID");
			return new ProviderLineKey(parts[0], parts[1]);
		}
	}

	@lombok.Getter
	@lombok.AllArgsConstructor
	static final class RouteProjection {
		private final int segmentIndex;
		private final double fraction;
		private final double distanceM;
	}

	@lombok.Getter
	@lombok.AllArgsConstructor
	static final class RouteProgress {
		private final ExternalDirection direction;
		private final ExternalStop currentStop;
		private final int currentIndex;
		private final int targetIndex;
		private final double projectionDistanceM;
		private final double remainingDistanceM;
	}
}
