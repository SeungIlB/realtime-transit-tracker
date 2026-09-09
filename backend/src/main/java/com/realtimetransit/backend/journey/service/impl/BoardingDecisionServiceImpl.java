package com.realtimetransit.backend.journey.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.dto.response.BoardingDecisionResponse;
import com.realtimetransit.backend.journey.dto.response.PacePredictionResponse;
import com.realtimetransit.backend.journey.dto.response.VehicleBoardingPredictionResponse;
import com.realtimetransit.backend.journey.entity.BoardingPredictionSnapshotEntity;
import com.realtimetransit.backend.journey.entity.JourneySessionEntity;
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
import com.realtimetransit.backend.journey.repository.BoardingPredictionMapper;
import com.realtimetransit.backend.journey.repository.JourneyLocationMapper;
import com.realtimetransit.backend.journey.repository.TravelerProfileMapper;
import com.realtimetransit.backend.journey.service.BoardingDecisionService;
import com.realtimetransit.backend.journey.service.validation.JourneySessionValidator;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
import com.realtimetransit.backend.transit.config.TransitArrivalProperties;
import com.realtimetransit.backend.transit.repository.ArrivalQueryMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties({JourneyProperties.class, TransitArrivalProperties.class})
public class BoardingDecisionServiceImpl implements BoardingDecisionService {

	private static final int ARRIVAL_LIMIT = 2;
	private static final String MODEL_VERSION = "HEURISTIC_V1";

	private final JourneySessionValidator journeySessionValidator;
	private final JourneyLocationMapper journeyLocationMapper;
	private final TravelerProfileMapper travelerProfileMapper;
	private final TransitStopMapper transitStopMapper;
	private final ArrivalQueryMapper arrivalQueryMapper;
	private final BoardingPredictionMapper boardingPredictionMapper;
	private final TransitExternalCollectionService externalCollectionService;
	private final HaversineDistanceCalculator distanceCalculator;
	private final TravelerEtaEstimator travelerEtaEstimator;
	private final VehicleEtaEstimator vehicleEtaEstimator;
	private final BoardingProbabilityCalculator probabilityCalculator;
	private final BoardingRecommendationPolicy recommendationPolicy;
	private final PredictionConfidenceEvaluator confidenceEvaluator;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final JourneyProperties properties;
	private final TransitArrivalProperties arrivalProperties;

	@Override
	public BoardingDecisionResponse calculateDecision(UUID journeyId, UUID anonymousKey) {
		Instant calculatedAt = clock.instant();
		JourneySessionEntity journey = journeySessionValidator.findActiveJourney(journeyId, anonymousKey, calculatedAt);
		var location = journeyLocationMapper.findLatestLocationByJourneyId(journeyId, calculatedAt);
		if (location.isEmpty() || !confidenceEvaluator.isLocationUsable(location.get(), calculatedAt)) {
			return statusResponse(journey, BoardingDecision.INSUFFICIENT_DATA,
					"UNKNOWN", "A recent user location is required", calculatedAt);
		}

		TravelerProfileEntity profile = travelerProfileMapper.findById(journey.getTravelerProfileId())
				.orElseThrow(() -> new BusinessException(
						ErrorCode.JOURNEY_PROFILE_NOT_FOUND,
						"journeyId=" + journeyId));
		TransitStopEntity boardingStop = transitStopMapper.findById(journey.getBoardingStopId())
				.orElseThrow(() -> new BusinessException(
						ErrorCode.RESOURCE_NOT_FOUND,
						"boardingStopId=" + journey.getBoardingStopId()));
		validateBoardingStopCoordinates(boardingStop);

		List<UpcomingArrivalEntity> arrivals = findArrivalCandidates(journey, calculatedAt);
		if (arrivals.isEmpty()) {
			return statusResponse(journey, BoardingDecision.NO_VEHICLE,
					"UNKNOWN", "No approaching vehicle is currently available", calculatedAt);
		}

		BigDecimal straightDistance = distanceCalculator.calculateMeters(
				location.get().getLatitude(), location.get().getLongitude(),
				boardingStop.getLatitude(), boardingStop.getLongitude());
		List<PaceEtaEstimate> travelerEtas = travelerEtaEstimator.estimateAll(
				straightDistance, location.get().getAccuracyM(), profile, calculatedAt);
		List<VehicleCalculation> calculations = arrivals.stream()
				.map(arrival -> calculateVehicle(
						arrival, travelerEtas, location.get(), journey.getTargetProbability(), calculatedAt))
				.toList();
		VehicleCalculation selected = selectBestVehicle(calculations);
		UUID calculationId = UUID.randomUUID();
		List<BoardingPredictionSnapshotEntity> snapshots = createSnapshots(
				calculationId, journey, location.get(), calculations, selected, straightDistance, calculatedAt);
		boardingPredictionMapper.insertBoardingPredictionSnapshots(snapshots);

		return decisionResponse(journey, calculations, selected, calculatedAt);
	}

	private List<UpcomingArrivalEntity> findArrivalCandidates(
			JourneySessionEntity journey,
			Instant calculatedAt) {
		Instant observedAfter = calculatedAt.minus(arrivalProperties.getObservationFreshness());
		List<UpcomingArrivalEntity> storedArrivals = findStoredArrivalCandidates(
				journey, calculatedAt, observedAfter);
		if (hasRecentlyCollectedArrival(storedArrivals, calculatedAt)) {
			return storedArrivals;
		}
		try {
			externalCollectionService.collectArrivals(
					journey.getLineId(), journey.getBoardingStopId(), journey.getAlightingStopId());
		} catch (BusinessException exception) {
			if (!storedArrivals.isEmpty()) return storedArrivals;
			throw exception;
		}
		List<UpcomingArrivalEntity> refreshedArrivals = findStoredArrivalCandidates(
				journey, calculatedAt, observedAfter);
		return refreshedArrivals.isEmpty() ? storedArrivals : refreshedArrivals;
	}

	private boolean hasRecentlyCollectedArrival(
			List<UpcomingArrivalEntity> arrivals,
			Instant calculatedAt) {
		Instant reusableAfter = calculatedAt.minus(arrivalProperties.getCollectionRefreshInterval());
		return arrivals.stream()
				.map(UpcomingArrivalEntity::getReceivedAt)
				.filter(java.util.Objects::nonNull)
				.anyMatch(receivedAt -> !receivedAt.isBefore(reusableAfter));
	}

	private List<UpcomingArrivalEntity> findStoredArrivalCandidates(
			JourneySessionEntity journey,
			Instant calculatedAt,
			Instant observedAfter) {
		if (journey.getAlightingStopId() == null) {
			return arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopId(
					journey.getLineId(), journey.getDirectionId(), journey.getBoardingStopId(), calculatedAt,
					observedAfter, ARRIVAL_LIMIT);
		}
		return arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				journey.getLineId(), journey.getBoardingStopId(), journey.getAlightingStopId(), calculatedAt,
				observedAfter, ARRIVAL_LIMIT);
	}

	private VehicleCalculation calculateVehicle(
			UpcomingArrivalEntity arrival,
			List<PaceEtaEstimate> travelerEtas,
			TravelerLocationObservationEntity location,
			BigDecimal targetProbability,
			Instant calculatedAt) {
		VehicleEtaEstimate vehicleEta = vehicleEtaEstimator.estimate(arrival, calculatedAt);
		List<PaceBoardingPrediction> predictions = travelerEtas.stream()
				.map(travelerEta -> PaceBoardingPrediction.builder()
						.travelerEta(travelerEta)
						.probability(probabilityCalculator.calculate(travelerEta, vehicleEta))
						.build())
				.toList();
		BoardingRecommendation recommendation = recommendationPolicy.recommend(predictions, targetProbability);
		PredictionConfidence confidence = confidenceEvaluator.evaluate(location, arrival, calculatedAt);
		return new VehicleCalculation(arrival, vehicleEta, predictions, recommendation, confidence);
	}

	private VehicleCalculation selectBestVehicle(List<VehicleCalculation> calculations) {
		return calculations.stream()
				.min(Comparator
						.comparingInt(this::requiredEffort)
						.thenComparing(calculation -> calculation.getVehicleEta().getExpectedAt()))
				.orElseThrow();
	}

	private int requiredEffort(VehicleCalculation calculation) {
		PaceType pace = calculation.getRecommendation().getRecommendedPace();
		return pace == null ? Integer.MAX_VALUE : pace.ordinal();
	}

	private List<BoardingPredictionSnapshotEntity> createSnapshots(
			UUID calculationId,
			JourneySessionEntity journey,
			TravelerLocationObservationEntity location,
			List<VehicleCalculation> calculations,
			VehicleCalculation selected,
			BigDecimal straightDistance,
			Instant calculatedAt) {
		List<BoardingPredictionSnapshotEntity> snapshots = new ArrayList<>();
		for (VehicleCalculation calculation : calculations) {
			for (PaceBoardingPrediction prediction : calculation.getPredictions()) {
				boolean recommended = calculation == selected
						&& prediction.getTravelerEta().getPaceType()
						== selected.getRecommendation().getRecommendedPace();
				snapshots.add(snapshot(
						calculationId, journey, location, calculation, prediction,
						recommended, straightDistance, calculatedAt));
			}
		}
		return snapshots;
	}

	private BoardingPredictionSnapshotEntity snapshot(
			UUID calculationId,
			JourneySessionEntity journey,
			TravelerLocationObservationEntity location,
			VehicleCalculation calculation,
			PaceBoardingPrediction prediction,
			boolean recommended,
			BigDecimal straightDistance,
			Instant calculatedAt) {
		PaceEtaEstimate travelerEta = prediction.getTravelerEta();
		VehicleEtaEstimate vehicleEta = calculation.getVehicleEta();
		UpcomingArrivalEntity arrival = calculation.getArrival();
		return BoardingPredictionSnapshotEntity.builder()
				.calculationId(calculationId)
				.journeyId(journey.getId())
				.locationObservationId(location.getId())
				.vehicleRunObservationId(arrival.getVehicleRunObservationId())
				.arrivalPredictionObservationId(arrival.getArrivalPredictionId())
				.providerVehicleId(arrival.getProviderVehicleId())
				.paceType(travelerEta.getPaceType().name())
				.paceSpeedMps(travelerEta.getSpeedMps())
				.distanceM(travelerEta.getDistanceM())
				.userMinExpectedAt(travelerEta.getMinExpectedAt())
				.userExpectedAt(travelerEta.getExpectedAt())
				.userMaxExpectedAt(travelerEta.getMaxExpectedAt())
				.vehicleMinExpectedAt(vehicleEta.getMinExpectedAt())
				.vehicleExpectedAt(vehicleEta.getExpectedAt())
				.vehicleMaxExpectedAt(vehicleEta.getMaxExpectedAt())
				.boardingProbability(prediction.getProbability())
				.decision(calculation.getRecommendation().getDecision().name())
				.recommended(recommended)
				.confidence(calculation.getConfidence().name())
				.modelVersion(MODEL_VERSION)
				.factorsJson(factorsJson(location, straightDistance))
				.calculatedAt(calculatedAt)
				.expiresAt(calculatedAt.plus(properties.getPredictionTtl()))
				.build();
	}

	private String factorsJson(
			TravelerLocationObservationEntity location,
			BigDecimal straightDistance) {
		try {
			return objectMapper.writeValueAsString(Map.of(
					"distanceSource", "HAVERSINE",
					"straightDistanceM", straightDistance,
					"gpsAccuracyM", location.getAccuracyM(),
					"walkingDetourFactor", properties.getWalkingDetourFactor(),
					"boardingBufferSeconds", properties.getBoardingBuffer().toSeconds()));
		} catch (JacksonException exception) {
			throw new BusinessException(ErrorCode.PREDICTION_SERIALIZATION_FAILED);
		}
	}

	private BoardingDecisionResponse decisionResponse(
			JourneySessionEntity journey,
			List<VehicleCalculation> calculations,
			VehicleCalculation selected,
			Instant calculatedAt) {
		List<VehicleBoardingPredictionResponse> vehicles = calculations.stream()
				.map(calculation -> vehicleResponse(calculation, calculation == selected))
				.toList();
		return BoardingDecisionResponse.builder()
				.journeyId(journey.getId())
				.decision(selected.getRecommendation().getDecision().name())
				.recommendedVehicleId(selected.getArrival().getProviderVehicleId())
				.recommendedPace(selected.getRecommendation().getRecommendedPace() == null
						? null : selected.getRecommendation().getRecommendedPace().name())
				.targetProbability(journey.getTargetProbability())
				.confidence(selected.getConfidence().name())
				.reasons(List.of(reason(selected)))
				.vehicles(vehicles)
				.calculatedAt(calculatedAt)
				.nextRefreshAt(calculatedAt.plus(properties.getDecisionRefreshInterval()))
				.build();
	}

	private VehicleBoardingPredictionResponse vehicleResponse(
			VehicleCalculation calculation,
			boolean selectedVehicle) {
		PaceType recommendedPace = calculation.getRecommendation().getRecommendedPace();
		List<PacePredictionResponse> paces = calculation.getPredictions().stream()
				.map(prediction -> paceResponse(
						prediction,
						selectedVehicle && prediction.getTravelerEta().getPaceType() == recommendedPace))
				.toList();
		return VehicleBoardingPredictionResponse.builder()
				.arrivalPredictionId(calculation.getArrival().getArrivalPredictionId())
				.vehicleRunObservationId(calculation.getArrival().getVehicleRunObservationId())
				.providerVehicleId(calculation.getArrival().getProviderVehicleId())
				.serviceType(calculation.getArrival().getServiceType())
				.alightingStopStatus(calculation.getArrival().getAlightingStopStatus())
				.movementStatus(calculation.getArrival().getMovementStatus())
				.currentStopName(calculation.getArrival().getCurrentStopName())
				.destinationStopName(calculation.getArrival().getDestinationStopName())
				.currentSequence(calculation.getArrival().getCurrentSequence())
				.remainingStops(calculation.getArrival().getRemainingStops())
				.latitude(calculation.getArrival().getLatitude())
				.longitude(calculation.getArrival().getLongitude())
				.observedAt(calculation.getArrival().getObservedAt())
				.vehicleMinExpectedAt(calculation.getVehicleEta().getMinExpectedAt())
				.vehicleExpectedAt(calculation.getVehicleEta().getExpectedAt())
				.vehicleMaxExpectedAt(calculation.getVehicleEta().getMaxExpectedAt())
				.decision(calculation.getRecommendation().getDecision().name())
				.recommendedPace(recommendedPace == null ? null : recommendedPace.name())
				.confidence(calculation.getConfidence().name())
				.pacePredictions(paces)
				.build();
	}

	private PacePredictionResponse paceResponse(
			PaceBoardingPrediction prediction,
			boolean recommended) {
		PaceEtaEstimate eta = prediction.getTravelerEta();
		return PacePredictionResponse.builder()
				.paceType(eta.getPaceType().name())
				.speedMps(eta.getSpeedMps())
				.distanceM(eta.getDistanceM())
				.minExpectedAt(eta.getMinExpectedAt())
				.expectedAt(eta.getExpectedAt())
				.maxExpectedAt(eta.getMaxExpectedAt())
				.boardingProbability(prediction.getProbability())
				.recommended(recommended)
				.build();
	}

	private String reason(VehicleCalculation selected) {
		if (selected.getRecommendation().getRecommendedPace() == null) {
			return "No movement pace reaches the target boarding probability";
		}
		return "The least demanding pace meeting the target probability was selected";
	}

	private BoardingDecisionResponse statusResponse(
			JourneySessionEntity journey,
			BoardingDecision decision,
			String confidence,
			String reason,
			Instant calculatedAt) {
		return BoardingDecisionResponse.builder()
				.journeyId(journey.getId())
				.decision(decision.name())
				.targetProbability(journey.getTargetProbability())
				.confidence(confidence)
				.reasons(List.of(reason))
				.vehicles(List.of())
				.calculatedAt(calculatedAt)
				.nextRefreshAt(calculatedAt.plus(properties.getDecisionRefreshInterval()))
				.build();
	}

	private void validateBoardingStopCoordinates(TransitStopEntity boardingStop) {
		if (boardingStop.getLatitude() == null || boardingStop.getLongitude() == null) {
			throw new BusinessException(
					ErrorCode.BOARDING_STOP_COORDINATES_MISSING,
					"boardingStopId=" + boardingStop.getId());
		}
	}

	@Getter
	@RequiredArgsConstructor
	private static class VehicleCalculation {
		private final UpcomingArrivalEntity arrival;
		private final VehicleEtaEstimate vehicleEta;
		private final List<PaceBoardingPrediction> predictions;
		private final BoardingRecommendation recommendation;
		private final PredictionConfidence confidence;
	}
}
