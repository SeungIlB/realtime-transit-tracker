package com.realtimetransit.backend.common.maintenance;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.journey.repository.JourneyLocationMapper;
import com.realtimetransit.backend.journey.repository.JourneyMapper;
import com.realtimetransit.backend.provider.service.ObservationRetentionService;

import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(TransitMaintenanceProperties.class)
public class TransitMaintenanceScheduler {

	private final ObservationRetentionService observationRetentionService;
	private final JourneyLocationMapper journeyLocationMapper;
	private final JourneyMapper journeyMapper;
	private final TransitMaintenanceProperties properties;
	private final Clock clock;

	@Scheduled(
			fixedDelayString = "${transit.maintenance.interval:10m}",
			initialDelayString = "${transit.maintenance.interval:10m}")
	@Transactional
	public void cleanExpiredData() {
		Instant now = clock.instant();
		int limit = properties.getBatchSize();
		observationRetentionService.deleteOldArrivalPredictions(
				properties.getObservationRetention(), limit);
		observationRetentionService.deleteOldVehicleRunObservations(
				properties.getObservationRetention(), limit);
		observationRetentionService.deleteExpiredRawObservations(limit);
		journeyLocationMapper.deleteExpiredLocations(now, limit);
		journeyMapper.expireJourneySessionsBefore(now, now, limit);
	}
}
