package com.realtimetransit.backend.provider.seoulsubway.client;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class SeoulSubwayClient extends ProviderClientSupport implements TransitProviderClient {
	private static final Pattern REMAINING_STOPS_PATTERN = Pattern.compile("\\[(\\d+)]번째 전역");
	private static final long FALLBACK_SECONDS_PER_STOP = 120L;
	private static final List<String> LINES = List.of(
			"01호선", "02호선", "03호선", "04호선", "05호선", "06호선", "07호선", "08호선", "09호선",
			"경강선", "경의선", "경춘선", "공항철도", "서해선", "수인분당선", "신림선",
			"신분당선", "우이신설경전철", "GTX-A");
	private final RestClient realtimeClient;
	private final String realtimeServiceKey;
	private final SeoulSubwayReferenceClient referenceClient;
	private final Clock clock;

	public SeoulSubwayClient(RestClient.Builder builder, ExternalApiQuotaService quotaService,
			TransitProviderProperties properties, SeoulSubwayReferenceClient referenceClient, Clock clock) {
		super(quotaService);
		var config = properties.getSeoulSubway();
		this.realtimeClient = builder.clone().baseUrl(config.getBaseUrl()).build();
		this.realtimeServiceKey = config.getServiceKey();
		this.referenceClient = referenceClient;
		this.clock = clock;
	}

	@Override
	public ExternalApiProvider provider() {
		return ExternalApiProvider.SEOUL_SUBWAY;
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'SEOUL:lines:' + #query + ':' + #limit")
	public List<ExternalTransitLine> searchLines(String query, int limit) {
		String normalized = query.strip().toLowerCase(Locale.ROOT);
		return LINES.stream().filter(line -> displayName(line).toLowerCase(Locale.ROOT).contains(normalized))
				.limit(limit).map(line -> ExternalTransitLine.builder().providerLineId(line).publicName(displayName(line))
						.operatorName("서울교통공사/TOPIS").routeType("SUBWAY").sourceUpdatedAt(clock.instant()).build())
				.toList();
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'SEOUL:route:v5:' + #providerLineId")
	public ExternalRouteReference fetchRoute(String providerLineId) {
		List<JsonNode> rows = referenceClient.findAllLineStations().stream()
				.filter(row -> providerLineId.equals(text(row, "LINE_NUM")))
				.sorted(Comparator.comparing(
						row -> fallback(text(row, "FR_CODE"), text(row, "STATION_CD")),
						Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		Map<String, JsonNode> coordinatesByStationId = new HashMap<>();
		Map<String, JsonNode> coordinatesByStationName = new HashMap<>();
		for (JsonNode coordinate : referenceClient.findAllMasterStations()) {
			coordinatesByStationId.putIfAbsent(text(coordinate, "BLDN_ID"), coordinate);
			coordinatesByStationName.putIfAbsent(normalizeStationName(text(coordinate, "BLDN_NM")), coordinate);
		}
		if ("01호선".equals(providerLineId)) {
			return route(providerLineId, oneLineDirections(rows, coordinatesByStationId, coordinatesByStationName));
		}
		if ("02호선".equals(providerLineId)) {
			return route(providerLineId, twoLineDirections(rows, coordinatesByStationId, coordinatesByStationName));
		}
		if ("05호선".equals(providerLineId)) {
			return route(providerLineId, fiveLineDirections(rows, coordinatesByStationId, coordinatesByStationName));
		}
		if ("06호선".equals(providerLineId)) {
			return route(providerLineId, sixLineDirections(rows, coordinatesByStationId, coordinatesByStationName));
		}
		if ("경의선".equals(providerLineId)) {
			return route(providerLineId, gyeonguiLineDirections(rows, coordinatesByStationId, coordinatesByStationName));
		}
		if ("경춘선".equals(providerLineId)) {
			return route(providerLineId, gyeongchunLineDirections(rows, coordinatesByStationId, coordinatesByStationName));
		}
		List<JsonNode> upRows = "02호선".equals(providerLineId) ? rows : rows.reversed();
		List<JsonNode> downRows = "02호선".equals(providerLineId) ? rows.reversed() : rows;
		List<ExternalStop> up = toStops(upRows, coordinatesByStationId, coordinatesByStationName);
		List<ExternalStop> down = toStops(downRows, coordinatesByStationId, coordinatesByStationName);
		return route(providerLineId, List.of(
						ExternalDirection.builder().providerDirectionId("UP").displayName("상행/내선").stops(up).build(),
						ExternalDirection.builder().providerDirectionId("DOWN").displayName("하행/외선").stops(down).build()));
	}

	private ExternalRouteReference route(String providerLineId, List<ExternalDirection> directions) {
		return ExternalRouteReference.builder().providerLineId(providerLineId).sourceUpdatedAt(clock.instant())
				.directions(directions)
				.build();
	}

	static List<ExternalDirection> oneLineDirections(
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		List<JsonNode> northTrunk = rows.stream()
				.filter(row -> isOneLineNorthTrunk(code(row)))
				.sorted(SeoulSubwayClient::compareOneLineNorth)
				.toList();
		List<JsonNode> gyeongin = rows.stream()
				.filter(row -> isPlainCodeInRange(code(row), 142, 161))
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder))
				.toList();
		List<JsonNode> gyeongbu = rows.stream()
				.filter(row -> code(row) != null && code(row).matches("P\\d+") && stationOrder(row) >= 142)
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder))
				.toList();
		List<JsonNode> gwangmyeong = appendUntil(northTrunk, gyeongbu, 144, findByCode(rows, "P144-1"));
		List<JsonNode> seodongtan = appendUntil(northTrunk, gyeongbu, 157, findByCode(rows, "P157-1"));

		List<ExternalDirection> directions = new ArrayList<>();
		addBidirectionalPath(directions, "INCHEON", "인천 방면", concat(northTrunk, gyeongin),
				coordinatesByStationId, coordinatesByStationName);
		addBidirectionalPath(directions, "SINCHANG", "신창 방면", concat(northTrunk, gyeongbu),
				coordinatesByStationId, coordinatesByStationName);
		addBidirectionalPath(directions, "GWANGMYEONG", "광명 방면", gwangmyeong,
				coordinatesByStationId, coordinatesByStationName);
		addBidirectionalPath(directions, "SEODONGTAN", "서동탄 방면", seodongtan,
				coordinatesByStationId, coordinatesByStationName);
		return directions;
	}

	static List<ExternalDirection> twoLineDirections(
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		List<JsonNode> mainLoop = rows.stream()
				.filter(row -> isPlainCodeInRange(code(row), 201, 243))
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder))
				.toList();
		List<JsonNode> innerLoop = concat(mainLoop, mainLoop);
		List<JsonNode> outerLoop = new ArrayList<>(innerLoop);
		java.util.Collections.reverse(outerLoop);
		List<JsonNode> seongsuBranch = branch(rows, "211", "211-");
		List<JsonNode> sinjeongBranch = branch(rows, "234", "234-");

		List<ExternalDirection> directions = new ArrayList<>();
		directions.add(direction("UP", "내선순환", innerLoop, coordinatesByStationId, coordinatesByStationName));
		directions.add(direction("DOWN", "외선순환", outerLoop, coordinatesByStationId, coordinatesByStationName));
		addBidirectionalPath(directions, "SEONGSU_BRANCH", "성수지선", seongsuBranch,
				coordinatesByStationId, coordinatesByStationName);
		addBidirectionalPath(directions, "SINJEONG_BRANCH", "신정지선", sinjeongBranch,
				coordinatesByStationId, coordinatesByStationName);
		return directions;
	}

	static List<ExternalDirection> fiveLineDirections(
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		List<JsonNode> trunk = rows.stream()
				.filter(row -> isPlainCodeInRange(code(row), 510, 548))
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder)).toList();
		List<JsonNode> hanam = rows.stream()
				.filter(row -> isPlainCodeInRange(code(row), 549, 558))
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder)).toList();
		List<JsonNode> macheon = rows.stream()
				.filter(row -> code(row) != null && code(row).matches("P\\d+"))
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder)).toList();
		List<ExternalDirection> directions = new ArrayList<>();
		addBidirectionalPath(directions, "HANAM", "하남검단산 방면", concat(trunk, hanam),
				coordinatesByStationId, coordinatesByStationName);
		addBidirectionalPath(directions, "MACHEON", "마천 방면", concat(trunk, macheon),
				coordinatesByStationId, coordinatesByStationName);
		return directions;
	}

	static List<ExternalDirection> sixLineDirections(
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		JsonNode eungam = findByCode(rows, "610");
		List<JsonNode> loop = rows.stream()
				.filter(row -> isPlainCodeInRange(code(row), 611, 615))
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder)).toList();
		List<JsonNode> main = rows.stream()
				.filter(row -> isPlainCodeInRange(code(row), 616, 648))
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder)).toList();
		List<JsonNode> eastbound = new ArrayList<>(loop);
		if (eungam != null) eastbound.add(eungam);
		eastbound.addAll(main);
		List<JsonNode> westbound = new ArrayList<>(main);
		java.util.Collections.reverse(westbound);
		if (eungam != null) westbound.add(eungam);
		westbound.addAll(loop);
		return List.of(
				direction("DOWN", "신내 방면", eastbound, coordinatesByStationId, coordinatesByStationName),
				direction("UP", "응암순환 방면", westbound, coordinatesByStationId, coordinatesByStationName));
	}

	static List<ExternalDirection> gyeonguiLineDirections(
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		List<JsonNode> west = rows.stream()
				.filter(row -> codeNumber(code(row), "K") >= 315 && codeNumber(code(row), "K") <= 337)
				.sorted(Comparator.comparingInt((JsonNode row) -> codeNumber(code(row), "K")).reversed())
				.toList();
		List<JsonNode> central = new ArrayList<>();
		for (String stationCode : List.of("K314", "K313", "K312", "K826")) {
			JsonNode station = findByCode(rows, stationCode);
			if (station != null) central.add(station);
		}
		rows.stream().filter(row -> codeNumber(code(row), "K") >= 110 && codeNumber(code(row), "K") <= 138)
				.sorted(Comparator.comparingInt(row -> codeNumber(code(row), "K"))).forEach(central::add);
		List<JsonNode> seoulBranch = new ArrayList<>(west);
		for (String stationCode : List.of("P312", "P313")) {
			JsonNode station = findByCode(rows, stationCode);
			if (station != null) seoulBranch.add(station);
		}
		List<ExternalDirection> directions = new ArrayList<>();
		addBidirectionalPath(directions, "CENTRAL", "용문·지평 방면", concat(west, central),
				coordinatesByStationId, coordinatesByStationName);
		addBidirectionalPath(directions, "SEOUL", "서울역 방면", seoulBranch,
				coordinatesByStationId, coordinatesByStationName);
		return directions;
	}

	static List<ExternalDirection> gyeongchunLineDirections(
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		List<JsonNode> east = rows.stream()
				.filter(row -> codeNumber(code(row), "P") >= 120 && codeNumber(code(row), "P") <= 140)
				.sorted(Comparator.comparingInt(row -> codeNumber(code(row), "P"))).toList();
		List<JsonNode> cheongnyangni = new ArrayList<>();
		for (String stationCode : List.of("P117", "P118", "P119")) {
			JsonNode station = findByCode(rows, stationCode);
			if (station != null) cheongnyangni.add(station);
		}
		cheongnyangni.addAll(east);
		List<JsonNode> gwangundae = new ArrayList<>();
		JsonNode gwangundaeStation = findByCode(rows, "P116");
		if (gwangundaeStation != null) gwangundae.add(gwangundaeStation);
		gwangundae.addAll(east);
		List<ExternalDirection> directions = new ArrayList<>();
		addBidirectionalPath(directions, "CHEONGNYANGNI", "춘천 방면", cheongnyangni,
				coordinatesByStationId, coordinatesByStationName);
		addBidirectionalPath(directions, "GWANGUNDAE", "춘천 방면(광운대 계통)", gwangundae,
				coordinatesByStationId, coordinatesByStationName);
		return directions;
	}

	private static void addBidirectionalPath(
			List<ExternalDirection> directions,
			String pathId,
			String displayName,
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		if (rows.isEmpty()) return;
		directions.add(direction("DOWN:" + pathId, displayName, rows,
				coordinatesByStationId, coordinatesByStationName));
		List<JsonNode> reversed = new ArrayList<>(rows);
		java.util.Collections.reverse(reversed);
		directions.add(direction("UP:" + pathId, reverseDisplayName(rows), reversed,
				coordinatesByStationId, coordinatesByStationName));
	}

	private static ExternalDirection direction(
			String id,
			String displayName,
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		return ExternalDirection.builder().providerDirectionId(id).displayName(displayName)
				.stops(toStops(rows, coordinatesByStationId, coordinatesByStationName)).build();
	}

	private static List<JsonNode> branch(List<JsonNode> rows, String junctionCode, String branchPrefix) {
		List<JsonNode> result = new ArrayList<>();
		JsonNode junction = findByCode(rows, junctionCode);
		if (junction != null) result.add(junction);
		rows.stream().filter(row -> code(row) != null && code(row).startsWith(branchPrefix))
				.sorted(Comparator.comparingInt(SeoulSubwayClient::stationOrder))
				.forEach(result::add);
		return result;
	}

	private static List<JsonNode> appendUntil(
			List<JsonNode> trunk,
			List<JsonNode> branch,
			int lastMainCode,
			JsonNode spurTerminal) {
		List<JsonNode> result = new ArrayList<>(trunk);
		branch.stream().filter(row -> stationOrder(row) <= lastMainCode).forEach(result::add);
		if (spurTerminal != null) result.add(spurTerminal);
		return result;
	}

	private static List<JsonNode> concat(List<JsonNode> first, List<JsonNode> second) {
		List<JsonNode> result = new ArrayList<>(first.size() + second.size());
		result.addAll(first);
		result.addAll(second);
		return result;
	}

	private static boolean isOneLineNorthTrunk(String code) {
		if (code == null || code.startsWith("P")) return false;
		if (code.matches("100-[123]")) return true;
		return code.matches("\\d+") && Integer.parseInt(code) >= 100 && Integer.parseInt(code) <= 141;
	}

	private static int compareOneLineNorth(JsonNode first, JsonNode second) {
		return Integer.compare(oneLineNorthOrder(code(first)), oneLineNorthOrder(code(second)));
	}

	private static int oneLineNorthOrder(String code) {
		return switch (code == null ? "" : code) {
			case "100-3" -> 97;
			case "100-2" -> 98;
			case "100-1" -> 99;
			default -> code != null && code.matches("\\d+") ? Integer.parseInt(code) : Integer.MAX_VALUE;
		};
	}

	private static boolean isPlainCodeInRange(String code, int min, int max) {
		return code != null && code.matches("\\d+")
				&& Integer.parseInt(code) >= min && Integer.parseInt(code) <= max;
	}

	private static int codeNumber(String code, String prefix) {
		if (code == null || !code.matches(Pattern.quote(prefix) + "\\d+")) return Integer.MIN_VALUE;
		return Integer.parseInt(code.substring(prefix.length()));
	}

	private static JsonNode findByCode(List<JsonNode> rows, String code) {
		return rows.stream().filter(row -> code.equals(code(row))).findFirst().orElse(null);
	}

	private static String code(JsonNode row) {
		return text(row, "FR_CODE");
	}

	private static String reverseDisplayName(List<JsonNode> rows) {
		return rows.isEmpty() ? "반대 방면" : text(rows.getFirst(), "STATION_NM") + " 방면";
	}

	private static List<ExternalStop> toStops(
			List<JsonNode> rows,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		List<ExternalStop> stops = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			JsonNode row = rows.get(index);
			stops.add(toStop(row, findCoordinate(row, coordinatesByStationId, coordinatesByStationName), index + 1));
		}
		return stops;
	}

	static int stationOrder(JsonNode station) {
		String code = fallback(text(station, "FR_CODE"), text(station, "STATION_CD"));
		if (code == null) return Integer.MAX_VALUE;
		String digits = code.replaceAll("\\D", "");
		try {
			return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
		} catch (NumberFormatException ignored) {
			return Integer.MAX_VALUE;
		}
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.SEOUL_SUBWAY_ARRIVALS, key = "#providerLineId + ':' + #providerStopId")
	public List<ExternalArrival> fetchArrivals(String providerLineId, String providerStopId) {
		List<JsonNode> lineStations = referenceClient.findAllLineStations().stream()
				.filter(row -> providerLineId.equals(text(row, "LINE_NUM")))
				.toList();
		JsonNode station = lineStations.stream()
				.filter(row -> providerStopId.equals(text(row, "STATION_CD")))
				.findFirst().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Subway station " + providerStopId));
		JsonNode response = callRealtime(realtimeStationName(text(station, "STATION_NM")));
		Instant receivedAt = clock.instant();
		List<ExternalArrival> result = new ArrayList<>();
		response.path("realtimeArrivalList").forEach(row -> {
			if (!lineMatches(providerLineId, text(row, "subwayId"))) return;
			Instant observedAt = koreaInstant(text(row, "recptnDt"), "yyyy-MM-dd HH:mm:ss");
			if (observedAt == null) observedAt = receivedAt;
			Integer seconds = integer(row, "barvlDt");
			String arrivalCode = text(row, "arvlCd");
			Integer remainingStops = remainingStops(text(row, "arvlMsg2"), arrivalCode);
			String currentProviderStopId = currentProviderStopId(row, lineStations, providerStopId);
			String destinationProviderStopId = destinationProviderStopId(row, lineStations);
			result.add(ExternalArrival.builder().providerVehicleId(text(row, "btrainNo"))
					.providerRunId(text(row, "btrainNo")).providerDirectionId(directionId(text(row, "updnLine")))
					.serviceType(serviceType(text(row, "btrainSttus"), text(row, "trainLineNm")))
					.destinationProviderStopId(destinationProviderStopId)
					.currentProviderStopId(currentProviderStopId)
					.expectedAt(arrivalExpectedAt(arrivalCode, observedAt, seconds, remainingStops, receivedAt))
					.remainingStops(remainingStops)
					.movementStatus(movementStatus(arrivalCode))
					.positionSource("STOP_SEQUENCE").observedAt(observedAt).build());
		});
		return result;
	}

	static String destinationProviderStopId(JsonNode arrival, List<JsonNode> lineStations) {
		String destinationId = text(arrival, "bstatnId");
		if (destinationId != null) {
			var exactId = lineStations.stream()
					.filter(station -> destinationId.equals(text(station, "STATION_CD")))
					.findFirst();
			if (exactId.isPresent()) return text(exactId.get(), "STATION_CD");
		}
		String destinationName = normalizeStationName(text(arrival, "bstatnNm"));
		if (destinationName == null || destinationName.isBlank()) return null;
		return lineStations.stream()
				.filter(station -> destinationName.equals(normalizeStationName(text(station, "STATION_NM"))))
				.map(station -> text(station, "STATION_CD"))
				.findFirst()
				.orElse(null);
	}

	static Instant arrivalExpectedAt(
			String arrivalCode,
			Instant observedAt,
			Integer remainingSeconds,
			Integer remainingStops,
			Instant receivedAt) {
		Instant providerExpectedAt = remainingSeconds == null
				? null
				: observedAt.plusSeconds(Math.max(remainingSeconds, 0));
		if (providerExpectedAt != null && providerExpectedAt.isAfter(receivedAt)) return providerExpectedAt;
		Integer effectiveRemainingStops = remainingStops != null
				? remainingStops
				: remainingStops(null, arrivalCode);
		if (effectiveRemainingStops != null && effectiveRemainingStops > 0) {
			return observedAt.plusSeconds(effectiveRemainingStops * FALLBACK_SECONDS_PER_STOP);
		}
		return providerExpectedAt;
	}

	static Integer remainingStops(String arrivalMessage) {
		if (arrivalMessage == null) return null;
		Matcher matcher = REMAINING_STOPS_PATTERN.matcher(arrivalMessage);
		return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
	}

	static Integer remainingStops(String arrivalMessage, String arrivalCode) {
		Integer parsed = remainingStops(arrivalMessage);
		if (parsed != null) return parsed;
		return switch (arrivalCode == null ? "" : arrivalCode) {
			case "0", "1", "2" -> 0;
			case "3", "4", "5" -> 1;
			default -> null;
		};
	}

	private static String currentProviderStopId(
			JsonNode arrival,
			List<JsonNode> lineStations,
			String requestedStopId) {
		String arrivalCode = text(arrival, "arvlCd");
		if ("0".equals(arrivalCode) || "1".equals(arrivalCode) || "2".equals(arrivalCode)) {
			return requestedStopId;
		}
		String locationMessage = fallback(text(arrival, "arvlMsg3"), text(arrival, "arvlMsg2"));
		if (locationMessage == null) return null;
		return lineStations.stream()
				.filter(candidate -> {
					String stationName = text(candidate, "STATION_NM");
					return stationName != null && locationMessage.contains(stationName);
				})
				.max(Comparator.comparingInt(candidate -> text(candidate, "STATION_NM").length()))
				.map(candidate -> text(candidate, "STATION_CD"))
				.orElse(null);
	}

	private JsonNode callRealtime(String stationName) {
		requireKey(realtimeServiceKey, provider());
		acquireQuota(provider());
		try {
			JsonNode response = realtimeClient.get()
					.uri("/{key}/json/realtimeStationArrival/0/100/{station}", realtimeServiceKey, stationName)
					.retrieve().body(JsonNode.class);
			if (response == null || !"INFO-000".equals(text(response.path("errorMessage"), "code"))) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "SEOUL_SUBWAY realtime arrival");
			}
			return response;
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "SEOUL_SUBWAY realtime arrival");
		}
	}

	private static JsonNode findCoordinate(
			JsonNode station,
			Map<String, JsonNode> coordinatesByStationId,
			Map<String, JsonNode> coordinatesByStationName) {
		JsonNode coordinate = coordinatesByStationId.get(text(station, "STATION_CD"));
		return coordinate != null
				? coordinate
				: coordinatesByStationName.get(normalizeStationName(text(station, "STATION_NM")));
	}

	private static ExternalStop toStop(JsonNode row, JsonNode coordinate, int sequence) {
		return ExternalStop.builder().providerStopId(text(row, "STATION_CD")).publicName(text(row, "STATION_NM"))
				.latitude(decimal(coordinate, "LAT")).longitude(decimal(coordinate, "LOT"))
				.platformId(text(row, "FR_CODE")).sequence(sequence).build();
	}

	private static String normalizeStationName(String stationName) {
		if (stationName == null) return null;
		return stationName.replaceFirst("\\(.*$", "").replaceFirst("행$", "")
				.replaceFirst("역$", "").replaceAll("\\s+", "");
	}

	private static String directionId(String value) {
		return value != null && (value.contains("하행") || value.contains("외선")) ? "DOWN" : "UP";
	}

	private static String realtimeStationName(String referenceStationName) {
		return "서울역".equals(referenceStationName) ? "서울" : referenceStationName;
	}

	private static String movementStatus(String arrivalCode) {
		return switch (arrivalCode == null ? "" : arrivalCode) {
			case "0", "4" -> "APPROACHING";
			case "1", "5" -> "ARRIVED";
			case "2" -> "DEPARTED";
			case "3", "99" -> "BETWEEN";
			default -> "UNKNOWN";
		};
	}

	static String serviceType(String trainStatus, String trainLineName) {
		String value = fallback(trainStatus, trainLineName);
		if (value == null) return "UNKNOWN";
		if (value.contains("특급")) return "RAPID";
		if (value.contains("급행")) return "EXPRESS";
		if (value.contains("일반")) return "LOCAL";
		return "UNKNOWN";
	}

	private static boolean lineMatches(String line, String subwayId) {
		if (subwayId == null) return false;
		return switch (subwayId) {
			case "1001" -> "01호선".equals(line); case "1002" -> "02호선".equals(line);
			case "1003" -> "03호선".equals(line); case "1004" -> "04호선".equals(line);
			case "1005" -> "05호선".equals(line); case "1006" -> "06호선".equals(line);
			case "1007" -> "07호선".equals(line); case "1008" -> "08호선".equals(line);
			case "1009" -> "09호선".equals(line); case "1032" -> "GTX-A".equals(line);
			case "1063" -> "경의선".equals(line); case "1065" -> "공항철도".equals(line);
			case "1067" -> "경춘선".equals(line); case "1075" -> "수인분당선".equals(line);
			case "1077" -> "신분당선".equals(line); case "1081" -> "경강선".equals(line);
			case "1092" -> "우이신설경전철".equals(line); case "1093" -> "서해선".equals(line);
			case "1094" -> "신림선".equals(line);
			default -> false;
		};
	}

	private static String displayName(String providerLineId) {
		if (providerLineId.matches("0[1-9]호선")) return providerLineId.substring(1);
		if ("경의선".equals(providerLineId)) return "경의중앙선";
		if ("우이신설경전철".equals(providerLineId)) return "우이신설선";
		return providerLineId;
	}

	private static String fallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

}
