package com.realtimetransit.backend.journey.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.realtimetransit.backend.journey.entity.BoardingPredictionSnapshotEntity;
import com.realtimetransit.backend.journey.entity.JourneySessionEntity;
import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;
import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;
import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.transit.entity.DirectedStopAssignmentEntity;
import com.realtimetransit.backend.transit.entity.RouteDirectionEntity;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.repository.DirectedStopMapper;
import com.realtimetransit.backend.transit.repository.RouteDirectionMapper;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;

@SpringBootTest
@Testcontainers
class JourneyMapperIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	@Autowired
	private TravelerProfileMapper travelerProfileMapper;

	@Autowired
	private JourneyMapper journeyMapper;

	@Autowired
	private JourneyLocationMapper journeyLocationMapper;

	@Autowired
	private BoardingPredictionMapper boardingPredictionMapper;

	@Autowired
	private TransitProviderMapper transitProviderMapper;

	@Autowired
	private TransitLineMapper transitLineMapper;

	@Autowired
	private TransitStopMapper transitStopMapper;

	@Autowired
	private RouteDirectionMapper routeDirectionMapper;

	@Autowired
	private DirectedStopMapper directedStopMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void upsertsAnonymousTravelerProfileAndEnforcesSpeedOrder() {
		UUID profileId = UUID.randomUUID();
		UUID anonymousKey = UUID.randomUUID();
		travelerProfileMapper.upsertTravelerProfile(profile(
				profileId, anonymousKey, "0.90", "1.30", "1.70", "2.50", 0));
		travelerProfileMapper.upsertTravelerProfile(profile(
				UUID.randomUUID(), anonymousKey, "1.00", "1.40", "1.90", "2.80", 12));

		assertThat(travelerProfileMapper.findByAnonymousKey(anonymousKey))
				.get()
				.satisfies(profile -> {
					assertThat(profile.getId()).isEqualTo(profileId);
					assertThat(profile.getWalkSpeedMps()).isEqualByComparingTo("1.40");
					assertThat(profile.getSampleCount()).isEqualTo(12);
				});
		assertThat(travelerProfileMapper.findById(profileId)).isPresent();

		assertThatThrownBy(() -> travelerProfileMapper.upsertTravelerProfile(profile(
				UUID.randomUUID(), UUID.randomUUID(), "1.50", "1.20", "1.70", "2.50", 0)))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void validatesJourneyStopsAndUpdatesJourneyLifecycle() {
		TransitFixture fixture = transitFixture("journey");
		Instant now = Instant.now();
		UUID profileId = insertProfile();

		assertThat(journeyMapper.validateJourneyStopsOnSameDirection(
				fixture.getLineId(), fixture.getDirectionId(), fixture.getBoardingStopId(), fixture.getAlightingStopId()))
				.get()
				.satisfies(validation -> {
					assertThat(validation.getBoardingSequence()).isEqualTo(1);
					assertThat(validation.getAlightingSequence()).isEqualTo(2);
				});
		assertThat(journeyMapper.validateJourneyStopsOnSameDirection(
				fixture.getLineId(), fixture.getDirectionId(), fixture.getBoardingStopId(), null))
				.get().extracting(validation -> validation.getAlightingStopId()).isNull();
		assertThat(journeyMapper.validateJourneyStopsOnSameDirection(
				fixture.getLineId(), fixture.getDirectionId(), fixture.getAlightingStopId(), fixture.getBoardingStopId()))
				.isEmpty();

		UUID journeyId = UUID.randomUUID();
		journeyMapper.insertJourneySession(journey(
				journeyId, profileId, fixture, now, now.plusSeconds(3600), "ACTIVE"));
		assertThat(journeyMapper.findJourneySessionById(journeyId))
				.get()
				.satisfies(journey -> {
					assertThat(journey.getLineId()).isEqualTo(fixture.getLineId());
					assertThat(journey.getTargetProbability()).isEqualByComparingTo("0.8000");
					assertThat(journey.getStatus()).isEqualTo("ACTIVE");
				});
		assertThat(journeyMapper.updateJourneySessionStatus(journeyId, "CANCELLED", now.plusSeconds(10)))
				.isEqualTo(1);
		assertThat(journeyMapper.findJourneySessionById(journeyId))
				.get().extracting(JourneySessionEntity::getStatus).isEqualTo("CANCELLED");

		UUID expiredJourneyId = UUID.randomUUID();
		journeyMapper.insertJourneySession(journey(
				expiredJourneyId, profileId, fixture, now.minusSeconds(7200), now.minusSeconds(3600), "ACTIVE"));
		assertThat(journeyMapper.expireJourneySessionsBefore(now, now, 1)).isEqualTo(1);
		assertThat(journeyMapper.findJourneySessionById(expiredJourneyId))
				.get().extracting(JourneySessionEntity::getStatus).isEqualTo("EXPIRED");
	}

	@Test
	void validatesOneJourneyPositionWhenCircularDirectionRepeatsStops() {
		TransitFixture fixture = transitFixture("circular-journey");
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), fixture.getLineId(), fixture.getDirectionId(),
				fixture.getBoardingStopId(), 3, fixture.getAlightingStopId(),
				"loop-platform-3", "순환", "loop-segment-3"));
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), fixture.getLineId(), fixture.getDirectionId(),
				fixture.getAlightingStopId(), 4, null,
				"loop-platform-4", "순환", "loop-segment-4"));

		assertThat(journeyMapper.validateJourneyStopsOnSameDirection(
				fixture.getLineId(), fixture.getDirectionId(),
				fixture.getBoardingStopId(), fixture.getAlightingStopId()))
				.get()
				.satisfies(validation -> {
					assertThat(validation.getBoardingSequence()).isEqualTo(1);
					assertThat(validation.getAlightingSequence()).isEqualTo(2);
				});
	}

	@Test
	void returnsLatestUnexpiredLocationAndDeletesExpiredLocationsInBatches() {
		TransitFixture fixture = transitFixture("location");
		Instant now = Instant.now();
		UUID journeyId = insertJourney(fixture, now);

		long expiredLocationId = journeyLocationMapper.insertTravelerLocationObservation(location(
				journeyId, "37.500000", "127.000000", now.minusSeconds(30), now.minusSeconds(1)));
		long olderLocationId = journeyLocationMapper.insertTravelerLocationObservation(location(
				journeyId, "37.500100", "127.000100", now.minusSeconds(10), now.plusSeconds(300)));
		long latestLocationId = journeyLocationMapper.insertTravelerLocationObservation(location(
				journeyId, "37.500200", "127.000200", now.minusSeconds(5), now.plusSeconds(300)));

		assertThat(journeyLocationMapper.findLatestLocationByJourneyId(journeyId, now))
				.get()
				.satisfies(location -> {
					assertThat(location.getId()).isEqualTo(latestLocationId);
					assertThat(location.getLatitude()).isEqualByComparingTo("37.500200");
				});
		assertThat(journeyLocationMapper.deleteExpiredLocations(now, 1)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM traveler_location_observation WHERE id = ?",
				Integer.class, expiredLocationId)).isZero();
		assertThat(jdbcTemplate.queryForList(
				"SELECT id FROM traveler_location_observation WHERE id IN (?, ?) ORDER BY id",
				Long.class, olderLocationId, latestLocationId))
				.containsExactly(olderLocationId, latestLocationId);
	}

	@Test
	void storesPredictionBatchAndReturnsOnlyLatestCalculation() {
		TransitFixture fixture = transitFixture("prediction");
		Instant now = Instant.now();
		UUID journeyId = insertJourney(fixture, now);
		long locationId = journeyLocationMapper.insertTravelerLocationObservation(location(
				journeyId, "37.500000", "127.000000", now, now.plusSeconds(300)));
		UUID oldCalculationId = UUID.randomUUID();
		UUID latestCalculationId = UUID.randomUUID();

		boardingPredictionMapper.insertBoardingPredictionSnapshots(List.of(
				snapshot(oldCalculationId, journeyId, locationId, "vehicle-1", "WALK",
						"0.45000", false, now.minusSeconds(10), now.plusSeconds(300)),
				snapshot(latestCalculationId, journeyId, locationId, "vehicle-1", "SLOW_WALK",
						"0.30000", false, now, now.plusSeconds(300)),
				snapshot(latestCalculationId, journeyId, locationId, "vehicle-1", "WALK",
						"0.61000", false, now, now.plusSeconds(300)),
				snapshot(latestCalculationId, journeyId, locationId, "vehicle-1", "FAST_WALK",
						"0.84000", true, now, now.plusSeconds(300)),
				snapshot(latestCalculationId, journeyId, locationId, "vehicle-1", "RUN",
						"0.96000", false, now, now.plusSeconds(300))));

		assertThat(boardingPredictionMapper.findLatestBoardingDecisionByJourneyId(journeyId, now))
				.extracting(BoardingPredictionSnapshotEntity::getPaceType)
				.containsExactly("SLOW_WALK", "WALK", "FAST_WALK", "RUN");
		assertThat(boardingPredictionMapper.findLatestBoardingDecisionByJourneyId(journeyId, now))
				.filteredOn(BoardingPredictionSnapshotEntity::getRecommended)
				.singleElement()
				.satisfies(snapshot -> {
					assertThat(snapshot.getCalculationId()).isEqualTo(latestCalculationId);
					assertThat(snapshot.getBoardingProbability()).isEqualByComparingTo("0.84000");
					assertThat(snapshot.getFactorsJson()).contains("HAVERSINE");
				});

		UUID expiredCalculationId = UUID.randomUUID();
		boardingPredictionMapper.insertBoardingPredictionSnapshots(List.of(snapshot(
				expiredCalculationId, journeyId, locationId, "expired-vehicle", "WALK",
				"0.10000", false, now.minusSeconds(60), now.minusSeconds(1))));
		assertThat(boardingPredictionMapper.deleteExpiredPredictions(now, 1)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM boarding_prediction_snapshot WHERE calculation_id = ?",
				Integer.class, expiredCalculationId)).isZero();
	}

	@Test
	void findsOnlyStopsServedByActiveLinesInsideCoordinateBounds() {
		TransitFixture fixture = transitFixture("nearby", "35.000000", "129.000000");
		long providerId = transitProviderMapper.findByCode("GBIS").orElseThrow().getId();
		TransitStopEntity standaloneStop = stop(providerId, "standalone", "35.000050", "129.000050");
		transitStopMapper.upsertTransitStop(standaloneStop);

		assertThat(transitStopMapper.findActiveStopsWithinCoordinateBounds(
				decimal("34.999000"), decimal("35.001000"),
				decimal("128.999000"), decimal("129.002000"),
				decimal("35.000000"), decimal("129.000000"), 10))
				.extracting(TransitStopEntity::getId)
				.containsExactly(fixture.getBoardingStopId(), fixture.getAlightingStopId())
				.doesNotContain(standaloneStop.getId());
	}

	private UUID insertProfile() {
		UUID id = UUID.randomUUID();
		travelerProfileMapper.upsertTravelerProfile(profile(
				id, UUID.randomUUID(), "0.90", "1.30", "1.70", "2.50", 0));
		return id;
	}

	private UUID insertJourney(TransitFixture fixture, Instant now) {
		UUID journeyId = UUID.randomUUID();
		journeyMapper.insertJourneySession(journey(
				journeyId, insertProfile(), fixture, now, now.plusSeconds(3600), "ACTIVE"));
		return journeyId;
	}

	private TransitFixture transitFixture(String prefix) {
		return transitFixture(prefix, "37.500000", "127.000000");
	}

	private TransitFixture transitFixture(
			String prefix,
			String boardingLatitude,
			String boardingLongitude) {
		long providerId = transitProviderMapper.findByCode("GBIS").orElseThrow().getId();
		TransitLineEntity line = TransitLineEntity.builder()
				.id(UUID.randomUUID())
				.providerId(providerId)
				.providerLineId(prefix + "-" + UUID.randomUUID())
				.publicName(prefix)
				.operatorName("test")
				.routeType("CITY_BUS")
				.active(true)
				.sourceUpdatedAt(Instant.now())
				.build();
		TransitStopEntity boarding = stop(providerId, prefix + "-boarding", boardingLatitude, boardingLongitude);
		TransitStopEntity alighting = stop(
				providerId,
				prefix + "-alighting",
				decimal(boardingLatitude).add(decimal("0.000500")).toPlainString(),
				decimal(boardingLongitude).add(decimal("0.000500")).toPlainString());
		transitLineMapper.upsertTransitLine(line);
		transitStopMapper.upsertTransitStop(boarding);
		transitStopMapper.upsertTransitStop(alighting);
		UUID directionId = UUID.randomUUID();
		routeDirectionMapper.upsertRouteDirection(RouteDirectionEntity.builder()
				.id(directionId)
				.lineId(line.getId())
				.providerDirectionId(prefix + "-outbound")
				.originStopId(boarding.getId())
				.terminalStopId(alighting.getId())
				.representativeNextStopId(alighting.getId())
				.displayName("도착지 방면")
				.active(true)
				.build());
		directedStopMapper.upsertDirectedStop(DirectedStopAssignmentEntity.builder()
				.id(UUID.randomUUID()).lineId(line.getId()).directionId(directionId)
				.stopId(boarding.getId()).stopSequence(1).nextStopId(alighting.getId())
				.displayDirection("도착지 방면").build());
		directedStopMapper.upsertDirectedStop(DirectedStopAssignmentEntity.builder()
				.id(UUID.randomUUID()).lineId(line.getId()).directionId(directionId)
				.stopId(alighting.getId()).stopSequence(2)
				.displayDirection("도착지 방면").build());
		return new TransitFixture(line.getId(), directionId, boarding.getId(), alighting.getId());
	}

	private TransitStopEntity stop(long providerId, String prefix, String latitude, String longitude) {
		return TransitStopEntity.builder()
				.id(UUID.randomUUID())
				.providerId(providerId)
				.providerStopId(prefix + "-" + UUID.randomUUID())
				.publicName(prefix)
				.latitude(decimal(latitude))
				.longitude(decimal(longitude))
				.sourceUpdatedAt(Instant.now())
				.build();
	}

	private TravelerProfileEntity profile(
			UUID id,
			UUID anonymousKey,
			String slow,
			String walk,
			String fast,
			String run,
			int sampleCount) {
		return TravelerProfileEntity.builder()
				.id(id)
				.anonymousKey(anonymousKey)
				.slowWalkSpeedMps(decimal(slow))
				.walkSpeedMps(decimal(walk))
				.fastWalkSpeedMps(decimal(fast))
				.runSpeedMps(decimal(run))
				.sampleCount(sampleCount)
				.build();
	}

	private JourneySessionEntity journey(
			UUID id,
			UUID profileId,
			TransitFixture fixture,
			Instant createdAt,
			Instant expiresAt,
			String status) {
		return JourneySessionEntity.builder()
				.id(id)
				.travelerProfileId(profileId)
				.lineId(fixture.getLineId())
				.directionId(fixture.getDirectionId())
				.boardingStopId(fixture.getBoardingStopId())
				.alightingStopId(fixture.getAlightingStopId())
				.targetProbability(decimal("0.8000"))
				.status(status)
				.expiresAt(expiresAt)
				.createdAt(createdAt)
				.updatedAt(createdAt)
				.build();
	}

	private TravelerLocationObservationEntity location(
			UUID journeyId,
			String latitude,
			String longitude,
			Instant observedAt,
			Instant expiresAt) {
		return TravelerLocationObservationEntity.builder()
				.journeyId(journeyId)
				.latitude(decimal(latitude))
				.longitude(decimal(longitude))
				.accuracyM(decimal("12.50"))
				.speedMps(decimal("1.30"))
				.observedAt(observedAt)
				.receivedAt(observedAt.plusMillis(100))
				.expiresAt(expiresAt)
				.build();
	}

	private BoardingPredictionSnapshotEntity snapshot(
			UUID calculationId,
			UUID journeyId,
			long locationId,
			String vehicleId,
			String paceType,
			String probability,
			boolean recommended,
			Instant calculatedAt,
			Instant expiresAt) {
		return BoardingPredictionSnapshotEntity.builder()
				.calculationId(calculationId)
				.journeyId(journeyId)
				.locationObservationId(locationId)
				.providerVehicleId(vehicleId)
				.paceType(paceType)
				.paceSpeedMps(decimal("1.70"))
				.distanceM(decimal("280.00"))
				.userMinExpectedAt(calculatedAt.plusSeconds(150))
				.userExpectedAt(calculatedAt.plusSeconds(180))
				.userMaxExpectedAt(calculatedAt.plusSeconds(240))
				.vehicleMinExpectedAt(calculatedAt.plusSeconds(210))
				.vehicleExpectedAt(calculatedAt.plusSeconds(270))
				.vehicleMaxExpectedAt(calculatedAt.plusSeconds(330))
				.boardingProbability(decimal(probability))
				.decision(recommended ? "LEAVE_NOW" : "UNLIKELY")
				.recommended(recommended)
				.confidence("MEDIUM")
				.modelVersion("HEURISTIC_V1")
				.factorsJson("{\"distanceSource\":\"HAVERSINE\"}")
				.calculatedAt(calculatedAt)
				.expiresAt(expiresAt)
				.build();
	}

	private BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}

	@Getter
	@RequiredArgsConstructor
	private static class TransitFixture {
		private final UUID lineId;
		private final UUID directionId;
		private final UUID boardingStopId;
		private final UUID alightingStopId;
	}
}
