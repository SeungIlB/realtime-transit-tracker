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
import com.realtimetransit.backend.journey.repository.TravelerProfileMapper;
import com.realtimetransit.backend.journey.service.BoardingDecisionService;
import com.realtimetransit.backend.journey.config.JourneyProperties;
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
	private final TravelerProfileMapper travelerProfileMapper;
	private final TransitMaintenanceProperties properties;
	private final JourneyProperties journeyProperties;
	private final Clock clock;
	private final BoardingDecisionService boardingDecisionService;

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
		journeyLocationMapper.deleteExpiredLocations(now);
		journeyMapper.expireJourneySessionsBefore(now, now, limit);
		Instant retainedAfter = now.minus(journeyProperties.getSessionRetention());
		journeyMapper.deleteInactiveJourneySessionsBefore(retainedAfter, limit);
		travelerProfileMapper.deleteUnusedProfilesBefore(retainedAfter, limit);
	}

	@Scheduled(
			fixedDelayString = "${transit.push.interval:1m}",
			initialDelayString = "${transit.push.interval:1m}")
	public void refreshActiveJourneyNotifications() {
		Instant now = clock.instant();
		for (var journey : journeyMapper.findActiveJourneySessions(now, properties.getBatchSize())) {
			travelerProfileMapper.findById(journey.getTravelerProfileId())
					.ifPresent(profile -> {
						try {
							boardingDecisionService.calculateDecision(journey.getId(), profile.getAnonymousKey());
						} catch (RuntimeException ignored) {
							// A provider failure must not stop notifications for other journeys.
						}
					});
		}
	}
}
