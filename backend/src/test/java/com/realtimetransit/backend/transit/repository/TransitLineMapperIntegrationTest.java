package com.realtimetransit.backend.transit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.DirectedStopAssignmentEntity;
import com.realtimetransit.backend.transit.entity.RouteDirectionEntity;
import com.realtimetransit.backend.transit.entity.StopPatternEntity;
import com.realtimetransit.backend.transit.entity.StopPatternStopEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;
import com.realtimetransit.backend.provider.entity.RawObservationEntity;
import com.realtimetransit.backend.provider.entity.VehicleRunObservationEntity;
import com.realtimetransit.backend.provider.entity.ArrivalPredictionObservationEntity;
import com.realtimetransit.backend.provider.repository.ArrivalPredictionObservationMapper;
import com.realtimetransit.backend.provider.repository.RawObservationMapper;
import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.provider.repository.VehicleRunObservationMapper;

@SpringBootTest
@Testcontainers
class TransitLineMapperIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

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
	private StopPatternMapper stopPatternMapper;

	@Autowired
	private RawObservationMapper rawObservationMapper;

	@Autowired
	private VehicleRunObservationMapper vehicleRunObservationMapper;

	@Autowired
	private ArrivalPredictionObservationMapper arrivalPredictionObservationMapper;

	@Autowired
	private ArrivalQueryMapper arrivalQueryMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void upsertsAndReadsLineAndStopThroughXmlMappers() {
		long providerId = transitProviderMapper.findByCode("GBIS").orElseThrow().getId();
		Instant sourceUpdatedAt = Instant.parse("2026-08-28T00:00:00Z");
		var line = new TransitLineEntity(
				UUID.randomUUID(), providerId, "route-1", "1000", "operator", "CITY_BUS",
				true, sourceUpdatedAt, null, null);
		var stop = new TransitStopEntity(
				UUID.randomUUID(), providerId, "stop-1", null, "테스트 정류장",
				new BigDecimal("37.123456"), new BigDecimal("127.123456"), sourceUpdatedAt, null, null);
		var destinationStop = new TransitStopEntity(
				UUID.randomUUID(), providerId, "stop-2", null, "다음 정류장",
				new BigDecimal("37.223456"), new BigDecimal("127.223456"), sourceUpdatedAt, null, null);

		transitLineMapper.upsertTransitLine(line);
		transitStopMapper.upsertTransitStop(stop);
		transitStopMapper.upsertTransitStop(destinationStop);
		assertThat(transitLineMapper.findActiveLinesByProviderLineIds(
				providerId, List.of("route-1", "missing-route")))
				.extracting(TransitLineEntity::getProviderLineId)
				.containsExactly("route-1");

		UUID directionId = UUID.randomUUID();
		routeDirectionMapper.upsertRouteDirection(new RouteDirectionEntity(
				directionId, line.getId(), "outbound", stop.getId(), destinationStop.getId(), destinationStop.getId(),
				"종점 방면", true, null, null));
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), line.getId(), directionId, stop.getId(), 1, destinationStop.getId(),
				"platform-1", "종점 방면", "segment-1"));
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), line.getId(), directionId, destinationStop.getId(), 2, null,
				"platform-2", "종점 방면", "segment-2"));
		UUID duplicateDisplayDirectionId = UUID.randomUUID();
		routeDirectionMapper.upsertRouteDirection(new RouteDirectionEntity(
				duplicateDisplayDirectionId, line.getId(), "outbound-branch", stop.getId(),
				destinationStop.getId(), destinationStop.getId(), "종점 방면", true, null, null));
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), line.getId(), duplicateDisplayDirectionId, stop.getId(), 10,
				destinationStop.getId(), "platform-branch-1", "종점 방면", "segment-branch-1"));
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), line.getId(), duplicateDisplayDirectionId, destinationStop.getId(), 11,
				null, "platform-branch-2", "종점 방면", "segment-branch-2"));
		UUID arrivalPatternId = UUID.randomUUID();
		stopPatternMapper.upsertStopPattern(new StopPatternEntity(
				arrivalPatternId, line.getId(), "arrival-pattern", "LOCAL",
				LocalDate.parse("2026-09-01"), null, true, null, null));
		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(arrivalPatternId, 1, stop.getId(), true, false),
				new StopPatternStopEntity(arrivalPatternId, 2, destinationStop.getId(), false, true)));

		Instant receivedAt = Instant.parse("2026-08-31T01:00:00Z");
		long rawObservationId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, "/vehicle-position", "route-1:vehicle-1", receivedAt,
				receivedAt.minusSeconds(5), 200, "{\"vehicleId\":\"vehicle-1\"}",
				receivedAt.plusSeconds(30)));
		long vehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, rawObservationId, line.getId(), directionId, arrivalPatternId,
						"vehicle-1", "run-1", destinationStop.getId(), stop.getId(), 1,
						"LOCAL", "APPROACHING", new BigDecimal("37.123456"),
						new BigDecimal("127.123456"), new BigDecimal("32.50"),
						new BigDecimal("175.25"), "GPS", receivedAt.minusSeconds(5), receivedAt));

		assertThat(jdbcTemplate.queryForMap(
				"SELECT raw_observation_id, line_id, direction_id, provider_vehicle_id, "
						+ "current_stop_id, movement_status, position_source "
						+ "FROM vehicle_run_observation WHERE id = ?",
				vehicleObservationId))
				.containsEntry("raw_observation_id", rawObservationId)
				.containsEntry("line_id", line.getId())
				.containsEntry("direction_id", directionId)
				.containsEntry("provider_vehicle_id", "vehicle-1")
				.containsEntry("current_stop_id", stop.getId())
				.containsEntry("movement_status", "APPROACHING")
				.containsEntry("position_source", "GPS");

		long nullableVehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, null, line.getId(), null, arrivalPatternId, "vehicle-2", null,
						null, null, null, "LOCAL", "UNKNOWN", null, null,
						null, null, "STOP_SEQUENCE", receivedAt, receivedAt));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT raw_observation_id IS NULL AND direction_id IS NULL "
						+ "AND latitude IS NULL AND speed_kph IS NULL "
						+ "FROM vehicle_run_observation WHERE id = ?",
				Boolean.class, nullableVehicleObservationId))
				.isTrue();

		long arrivalPredictionId = arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				ArrivalPredictionObservationEntity.builder()
						.rawObservationId(rawObservationId)
						.vehicleRunObservationId(vehicleObservationId)
						.boardingStopId(stop.getId())
						.requestedAlightingStopId(destinationStop.getId())
						.alightingStopConfirmed(true)
						.alightingStopStatus("STOPS")
						.expectedAt(receivedAt.plusSeconds(300))
						.minExpectedAt(receivedAt.plusSeconds(240))
						.maxExpectedAt(receivedAt.plusSeconds(420))
						.remainingStops(3)
						.source("PROVIDER")
						.confidence("HIGH")
						.observedAt(receivedAt.minusSeconds(5))
						.receivedAt(receivedAt)
						.build());
		assertThat(jdbcTemplate.queryForMap(
				"SELECT raw_observation_id, vehicle_run_observation_id, boarding_stop_id, "
						+ "remaining_stops, source, confidence "
						+ "FROM arrival_prediction_observation WHERE id = ?",
				arrivalPredictionId))
				.containsEntry("raw_observation_id", rawObservationId)
				.containsEntry("vehicle_run_observation_id", vehicleObservationId)
				.containsEntry("boarding_stop_id", stop.getId())
				.containsEntry("remaining_stops", 3)
				.containsEntry("source", "PROVIDER")
				.containsEntry("confidence", "HIGH");

		long nullableArrivalPredictionId = arrivalPredictionObservationMapper
				.insertArrivalPredictionObservation(new ArrivalPredictionObservationEntity(
						null, null, null, destinationStop.getId(), null, null, null, null,
						"CALCULATED", "UNKNOWN", receivedAt, receivedAt));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT raw_observation_id IS NULL AND vehicle_run_observation_id IS NULL "
						+ "AND expected_at IS NULL AND remaining_stops IS NULL "
						+ "FROM arrival_prediction_observation WHERE id = ?",
				Boolean.class, nullableArrivalPredictionId))
				.isTrue();

		long oldestArrivalPredictionId = arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				new ArrivalPredictionObservationEntity(
						null, rawObservationId, vehicleObservationId, stop.getId(),
						receivedAt.plusSeconds(60), null, null, 5, "PROVIDER", "LOW",
						receivedAt.minusSeconds(60), receivedAt.minusSeconds(55)));
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				new ArrivalPredictionObservationEntity(
						null, null, nullableVehicleObservationId, stop.getId(),
						receivedAt.plusSeconds(180), null, null, 2, "CALCULATED", "MEDIUM",
						receivedAt, receivedAt));
		UUID reversedPatternId = UUID.randomUUID();
		stopPatternMapper.upsertStopPattern(new StopPatternEntity(
				reversedPatternId, line.getId(), "reversed-arrival-pattern", "LOCAL",
				LocalDate.parse("2026-09-01"), null, true, null, null));
		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(reversedPatternId, 1, destinationStop.getId(), false, true),
				new StopPatternStopEntity(reversedPatternId, 2, stop.getId(), true, false)));
		long reversedVehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, null, line.getId(), directionId, reversedPatternId, "vehicle-reversed", null,
						destinationStop.getId(), stop.getId(), 2, "LOCAL", "APPROACHING",
						null, null, null, null, "STOP_SEQUENCE", receivedAt, receivedAt));
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				new ArrivalPredictionObservationEntity(
						null, null, reversedVehicleObservationId, stop.getId(),
						receivedAt.plusSeconds(30), null, null, 1, "CALCULATED", "HIGH",
						receivedAt, receivedAt));
		UUID sharedCorridorPatternId = UUID.randomUUID();
		stopPatternMapper.upsertStopPattern(new StopPatternEntity(
				sharedCorridorPatternId, line.getId(), "shared-corridor-pattern", "LOCAL",
				LocalDate.parse("2026-09-01"), null, true, null, null));
		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(sharedCorridorPatternId, 1, stop.getId(), true, false),
				new StopPatternStopEntity(sharedCorridorPatternId, 2, destinationStop.getId(), false, true)));
		long sharedCorridorVehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				VehicleRunObservationEntity.builder()
						.lineId(line.getId())
						.directionId(duplicateDisplayDirectionId)
						.stopPatternId(sharedCorridorPatternId)
						.providerVehicleId("vehicle-shared-corridor")
						.serviceType("LOCAL")
						.movementStatus("APPROACHING")
						.positionSource("STOP_SEQUENCE")
						.observedAt(receivedAt)
						.receivedAt(receivedAt)
						.build());
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				ArrivalPredictionObservationEntity.builder()
						.vehicleRunObservationId(sharedCorridorVehicleObservationId)
						.boardingStopId(stop.getId())
						.requestedAlightingStopId(destinationStop.getId())
						.alightingStopConfirmed(true)
						.alightingStopStatus("STOPS")
						.expectedAt(receivedAt.plusSeconds(120))
						.source("PROVIDER")
						.confidence("HIGH")
						.observedAt(receivedAt)
						.receivedAt(receivedAt)
						.build());

		assertThat(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				line.getId(), stop.getId(), destinationStop.getId(), receivedAt,
				receivedAt.minusSeconds(120), 2))
				.satisfiesExactly(
						sharedCorridorVehicle -> {
							assertThat(sharedCorridorVehicle.getProviderVehicleId())
									.isEqualTo("vehicle-shared-corridor");
							assertThat(sharedCorridorVehicle.getExpectedAt())
									.isEqualTo(receivedAt.plusSeconds(120));
						},
						firstVehicle -> {
							assertThat(firstVehicle.getProviderVehicleId()).isEqualTo("vehicle-1");
							assertThat(firstVehicle.getExpectedAt()).isEqualTo(receivedAt.plusSeconds(300));
							assertThat(firstVehicle.getConfidence()).isEqualTo("HIGH");
						});

		assertThat(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopId(
				line.getId(), directionId, stop.getId(), receivedAt, receivedAt.minusSeconds(30), 3))
				.satisfiesExactly(
						reversedVehicle -> {
							assertThat(reversedVehicle.getProviderVehicleId()).isEqualTo("vehicle-reversed");
							assertThat(reversedVehicle.getExpectedAt()).isEqualTo(receivedAt.plusSeconds(30));
						},
						firstVehicle -> {
							assertThat(firstVehicle.getProviderVehicleId()).isEqualTo("vehicle-1");
							assertThat(firstVehicle.getExpectedAt()).isEqualTo(receivedAt.plusSeconds(300));
							assertThat(firstVehicle.getConfidence()).isEqualTo("HIGH");
						});
		assertThat(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopId(
				line.getId(), directionId, stop.getId(), receivedAt, receivedAt.minusSeconds(30), 2))
				.hasSize(2);

		long freshlyReceivedVehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, null, line.getId(), directionId, arrivalPatternId, "freshly-received", null,
						destinationStop.getId(), stop.getId(), 1, "LOCAL", "APPROACHING",
						null, null, null, null, "STOP_SEQUENCE",
						receivedAt.minusSeconds(90), receivedAt));
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				new ArrivalPredictionObservationEntity(
						null, null, freshlyReceivedVehicleObservationId, stop.getId(),
						receivedAt.plusSeconds(10), null, null, 0, "PROVIDER", "HIGH",
						receivedAt.minusSeconds(90), receivedAt));

		assertThat(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopId(
				line.getId(), directionId, stop.getId(), receivedAt, receivedAt.minusSeconds(30), 1))
				.singleElement()
				.extracting(UpcomingArrivalEntity::getProviderVehicleId)
				.isEqualTo("vehicle-reversed");

		long subwayProviderIdForArrival = transitProviderMapper.findByCode("SEOUL_SUBWAY").orElseThrow().getId();
		jdbcTemplate.update("UPDATE transit_line SET provider_id = ? WHERE id = ?", subwayProviderIdForArrival, line.getId());
		long confirmedExpressVehicleId = vehicleRunObservationMapper.insertVehicleRunObservation(
				VehicleRunObservationEntity.builder()
						.lineId(line.getId())
						.directionId(directionId)
						.stopPatternId(arrivalPatternId)
						.providerVehicleId("express-confirmed")
						.serviceType("EXPRESS")
						.movementStatus("BETWEEN")
						.positionSource("STOP_SEQUENCE")
						.observedAt(receivedAt)
						.receivedAt(receivedAt)
						.build());
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				ArrivalPredictionObservationEntity.builder()
						.vehicleRunObservationId(confirmedExpressVehicleId)
						.boardingStopId(stop.getId())
						.requestedAlightingStopId(destinationStop.getId())
						.alightingStopConfirmed(true)
						.alightingStopStatus("STOPS")
						.expectedAt(receivedAt.plusSeconds(20))
						.source("PROVIDER")
						.confidence("HIGH")
						.observedAt(receivedAt)
						.receivedAt(receivedAt)
						.build());
		long unconfirmedExpressVehicleId = vehicleRunObservationMapper.insertVehicleRunObservation(
				VehicleRunObservationEntity.builder()
						.lineId(line.getId())
						.directionId(directionId)
						.stopPatternId(arrivalPatternId)
						.providerVehicleId("express-unconfirmed")
						.serviceType("EXPRESS")
						.movementStatus("BETWEEN")
						.positionSource("STOP_SEQUENCE")
						.observedAt(receivedAt)
						.receivedAt(receivedAt)
						.build());
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				ArrivalPredictionObservationEntity.builder()
						.vehicleRunObservationId(unconfirmedExpressVehicleId)
						.boardingStopId(stop.getId())
						.requestedAlightingStopId(destinationStop.getId())
						.alightingStopConfirmed(false)
						.alightingStopStatus("SKIPS")
						.expectedAt(receivedAt.plusSeconds(10))
						.source("PROVIDER")
						.confidence("HIGH")
						.observedAt(receivedAt)
						.receivedAt(receivedAt)
						.build());
		long unknownExpressVehicleId = vehicleRunObservationMapper.insertVehicleRunObservation(
				VehicleRunObservationEntity.builder()
						.lineId(line.getId())
						.directionId(directionId)
						.stopPatternId(arrivalPatternId)
						.providerVehicleId("express-unknown")
						.serviceType("EXPRESS")
						.movementStatus("BETWEEN")
						.positionSource("STOP_SEQUENCE")
						.observedAt(receivedAt)
						.receivedAt(receivedAt)
						.build());
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				ArrivalPredictionObservationEntity.builder()
						.vehicleRunObservationId(unknownExpressVehicleId)
						.boardingStopId(stop.getId())
						.requestedAlightingStopId(destinationStop.getId())
						.alightingStopConfirmed(false)
						.alightingStopStatus("UNKNOWN")
						.expectedAt(receivedAt.plusSeconds(15))
						.source("PROVIDER")
						.confidence("LOW")
						.observedAt(receivedAt)
						.receivedAt(receivedAt)
						.build());

		assertThat(arrivalQueryMapper.findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
				line.getId(), stop.getId(), destinationStop.getId(), receivedAt,
				receivedAt.minusSeconds(30), 10))
				.extracting(UpcomingArrivalEntity::getProviderVehicleId)
				.contains("express-confirmed")
				.doesNotContain("express-unconfirmed", "express-unknown");
		jdbcTemplate.update("UPDATE transit_line SET provider_id = ? WHERE id = ?", providerId, line.getId());

		vehicleRunObservationMapper.insertVehicleRunObservation(new VehicleRunObservationEntity(
				null, null, line.getId(), directionId, arrivalPatternId, "history-vehicle", null,
				destinationStop.getId(), stop.getId(), 1, "LOCAL", "BETWEEN",
				null, null, null, null, "STOP_SEQUENCE",
				receivedAt.minusSeconds(10), receivedAt.minusSeconds(10)));
		long boundaryHistoryId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, null, line.getId(), directionId, arrivalPatternId, "history-vehicle", null,
						destinationStop.getId(), stop.getId(), 1, "LOCAL", "APPROACHING",
						null, null, null, null, "STOP_SEQUENCE", receivedAt, receivedAt));
		long latestReceivedHistoryId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, null, line.getId(), directionId, arrivalPatternId, "history-vehicle", null,
						destinationStop.getId(), stop.getId(), 1, "LOCAL", "ARRIVED",
						null, null, null, null, "STOP_SEQUENCE",
						receivedAt, receivedAt.plusSeconds(1)));
		assertThat(vehicleRunObservationMapper.findRecentVehicleRunObservations(
				line.getId(), "history-vehicle", receivedAt, 2))
				.extracting(VehicleRunObservationEntity::getId)
				.containsExactly(latestReceivedHistoryId, boundaryHistoryId);
		assertThat(vehicleRunObservationMapper.findRecentVehicleRunObservations(
				line.getId(), "missing-vehicle", receivedAt.minusSeconds(60), 2))
				.isEmpty();

		long oldestExpiredRawId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, "/cleanup", "oldest", receivedAt.minusSeconds(300),
				null, 200, null, receivedAt.minusSeconds(200)));
		long newerExpiredRawId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, "/cleanup", "newer", receivedAt.minusSeconds(100),
				null, 200, null, receivedAt.minusSeconds(50)));
		long futureRawId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, "/cleanup", "future", receivedAt,
				null, 200, null, receivedAt.plusSeconds(300)));
		long noExpiryRawId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, "/cleanup", "no-expiry", receivedAt,
				null, 200, null, null));

		assertThat(rawObservationMapper.deleteExpiredRawObservations(receivedAt, 1)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM raw_observation WHERE id = ?", Integer.class, oldestExpiredRawId))
				.isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM raw_observation WHERE id = ?", Integer.class, newerExpiredRawId))
				.isEqualTo(1);
		assertThat(rawObservationMapper.deleteExpiredRawObservations(receivedAt.plusSeconds(30), 10))
				.isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT raw_observation_id IS NULL FROM vehicle_run_observation WHERE id = ?",
				Boolean.class, vehicleObservationId))
				.isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT raw_observation_id IS NULL FROM arrival_prediction_observation WHERE id = ?",
				Boolean.class, arrivalPredictionId))
				.isTrue();
		assertThat(jdbcTemplate.queryForList(
				"SELECT id FROM raw_observation WHERE id IN (?, ?) ORDER BY id",
				Long.class, futureRawId, noExpiryRawId))
				.containsExactlyInAnyOrder(futureRawId, noExpiryRawId);

		long newerOldArrivalPredictionId = arrivalPredictionObservationMapper
				.insertArrivalPredictionObservation(new ArrivalPredictionObservationEntity(
						null, null, vehicleObservationId, stop.getId(), receivedAt.plusSeconds(240),
						null, null, 4, "CALCULATED", "LOW",
						receivedAt.minusSeconds(35), receivedAt.minusSeconds(30)));
		assertThat(arrivalPredictionObservationMapper.deleteArrivalPredictionsReceivedBefore(
				receivedAt, 1))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM arrival_prediction_observation WHERE id = ?",
				Integer.class, oldestArrivalPredictionId))
				.isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM arrival_prediction_observation WHERE id = ?",
				Integer.class, newerOldArrivalPredictionId))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM arrival_prediction_observation WHERE id = ?",
				Integer.class, arrivalPredictionId))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM vehicle_run_observation WHERE id = ?",
				Integer.class, vehicleObservationId))
				.isEqualTo(1);

		long oldestVehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, null, line.getId(), directionId, null, "cleanup-vehicle-1", null,
						null, stop.getId(), 1, "LOCAL", "BETWEEN", null, null, null, null,
						"STOP_SEQUENCE", receivedAt.minusSeconds(105), receivedAt.minusSeconds(100)));
		long newerOldVehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, null, line.getId(), directionId, null, "cleanup-vehicle-2", null,
						null, stop.getId(), 1, "LOCAL", "BETWEEN", null, null, null, null,
						"STOP_SEQUENCE", receivedAt.minusSeconds(55), receivedAt.minusSeconds(50)));
		long referencingPredictionId = arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				new ArrivalPredictionObservationEntity(
						null, null, oldestVehicleObservationId, stop.getId(), receivedAt.plusSeconds(600),
						null, null, 6, "CALCULATED", "LOW", receivedAt, receivedAt));

		assertThat(vehicleRunObservationMapper.deleteVehicleRunObservationsReceivedBefore(receivedAt, 1))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM vehicle_run_observation WHERE id = ?",
				Integer.class, oldestVehicleObservationId))
				.isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM vehicle_run_observation WHERE id = ?",
				Integer.class, newerOldVehicleObservationId))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT vehicle_run_observation_id IS NULL "
						+ "FROM arrival_prediction_observation WHERE id = ?",
				Boolean.class, referencingPredictionId))
				.isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM transit_line WHERE id = ?", Integer.class, line.getId()))
				.isEqualTo(1);

		assertThat(transitLineMapper.findByProviderResourceId(providerId, "route-1"))
				.get().extracting(TransitLineEntity::getPublicName).isEqualTo("1000");
		assertThat(transitStopMapper.findByProviderResourceId(providerId, "stop-1"))
				.get().extracting(TransitStopEntity::getPublicName).isEqualTo("테스트 정류장");
		assertThat(transitStopMapper.findActiveStopsByLineId(line.getId()))
				.extracting(directedStop -> directedStop.getStopSequence())
				.containsExactly(1, 2);
		assertThat(transitStopMapper.findDestinationsAfterBoardingStop(line.getId(), stop.getId()))
				.singleElement()
				.extracting(destination -> destination.getStopId())
				.isEqualTo(destinationStop.getId());
		routeDirectionMapper.upsertRouteDirection(new RouteDirectionEntity(
				duplicateDisplayDirectionId, line.getId(), "outbound-branch", stop.getId(),
				destinationStop.getId(), destinationStop.getId(), "종점 방면", false, null, null));
		assertThat(transitStopMapper.findDestinationsAfterBoardingStop(line.getId(), stop.getId()))
				.singleElement()
				.satisfies(destination -> {
					assertThat(destination.getDirectionId()).isEqualTo(directionId);
					assertThat(destination.getStopId()).isEqualTo(destinationStop.getId());
					assertThat(destination.getStopName()).isEqualTo("다음 정류장");
					assertThat(destination.getStopSequence()).isEqualTo(2);
				});
		assertThat(transitStopMapper.canReachAlightingBeforeTerminal(
				line.getId(), "outbound", stop.getId(), destinationStop.getId(), "stop-2"))
				.contains(true);

		routeDirectionMapper.upsertRouteDirection(new RouteDirectionEntity(
				UUID.randomUUID(), line.getId(), "outbound", stop.getId(), destinationStop.getId(), destinationStop.getId(),
				"수정된 종점 방면", false, null, null));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT display_name FROM route_direction WHERE id = ?", String.class, directionId))
				.isEqualTo("수정된 종점 방면");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM route_direction WHERE line_id = ? AND provider_direction_id = ?",
				Integer.class, line.getId(), "outbound"))
				.isEqualTo(1);
		assertThat(routeDirectionMapper.findRouteDirectionByBusinessKey(line.getId(), "outbound"))
				.get()
				.satisfies(direction -> {
					assertThat(direction.getId()).isEqualTo(directionId);
					assertThat(direction.getDisplayName()).isEqualTo("수정된 종점 방면");
					assertThat(direction.getActive()).isFalse();
				});
		assertThat(routeDirectionMapper.findRouteDirectionByBusinessKey(line.getId(), "missing-direction"))
				.isEmpty();

		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), line.getId(), directionId, destinationStop.getId(), 2, null,
				"platform-2-updated", "수정된 종점 방면", "segment-2-updated"));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM directed_stop WHERE direction_id = ? AND stop_sequence = ?",
				Integer.class, directionId, 2))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForMap(
				"SELECT platform_id, display_direction, segment_id FROM directed_stop "
						+ "WHERE direction_id = ? AND stop_sequence = ?",
				directionId, 2))
				.containsEntry("platform_id", "platform-2-updated")
				.containsEntry("display_direction", "수정된 종점 방면")
				.containsEntry("segment_id", "segment-2-updated");

		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), line.getId(), directionId, destinationStop.getId(), 3, null,
				"platform-3", "수정된 종점 방면", "segment-3"));
		UUID otherDirectionId = UUID.randomUUID();
		routeDirectionMapper.upsertRouteDirection(new RouteDirectionEntity(
				otherDirectionId, line.getId(), "inbound", destinationStop.getId(), stop.getId(), stop.getId(),
				"기점 방면", true, null, null));
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), line.getId(), otherDirectionId, destinationStop.getId(), 1, null,
				"platform-inbound", "기점 방면", "segment-inbound"));
		UUID removedDirectionId = UUID.randomUUID();
		routeDirectionMapper.upsertRouteDirection(new RouteDirectionEntity(
				removedDirectionId, line.getId(), "weekend", stop.getId(), destinationStop.getId(), destinationStop.getId(),
				"주말 종점 방면", true, null, null));

		assertThat(routeDirectionMapper.deactivateRouteDirectionsNotInProviderIds(
				line.getId(), List.of("inbound")))
				.isEqualTo(1);
		assertThat(routeDirectionMapper.findRouteDirectionByBusinessKey(line.getId(), "inbound"))
				.get().extracting(RouteDirectionEntity::getActive).isEqualTo(true);
		assertThat(routeDirectionMapper.findRouteDirectionByBusinessKey(line.getId(), "weekend"))
				.get().extracting(RouteDirectionEntity::getActive).isEqualTo(false);
		assertThat(routeDirectionMapper.deactivateRouteDirectionsNotInProviderIds(line.getId(), List.of()))
				.isEqualTo(1);
		assertThat(routeDirectionMapper.findRouteDirectionByBusinessKey(line.getId(), "inbound"))
				.get().extracting(RouteDirectionEntity::getActive).isEqualTo(false);

		assertThat(directedStopMapper.deleteDirectedStopsNotInSequences(directionId, List.of(1, 2)))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForList(
				"SELECT stop_sequence FROM directed_stop WHERE direction_id = ? ORDER BY stop_sequence",
				Integer.class, directionId))
				.containsExactly(1, 2);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM directed_stop WHERE direction_id = ?",
				Integer.class, otherDirectionId))
				.isEqualTo(1);
		assertThat(directedStopMapper.deleteDirectedStopsNotInSequences(otherDirectionId, List.of()))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM directed_stop WHERE direction_id = ?",
				Integer.class, otherDirectionId))
				.isZero();

		insertStopPattern(line.getId(), "local-valid-on-end-date", "LOCAL",
				LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), true);
		insertStopPattern(line.getId(), "express-open-ended", "EXPRESS",
				LocalDate.parse("2026-08-31"), null, true);
		insertStopPattern(line.getId(), "expired", "LOCAL",
				LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-30"), true);
		insertStopPattern(line.getId(), "future", "LOCAL",
				LocalDate.parse("2026-09-01"), null, true);
		insertStopPattern(line.getId(), "inactive", "LOCAL",
				LocalDate.parse("2026-08-01"), null, false);

		assertThat(stopPatternMapper.findActiveStopPatternsByLineIdAndServiceDate(
				line.getId(), LocalDate.parse("2026-08-31")))
				.extracting(pattern -> pattern.getProviderPatternId())
				.containsExactly("express-open-ended", "local-valid-on-end-date");

		UUID upsertedPatternId = UUID.randomUUID();
		LocalDate upsertedPatternValidFrom = LocalDate.parse("2026-09-01");
		stopPatternMapper.upsertStopPattern(new StopPatternEntity(
				upsertedPatternId, line.getId(), "upsert-pattern", "LOCAL",
				upsertedPatternValidFrom, null, true, null, null));
		stopPatternMapper.upsertStopPattern(new StopPatternEntity(
				UUID.randomUUID(), line.getId(), "upsert-pattern", "EXPRESS",
				upsertedPatternValidFrom, LocalDate.parse("2026-12-31"), false, null, null));

		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM stop_pattern "
						+ "WHERE line_id = ? AND provider_pattern_id = ? AND valid_from = ?",
				Integer.class, line.getId(), "upsert-pattern", upsertedPatternValidFrom))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForMap(
				"SELECT id, service_type, valid_to, active FROM stop_pattern "
						+ "WHERE line_id = ? AND provider_pattern_id = ? AND valid_from = ?",
				line.getId(), "upsert-pattern", upsertedPatternValidFrom))
				.containsEntry("id", upsertedPatternId)
				.containsEntry("service_type", "EXPRESS")
				.containsEntry("valid_to", java.sql.Date.valueOf("2026-12-31"))
				.containsEntry("active", false);
		assertThat(stopPatternMapper.findStopPatternByBusinessKey(
				line.getId(), "upsert-pattern", upsertedPatternValidFrom))
				.get()
				.satisfies(pattern -> {
					assertThat(pattern.getId()).isEqualTo(upsertedPatternId);
					assertThat(pattern.getServiceType()).isEqualTo("EXPRESS");
					assertThat(pattern.getActive()).isFalse();
				});
		assertThat(stopPatternMapper.findStopPatternByBusinessKey(
				line.getId(), "missing-pattern", upsertedPatternValidFrom))
				.isEmpty();

		UUID batchPatternId = insertStopPattern(line.getId(), "batch-pattern", "LOCAL",
				LocalDate.parse("2026-08-31"), null, true);
		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(batchPatternId, 1, stop.getId(), true, false),
				new StopPatternStopEntity(batchPatternId, 2, destinationStop.getId(), false, true)));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM stop_pattern_stop WHERE stop_pattern_id = ?",
				Integer.class, batchPatternId))
				.isEqualTo(2);

		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(batchPatternId, 2, stop.getId(), true, true)));
		assertThat(jdbcTemplate.queryForMap(
				"SELECT stop_id, pickup_allowed, dropoff_allowed FROM stop_pattern_stop "
						+ "WHERE stop_pattern_id = ? AND stop_sequence = ?",
				batchPatternId, 2))
				.containsEntry("stop_id", stop.getId())
				.containsEntry("pickup_allowed", true)
				.containsEntry("dropoff_allowed", true);
		assertThat(stopPatternMapper.findStopsByStopPatternId(batchPatternId))
				.satisfiesExactly(
						firstStop -> {
							assertThat(firstStop.getStopSequence()).isEqualTo(1);
							assertThat(firstStop.getStopId()).isEqualTo(stop.getId());
							assertThat(firstStop.getStopName()).isEqualTo("테스트 정류장");
							assertThat(firstStop.getPickupAllowed()).isTrue();
							assertThat(firstStop.getDropoffAllowed()).isFalse();
						},
						secondStop -> {
							assertThat(secondStop.getStopSequence()).isEqualTo(2);
							assertThat(secondStop.getStopId()).isEqualTo(stop.getId());
							assertThat(secondStop.getStopName()).isEqualTo("테스트 정류장");
							assertThat(secondStop.getPickupAllowed()).isTrue();
							assertThat(secondStop.getDropoffAllowed()).isTrue();
						});

		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(batchPatternId, 3, destinationStop.getId(), true, true)));
		UUID otherPatternId = insertStopPattern(line.getId(), "other-delete-pattern", "LOCAL",
				LocalDate.parse("2026-09-02"), null, true);
		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(otherPatternId, 1, destinationStop.getId(), true, true)));

		assertThat(stopPatternMapper.deleteStopPatternStopsNotInSequences(batchPatternId, List.of(1, 2)))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForList(
				"SELECT stop_sequence FROM stop_pattern_stop "
						+ "WHERE stop_pattern_id = ? ORDER BY stop_sequence",
				Integer.class, batchPatternId))
				.containsExactly(1, 2);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM stop_pattern_stop WHERE stop_pattern_id = ?",
				Integer.class, otherPatternId))
				.isEqualTo(1);
		assertThat(stopPatternMapper.deleteStopPatternStopsNotInSequences(otherPatternId, List.of()))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM stop_pattern_stop WHERE stop_pattern_id = ?",
				Integer.class, otherPatternId))
				.isZero();

		var patternSyncLine = new TransitLineEntity(
				UUID.randomUUID(), providerId, "pattern-sync-route", "패턴 동기화", "operator", "CITY_BUS",
				true, sourceUpdatedAt, null, null);
		transitLineMapper.upsertTransitLine(patternSyncLine);
		LocalDate retainedValidFrom = LocalDate.parse("2026-09-01");
		insertStopPattern(patternSyncLine.getId(), "weekday", "LOCAL", retainedValidFrom, null, true);
		insertStopPattern(patternSyncLine.getId(), "weekday", "LOCAL",
				LocalDate.parse("2026-10-01"), null, true);
		insertStopPattern(patternSyncLine.getId(), "weekend", "LOCAL", retainedValidFrom, null, true);

		assertThat(stopPatternMapper.deactivateStopPatternsNotInBusinessKeys(
				patternSyncLine.getId(),
				List.of(new StopPatternEntity(
						null, patternSyncLine.getId(), "weekday", "LOCAL", retainedValidFrom,
						null, true, null, null))))
				.isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT active FROM stop_pattern "
						+ "WHERE line_id = ? AND provider_pattern_id = ? AND valid_from = ?",
				Boolean.class, patternSyncLine.getId(), "weekday", retainedValidFrom))
				.isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT active FROM stop_pattern "
						+ "WHERE line_id = ? AND provider_pattern_id = ? AND valid_from = ?",
				Boolean.class, patternSyncLine.getId(), "weekday", LocalDate.parse("2026-10-01")))
				.isFalse();
		assertThat(stopPatternMapper.deactivateStopPatternsNotInBusinessKeys(
				patternSyncLine.getId(), List.of()))
				.isEqualTo(1);

		long subwayProviderId = transitProviderMapper.findByCode("SEOUL_SUBWAY").orElseThrow().getId();
		long nationalProviderId = transitProviderMapper.findByCode("NATIONAL_PRECISION_BUS").orElseThrow().getId();
		for (String providerLineId : List.of("sync-line-1", "sync-line-2", "sync-line-3")) {
			transitLineMapper.upsertTransitLine(new TransitLineEntity(
					UUID.randomUUID(), subwayProviderId, providerLineId, providerLineId,
					"operator", "SUBWAY", true, sourceUpdatedAt, null, null));
		}
		var otherProviderLine = new TransitLineEntity(
				UUID.randomUUID(), nationalProviderId, "other-provider-line", "other-provider-line",
				"operator", "CITY_BUS", true, sourceUpdatedAt, null, null);
		transitLineMapper.upsertTransitLine(otherProviderLine);

		assertThat(transitLineMapper.deactivateTransitLinesNotInProviderLineIds(
				subwayProviderId, List.of("sync-line-1")))
				.isEqualTo(2);
		assertThat(transitLineMapper.findByProviderResourceId(subwayProviderId, "sync-line-1"))
				.get().extracting(TransitLineEntity::getActive).isEqualTo(true);
		assertThat(transitLineMapper.findByProviderResourceId(subwayProviderId, "sync-line-2"))
				.get().extracting(TransitLineEntity::getActive).isEqualTo(false);
		assertThat(transitLineMapper.findByProviderResourceId(nationalProviderId, "other-provider-line"))
				.get().extracting(TransitLineEntity::getActive).isEqualTo(true);
		assertThat(transitLineMapper.deactivateTransitLinesNotInProviderLineIds(
				subwayProviderId, List.of()))
				.isEqualTo(1);
	}

	private UUID insertStopPattern(
			UUID lineId,
			String providerPatternId,
			String serviceType,
			LocalDate validFrom,
			LocalDate validTo,
			boolean active) {
		UUID patternId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO stop_pattern (
				    id, line_id, provider_pattern_id, service_type, valid_from, valid_to, active
				) VALUES (?, ?, ?, ?, ?, ?, ?)
				""", patternId, lineId, providerPatternId, serviceType, validFrom, validTo, active);
		return patternId;
	}
}

