package com.realtimetransit.backend.provider.gbis.client;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
public class GbisClient extends ProviderClientSupport implements TransitProviderClient {
	private static final String ROUTE_LIST = "/6410000/busrouteservice/v2/getBusRouteListv2";
	private static final String ROUTE_INFO = "/6410000/busrouteservice/v2/getBusRouteInfoItemv2";
	private static final String ROUTE_STOPS = "/6410000/busrouteservice/v2/getBusRouteStationListv2";
	private static final String ARRIVAL = "/6410000/busarrivalservice/v2/getBusArrivalItemv2";
	private static final String LOCATION = "/6410000/buslocationservice/v2/getBusLocationListv2";
	private final RestClient restClient;
	private final String serviceKey;
	private final Clock clock;
	private final CacheManager cacheManager;

	public GbisClient(RestClient.Builder builder, ExternalApiQuotaService quotaService,
			TransitProviderProperties properties, Clock clock, CacheManager cacheManager) {
		super(quotaService);
		var config = properties.getGbis();
		this.restClient = builder.baseUrl(config.getBaseUrl()).build();
		this.serviceKey = decodeServiceKey(config.getServiceKey());
		this.clock = clock;
		this.cacheManager = cacheManager;
	}

	@Override
	public ExternalApiProvider provider() {
		return ExternalApiProvider.GBIS;
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'GBIS:lines:' + #query + ':' + #limit")
	public List<ExternalTransitLine> searchLines(String query, int limit) {
		return list(call(ROUTE_LIST, "keyword", query), "busRouteList").stream()
				.limit(limit)
				.map(item -> ExternalTransitLine.builder()
						.providerLineId(text(item, "routeId"))
						.publicName(text(item, "routeName"))
						.operatorName(text(item, "regionName"))
						.routeType(fallback(text(item, "routeTypeName"), text(item, "routeTypeCd")))
						.sourceUpdatedAt(clock.instant())
						.build())
				.toList();
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'GBIS:route:' + #providerLineId")
	public ExternalRouteReference fetchRoute(String providerLineId) {
		JsonNode info = first(call(ROUTE_INFO, "routeId", providerLineId), "busRouteInfoItem");
		List<JsonNode> stationNodes = routeStationNodes(providerLineId);
		List<ExternalStop> allStops = stationNodes.stream().map(this::toStop)
				.sorted(Comparator.comparingInt(ExternalStop::getSequence)).toList();
		int turnSequence = integer(info, "turnSeq") == null ? findTurnSequence(stationNodes, allStops.size()) : integer(info, "turnSeq");
		List<ExternalDirection> directions = new ArrayList<>();
		addDirection(directions, "OUTBOUND", "상행", allStops.stream().filter(stop -> stop.getSequence() <= turnSequence).toList());
		addDirection(directions, "INBOUND", "하행", normalize(allStops.stream().filter(stop -> stop.getSequence() >= turnSequence).toList()));
		return ExternalRouteReference.builder().providerLineId(providerLineId)
				.sourceUpdatedAt(clock.instant()).directions(directions).build();
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.GBIS_ARRIVALS, key = "#providerLineId + ':' + #providerStopId")
	public List<ExternalArrival> fetchArrivals(String providerLineId, String providerStopId) {
		List<JsonNode> stationNodes = routeStationNodes(providerLineId);
		List<JsonNode> matchingStations = stationNodes.stream()
				.filter(item -> providerStopId.equals(text(item, "stationId")))
				.toList();
		if (matchingStations.isEmpty()) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "GBIS station " + providerStopId);
		}
		int turnSequence = findTurnSequence(stationNodes, -1);
		if (turnSequence < 0) {
			JsonNode routeInfo = first(call(ROUTE_INFO, "routeId", providerLineId), "busRouteInfoItem");
			Integer configuredTurnSequence = integer(routeInfo, "turnSeq");
			turnSequence = configuredTurnSequence == null ? Integer.MAX_VALUE : configuredTurnSequence;
		}
		Map<String, JsonNode> locationsByPlate = locationsByPlate(providerLineId);
		List<ExternalArrival> arrivals = new ArrayList<>();
		for (JsonNode station : matchingStations) {
			Integer stationSequence = integer(station, "stationSeq");
			JsonNode item = first(callArrival(
					providerLineId, providerStopId, text(station, "stationSeq")), "busArrivalItem");
			addArrival(arrivals, item, 1, stationSequence, turnSequence, locationsByPlate);
			addArrival(arrivals, item, 2, stationSequence, turnSequence, locationsByPlate);
		}
		return arrivals;
	}

	@SuppressWarnings("unchecked")
	private List<JsonNode> routeStationNodes(String providerLineId) {
		var cache = cacheManager.getCache(TransitCacheNames.TRANSIT_STATIC_DATA);
		if (cache == null) {
			return list(call(ROUTE_STOPS, "routeId", providerLineId), "busRouteStationList");
		}
		String cacheKey = "GBIS:raw-route-stops:v1:" + providerLineId;
		var cached = cache.get(cacheKey);
		if (cached != null) return (List<JsonNode>) cached.get();
		List<JsonNode> loaded = list(call(ROUTE_STOPS, "routeId", providerLineId), "busRouteStationList");
		cache.put(cacheKey, loaded);
		return loaded;
	}

	private Map<String, JsonNode> locationsByPlate(String routeId) {
		Map<String, JsonNode> locations = new LinkedHashMap<>();
		for (JsonNode location : list(call(LOCATION, "routeId", routeId), "busLocationList")) {
			String plateNumber = text(location, "plateNo");
			if (plateNumber != null && !plateNumber.isBlank()) locations.put(plateNumber, location);
		}
		return locations;
	}

	private JsonNode callArrival(String routeId, String stationId, String sequence) {
		return call(ARRIVAL, builder -> builder.queryParam("routeId", routeId)
				.queryParam("stationId", stationId).queryParam("staOrder", sequence));
	}

	private JsonNode call(String path, String name, String value) {
		return call(path, builder -> builder.queryParam(name, value));
	}

	private JsonNode call(String path, java.util.function.Consumer<org.springframework.web.util.UriBuilder> customizer) {
		requireKey(serviceKey, provider());
		acquireQuota(provider());
		try {
			JsonNode response = restClient.get().uri(builder -> {
				builder.path(path).queryParam("serviceKey", "{serviceKey}").queryParam("format", "json");
				customizer.accept(builder);
				return builder.build(serviceKey);
			}).retrieve().body(JsonNode.class);
			JsonNode authenticationHeader = response == null ? null
					: response.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
			if ("30".equals(text(authenticationHeader, "returnReasonCode"))) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_AUTHENTICATION_FAILED, "GBIS service key is not registered");
			}
			JsonNode header = response == null ? null : response.path("response").path("msgHeader");
			String resultCode = text(header, "resultCode");
			if ("30".equals(resultCode)) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_AUTHENTICATION_FAILED, "GBIS service key is not registered");
			}
			if (response == null || !"0".equals(resultCode)) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "GBIS " + path);
			}
			return response;
		} catch (HttpClientErrorException.Forbidden exception) {
			if (exception.getResponseBodyAsString().contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_AUTHENTICATION_FAILED, "GBIS service key is not registered");
			}
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "GBIS " + path);
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "GBIS " + path);
		}
	}

	private ExternalStop toStop(JsonNode item) {
		return ExternalStop.builder().providerStopId(text(item, "stationId"))
				.publicName(text(item, "stationName")).platformId(text(item, "mobileNo"))
				.sequence(integer(item, "stationSeq")).latitude(decimal(item, "y"))
				.longitude(decimal(item, "x")).build();
	}

	private void addArrival(List<ExternalArrival> target, JsonNode item, int index,
			Integer targetSequence, int turnSequence, Map<String, JsonNode> locationsByPlate) {
		String vehicleId = text(item, "plateNo" + index);
		Integer minutes = integer(item, "predictTime" + index);
		if (vehicleId == null || vehicleId.isBlank() || minutes == null) return;
		JsonNode location = locationsByPlate.get(vehicleId);
		String directionId = directionId(
				targetSequence,
				location == null ? null : integer(location, "stationSeq"),
				turnSequence);
		target.add(ExternalArrival.builder().providerVehicleId(vehicleId)
				.providerRunId(location == null ? vehicleId : fallback(text(location, "vehId"), vehicleId))
				.providerDirectionId(directionId)
				.currentProviderStopId(location == null ? null : text(location, "stationId"))
				.currentSequence(location == null ? null : integer(location, "stationSeq"))
				.expectedAt(clock.instant().plusSeconds(minutes * 60L))
				.remainingStops(integer(item, "locationNo" + index))
				.movementStatus(location == null ? "BETWEEN" : movementStatus(text(location, "stateCd")))
				.positionSource(location == null ? "ESTIMATED" : "STOP_SEQUENCE")
				.observedAt(clock.instant()).build());
	}

	static String directionId(Integer targetSequence, Integer currentSequence, int turnSequence) {
		if (targetSequence == null || turnSequence == Integer.MAX_VALUE) {
			return "OUTBOUND";
		}
		if (targetSequence > turnSequence) return "INBOUND";
		if (targetSequence < turnSequence) return "OUTBOUND";
		return currentSequence != null && currentSequence > turnSequence ? "INBOUND" : "OUTBOUND";
	}

	private static String movementStatus(String stateCode) {
		return switch (stateCode == null ? "" : stateCode) {
			case "1" -> "ARRIVED";
			case "2" -> "DEPARTED";
			case "0" -> "BETWEEN";
			default -> "UNKNOWN";
		};
	}

	private static List<JsonNode> list(JsonNode response, String field) {
		JsonNode node = response.path("response").path("msgBody").path(field);
		List<JsonNode> result = new ArrayList<>();
		if (node.isArray()) node.forEach(result::add);
		else if (node.isObject()) result.add(node);
		return result;
	}

	private static JsonNode first(JsonNode response, String field) {
		List<JsonNode> items = list(response, field);
		return items.isEmpty() ? response.path("response").path("msgBody").path(field) : items.getFirst();
	}

	private static int findTurnSequence(List<JsonNode> nodes, int fallback) {
		return nodes.stream().filter(node -> "Y".equalsIgnoreCase(text(node, "turnYn")))
				.map(node -> integer(node, "stationSeq")).filter(java.util.Objects::nonNull).findFirst().orElse(fallback);
	}

	private static void addDirection(List<ExternalDirection> target, String id, String name, List<ExternalStop> stops) {
		if (!stops.isEmpty()) target.add(ExternalDirection.builder().providerDirectionId(id).displayName(name).stops(stops).build());
	}

	private static List<ExternalStop> normalize(List<ExternalStop> stops) {
		List<ExternalStop> result = new ArrayList<>();
		for (int i = 0; i < stops.size(); i++) {
			ExternalStop stop = stops.get(i);
			result.add(ExternalStop.builder().providerStopId(stop.getProviderStopId()).publicName(stop.getPublicName())
					.latitude(stop.getLatitude()).longitude(stop.getLongitude()).platformId(stop.getPlatformId()).sequence(i + 1).build());
		}
		return result;
	}

	private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
