package com.realtimetransit.backend.provider.nationalbus.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.realtimetransit.backend.provider.client.dto.ExternalDirection;
import com.realtimetransit.backend.provider.client.dto.ExternalStop;

class NationalBusClientTest {

	@Test
	void projectsVehicleOntoStopsBeforeRequestedBoardingStop() {
		ExternalDirection direction = ExternalDirection.builder()
				.providerDirectionId("0")
				.stops(List.of(
						stop("first", "37.0000", "127.0000"),
						stop("second", "37.0010", "127.0000"),
						stop("target", "37.0020", "127.0000")))
				.build();

		NationalBusClient.RouteProgress progress = NationalBusClient.progress(
				direction, "target", decimal("37.0001"), decimal("127.0000"));

		assertThat(progress).isNotNull();
		assertThat(progress.getCurrentStop().getProviderStopId()).isEqualTo("first");
		assertThat(progress.getTargetIndex() - progress.getCurrentIndex()).isEqualTo(2);
		assertThat(progress.getRemainingDistanceM()).isGreaterThan(200.0);
	}

	@Test
	void rejectsDirectionThatDoesNotContainRequestedStop() {
		ExternalDirection direction = ExternalDirection.builder()
				.providerDirectionId("1")
				.stops(List.of(stop("different", "37.0000", "127.0000")))
				.build();

		assertThat(NationalBusClient.progress(
				direction, "target", decimal("37.0000"), decimal("127.0000")))
				.isNull();
	}

	@Test
	void rejectsVehicleThatAlreadyPassedRequestedBoardingStop() {
		ExternalDirection direction = ExternalDirection.builder()
				.providerDirectionId("0")
				.stops(List.of(
						stop("first", "37.0000", "127.0000"),
						stop("target", "37.0010", "127.0000"),
						stop("passed", "37.0020", "127.0000")))
				.build();

		assertThat(NationalBusClient.progress(
				direction, "target", decimal("37.0020"), decimal("127.0000")))
				.isNull();
	}

	@Test
	void targetsNextOccurrenceWhenCircularRouteRepeatsBoardingStop() {
		ExternalDirection direction = ExternalDirection.builder()
				.providerDirectionId("0")
				.stops(List.of(
						stop("first", "37.0000", "127.0000"),
						stop("target", "37.0010", "127.0000"),
						stop("middle", "37.0020", "127.0000"),
						stop("target", "37.0030", "127.0000"),
						stop("last", "37.0040", "127.0000")))
				.build();

		NationalBusClient.RouteProgress progress = NationalBusClient.progress(
				direction, "target", decimal("37.0021"), decimal("127.0000"));

		assertThat(progress).isNotNull();
		assertThat(progress.getTargetIndex()).isEqualTo(3);
		assertThat(progress.getCurrentStop().getProviderStopId()).isEqualTo("middle");
	}

	@Test
	void usesVehicleBearingToRejectNearbyOppositeDirectionSegment() {
		ExternalDirection direction = ExternalDirection.builder()
				.providerDirectionId("0")
				.stops(List.of(
						stop("west", "37.0000", "127.0000"),
						stop("east", "37.0000", "127.0020"),
						stop("target", "37.0010", "127.0020")))
				.build();

		NationalBusClient.RouteProgress progress = NationalBusClient.progress(
				direction, "target", decimal("37.0000"), decimal("127.0010"), decimal("90"));

		assertThat(progress).isNotNull();
		assertThat(progress.getCurrentStop().getProviderStopId()).isEqualTo("west");
	}

	private static ExternalStop stop(String id, String latitude, String longitude) {
		return ExternalStop.builder().providerStopId(id)
				.latitude(decimal(latitude)).longitude(decimal(longitude)).build();
	}

	private static BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
