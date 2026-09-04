package com.realtimetransit.backend.transit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("transit.arrival")
public class TransitArrivalProperties {

	private Duration observationFreshness = Duration.ofMinutes(2);
}
