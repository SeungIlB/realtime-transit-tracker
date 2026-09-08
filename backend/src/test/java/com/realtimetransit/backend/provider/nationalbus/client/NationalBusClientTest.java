package com.realtimetransit.backend.provider.nationalbus.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.realtimetransit.backend.provider.client.dto.ExternalDirection;
import com.realtimetransit.backend.provider.client.dto.ExternalStop;

import tools.jackson.databind.ObjectMapper;

class NationalBusClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

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
