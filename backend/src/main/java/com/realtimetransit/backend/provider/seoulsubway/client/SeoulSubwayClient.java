package com.realtimetransit.backend.provider.seoulsubway.client;

import java.time.Clock;
import java.time.Instant;
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
public class SeoulSubwayClient extends ProviderClientSupport implements TransitProviderClient {
	private static final List<String> LINES = List.of(
			"01호선", "02호선", "03호선", "04호선", "05호선", "06호선", "07호선", "08호선", "09호선",
			"경강선", "경의선", "경춘선", "공항철도", "김포도시철도", "서해선", "수인분당선", "신림선",
			"신분당선", "용인경전철", "우이신설경전철", "의정부경전철", "인천2호선", "인천선", "GTX-A");
	private final RestClient realtimeClient;
	private final RestClient referenceClient;
	private final String serviceKey;
	private final Clock clock;

	public SeoulSubwayClient(RestClient.Builder builder, ExternalApiQuotaService quotaService,
			TransitProviderProperties properties, Clock clock) {
		super(quotaService);
		var config = properties.getSeoulSubway();
		this.realtimeClient = builder.clone().baseUrl(config.getBaseUrl()).build();
		this.referenceClient = builder.clone().baseUrl(config.getReferenceBaseUrl()).build();
		this.serviceKey = config.getServiceKey();
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
	@Cacheable(cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA, key = "'SEOUL:route:' + #providerLineId")
	public ExternalRouteReference fetchRoute(String providerLineId) {
		List<JsonNode> rows = referenceStations().stream()
				.filter(row -> providerLineId.equals(text(row, "LINE_NUM")))
				.sorted(Comparator.comparing(row -> text(row, "STATION_CD")))
				.toList();
		List<ExternalStop> forward = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) forward.add(toStop(rows.get(i), i + 1));
		List<ExternalStop> reverse = new ArrayList<>();
		for (int i = rows.size() - 1; i >= 0; i--) reverse.add(toStop(rows.get(i), rows.size() - i));
		return ExternalRouteReference.builder().providerLineId(providerLineId).sourceUpdatedAt(clock.instant())
				.directions(List.of(
						ExternalDirection.builder().providerDirectionId("UP").displayName("상행/내선").stops(forward).build(),
						ExternalDirection.builder().providerDirectionId("DOWN").displayName("하행/외선").stops(reverse).build()))
				.build();
	}

	@Override
	@Cacheable(cacheNames = TransitCacheNames.SEOUL_SUBWAY_ARRIVALS, key = "#providerLineId + ':' + #providerStopId")
	public List<ExternalArrival> fetchArrivals(String providerLineId, String providerStopId) {
		JsonNode station = referenceStations().stream()
				.filter(row -> providerLineId.equals(text(row, "LINE_NUM")) && providerStopId.equals(text(row, "STATION_CD")))
				.findFirst().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Subway station " + providerStopId));
		JsonNode response = callRealtime(realtimeStationName(text(station, "STATION_NM")));
		List<ExternalArrival> result = new ArrayList<>();
		response.path("realtimeArrivalList").forEach(row -> {
			if (!lineMatches(providerLineId, text(row, "subwayId"))) return;
			Instant observedAt = koreaInstant(text(row, "recptnDt"), "yyyy-MM-dd HH:mm:ss");
			if (observedAt == null) observedAt = clock.instant();
			Integer seconds = integer(row, "barvlDt");
			result.add(ExternalArrival.builder().providerVehicleId(text(row, "btrainNo"))
					.providerRunId(text(row, "btrainNo")).providerDirectionId(directionId(text(row, "updnLine")))
					.expectedAt(seconds == null ? null : observedAt.plusSeconds(seconds))
					.movementStatus(movementStatus(text(row, "arvlCd")))
					.positionSource("STOP_SEQUENCE").observedAt(observedAt).build());
		});
		return result;
	}

	private List<JsonNode> referenceStations() {
		requireKey(serviceKey, provider());
		acquireQuota(provider());
		try {
			JsonNode response = referenceClient.get()
					.uri("/{key}/json/SearchSTNBySubwayLineInfo/1/1000/", serviceKey)
					.retrieve().body(JsonNode.class);
			JsonNode service = response == null ? null : response.path("SearchSTNBySubwayLineInfo");
			if (service == null || !"INFO-000".equals(text(service.path("RESULT"), "CODE"))) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "SEOUL_SUBWAY reference stations");
			}
			List<JsonNode> rows = new ArrayList<>();
			service.path("row").forEach(rows::add);
			return rows;
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "SEOUL_SUBWAY reference stations");
		}
	}

	private JsonNode callRealtime(String stationName) {
		requireKey(serviceKey, provider());
		acquireQuota(provider());
		try {
			JsonNode response = realtimeClient.get()
					.uri("/{key}/json/realtimeStationArrival/0/100/{station}", serviceKey, stationName)
					.retrieve().body(JsonNode.class);
			if (response == null || !"INFO-000".equals(text(response.path("errorMessage"), "code"))) {
				throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "SEOUL_SUBWAY realtime arrival");
			}
			return response;
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "SEOUL_SUBWAY realtime arrival");
		}
	}

	private static ExternalStop toStop(JsonNode row, int sequence) {
		return ExternalStop.builder().providerStopId(text(row, "STATION_CD")).publicName(text(row, "STATION_NM"))
				.platformId(text(row, "FR_CODE")).sequence(sequence).build();
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

	private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
