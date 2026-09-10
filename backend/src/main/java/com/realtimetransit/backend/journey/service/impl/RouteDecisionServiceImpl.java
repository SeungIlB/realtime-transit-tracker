package com.realtimetransit.backend.journey.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.dto.request.RouteDecisionRequest;
import com.realtimetransit.backend.journey.dto.request.RouteTransitLegRequest;
import com.realtimetransit.backend.journey.dto.response.RouteDecisionResponse;
import com.realtimetransit.backend.journey.dto.response.RouteLegDecisionResponse;
import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;
import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;
import com.realtimetransit.backend.journey.prediction.BoardingDecision;
import com.realtimetransit.backend.journey.prediction.BoardingProbabilityCalculator;
import com.realtimetransit.backend.journey.prediction.BoardingRecommendation;
import com.realtimetransit.backend.journey.prediction.BoardingRecommendationPolicy;
import com.realtimetransit.backend.journey.prediction.HaversineDistanceCalculator;
import com.realtimetransit.backend.journey.prediction.PaceBoardingPrediction;
import com.realtimetransit.backend.journey.prediction.PaceEtaEstimate;
import com.realtimetransit.backend.journey.prediction.PaceType;
import com.realtimetransit.backend.journey.prediction.PredictionConfidence;
import com.realtimetransit.backend.journey.prediction.PredictionConfidenceEvaluator;
import com.realtimetransit.backend.journey.prediction.TravelerEtaEstimator;
import com.realtimetransit.backend.journey.prediction.VehicleEtaEstimate;
import com.realtimetransit.backend.journey.prediction.VehicleEtaEstimator;
import com.realtimetransit.backend.journey.repository.JourneyMapper;
import com.realtimetransit.backend.journey.repository.TravelerProfileMapper;
import com.realtimetransit.backend.journey.service.RouteDecisionService;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.config.TransitArrivalProperties;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
import com.realtimetransit.backend.transit.repository.ArrivalQueryMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties({JourneyProperties.class, TransitArrivalProperties.class})
public class RouteDecisionServiceImpl implements RouteDecisionService {

	private static final int MAX_ROUTE_LEGS = 3;
	private static final int ARRIVAL_LIMIT = 2;

	private final JourneyMapper journeyMapper;
	private final TravelerProfileMapper travelerProfileMapper;
	private final TransitStopMapper transitStopMapper;
	private final ArrivalQueryMapper arrivalQueryMapper;
	private final TransitExternalCollectionService externalCollectionService;
	private final HaversineDistanceCalculator distanceCalculator;
	private final TravelerEtaEstimator travelerEtaEstimator;
	private final VehicleEtaEstimator vehicleEtaEstimator;
	private final BoardingProbabilityCalculator probabilityCalculator;
	private final BoardingRecommendationPolicy recommendationPolicy;
	private final PredictionConfidenceEvaluator confidenceEvaluator;
	private final JourneyProperties properties;
	private final TransitArrivalProperties arrivalProperties;
	private final Clock clock;

	@Override
	public RouteDecisionResponse calculateRouteDecision(UUID anonymousKey, RouteDecisionRequest request) {
		Instant calculatedAt = clock.instant();
		validateRequest(anonymousKey, request, calculatedAt);
		List<RouteTransitLegRequest> legs = request.getLegs();
		legs.forEach(this::validateStops);
		TravelerLocationObservationEntity location = location(request, calculatedAt);
		TravelerProfileEntity profile = resolveProfile(anonymousKey);
		TransitStopEntity firstStop = transitStopMapper.findById(legs.getFirst().getBoardingStopId())
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "boarding stop was not found"));
		if (firstStop.getLatitude() == null || firstStop.getLongitude() == null) {
			throw new BusinessException(ErrorCode.BOARDING_STOP_COORDINATES_MISSING);
		}

		List<List<UpcomingArrivalEntity>> arrivalsByLeg = new ArrayList<>();
		for (RouteTransitLegRequest leg : legs) {
			List<UpcomingArrivalEntity> arrivals = findArrivalCandidates(leg, calculatedAt);
			if (arrivals.isEmpty()) {
				return unavailableResponse(request, leg, calculatedAt);
			}
			arrivalsByLeg.add(arrivals);
		}

		BigDecimal distance = distanceCalculator.calculateMeters(
				request.getLatitude(), request.getLongitude(), firstStop.getLatitude(), firstStop.getLongitude());
		List<PaceEtaEstimate> startingEtas = travelerEtaEstimator.estimateAll(
				distance, request.getAccuracyM(), profile, calculatedAt);
		List<RouteOption> bestByPace = startingEtas.stream()
				.map(eta -> bestOptionForPace(eta, legs, arrivalsByLeg, location, calculatedAt))
				.toList();
		List<PaceBoardingPrediction> pacePredictions = bestByPace.stream()
				.map(option -> PaceBoardingPrediction.builder()
						.travelerEta(option.getStartingEta())
						.probability(option.getProbability())
						.build())
				.toList();
		BigDecimal target = request.getTargetProbability() == null
				? properties.getDefaultTargetProbability() : request.getTargetProbability();
		BoardingRecommendation recommendation = recommendationPolicy.recommend(pacePredictions, target);
		RouteOption selected = selectOption(bestByPace, recommendation.getRecommendedPace());

		return response(selected, recommendation, target, calculatedAt);
	}

	private RouteOption bestOptionForPace(
			PaceEtaEstimate startingEta,
			List<RouteTransitLegRequest> legs,
			List<List<UpcomingArrivalEntity>> arrivalsByLeg,
			TravelerLocationObservationEntity location,
			Instant calculatedAt) {
		List<RouteOption> options = List.of(RouteOption.builder()
				.startingEta(startingEta)
				.readyEta(startingEta)
				.probability(BigDecimal.ONE)
				.confidence(PredictionConfidence.HIGH)
				.legs(List.of())
				.build());

		for (int index = 0; index < legs.size(); index++) {
			RouteTransitLegRequest leg = legs.get(index);
			List<RouteOption> expanded = new ArrayList<>();
			for (RouteOption option : options) {
				PaceEtaEstimate ready = index == 0
						? option.getReadyEta()
						: addDuration(option.getReadyEta(), Duration.ofMinutes(leg.getTransferWalkTimeMinutes()), true);
				for (UpcomingArrivalEntity arrival : arrivalsByLeg.get(index)) {
					VehicleEtaEstimate vehicleEta = vehicleEtaEstimator.estimate(arrival, calculatedAt);
					BigDecimal connectionProbability = probabilityCalculator.calculate(ready, vehicleEta);
					BigDecimal cumulative = option.getProbability().multiply(connectionProbability)
							.setScale(5, RoundingMode.HALF_UP);
					PaceEtaEstimate alightingEta = projectAlighting(ready, vehicleEta, leg.getSectionTimeMinutes());
					PredictionConfidence legConfidence = confidenceEvaluator.evaluate(location, arrival, calculatedAt);
					PredictionConfidence confidence = PredictionConfidence.lowest(option.getConfidence(), legConfidence);
					List<RouteLegCalculation> calculatedLegs = new ArrayList<>(option.getLegs());
					calculatedLegs.add(new RouteLegCalculation(
							index, leg, arrival, ready, vehicleEta, alightingEta,
							connectionProbability, cumulative, legConfidence));
					expanded.add(RouteOption.builder()
							.startingEta(startingEta)
							.readyEta(alightingEta)
							.probability(cumulative)
							.confidence(confidence)
							.legs(calculatedLegs)
							.build());
				}
			}
			options = expanded;
		}

		return options.stream()
				.max(Comparator.comparing(RouteOption::getProbability)
						.thenComparing(option -> option.getReadyEta().getExpectedAt(), Comparator.reverseOrder()))
				.orElseThrow();
	}

	private RouteOption selectOption(List<RouteOption> options, PaceType recommendedPace) {
		if (recommendedPace != null) {
			return options.stream()
					.filter(option -> option.getStartingEta().getPaceType() == recommendedPace)
					.findFirst()
					.orElseThrow();
		}
		return options.stream()
				.max(Comparator.comparing(RouteOption::getProbability)
						.thenComparing(option -> option.getStartingEta().getPaceType(), Comparator.reverseOrder()))
				.orElseThrow();
	}

	private PaceEtaEstimate projectAlighting(
			PaceEtaEstimate ready,
			VehicleEtaEstimate vehicle,
			int sectionTimeMinutes) {
		Duration duration = Duration.ofMinutes(sectionTimeMinutes);
		Duration uncertainty = timingUncertainty(duration);
		return PaceEtaEstimate.builder()
				.paceType(ready.getPaceType())
				.speedMps(ready.getSpeedMps())
				.distanceM(BigDecimal.ZERO)
				.minExpectedAt(vehicle.getMinExpectedAt().plus(duration).minus(uncertainty))
				.expectedAt(vehicle.getExpectedAt().plus(duration))
				.maxExpectedAt(vehicle.getMaxExpectedAt().plus(duration).plus(uncertainty))
				.build();
	}

	private PaceEtaEstimate addDuration(PaceEtaEstimate source, Duration duration, boolean uncertain) {
		Duration uncertainty = uncertain ? timingUncertainty(duration) : Duration.ZERO;
		return PaceEtaEstimate.builder()
				.paceType(source.getPaceType())
				.speedMps(source.getSpeedMps())
				.distanceM(source.getDistanceM())
				.minExpectedAt(source.getMinExpectedAt().plus(duration).minus(uncertainty))
				.expectedAt(source.getExpectedAt().plus(duration))
				.maxExpectedAt(source.getMaxExpectedAt().plus(duration).plus(uncertainty))
				.build();
	}

	private Duration timingUncertainty(Duration duration) {
		long scaledMillis = BigDecimal.valueOf(duration.toMillis())
				.multiply(properties.getRouteTimingUncertaintyRatio())
				.setScale(0, RoundingMode.CEILING)
				.longValueExact();
		long uncertaintyMillis = Math.max(
				properties.getRouteMinimumTimingUncertainty().toMillis(), scaledMillis);
		return Duration.ofMillis(Math.min(duration.toMillis(), uncertaintyMillis));
	}

	private List<UpcomingArrivalEntity> findArrivalCandidates(RouteTransitLegRequest leg, Instant calculatedAt) {
		Instant observedAfter = calculatedAt.minus(arrivalProperties.getObservationFreshness());
		List<UpcomingArrivalEntity> stored = queryArrivals(leg, calculatedAt, observedAfter);
		try {
			externalCollectionService.collectArrivals(
					leg.getLineId(), leg.getBoardingStopId(), leg.getAlightingStopId());
		} catch (BusinessException | DataAccessException exception) {
			if (!stored.isEmpty()) return stored;
			throw exception;
		}
		List<UpcomingArrivalEntity> refreshed = queryArrivals(leg, calculatedAt, observedAfter);
		return refreshed.isEmpty() ? stored : refreshed;
	}

	private List<UpcomingArrivalEntity> queryArrivals(
			RouteTransitLegRequest leg,
			Instant calculatedAt,
			Instant observedAfter) {
		return arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				leg.getLineId(), leg.getBoardingStopId(), leg.getAlightingStopId(),
				calculatedAt, observedAfter, ARRIVAL_LIMIT);
	}

	private RouteDecisionResponse response(
			RouteOption option,
			BoardingRecommendation recommendation,
			BigDecimal target,
			Instant calculatedAt) {
		List<RouteLegDecisionResponse> legs = option.getLegs().stream()
				.map(this::legResponse)
				.toList();
		String reason = option.getProbability().compareTo(target) >= 0
				? "모든 탑승과 환승을 포함해 목표 확률을 넘는 가장 여유로운 이동 속도예요."
				: "현재 확인된 차량 조합으로는 전체 경로의 목표 확률에 미치지 못해요.";
		return RouteDecisionResponse.builder()
				.decision(recommendation.getDecision().name())
				.recommendedPace(recommendation.getRecommendedPace() == null
						? null : recommendation.getRecommendedPace().name())
				.overallProbability(option.getProbability())
				.targetProbability(target)
				.confidence(option.getConfidence().name())
				.reasons(List.of(reason, "경로 예정 소요시간과 현재 실시간 차량 후보를 함께 사용한 근사값이에요."))
				.legs(legs)
				.expectedArrivalAt(option.getReadyEta().getExpectedAt())
				.calculatedAt(calculatedAt)
				.nextRefreshAt(calculatedAt.plus(properties.getDecisionRefreshInterval()))
				.build();
	}

	private RouteLegDecisionResponse legResponse(RouteLegCalculation calculation) {
		return RouteLegDecisionResponse.builder()
				.legIndex(calculation.getIndex())
				.lineId(calculation.getRequest().getLineId())
				.lineName(calculation.getRequest().getLineName())
				.boardingStopName(calculation.getRequest().getBoardingStopName())
				.alightingStopName(calculation.getRequest().getAlightingStopName())
				.providerVehicleId(calculation.getArrival().getProviderVehicleId())
				.serviceType(calculation.getArrival().getServiceType())
				.connectionProbability(calculation.getConnectionProbability())
				.cumulativeProbability(calculation.getCumulativeProbability())
				.readyExpectedAt(calculation.getReadyEta().getExpectedAt())
				.vehicleExpectedAt(calculation.getVehicleEta().getExpectedAt())
				.alightingExpectedAt(calculation.getAlightingEta().getExpectedAt())
				.confidence(calculation.getConfidence().name())
				.build();
	}

	private RouteDecisionResponse unavailableResponse(
			RouteDecisionRequest request,
			RouteTransitLegRequest leg,
			Instant calculatedAt) {
		BigDecimal target = request.getTargetProbability() == null
				? properties.getDefaultTargetProbability() : request.getTargetProbability();
		return RouteDecisionResponse.builder()
				.decision(BoardingDecision.INSUFFICIENT_DATA.name())
				.targetProbability(target)
				.confidence(PredictionConfidence.UNKNOWN.name())
				.reasons(List.of(leg.getLineName() + "의 접근 차량을 현재 확인하지 못했어요."))
				.legs(List.of())
				.calculatedAt(calculatedAt)
				.nextRefreshAt(calculatedAt.plus(properties.getDecisionRefreshInterval()))
				.build();
	}

	private TravelerProfileEntity resolveProfile(UUID anonymousKey) {
		return travelerProfileMapper.findByAnonymousKey(anonymousKey)
				.orElseGet(() -> TravelerProfileEntity.builder()
						.anonymousKey(anonymousKey)
						.slowWalkSpeedMps(properties.getDefaultSlowWalkSpeedMps())
						.walkSpeedMps(properties.getDefaultWalkSpeedMps())
						.fastWalkSpeedMps(properties.getDefaultFastWalkSpeedMps())
						.runSpeedMps(properties.getDefaultRunSpeedMps())
						.sampleCount(0)
						.build());
	}

	private TravelerLocationObservationEntity location(RouteDecisionRequest request, Instant calculatedAt) {
		return TravelerLocationObservationEntity.builder()
				.latitude(request.getLatitude())
				.longitude(request.getLongitude())
				.accuracyM(request.getAccuracyM())
				.observedAt(request.getObservedAt())
				.receivedAt(calculatedAt)
				.build();
	}

	private void validateStops(RouteTransitLegRequest leg) {
		journeyMapper.validateJourneyStopsOnSameDirection(
				leg.getLineId(), leg.getDirectionId(), leg.getBoardingStopId(), leg.getAlightingStopId())
				.orElseThrow(() -> new BusinessException(
						ErrorCode.INVALID_JOURNEY_STOPS,
						"route leg does not match the selected direction"));
	}

	private void validateRequest(UUID anonymousKey, RouteDecisionRequest request, Instant calculatedAt) {
		if (anonymousKey == null || request == null || request.getLatitude() == null
				|| request.getLongitude() == null || request.getAccuracyM() == null
				|| request.getObservedAt() == null || request.getLegs() == null
				|| request.getLegs().isEmpty() || request.getLegs().size() > MAX_ROUTE_LEGS) {
			throw invalidRequest("location and one to three transit legs are required");
		}
		if (request.getLatitude().compareTo(new BigDecimal("-90")) < 0
				|| request.getLatitude().compareTo(new BigDecimal("90")) > 0
				|| request.getLongitude().compareTo(new BigDecimal("-180")) < 0
				|| request.getLongitude().compareTo(new BigDecimal("180")) > 0
				|| request.getAccuracyM().compareTo(BigDecimal.ZERO) < 0) {
			throw invalidRequest("location values are outside the allowed range");
		}
		if (request.getObservedAt().isAfter(calculatedAt.plus(properties.getMaxLocationFutureSkew()))
				|| request.getObservedAt().isBefore(calculatedAt.minus(properties.getUsableLocationThreshold()))) {
			throw invalidRequest("location observation is not recent");
		}
		if (request.getTargetProbability() != null
				&& (request.getTargetProbability().compareTo(BigDecimal.ZERO) <= 0
						|| request.getTargetProbability().compareTo(BigDecimal.ONE) > 0)) {
			throw invalidRequest("targetProbability must be greater than 0 and at most 1");
		}
		for (int index = 0; index < request.getLegs().size(); index++) {
			RouteTransitLegRequest leg = request.getLegs().get(index);
			if (leg == null || leg.getLineId() == null || leg.getDirectionId() == null
					|| leg.getBoardingStopId() == null || leg.getAlightingStopId() == null
					|| leg.getBoardingStopId().equals(leg.getAlightingStopId())
					|| leg.getSectionTimeMinutes() == null || leg.getSectionTimeMinutes() <= 0
					|| leg.getSectionTimeMinutes() > 240 || leg.getTransferWalkTimeMinutes() == null
					|| leg.getTransferWalkTimeMinutes() < 0 || leg.getTransferWalkTimeMinutes() > 60) {
				throw invalidRequest("each route leg must contain valid stops and timing");
			}
			if (index == 0 && leg.getTransferWalkTimeMinutes() != 0) {
				throw invalidRequest("the first route leg cannot contain transfer walking time");
			}
		}
	}

	private BusinessException invalidRequest(String detail) {
		return new BusinessException(ErrorCode.INVALID_ROUTE_DECISION_REQUEST, detail);
	}

	@Getter
	@Builder
	private static class RouteOption {
		private PaceEtaEstimate startingEta;
		private PaceEtaEstimate readyEta;
		private BigDecimal probability;
		private PredictionConfidence confidence;
		private List<RouteLegCalculation> legs;
	}

	@Getter
	@AllArgsConstructor
	private static class RouteLegCalculation {
		private int index;
		private RouteTransitLegRequest request;
		private UpcomingArrivalEntity arrival;
		private PaceEtaEstimate readyEta;
		private VehicleEtaEstimate vehicleEta;
		private PaceEtaEstimate alightingEta;
		private BigDecimal connectionProbability;
		private BigDecimal cumulativeProbability;
		private PredictionConfidence confidence;
	}
}
