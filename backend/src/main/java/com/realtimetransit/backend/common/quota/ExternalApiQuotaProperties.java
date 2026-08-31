package com.realtimetransit.backend.common.quota;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("transit.api-quota")
public record ExternalApiQuotaProperties(
		long seoulSubwayDailyLimit,
		long gbisDailyLimit,
		long nationalPrecisionBusDailyLimit) {

	public long dailyLimit(ExternalApiProvider provider) {
		return switch (provider) {
			case SEOUL_SUBWAY -> seoulSubwayDailyLimit;
			case GBIS -> gbisDailyLimit;
			case NATIONAL_PRECISION_BUS -> nationalPrecisionBusDailyLimit;
		};
	}
}
