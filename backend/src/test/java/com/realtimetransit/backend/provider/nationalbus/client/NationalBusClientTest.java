package com.realtimetransit.backend.provider.nationalbus.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.realtimetransit.backend.common.quota.ExternalApiQuotaService;
import com.realtimetransit.backend.provider.client.dto.ExternalDirection;
import com.realtimetransit.backend.provider.client.dto.ExternalStop;

import tools.jackson.databind.ObjectMapper;

class NationalBusClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void identifiesNearbyGyeonggiRegionWhileSearchingLines() {
		NationalBusReferenceClient referenceClient = mock(NationalBusReferenceClient.class);
		when(referenceClient.findNearbyCityCodes(any(BigDecimal.class), any(BigDecimal.class)))
				.thenReturn(List.of("31200"));
		when(referenceClient.findRoutes("31200", "033")).thenReturn(List.of(
				objectMapper.readTree("""
						{"routeid":"GGB229000006","routeno":"033","startnodenm":"금촌","endnodenm":"탄현"}
						""")));
		when(referenceClient.findCityNamesByCode()).thenReturn(Map.of("31200", "파주시"));
		NationalBusClient client = new NationalBusClient(
				mock(ExternalApiQuotaService.class),
				referenceClient,
				Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));

		var result = client.searchNearbyLines(
				"033", 20, new BigDecimal("37.7599"), new BigDecimal("126.7800"));

		assertThat(result.getNearbyGyeonggiRegionNames()).containsExactly("파주시");
		assertThat(result.getLines())
				.singleElement()
				.satisfies(line -> {
					assertThat(line.getPublicName()).isEqualTo("033");
					assertThat(line.getProviderLineId()).isEqualTo("TAGO:31200:GGB229000006");
				});
	}

	@Test
	void matchesOfficialArrivalToClosestVehicleByRemainingStopCount() {
		ExternalDirection direction = direction("TAGO:0", "first", "second", "third", "target");
		var farVehicle = objectMapper.readTree("""
				{"nodeid":"first","vehicleno":"far"}
				""");
		var matchingVehicle = objectMapper.readTree("""
				{"nodeid":"second","vehicleno":"matching"}
				""");

		NationalBusClient.ArrivalPosition position = NationalBusClient.resolvePosition(
				List.of(direction), List.of(farVehicle, matchingVehicle), "target", 2);

		assertThat(position).isNotNull();
		assertThat(position.getDirection().getProviderDirectionId()).isEqualTo("TAGO:0");
		assertThat(position.getCurrentStop().getProviderStopId()).isEqualTo("TAGO:25:second");
		assertThat(position.getLocation()).isSameAs(matchingVehicle);
		assertThat(position.getScore()).isZero();
	}

	@Test
	void choosesDirectionThatStillHasTheBoardingStopAhead() {
		ExternalDirection outbound = direction("TAGO:0", "start", "target", "terminal");
		ExternalDirection inbound = direction("TAGO:1", "terminal", "middle", "target", "start");
		var vehicle = objectMapper.readTree("""
				{"nodeid":"middle","vehicleno":"inbound-bus"}
				""");

		NationalBusClient.ArrivalPosition position = NationalBusClient.resolvePosition(
				List.of(outbound, inbound), List.of(vehicle), "target", 1);

		assertThat(position).isNotNull();
		assertThat(position.getDirection().getProviderDirectionId()).isEqualTo("TAGO:1");
		assertThat(position.getCurrentStop().getProviderStopId()).isEqualTo("TAGO:25:middle");
	}

	@Test
	void infersCurrentStopWhenMunicipalityDoesNotPublishVehicleLocations() {
		ExternalDirection direction = direction("TAGO:2", "first", "second", "third", "target");

		NationalBusClient.ArrivalPosition position = NationalBusClient.resolvePosition(
				List.of(direction), List.of(), "target", 2);

		assertThat(position).isNotNull();
		assertThat(position.getCurrentStop().getProviderStopId()).isEqualTo("TAGO:25:second");
		assertThat(position.getLocation()).isNull();
	}

	@Test
	void usesLaterOccurrenceForCircularRoute() {
		ExternalDirection circular = direction(
				"TAGO:2", "target", "middle", "second", "target");
		var vehicle = objectMapper.readTree("""
				{"nodeid":"second","vehicleno":"circular-bus"}
				""");

		NationalBusClient.ArrivalPosition position = NationalBusClient.resolvePosition(
				List.of(circular), List.of(vehicle), "target", 1);

		assertThat(position).isNotNull();
		assertThat(position.getCurrentStop().getProviderStopId()).isEqualTo("TAGO:25:second");
		assertThat(position.getCurrentIndex()).isEqualTo(2);
	}

	private static ExternalDirection direction(String id, String... stopIds) {
		List<ExternalStop> stops = java.util.Arrays.stream(stopIds)
				.map(stopId -> ExternalStop.builder()
						.providerStopId("TAGO:25:" + stopId)
						.publicName(stopId)
						.build())
				.toList();
		return ExternalDirection.builder()
				.providerDirectionId(id)
				.stops(stops)
				.build();
	}
}
