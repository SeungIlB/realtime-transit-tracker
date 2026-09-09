package com.realtimetransit.backend.journey.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.dto.request.JourneyCreateRequest;
import com.realtimetransit.backend.journey.entity.JourneySessionEntity;
import com.realtimetransit.backend.journey.entity.JourneyStopValidationEntity;
import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;
import com.realtimetransit.backend.journey.repository.JourneyMapper;
import com.realtimetransit.backend.journey.repository.TravelerProfileMapper;
import com.realtimetransit.backend.journey.service.validation.JourneySessionValidator;

@ExtendWith(MockitoExtension.class)
class JourneyServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");
	private static final UUID ANONYMOUS_KEY = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private JourneyMapper journeyMapper;

	@Mock
	private TravelerProfileMapper travelerProfileMapper;

	private JourneyServiceImpl journeyService;

	@BeforeEach
	void setUp() {
		JourneyProperties properties = new JourneyProperties();
		properties.setDefaultTargetProbability(new BigDecimal("0.8000"));
		properties.setSessionTtl(Duration.ofMinutes(30));
		properties.setMaxActiveJourneys(3);
		properties.setDefaultSlowWalkSpeedMps(new BigDecimal("0.90"));
		properties.setDefaultWalkSpeedMps(new BigDecimal("1.30"));
		properties.setDefaultFastWalkSpeedMps(new BigDecimal("1.70"));
		properties.setDefaultRunSpeedMps(new BigDecimal("2.50"));
		journeyService = new JourneyServiceImpl(
				journeyMapper,
				travelerProfileMapper,
				Clock.fixed(NOW, ZoneOffset.UTC),
				properties,
				new JourneySessionValidator(journeyMapper));
	}

	@Test
	void createsJourneyWithDefaultProfileAndTargetProbability() {
		JourneyCreateRequest request = validRequest();
		JourneyStopValidationEntity validatedStops = validatedStops(request);
		TravelerProfileEntity storedProfile = TravelerProfileEntity.builder()
				.id(UUID.randomUUID())
				.anonymousKey(request.getAnonymousKey())
				.build();
		when(journeyMapper.validateJourneyStopsOnSameDirection(
				request.getLineId(), request.getDirectionId(), request.getBoardingStopId(), null))
				.thenReturn(Optional.of(validatedStops));
		when(travelerProfileMapper.findByAnonymousKey(request.getAnonymousKey()))
				.thenReturn(Optional.empty(), Optional.of(storedProfile));

		AtomicReference<JourneySessionEntity> insertedJourney = new AtomicReference<>();
		doAnswer(invocation -> {
			insertedJourney.set(invocation.getArgument(0));
			return null;
		}).when(journeyMapper).insertJourneySession(any(JourneySessionEntity.class));
		when(journeyMapper.findJourneySessionById(any(UUID.class)))
				.thenAnswer(invocation -> Optional.ofNullable(insertedJourney.get()));

		var response = journeyService.createJourney(request);

		assertThat(response.getTargetProbability()).isEqualByComparingTo("0.8000");
		assertThat(response.getStatus()).isEqualTo("ACTIVE");
		assertThat(response.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
		assertThat(response.getBoardingStopId()).isEqualTo(request.getBoardingStopId());

		ArgumentCaptor<TravelerProfileEntity> profileCaptor =
				ArgumentCaptor.forClass(TravelerProfileEntity.class);
		verify(travelerProfileMapper).upsertTravelerProfile(profileCaptor.capture());
		assertThat(profileCaptor.getValue().getWalkSpeedMps()).isEqualByComparingTo("1.30");
		assertThat(profileCaptor.getValue().getSampleCount()).isZero();
	}

	@Test
	void rejectsInvalidRequestBeforeMapperCalls() {
		JourneyCreateRequest request = validRequest();
		request.setTargetProbability(BigDecimal.ZERO);

		assertThatThrownBy(() -> journeyService.createJourney(request))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.INVALID_JOURNEY_REQUEST));
		verifyNoInteractions(journeyMapper, travelerProfileMapper);
	}

	@Test
	void rejectsStopsThatDoNotBelongToSelectedDirection() {
		JourneyCreateRequest request = validRequest();
		when(journeyMapper.validateJourneyStopsOnSameDirection(
				request.getLineId(), request.getDirectionId(), request.getBoardingStopId(), null))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> journeyService.createJourney(request))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.INVALID_JOURNEY_STOPS));
		verifyNoInteractions(travelerProfileMapper);
	}

	@Test
	void rejectsJourneyCreationAtActiveSessionLimit() {
		JourneyCreateRequest request = validRequest();
		TravelerProfileEntity profile = TravelerProfileEntity.builder()
				.id(UUID.randomUUID())
				.anonymousKey(request.getAnonymousKey())
				.build();
		when(journeyMapper.validateJourneyStopsOnSameDirection(
				request.getLineId(), request.getDirectionId(), request.getBoardingStopId(), null))
				.thenReturn(Optional.of(validatedStops(request)));
		when(travelerProfileMapper.findByAnonymousKey(request.getAnonymousKey()))
				.thenReturn(Optional.of(profile));
		when(journeyMapper.countActiveJourneySessions(profile.getId(), NOW)).thenReturn(3);

		assertJourneyError(
				() -> journeyService.createJourney(request),
				ErrorCode.ACTIVE_JOURNEY_LIMIT_EXCEEDED);
	}

	@Test
	void cancelsActiveJourneyAndReturnsStoredResult() {
		UUID journeyId = UUID.randomUUID();
		JourneySessionEntity activeJourney = journey(journeyId, "ACTIVE", NOW.plusSeconds(60));
		JourneySessionEntity cancelledJourney = journey(journeyId, "CANCELLED", NOW.plusSeconds(60));
		when(journeyMapper.findJourneySessionByIdAndAnonymousKey(journeyId, ANONYMOUS_KEY))
				.thenReturn(Optional.of(activeJourney));
		when(journeyMapper.findJourneySessionById(journeyId)).thenReturn(Optional.of(cancelledJourney));
		when(journeyMapper.updateJourneySessionStatus(journeyId, "CANCELLED", NOW)).thenReturn(1);

		var response = journeyService.cancelJourney(journeyId, ANONYMOUS_KEY);

		assertThat(response.getJourneyId()).isEqualTo(journeyId);
		assertThat(response.getStatus()).isEqualTo("CANCELLED");
		verify(journeyMapper).updateJourneySessionStatus(journeyId, "CANCELLED", NOW);
	}

	@Test
	void rejectsMissingJourneyDuringCancellation() {
		UUID journeyId = UUID.randomUUID();
		when(journeyMapper.findJourneySessionByIdAndAnonymousKey(journeyId, ANONYMOUS_KEY)).thenReturn(Optional.empty());

		assertJourneyError(
				() -> journeyService.cancelJourney(journeyId, ANONYMOUS_KEY),
				ErrorCode.JOURNEY_NOT_FOUND);
	}

	@Test
	void rejectsInactiveAndExpiredJourneys() {
		UUID inactiveJourneyId = UUID.randomUUID();
		UUID expiredJourneyId = UUID.randomUUID();
		when(journeyMapper.findJourneySessionByIdAndAnonymousKey(inactiveJourneyId, ANONYMOUS_KEY))
				.thenReturn(Optional.of(journey(inactiveJourneyId, "CANCELLED", NOW.plusSeconds(60))));
		when(journeyMapper.findJourneySessionByIdAndAnonymousKey(expiredJourneyId, ANONYMOUS_KEY))
				.thenReturn(Optional.of(journey(expiredJourneyId, "ACTIVE", NOW)));

		assertJourneyError(
				() -> journeyService.cancelJourney(inactiveJourneyId, ANONYMOUS_KEY),
				ErrorCode.JOURNEY_NOT_ACTIVE);
		assertJourneyError(
				() -> journeyService.cancelJourney(expiredJourneyId, ANONYMOUS_KEY),
				ErrorCode.JOURNEY_NOT_ACTIVE);
	}

	@Test
	void reportsCancellationUpdateFailure() {
		UUID journeyId = UUID.randomUUID();
		when(journeyMapper.findJourneySessionByIdAndAnonymousKey(journeyId, ANONYMOUS_KEY))
				.thenReturn(Optional.of(journey(journeyId, "ACTIVE", NOW.plusSeconds(60))));
		when(journeyMapper.updateJourneySessionStatus(journeyId, "CANCELLED", NOW)).thenReturn(0);

		assertJourneyError(
				() -> journeyService.cancelJourney(journeyId, ANONYMOUS_KEY),
				ErrorCode.JOURNEY_STATUS_UPDATE_FAILED);
	}

	private JourneyCreateRequest validRequest() {
		return JourneyCreateRequest.builder()
				.anonymousKey(UUID.randomUUID())
				.lineId(UUID.randomUUID())
				.directionId(UUID.randomUUID())
				.boardingStopId(UUID.randomUUID())
				.build();
	}

	private JourneyStopValidationEntity validatedStops(JourneyCreateRequest request) {
		return JourneyStopValidationEntity.builder()
				.lineId(request.getLineId())
				.directionId(request.getDirectionId())
				.boardingStopId(request.getBoardingStopId())
				.boardingSequence(1)
				.build();
	}

	private JourneySessionEntity journey(UUID id, String status, Instant expiresAt) {
		return JourneySessionEntity.builder()
				.id(id)
				.status(status)
				.expiresAt(expiresAt)
				.createdAt(NOW.minusSeconds(60))
				.updatedAt(NOW)
				.build();
	}

	private void assertJourneyError(Runnable invocation, ErrorCode errorCode) {
		assertThatThrownBy(invocation::run)
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
	}
}
