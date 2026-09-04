package com.realtimetransit.backend.provider.kric.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.realtimetransit.backend.common.cache.TransitCacheNames;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.common.quota.ExternalApiQuotaService;
import com.realtimetransit.backend.provider.client.ProviderClientSupport;
import com.realtimetransit.backend.provider.client.TransitProviderProperties;
import com.realtimetransit.backend.provider.kric.dto.KricStation;
import com.realtimetransit.backend.provider.kric.dto.KricTimetableCall;

import tools.jackson.databind.JsonNode;

@Component
@EnableConfigurationProperties(TransitProviderProperties.class)
public class KricRailwayTimetableClient extends ProviderClientSupport {

	private final RestClient client;
	private final String serviceKey;

	public KricRailwayTimetableClient(
			RestClient.Builder builder,
			ExternalApiQuotaService quotaService,
			TransitProviderProperties properties) {
		super(quotaService);
		var config = properties.getRailwayTimetable();
		this.client = builder.clone().baseUrl(config.getBaseUrl()).build();
		this.serviceKey = decodeServiceKey(config.getServiceKey());
	}

	public boolean isConfigured() {
		return serviceKey != null && !serviceKey.isBlank();
	}

	@Cacheable(
			cacheNames = TransitCacheNames.TRANSIT_STATIC_DATA,
			key = "'KRIC:stations:' + #stationName",
			unless = "#result.isEmpty()")
	public List<KricStation> findStations(String stationName) {
		JsonNode response = call("/convenientInfo/stationInfo", stationName, null, null);
		return objectsContaining(response, "stinNm", "railOprIsttCd", "lnCd", "stinCd").stream()
				.map(node -> KricStation.builder()
						.operatorCode(text(node, "railOprIsttCd"))
						.lineCode(text(node, "lnCd"))
						.stationCode(text(node, "stinCd"))
						.stationName(text(node, "stinNm"))
						.build())
				.filter(station -> sameStationName(station.getStationName(), stationName))
				.toList();
	}

	@Cacheable(
			cacheNames = TransitCacheNames.RAILWAY_TIMETABLE,
			key = "#station.operatorCode + ':' + #station.lineCode + ':' + #station.stationCode + ':' + #dayCode",
			unless = "#result.isEmpty()")
	public List<KricTimetableCall> findTimetable(KricStation station, int dayCode) {
		JsonNode response = call(
				"/trainUseInfo/subwayTimetableExp",
				null,
				station,
				dayCode);
		return objectsContaining(response, "trnNo", "railOprIsttCd", "lnCd", "stinCd").stream()
				.map(node -> KricTimetableCall.builder()
						.operatorCode(text(node, "railOprIsttCd"))
						.lineCode(text(node, "lnCd"))
						.stationCode(text(node, "stinCd"))
						.trainNumber(text(node, "trnNo"))
						.arrivalTime(text(node, "arvTm"))
						.departureTime(text(node, "dptTm"))
						.expressCode(text(node, "exptCd"))
						.dayCode(integer(node, "dayCd"))
						.build())
				.toList();
	}

	private JsonNode call(String path, String stationName, KricStation station, Integer dayCode) {
		requireKey(serviceKey, ExternalApiProvider.RAILWAY_TIMETABLE);
		acquireQuota(ExternalApiProvider.RAILWAY_TIMETABLE);
		try {
			return client.get()
					.uri(builder -> {
						builder.path(path)
								.queryParam("serviceKey", serviceKey)
								.queryParam("format", "json");
						if (stationName != null) builder.queryParam("stinNm", stationName);
						if (station != null) {
							builder.queryParam("railOprIsttCd", station.getOperatorCode())
									.queryParam("lnCd", station.getLineCode())
									.queryParam("stinCd", station.getStationCode());
						}
						if (dayCode != null) builder.queryParam("dayCd", dayCode);
						return builder.build();
					})
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientException exception) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "KRIC " + path);
		}
	}

	static List<JsonNode> objectsContaining(JsonNode root, String... fields) {
		List<JsonNode> result = new ArrayList<>();
		collectObjects(root, result, fields);
		return result;
	}

	private static void collectObjects(JsonNode node, List<JsonNode> result, String... fields) {
		if (node == null || node.isNull()) return;
		if (node.isObject()) {
			boolean containsAll = true;
			for (String field : fields) containsAll &= node.has(field);
			if (containsAll) result.add(node);
		}
		node.forEach(child -> collectObjects(child, result, fields));
	}

	private static boolean sameStationName(String first, String second) {
		return normalizeStationName(first).equals(normalizeStationName(second));
	}

	private static String normalizeStationName(String value) {
		if (value == null) return "";
		return value.replaceFirst("\\(.*$", "")
				.replaceFirst("역$", "")
				.replaceAll("\\s+", "");
	}
}
