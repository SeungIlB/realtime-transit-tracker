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

	private static ExternalStop stop(String id, String latitude, String longitude) {
		return ExternalStop.builder().providerStopId(id)
				.latitude(decimal(latitude)).longitude(decimal(longitude)).build();
	}

	private static BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
