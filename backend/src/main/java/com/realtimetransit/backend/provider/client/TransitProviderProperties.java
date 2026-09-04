package com.realtimetransit.backend.provider.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("transit.providers")
public class TransitProviderProperties {
	private Provider gbis = new Provider();
	private Provider nationalPrecisionBus = new Provider();
	private SeoulProvider seoulSubway = new SeoulProvider();
	private Provider railwayTimetable = new Provider();

	@Getter
	@Setter
	public static class Provider {
		private String baseUrl;
		private String serviceKey;
	}

	@Getter
	@Setter
	public static class SeoulProvider extends Provider {
		private String referenceBaseUrl = "http://openapi.seoul.go.kr:8088";
		private String referenceServiceKey;
	}
}
