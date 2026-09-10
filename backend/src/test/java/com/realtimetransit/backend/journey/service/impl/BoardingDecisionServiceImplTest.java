package com.realtimetransit.backend.journey.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.entity.BoardingPredictionSnapshotEntity;
import com.realtimetransit.backend.journey.entity.JourneySessionEntity;
import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;
import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;
import com.realtimetransit.backend.journey.prediction.BoardingProbabilityCalculator;
import com.realtimetransit.backend.journey.prediction.BoardingRecommendationPolicy;
import com.realtimetransit.backend.journey.prediction.HaversineDistanceCalculator;
import com.realtimetransit.backend.journey.prediction.PredictionConfidenceEvaluator;
import com.realtimetransit.backend.journey.prediction.TravelerEtaEstimator;
import com.realtimetransit.backend.journey.prediction.VehicleEtaEstimator;
import com.realtimetransit.backend.journey.repository.BoardingPredictionMapper;
import com.realtimetransit.backend.journey.repository.JourneyLocationMapper;
import com.realtimetransit.backend.journey.repository.TravelerProfileMapper;
import com.realtimetransit.backend.journey.service.validation.JourneySessionValidator;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
import com.realtimetransit.backend.transit.config.TransitArrivalProperties;
import com.realtimetransit.backend.transit.repository.ArrivalQueryMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class BoardingDecisionServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");
	private static final UUID ANONYMOUS_KEY = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock private JourneySessionValidator journeySessionValidator;
	@Mock private JourneyLocationMapper journeyLocationMapper;
	@Mock private TravelerProfileMapper travelerProfileMapper;
	@Mock private TransitStopMapper transitStopMapper;
	@Mock private ArrivalQueryMapper arrivalQueryMapper;
	@Mock private BoardingPredictionMapper boardingPredictionMapper;
	@Mock private TransitExternalCollectionService externalCollectionService;

	private BoardingDecisionServiceImpl service;
	private JourneyProperties properties;

	@BeforeEach
	void setUp() {
		properties = properties();
		TransitArrivalProperties arrivalProperties = new TransitArrivalProperties();
		arrivalProperties.setObservationFreshness(Duration.ofMinutes(2));
		service = new BoardingDecisionServiceImpl(
				journeySessionValidator,
				journeyLocationMapper,
				travelerProfileMapper,
				transitStopMapper,
				arrivalQueryMapper,
				boardingPredictionMapper,
				externalCollectionService,
				new HaversineDistanceCalculator(),
				new TravelerEtaEstimator(properties),
				new VehicleEtaEstimator(properties),
				new BoardingProbabilityCalculator(properties),
				new BoardingRecommendationPolicy(),
				new PredictionConfidenceEvaluator(properties),
				new ObjectMapper(),
				Clock.fixed(NOW, ZoneOffset.UTC),
				properties,
				arrivalProperties);
	}

	@Test
	void returnsInsufficientDataWhenRecentLocationDoesNotExist() {
		JourneySessionEntity journey = journey();
		when(journeySessionValidator.findActiveJourney(journey.getId(), ANONYMOUS_KEY, NOW)).thenReturn(journey);
		when(journeyLocationMapper.findLatestLocationByJourneyId(journey.getId(), NOW))
				.thenReturn(Optional.empty());

		var response = service.calculateDecision(journey.getId(), ANONYMOUS_KEY);

		assertThat(response.getDecision()).isEqualTo("INSUFFICIENT_DATA");
		assertThat(response.getVehicles()).isEmpty();
		verifyNoInteractions(arrivalQueryMapper, boardingPredictionMapper);
	}

	@Test
	void retriesCollectionAndReturnsNoVehicleAsNormalState() {
		JourneySessionEntity journey = stubPredictionInputs();
		when(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				journey.getLineId(), journey.getBoardingStopId(), journey.getAlightingStopId(), NOW,
				NOW.minusSeconds(120), 2)).thenReturn(List.of(), List.of());

		var response = service.calculateDecision(journey.getId(), ANONYMOUS_KEY);

		assertThat(response.getDecision()).isEqualTo("NO_VEHICLE");
		assertThat(response.getVehicles()).isEmpty();
		verify(externalCollectionService).collectArrivals(
				journey.getLineId(), journey.getBoardingStopId(), journey.getAlightingStopId());
		verifyNoInteractions(boardingPredictionMapper);
	}

	@Test
	void refreshesRecentlyStoredArrivalsBeforeCalculatingRecommendation() {
		JourneySessionEntity journey = stubPredictionInputs();
		UpcomingArrivalEntity storedFarArrival = UpcomingArrivalEntity.builder()
				.arrivalPredictionId(10L)
				.vehicleRunObservationId(20L)
				.providerVehicleId("vehicle-far")
				.expectedAt(NOW.plusSeconds(600))
				.confidence("HIGH")
				.observedAt(NOW.minusSeconds(5))
				.receivedAt(NOW.minusSeconds(3))
				.build();
		UpcomingArrivalEntity arrival = UpcomingArrivalEntity.builder()
				.arrivalPredictionId(11L)
				.vehicleRunObservationId(22L)
				.providerVehicleId("vehicle-1")
				.movementStatus("DEPARTED")
				.currentStopName("이전 정류장")
				.remainingStops(2)
				.expectedAt(NOW.plusSeconds(180))
				.minExpectedAt(NOW.plusSeconds(150))
				.maxExpectedAt(NOW.plusSeconds(210))
				.confidence("HIGH")
				.observedAt(NOW.minusSeconds(5))
				.receivedAt(NOW.minusSeconds(3))
				.build();
		when(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				journey.getLineId(), journey.getBoardingStopId(), journey.getAlightingStopId(), NOW,
				NOW.minusSeconds(120), 2)).thenReturn(List.of(storedFarArrival), List.of(arrival));

		var response = service.calculateDecision(journey.getId(), ANONYMOUS_KEY);

		assertThat(response.getDecision()).isEqualTo("COMFORTABLE");
		assertThat(response.getRecommendedVehicleId()).isEqualTo("vehicle-1");
		assertThat(response.getVehicles()).singleElement()
				.satisfies(vehicle -> {
					assertThat(vehicle.getCurrentStopName()).isEqualTo("이전 정류장");
					assertThat(vehicle.getMovementStatus()).isEqualTo("DEPARTED");
					assertThat(vehicle.getRemainingStops()).isEqualTo(2);
					assertThat(vehicle.getPacePredictions()).hasSize(4);
					assertThat(vehicle.getPacePredictions()).filteredOn(pace -> pace.getRecommended())
							.hasSize(1);
				});

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<BoardingPredictionSnapshotEntity>> captor =
				ArgumentCaptor.forClass(List.class);
		verify(boardingPredictionMapper).insertBoardingPredictionSnapshots(captor.capture());
		assertThat(captor.getValue()).hasSize(4);
		assertThat(captor.getValue()).filteredOn(BoardingPredictionSnapshotEntity::getRecommended)
				.hasSize(1);
		assertThat(captor.getValue()).allSatisfy(snapshot -> {
			assertThat(snapshot.getModelVersion()).isEqualTo("HEURISTIC_V1");
			assertThat(snapshot.getFactorsJson()).contains("HAVERSINE");
		});
		verify(externalCollectionService).collectArrivals(
				journey.getLineId(), journey.getBoardingStopId(), journey.getAlightingStopId());
	}

	private JourneySessionEntity stubPredictionInputs() {
		JourneySessionEntity journey = journey();
		TravelerLocationObservationEntity location = TravelerLocationObservationEntity.builder()
				.id(7L)
				.journeyId(journey.getId())
				.latitude(decimal("37.500000"))
				.longitude(decimal("127.000000"))
				.accuracyM(decimal("10"))
				.observedAt(NOW.minusSeconds(5))
				.build();
		TravelerProfileEntity profile = TravelerProfileEntity.builder()
				.id(journey.getTravelerProfileId())
				.slowWalkSpeedMps(decimal("0.90"))
				.walkSpeedMps(decimal("1.30"))
				.fastWalkSpeedMps(decimal("1.70"))
				.runSpeedMps(decimal("2.50"))
				.build();
		TransitStopEntity stop = TransitStopEntity.builder()
				.id(journey.getBoardingStopId())
				.latitude(decimal("37.500500"))
				.longitude(decimal("127.000500"))
				.build();
		when(journeySessionValidator.findActiveJourney(journey.getId(), ANONYMOUS_KEY, NOW)).thenReturn(journey);
		when(journeyLocationMapper.findLatestLocationByJourneyId(journey.getId(), NOW))
				.thenReturn(Optional.of(location));
		when(travelerProfileMapper.findById(journey.getTravelerProfileId())).thenReturn(Optional.of(profile));
		when(transitStopMapper.findById(journey.getBoardingStopId())).thenReturn(Optional.of(stop));
		return journey;
	}

	private JourneySessionEntity journey() {
		return JourneySessionEntity.builder()
				.id(UUID.randomUUID())
				.travelerProfileId(UUID.randomUUID())
				.lineId(UUID.randomUUID())
				.boardingStopId(UUID.randomUUID())
				.alightingStopId(UUID.randomUUID())
				.targetProbability(decimal("0.8000"))
				.status("ACTIVE")
				.expiresAt(NOW.plusSeconds(300))
				.build();
	}

	private JourneyProperties properties() {
		JourneyProperties value = new JourneyProperties();
		value.setWalkingDetourFactor(decimal("1.20"));
		value.setDeparturePreparationTime(Duration.ofSeconds(10));
		value.setBoardingBuffer(Duration.ofSeconds(15));
		value.setDefaultVehicleEtaUncertainty(Duration.ofSeconds(45));
		value.setPredictionTtl(Duration.ofMinutes(2));
		value.setDecisionRefreshInterval(Duration.ofSeconds(20));
		value.setFreshLocationThreshold(Duration.ofSeconds(30));
		value.setUsableLocationThreshold(Duration.ofMinutes(2));
		value.setHighAccuracyThresholdM(decimal("20"));
		value.setMediumAccuracyThresholdM(decimal("50"));
		return value;
	}

	private BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
