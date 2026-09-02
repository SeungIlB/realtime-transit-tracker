package com.realtimetransit.backend.transit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
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
		arrivalService = new ArrivalServiceImpl(
				arrivalQueryMapper,
				Clock.fixed(NOW, ZoneOffset.UTC),
				externalCollectionService);
	}

	@Test
	void returnsTwoMinuteFreshUpcomingArrivalsAsResponses() {
		UUID lineId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID alightingStopId = UUID.randomUUID();
		UUID currentStopId = UUID.randomUUID();
		when(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId, boardingStopId, alightingStopId, NOW, NOW.minusSeconds(120), 2))
				.thenReturn(List.of(new UpcomingArrivalEntity(
						1L, 2L, "vehicle-1", lineId, boardingStopId,
						NOW.plusSeconds(180), NOW.plusSeconds(120), NOW.plusSeconds(240),
						3, "PROVIDER", "HIGH", "APPROACHING", currentStopId, 4,
						NOW.minusSeconds(10), NOW.minusSeconds(5))));

		assertThat(arrivalService.findUpcomingArrivals(lineId, boardingStopId, alightingStopId))
				.singleElement()
				.satisfies(response -> {
					assertThat(response.getProviderVehicleId()).isEqualTo("vehicle-1");
					assertThat(response.getExpectedAt()).isEqualTo(NOW.plusSeconds(180));
					assertThat(response.getRemainingStops()).isEqualTo(3);
					assertThat(response.getCurrentStopId()).isEqualTo(currentStopId);
				});
		verify(arrivalQueryMapper).findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId, boardingStopId, alightingStopId, NOW, NOW.minusSeconds(120), 2);
	}

	@Test
	void returnsEmptyListWhenMapperReturnsNoArrivals() {
		UUID lineId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID alightingStopId = UUID.randomUUID();
		when(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				lineId, boardingStopId, alightingStopId, NOW, NOW.minusSeconds(120), 2))
				.thenReturn(List.of());

		assertThat(arrivalService.findUpcomingArrivals(lineId, boardingStopId, alightingStopId))
				.isEmpty();
	}

	@Test
	void rejectsNullInputsBeforeCallingClockOrMapper() {
		UUID lineId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID alightingStopId = UUID.randomUUID();

		assertThatNullPointerException()
				.isThrownBy(() -> arrivalService.findUpcomingArrivals(null, boardingStopId, alightingStopId))
				.withMessage("lineId must not be null");
		assertThatNullPointerException()
				.isThrownBy(() -> arrivalService.findUpcomingArrivals(lineId, null, alightingStopId))
				.withMessage("boardingStopId must not be null");
		assertThatNullPointerException()
				.isThrownBy(() -> arrivalService.findUpcomingArrivals(lineId, boardingStopId, null))
				.withMessage("alightingStopId must not be null");
		verifyNoInteractions(arrivalQueryMapper);
	}
}

