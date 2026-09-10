package com.realtimetransit.backend.transit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
import com.realtimetransit.backend.transit.config.TransitArrivalProperties;
import com.realtimetransit.backend.transit.repository.ArrivalQueryMapper;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;

@ExtendWith(MockitoExtension.class)
class ArrivalServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-08-31T06:00:00Z");

	@Mock
	private ArrivalQueryMapper arrivalQueryMapper;
	@Mock
	private TransitExternalCollectionService externalCollectionService;

	private ArrivalServiceImpl arrivalService;

	@BeforeEach
	void setUp() {
		TransitArrivalProperties properties = new TransitArrivalProperties();
		properties.setObservationFreshness(Duration.ofMinutes(2));
		arrivalService = new ArrivalServiceImpl(
				arrivalQueryMapper,
				Clock.fixed(NOW, ZoneOffset.UTC),
				externalCollectionService,
				properties);
	}

	@Test
	void refreshesRecentlyStoredArrivalsBeforeReturningCandidates() {
		UUID lineId = UUID.randomUUID();
		UUID directionId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID alightingStopId = UUID.randomUUID();
		UUID currentStopId = UUID.randomUUID();
		UpcomingArrivalEntity storedFarArrival = UpcomingArrivalEntity.builder()
				.arrivalPredictionId(1L)
				.vehicleRunObservationId(2L)
				.providerVehicleId("vehicle-far")
				.expectedAt(NOW.plusSeconds(600))
				.observedAt(NOW.minusSeconds(10))
				.receivedAt(NOW.minusSeconds(5))
				.build();
		UpcomingArrivalEntity refreshedNearArrival = UpcomingArrivalEntity.builder()
				.arrivalPredictionId(3L)
				.vehicleRunObservationId(4L)
				.providerVehicleId("vehicle-near")
				.lineId(lineId)
				.boardingStopId(boardingStopId)
				.expectedAt(NOW.plusSeconds(180))
				.minExpectedAt(NOW.plusSeconds(120))
				.maxExpectedAt(NOW.plusSeconds(240))
				.remainingStops(3)
				.source("PROVIDER")
				.confidence("HIGH")
				.movementStatus("APPROACHING")
				.currentStopId(currentStopId)
				.currentStopName("이전 정류장")
				.currentSequence(4)
				.observedAt(NOW.minusSeconds(10))
				.receivedAt(NOW.minusSeconds(5))
				.build();
		when(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId, boardingStopId, alightingStopId, NOW, NOW.minusSeconds(120), 2))
				.thenReturn(List.of(storedFarArrival), List.of(refreshedNearArrival));

		assertThat(arrivalService.findUpcomingArrivals(lineId, directionId, boardingStopId, alightingStopId))
				.singleElement()
				.satisfies(response -> {
					assertThat(response.getProviderVehicleId()).isEqualTo("vehicle-near");
					assertThat(response.getExpectedAt()).isEqualTo(NOW.plusSeconds(180));
					assertThat(response.getRemainingStops()).isEqualTo(3);
					assertThat(response.getCurrentStopId()).isEqualTo(currentStopId);
					assertThat(response.getCurrentStopName()).isEqualTo("이전 정류장");
				});
		verify(arrivalQueryMapper, times(2)).findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId, boardingStopId, alightingStopId, NOW, NOW.minusSeconds(120), 2);
		verify(externalCollectionService).collectArrivals(lineId, boardingStopId, alightingStopId);
	}

	@Test
	void returnsEmptyListWhenMapperReturnsNoArrivals() {
		UUID lineId = UUID.randomUUID();
		UUID directionId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID alightingStopId = UUID.randomUUID();
		when(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId, boardingStopId, alightingStopId, NOW, NOW.minusSeconds(120), 2))
				.thenReturn(List.of());

		assertThat(arrivalService.findUpcomingArrivals(lineId, directionId, boardingStopId, alightingStopId))
				.isEmpty();
		verify(externalCollectionService).collectArrivals(lineId, boardingStopId, alightingStopId);
	}

	@Test
	void rejectsNullInputsBeforeCallingClockOrMapper() {
		UUID lineId = UUID.randomUUID();
		UUID directionId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID alightingStopId = UUID.randomUUID();

		assertInvalidRequest(() -> arrivalService.findUpcomingArrivals(null, directionId, boardingStopId, alightingStopId));
		assertInvalidRequest(() -> arrivalService.findUpcomingArrivals(lineId, null, boardingStopId, alightingStopId));
		assertInvalidRequest(() -> arrivalService.findUpcomingArrivals(lineId, directionId, null, alightingStopId));
		assertInvalidRequest(() -> arrivalService.findUpcomingArrivals(lineId, directionId, boardingStopId, null));
		verifyNoInteractions(arrivalQueryMapper);
	}

	private void assertInvalidRequest(Runnable invocation) {
		assertThatThrownBy(invocation::run)
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
	}
}

