package com.realtimetransit.backend.journey.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.dto.request.RouteDecisionRequest;
import com.realtimetransit.backend.journey.dto.request.RouteTransitLegRequest;
import com.realtimetransit.backend.journey.entity.JourneyStopValidationEntity;
import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;
import com.realtimetransit.backend.journey.prediction.BoardingProbabilityCalculator;
import com.realtimetransit.backend.journey.prediction.BoardingRecommendationPolicy;
import com.realtimetransit.backend.journey.prediction.HaversineDistanceCalculator;
import com.realtimetransit.backend.journey.prediction.PredictionConfidenceEvaluator;
import com.realtimetransit.backend.journey.prediction.TravelerEtaEstimator;
import com.realtimetransit.backend.journey.prediction.VehicleEtaEstimator;
import com.realtimetransit.backend.journey.repository.JourneyMapper;
import com.realtimetransit.backend.journey.repository.TravelerProfileMapper;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.config.TransitArrivalProperties;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
import com.realtimetransit.backend.transit.repository.ArrivalQueryMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;

@ExtendWith(MockitoExtension.class)
class RouteDecisionServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-09-10T01:00:00Z");
	private static final UUID ANONYMOUS_KEY = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock private JourneyMapper journeyMapper;
	@Mock private TravelerProfileMapper travelerProfileMapper;
	@Mock private TransitStopMapper transitStopMapper;
	@Mock private ArrivalQueryMapper arrivalQueryMapper;
	@Mock private TransitExternalCollectionService externalCollectionService;

	private RouteDecisionServiceImpl service;
	private JourneyProperties properties;

	@BeforeEach
	void setUp() {
		properties = properties();
		TransitArrivalProperties arrivalProperties = new TransitArrivalProperties();
		arrivalProperties.setObservationFreshness(Duration.ofMinutes(2));
		service = new RouteDecisionServiceImpl(
				journeyMapper, travelerProfileMapper, transitStopMapper, arrivalQueryMapper,
				externalCollectionService, new HaversineDistanceCalculator(),
				new TravelerEtaEstimator(properties), new VehicleEtaEstimator(properties),
				new BoardingProbabilityCalculator(properties), new BoardingRecommendationPolicy(),
				new PredictionConfidenceEvaluator(properties), properties, arrivalProperties,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void combinesBoardingAndTransferProbabilitiesAcrossTwoLegs() {
		RouteTransitLegRequest first = leg("1호선", 5, 0);
		RouteTransitLegRequest second = leg("4호선", 6, 2);
		RouteDecisionRequest request = request(List.of(first, second));
		stubCommon(first, second);
		stubArrivals(first, arrival("train-1", NOW.plusSeconds(300)));
		stubArrivals(second, arrival("train-2", NOW.plusSeconds(900)));

		var response = service.calculateRouteDecision(ANONYMOUS_KEY, request);

		assertThat(response.getLegs()).hasSize(2);
		assertThat(response.getOverallProbability()).isGreaterThan(new BigDecimal("0.5"));
		assertThat(response.getLegs().get(1).getReadyExpectedAt()).isAfter(response.getLegs().get(0).getAlightingExpectedAt());
		assertThat(response.getExpectedArrivalAt()).isEqualTo(response.getLegs().get(1).getAlightingExpectedAt());
	}

	@Test
	void returnsInsufficientDataWhenAnyTransferLegHasNoLiveVehicle() {
		RouteTransitLegRequest first = leg("1호선", 5, 0);
		RouteTransitLegRequest second = leg("4호선", 6, 2);
		stubCommon(first, second);
		stubArrivals(first, arrival("train-1", NOW.plusSeconds(300)));
		stubArrivals(second);

		var response = service.calculateRouteDecision(ANONYMOUS_KEY, request(List.of(first, second)));

		assertThat(response.getDecision()).isEqualTo("INSUFFICIENT_DATA");
		assertThat(response.getReasons()).singleElement().asString().contains("4호선");
	}

	private void stubCommon(RouteTransitLegRequest... legs) {
		when(travelerProfileMapper.findByAnonymousKey(ANONYMOUS_KEY)).thenReturn(Optional.of(profile()));
		when(transitStopMapper.findById(legs[0].getBoardingStopId())).thenReturn(Optional.of(
				TransitStopEntity.builder().id(legs[0].getBoardingStopId())
						.latitude(decimal("37.5")).longitude(decimal("127.0")).build()));
		for (RouteTransitLegRequest leg : legs) {
			when(journeyMapper.validateJourneyStopsOnSameDirection(
					leg.getLineId(), leg.getDirectionId(), leg.getBoardingStopId(), leg.getAlightingStopId()))
					.thenReturn(Optional.of(JourneyStopValidationEntity.builder().lineId(leg.getLineId()).build()));
		}
	}

	private void stubArrivals(RouteTransitLegRequest leg, UpcomingArrivalEntity... arrivals) {
		when(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				leg.getLineId(), leg.getBoardingStopId(), leg.getAlightingStopId(),
				NOW, NOW.minus(Duration.ofMinutes(2)), 2))
				.thenReturn(List.of(arrivals));
	}

	private RouteDecisionRequest request(List<RouteTransitLegRequest> legs) {
		return RouteDecisionRequest.builder()
				.latitude(decimal("37.5"))
				.longitude(decimal("127.0"))
				.accuracyM(decimal("10"))
				.observedAt(NOW)
				.legs(legs)
				.build();
	}

	private RouteTransitLegRequest leg(String name, int sectionMinutes, int transferMinutes) {
		return RouteTransitLegRequest.builder()
				.lineId(UUID.randomUUID())
				.directionId(UUID.randomUUID())
				.boardingStopId(UUID.randomUUID())
				.alightingStopId(UUID.randomUUID())
				.lineName(name)
				.boardingStopName(name + " 출발")
				.alightingStopName(name + " 도착")
				.sectionTimeMinutes(sectionMinutes)
				.transferWalkTimeMinutes(transferMinutes)
				.build();
	}

	private UpcomingArrivalEntity arrival(String vehicleId, Instant expectedAt) {
		return UpcomingArrivalEntity.builder()
				.arrivalPredictionId(1L)
				.vehicleRunObservationId(2L)
				.providerVehicleId(vehicleId)
				.serviceType("LOCAL")
				.expectedAt(expectedAt)
				.minExpectedAt(expectedAt.minusSeconds(30))
				.maxExpectedAt(expectedAt.plusSeconds(30))
				.confidence("HIGH")
				.observedAt(NOW.minusSeconds(10))
				.build();
	}

	private TravelerProfileEntity profile() {
		return TravelerProfileEntity.builder()
				.slowWalkSpeedMps(decimal("0.9"))
				.walkSpeedMps(decimal("1.3"))
				.fastWalkSpeedMps(decimal("1.7"))
				.runSpeedMps(decimal("2.5"))
				.build();
	}

	private JourneyProperties properties() {
		JourneyProperties value = new JourneyProperties();
		value.setDefaultTargetProbability(decimal("0.8"));
		value.setMaxLocationFutureSkew(Duration.ofSeconds(5));
		value.setUsableLocationThreshold(Duration.ofMinutes(5));
		value.setWalkingDetourFactor(decimal("1.2"));
		value.setDeparturePreparationTime(Duration.ofSeconds(10));
		value.setBoardingBuffer(Duration.ofSeconds(15));
		value.setDefaultVehicleEtaUncertainty(Duration.ofSeconds(45));
		value.setDefaultVehicleStopTravelTime(Duration.ofMinutes(2));
		value.setRouteTimingUncertaintyRatio(decimal("0.15"));
		value.setRouteMinimumTimingUncertainty(Duration.ofSeconds(30));
		value.setDecisionRefreshInterval(Duration.ofMinutes(1));
		value.setFreshLocationThreshold(Duration.ofSeconds(30));
		value.setHighAccuracyThresholdM(decimal("20"));
		value.setMediumAccuracyThresholdM(decimal("50"));
		value.setDefaultSlowWalkSpeedMps(decimal("0.9"));
		value.setDefaultWalkSpeedMps(decimal("1.3"));
		value.setDefaultFastWalkSpeedMps(decimal("1.7"));
		value.setDefaultRunSpeedMps(decimal("2.5"));
		return value;
	}

	private BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
