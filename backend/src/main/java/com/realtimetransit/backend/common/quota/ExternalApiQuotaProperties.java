package com.realtimetransit.backend.common.quota;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("transit.api-quota")
public class ExternalApiQuotaProperties {
	private long seoulSubwayDailyLimit;
	private long gbisDailyLimit;
	private long nationalPrecisionBusDailyLimit;
	private long railwayTimetableDailyLimit;
	private long kakaoPublicTransitDailyLimit;

	public long dailyLimit(ExternalApiProvider provider) {
		return switch (provider) {
			case SEOUL_SUBWAY -> seoulSubwayDailyLimit;
			case GBIS -> gbisDailyLimit;
			case NATIONAL_PRECISION_BUS -> nationalPrecisionBusDailyLimit;
			case RAILWAY_TIMETABLE -> railwayTimetableDailyLimit;
			case KAKAO_PUBLIC_TRANSIT -> kakaoPublicTransitDailyLimit;
		};
	}
}
