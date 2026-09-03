package com.realtimetransit.backend.journey.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.dto.request.JourneyLocationCreateRequest;
import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;
import com.realtimetransit.backend.journey.repository.JourneyLocationMapper;
import com.realtimetransit.backend.journey.service.validation.JourneySessionValidator;

@ExtendWith(MockitoExtension.class)
class JourneyLocationServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");

	@Mock
	private JourneyLocationMapper journeyLocationMapper;

	@Mock
	private JourneySessionValidator journeySessionValidator;

	private JourneyLocationServiceImpl journeyLocationService;

	@BeforeEach
	void setUp() {
		JourneyProperties properties = new JourneyProperties();
		properties.setLocationTtl(Duration.ofMinutes(10));
		properties.setMaxLocationFutureSkew(Duration.ofSeconds(5));
		journeyLocationService = new JourneyLocationServiceImpl(
				journeyLocationMapper,
				Clock.fixed(NOW, ZoneOffset.UTC),
				properties,
				journeySessionValidator);
	}

	@Test
	void storesValidatedLocationExactlyOnce() {
		UUID journeyId = UUID.randomUUID();
		JourneyLocationCreateRequest request = validRequest();
		when(journeyLocationMapper.insertTravelerLocationObservation(any())).thenReturn(42L);

		var response = journeyLocationService.addLocation(journeyId, request);

		assertThat(response.getLocationObservationId()).isEqualTo(42L);
		assertThat(response.getJourneyId()).isEqualTo(journeyId);
		assertThat(response.getObservedAt()).isEqualTo(request.getObservedAt());
		assertThat(response.getReceivedAt()).isEqualTo(NOW);
		assertThat(response.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		verify(journeySessionValidator).findActiveJourney(journeyId, NOW);

		ArgumentCaptor<TravelerLocationObservationEntity> captor =
				ArgumentCaptor.forClass(TravelerLocationObservationEntity.class);
		verify(journeyLocationMapper, times(1)).insertTravelerLocationObservation(captor.capture());
		assertThat(captor.getValue().getLatitude()).isEqualByComparingTo("37.500000");
	}

	@Test
	void rejectsCoordinateOutsideValidRangeBeforeDependencies() {
		JourneyLocationCreateRequest request = validRequest();
		request.setLongitude(new BigDecimal("180.000001"));

		assertLocationError(
				() -> journeyLocationService.addLocation(UUID.randomUUID(), request),
				ErrorCode.INVALID_JOURNEY_LOCATION);
		verifyNoInteractions(journeySessionValidator, journeyLocationMapper);
	}

	@Test
	void rejectsObservationBeyondAllowedFutureSkew() {
		JourneyLocationCreateRequest request = validRequest();
		request.setObservedAt(NOW.plusSeconds(6));

		assertLocationError(
				() -> journeyLocationService.addLocation(UUID.randomUUID(), request),
				ErrorCode.INVALID_JOURNEY_LOCATION);
		verifyNoInteractions(journeySessionValidator, journeyLocationMapper);
	}

	@Test
	void doesNotStoreLocationWhenJourneyIsNotActive() {
		UUID journeyId = UUID.randomUUID();
		doThrow(new BusinessException(ErrorCode.JOURNEY_NOT_ACTIVE))
				.when(journeySessionValidator).findActiveJourney(journeyId, NOW);

		assertLocationError(
				() -> journeyLocationService.addLocation(journeyId, validRequest()),
				ErrorCode.JOURNEY_NOT_ACTIVE);
		verifyNoInteractions(journeyLocationMapper);
	}

	private JourneyLocationCreateRequest validRequest() {
		return JourneyLocationCreateRequest.builder()
				.latitude(new BigDecimal("37.500000"))
				.longitude(new BigDecimal("127.000000"))
				.accuracyM(new BigDecimal("12.50"))
				.speedMps(new BigDecimal("1.30"))
				.observedAt(NOW)
				.build();
	}

	private void assertLocationError(Runnable invocation, ErrorCode errorCode) {
		assertThatThrownBy(invocation::run)
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
	}
}
