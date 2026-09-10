package com.realtimetransit.backend.journey.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("journey")
public class JourneyProperties {
	private BigDecimal defaultTargetProbability;
	private Duration sessionTtl;
	private Duration sessionRetention;
	private int maxActiveJourneys;
	private Duration locationTtl;
	private Duration maxLocationFutureSkew;
	private BigDecimal walkingDetourFactor;
	private Duration departurePreparationTime;
	private Duration boardingBuffer;
	private Duration defaultVehicleEtaUncertainty;
	private Duration defaultVehicleStopTravelTime;
	private BigDecimal routeTimingUncertaintyRatio;
	private Duration routeMinimumTimingUncertainty;
	private Duration predictionTtl;
	private Duration decisionRefreshInterval;
	private Duration freshLocationThreshold;
	private Duration usableLocationThreshold;
	private BigDecimal highAccuracyThresholdM;
	private BigDecimal mediumAccuracyThresholdM;
	private BigDecimal defaultSlowWalkSpeedMps;
	private BigDecimal defaultWalkSpeedMps;
	private BigDecimal defaultFastWalkSpeedMps;
	private BigDecimal defaultRunSpeedMps;
}
