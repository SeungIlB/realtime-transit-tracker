package com.realtimetransit.backend.common.maintenance;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("transit.maintenance")
public class TransitMaintenanceProperties {
	private Duration interval;
	private Duration observationRetention;
	private int batchSize;
}
