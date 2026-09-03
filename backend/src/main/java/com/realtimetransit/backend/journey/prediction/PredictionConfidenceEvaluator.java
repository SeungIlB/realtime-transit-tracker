package com.realtimetransit.backend.journey.prediction;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(JourneyProperties.class)
public class PredictionConfidenceEvaluator {

	private final JourneyProperties properties;

	public PredictionConfidence evaluate(
			TravelerLocationObservationEntity location,
			UpcomingArrivalEntity arrival,
			Instant calculatedAt) {
		PredictionConfidence locationConfidence = locationConfidence(location, calculatedAt);
		PredictionConfidence arrivalConfidence = arrivalConfidence(arrival, calculatedAt);
		return PredictionConfidence.lowest(locationConfidence, arrivalConfidence);
	}

	public boolean isLocationUsable(
			TravelerLocationObservationEntity location,
			Instant calculatedAt) {
		return !location.getObservedAt().isBefore(
				calculatedAt.minus(properties.getUsableLocationThreshold()));
	}

	private PredictionConfidence locationConfidence(
			TravelerLocationObservationEntity location,
			Instant calculatedAt) {
		Duration age = nonNegativeAge(location.getObservedAt(), calculatedAt);
		PredictionConfidence ageConfidence = age.compareTo(properties.getFreshLocationThreshold()) <= 0
				? PredictionConfidence.HIGH
				: age.compareTo(properties.getUsableLocationThreshold()) <= 0
						? PredictionConfidence.MEDIUM
						: PredictionConfidence.LOW;
		PredictionConfidence accuracyConfidence;
		if (location.getAccuracyM().compareTo(properties.getHighAccuracyThresholdM()) <= 0) {
			accuracyConfidence = PredictionConfidence.HIGH;
		} else if (location.getAccuracyM().compareTo(properties.getMediumAccuracyThresholdM()) <= 0) {
			accuracyConfidence = PredictionConfidence.MEDIUM;
		} else {
			accuracyConfidence = PredictionConfidence.LOW;
		}
		return PredictionConfidence.lowest(ageConfidence, accuracyConfidence);
	}

	private PredictionConfidence arrivalConfidence(
			UpcomingArrivalEntity arrival,
			Instant calculatedAt) {
		PredictionConfidence sourceConfidence;
		try {
			sourceConfidence = PredictionConfidence.valueOf(arrival.getConfidence());
		} catch (IllegalArgumentException | NullPointerException exception) {
			sourceConfidence = PredictionConfidence.UNKNOWN;
		}
		Duration age = nonNegativeAge(arrival.getObservedAt(), calculatedAt);
		PredictionConfidence ageConfidence = age.compareTo(properties.getFreshLocationThreshold()) <= 0
				? PredictionConfidence.HIGH
				: age.compareTo(properties.getUsableLocationThreshold()) <= 0
						? PredictionConfidence.MEDIUM
						: PredictionConfidence.LOW;
		return PredictionConfidence.lowest(sourceConfidence, ageConfidence);
	}

	private Duration nonNegativeAge(Instant observedAt, Instant calculatedAt) {
		if (observedAt == null || observedAt.isAfter(calculatedAt)) {
			return Duration.ZERO;
		}
		return Duration.between(observedAt, calculatedAt);
	}
}
