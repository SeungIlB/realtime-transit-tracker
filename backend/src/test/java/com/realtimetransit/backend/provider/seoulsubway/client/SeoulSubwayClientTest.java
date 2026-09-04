package com.realtimetransit.backend.provider.seoulsubway.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SeoulSubwayClientTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void doesNotReviveAnApproachingTrainWhoseEtaIsAlreadyPast() {
		Instant observedAt = Instant.parse("2026-09-03T05:35:05Z");
		Instant receivedAt = Instant.parse("2026-09-03T05:36:01Z");

		Instant expectedAt = SeoulSubwayClient.arrivalExpectedAt(
				"0", observedAt, 0, null, receivedAt);

		assertThat(expectedAt).isEqualTo(observedAt);
	}

	@Test
	void keepsFutureEtaReportedByProvider() {
		Instant observedAt = Instant.parse("2026-09-03T05:35:50Z");
		Instant receivedAt = Instant.parse("2026-09-03T05:36:01Z");

		Instant expectedAt = SeoulSubwayClient.arrivalExpectedAt(
				"4", observedAt, 90, 1, receivedAt);

		assertThat(expectedAt).isEqualTo(Instant.parse("2026-09-03T05:37:20Z"));
	}

	@Test
	void doesNotTurnAnAlreadyDepartedTrainIntoAnUpcomingTrain() {
		Instant observedAt = Instant.parse("2026-09-03T05:35:05Z");
		Instant receivedAt = Instant.parse("2026-09-03T05:36:01Z");

		Instant expectedAt = SeoulSubwayClient.arrivalExpectedAt(
				"2", observedAt, 0, null, receivedAt);

		assertThat(expectedAt).isEqualTo(observedAt);
	}

	@Test
	void estimatesEtaFromRemainingStopsWhenSeoulApiReturnsZeroSeconds() {
		Instant observedAt = Instant.parse("2026-09-03T05:42:21Z");
		Instant receivedAt = Instant.parse("2026-09-03T05:43:40Z");

		Integer remainingStops = SeoulSubwayClient.remainingStops("[2]번째 전역 (석수)");
		Instant expectedAt = SeoulSubwayClient.arrivalExpectedAt(
				"99", observedAt, 0, remainingStops, receivedAt);

		assertThat(remainingStops).isEqualTo(2);
		assertThat(expectedAt).isEqualTo(observedAt.plusSeconds(240));
	}

	@Test
	void treatsPreviousStationApproachAsOneRemainingStop() {
		Instant observedAt = Instant.parse("2026-09-04T00:10:00Z");
		Instant receivedAt = Instant.parse("2026-09-04T00:10:30Z");

		Integer remainingStops = SeoulSubwayClient.remainingStops("전역 진입", "4");
		Instant expectedAt = SeoulSubwayClient.arrivalExpectedAt(
				"4", observedAt, 0, remainingStops, receivedAt);

		assertThat(remainingStops).isEqualTo(1);
		assertThat(expectedAt).isEqualTo(observedAt.plusSeconds(120));
	}

	@Test
	void doesNotTreatPreviousStationArrivalAsRequestedStationArrival() {
		Instant observedAt = Instant.parse("2026-09-04T00:10:00Z");
		Instant receivedAt = Instant.parse("2026-09-04T00:10:30Z");

		Instant expectedAt = SeoulSubwayClient.arrivalExpectedAt(
				"5", observedAt, 0, null, receivedAt);

		assertThat(expectedAt).isEqualTo(observedAt.plusSeconds(120));
	}

	@Test
	void mapsEveryDocumentedArrivalCodeToItsRemainingStopSemantics() {
		assertThat(SeoulSubwayClient.remainingStops("진입", "0")).isZero();
		assertThat(SeoulSubwayClient.remainingStops("도착", "1")).isZero();
		assertThat(SeoulSubwayClient.remainingStops("출발", "2")).isZero();
		assertThat(SeoulSubwayClient.remainingStops("전역 출발", "3")).isEqualTo(1);
		assertThat(SeoulSubwayClient.remainingStops("전역 진입", "4")).isEqualTo(1);
		assertThat(SeoulSubwayClient.remainingStops("전역 도착", "5")).isEqualTo(1);
		assertThat(SeoulSubwayClient.remainingStops("운행중", "99")).isNull();
		assertThat(SeoulSubwayClient.remainingStops("[3]번째 전역 (안양)", "99")).isEqualTo(3);
	}

	@Test
	void classifiesLimitedStopTrainService() {
		assertThat(SeoulSubwayClient.serviceType("급행", null)).isEqualTo("EXPRESS");
		assertThat(SeoulSubwayClient.serviceType("특급", null)).isEqualTo("RAPID");
		assertThat(SeoulSubwayClient.serviceType("일반", null)).isEqualTo("LOCAL");
	}

	@Test
	void separatesOneLineGyeonginAndGyeongbuBranches() throws Exception {
		List<JsonNode> rows = List.of(
				station("141", "구로", "1701"),
				station("142", "구일", "1813"),
				station("143", "개봉", "1801"),
				station("P142", "가산디지털단지", "1702"),
				station("P143", "독산", "1714"),
				station("P144", "금천구청", "1703"),
				station("P144-1", "광명", "1750"));

		var directions = SeoulSubwayClient.oneLineDirections(rows, new HashMap<>(), new HashMap<>());
		var incheon = directions.stream()
				.filter(direction -> "DOWN:INCHEON".equals(direction.getProviderDirectionId()))
				.findFirst().orElseThrow();
		var sinchang = directions.stream()
				.filter(direction -> "DOWN:SINCHANG".equals(direction.getProviderDirectionId()))
				.findFirst().orElseThrow();

		assertThat(incheon.getStops()).extracting(stop -> stop.getPublicName())
				.containsExactly("구로", "구일", "개봉");
		assertThat(sinchang.getStops()).extracting(stop -> stop.getPublicName())
				.containsExactly("구로", "가산디지털단지", "독산", "금천구청");
	}

	@Test
	void keepsTwoLineMainLoopSeparateFromBranches() throws Exception {
		List<JsonNode> rows = List.of(
				station("201", "시청", "0201"),
				station("202", "을지로입구", "0202"),
				station("211", "성수", "0211"),
				station("211-1", "용답", "0244"),
				station("211-2", "신답", "0245"));

		var directions = SeoulSubwayClient.twoLineDirections(rows, new HashMap<>(), new HashMap<>());
		var loop = directions.stream()
				.filter(direction -> "UP".equals(direction.getProviderDirectionId()))
				.findFirst().orElseThrow();
		var branch = directions.stream()
				.filter(direction -> "DOWN:SEONGSU_BRANCH".equals(direction.getProviderDirectionId()))
				.findFirst().orElseThrow();

		assertThat(loop.getStops()).extracting(stop -> stop.getPublicName())
				.containsExactly("시청", "을지로입구", "성수", "시청", "을지로입구", "성수");
		assertThat(branch.getStops()).extracting(stop -> stop.getPublicName())
				.containsExactly("성수", "용답", "신답");
	}

	private JsonNode station(String externalCode, String name, String stationCode) throws Exception {
		return objectMapper.readTree("""
				{"FR_CODE":"%s","STATION_NM":"%s","STATION_CD":"%s"}
				""".formatted(externalCode, name, stationCode));
	}
}
