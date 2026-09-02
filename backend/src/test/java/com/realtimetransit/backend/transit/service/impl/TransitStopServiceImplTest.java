package com.realtimetransit.backend.transit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.transit.entity.DirectedStopEntity;
import com.realtimetransit.backend.transit.entity.DestinationStopEntity;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;

@ExtendWith(MockitoExtension.class)
class TransitStopServiceImplTest {

	@Mock
	private TransitStopMapper transitStopMapper;
	@Mock
	private TransitExternalCollectionService externalCollectionService;

	@InjectMocks
	private TransitStopServiceImpl transitStopService;

	@Test
	void returnsActiveStopsAsResponses() {
		UUID lineId = UUID.randomUUID();
		UUID directionId = UUID.randomUUID();
		UUID stopId = UUID.randomUUID();
		UUID nextStopId = UUID.randomUUID();
		when(transitStopMapper.findActiveStopsByLineId(lineId)).thenReturn(List.of(
				new DirectedStopEntity(
						directionId, "종점 방면", stopId, "승차 정류장", 1,
						new BigDecimal("37.123456"), new BigDecimal("127.123456"),
						nextStopId, "종점 방면")));

		assertThat(transitStopService.findActiveStopsByLineId(lineId))
				.singleElement()
				.satisfies(response -> {
					assertThat(response.getDirectionId()).isEqualTo(directionId);
					assertThat(response.getStopId()).isEqualTo(stopId);
					assertThat(response.getStopName()).isEqualTo("승차 정류장");
					assertThat(response.getStopSequence()).isEqualTo(1);
					assertThat(response.getNextStopId()).isEqualTo(nextStopId);
				});
		verify(transitStopMapper).findActiveStopsByLineId(lineId);
	}

	@Test
	void returnsEmptyListWhenMapperReturnsNoStops() {
		UUID lineId = UUID.randomUUID();
		when(transitStopMapper.findActiveStopsByLineId(lineId)).thenReturn(List.of());

		assertThat(transitStopService.findActiveStopsByLineId(lineId)).isEmpty();
		verify(transitStopMapper, org.mockito.Mockito.times(2)).findActiveStopsByLineId(lineId);
	}

	@Test
	void rejectsNullLineIdBeforeCallingMapper() {
		assertThatNullPointerException()
				.isThrownBy(() -> transitStopService.findActiveStopsByLineId(null))
				.withMessage("lineId must not be null");
		verifyNoInteractions(transitStopMapper);
	}

	@Test
	void returnsDestinationsAfterBoardingStopAsResponses() {
		UUID lineId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID directionId = UUID.randomUUID();
		UUID destinationStopId = UUID.randomUUID();
		when(transitStopMapper.findDestinationsAfterBoardingStop(lineId, boardingStopId))
				.thenReturn(List.of(new DestinationStopEntity(
						directionId, "종점 방면", destinationStopId, "하차 정류장", 3)));

		assertThat(transitStopService.findDestinationsAfterBoardingStop(lineId, boardingStopId))
				.singleElement()
				.satisfies(response -> {
					assertThat(response.getDirectionId()).isEqualTo(directionId);
					assertThat(response.getStopId()).isEqualTo(destinationStopId);
					assertThat(response.getStopName()).isEqualTo("하차 정류장");
					assertThat(response.getStopSequence()).isEqualTo(3);
				});
		verify(transitStopMapper).findDestinationsAfterBoardingStop(lineId, boardingStopId);
	}

	@Test
	void returnsEmptyDestinationListWhenMapperReturnsNoDestinations() {
		UUID lineId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		when(transitStopMapper.findDestinationsAfterBoardingStop(lineId, boardingStopId))
				.thenReturn(List.of());

		assertThat(transitStopService.findDestinationsAfterBoardingStop(lineId, boardingStopId))
				.isEmpty();
		verify(transitStopMapper).findDestinationsAfterBoardingStop(lineId, boardingStopId);
	}

	@Test
	void rejectsNullDestinationInputsBeforeCallingMapper() {
		UUID lineId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();

		assertThatNullPointerException()
				.isThrownBy(() -> transitStopService.findDestinationsAfterBoardingStop(null, boardingStopId))
				.withMessage("lineId must not be null");
		assertThatNullPointerException()
				.isThrownBy(() -> transitStopService.findDestinationsAfterBoardingStop(lineId, null))
				.withMessage("boardingStopId must not be null");
		verifyNoInteractions(transitStopMapper);
	}
}

