package com.realtimetransit.backend.common.maintenance;

import static org.mockito.Mockito.inOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.journey.repository.JourneyLocationMapper;
import com.realtimetransit.backend.journey.repository.JourneyMapper;
import com.realtimetransit.backend.journey.repository.TravelerProfileMapper;
import com.realtimetransit.backend.journey.config.JourneyProperties;
import com.realtimetransit.backend.provider.service.ObservationRetentionService;

@ExtendWith(MockitoExtension.class)
class TransitMaintenanceSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-09-04T02:00:00Z");

	@Mock
	private ObservationRetentionService observationRetentionService;
	@Mock
	private JourneyLocationMapper journeyLocationMapper;
	@Mock
	private JourneyMapper journeyMapper;
	@Mock
	private TravelerProfileMapper travelerProfileMapper;

	@Test
	void deletesDependentObservationsBeforeTheirParentsAndExpiresJourneyData() {
		TransitMaintenanceProperties properties = new TransitMaintenanceProperties();
		properties.setObservationRetention(Duration.ofHours(2));
		properties.setBatchSize(1_000);
		JourneyProperties journeyProperties = new JourneyProperties();
		journeyProperties.setSessionRetention(Duration.ofHours(2));
		TransitMaintenanceScheduler scheduler = new TransitMaintenanceScheduler(
				observationRetentionService,
				journeyLocationMapper,
				journeyMapper,
				travelerProfileMapper,
				properties,
				journeyProperties,
				Clock.fixed(NOW, ZoneOffset.UTC));

		scheduler.cleanExpiredData();

		var ordered = inOrder(observationRetentionService, journeyLocationMapper, journeyMapper, travelerProfileMapper);
		ordered.verify(observationRetentionService)
				.deleteOldArrivalPredictions(Duration.ofHours(2), 1_000);
		ordered.verify(observationRetentionService)
				.deleteOldVehicleRunObservations(Duration.ofHours(2), 1_000);
		ordered.verify(observationRetentionService).deleteExpiredRawObservations(1_000);
		ordered.verify(journeyLocationMapper).deleteExpiredLocations(NOW);
		ordered.verify(journeyMapper).expireJourneySessionsBefore(NOW, NOW, 1_000);
		ordered.verify(journeyMapper).deleteInactiveJourneySessionsBefore(NOW.minus(Duration.ofHours(2)), 1_000);
		ordered.verify(travelerProfileMapper).deleteUnusedProfilesBefore(NOW.minus(Duration.ofHours(2)), 1_000);
	}
}
