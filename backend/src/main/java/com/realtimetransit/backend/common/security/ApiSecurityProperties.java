package com.realtimetransit.backend.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("app.security")
public class ApiSecurityProperties {
	private int journeyCreatesPerMinute = 10;
	private int lineSearchesPerMinute = 30;
	private int locationUpdatesPerMinute = 90;
	private int decisionsPerMinute = 60;
	private int defaultRequestsPerMinute = 180;
	private long maxRequestBytes = 32_768;
}
