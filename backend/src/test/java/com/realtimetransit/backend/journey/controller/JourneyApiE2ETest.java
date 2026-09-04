package com.realtimetransit.backend.journey.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.realtimetransit.backend.provider.entity.ArrivalPredictionObservationEntity;
import com.realtimetransit.backend.provider.entity.VehicleRunObservationEntity;
import com.realtimetransit.backend.provider.repository.ArrivalPredictionObservationMapper;
import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.provider.repository.VehicleRunObservationMapper;
import com.realtimetransit.backend.transit.entity.DirectedStopAssignmentEntity;
import com.realtimetransit.backend.transit.entity.RouteDirectionEntity;
import com.realtimetransit.backend.transit.entity.StopPatternEntity;
import com.realtimetransit.backend.transit.entity.StopPatternStopEntity;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.repository.DirectedStopMapper;
import com.realtimetransit.backend.transit.repository.RouteDirectionMapper;
import com.realtimetransit.backend.transit.repository.StopPatternMapper;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class JourneyApiE2ETest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private TransitProviderMapper transitProviderMapper;
	@Autowired private TransitLineMapper transitLineMapper;
	@Autowired private TransitStopMapper transitStopMapper;
	@Autowired private RouteDirectionMapper routeDirectionMapper;
	@Autowired private DirectedStopMapper directedStopMapper;
	@Autowired private StopPatternMapper stopPatternMapper;
	@Autowired private VehicleRunObservationMapper vehicleRunObservationMapper;
	@Autowired private ArrivalPredictionObservationMapper arrivalPredictionObservationMapper;
	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	void createsJourneyStoresLocationCalculatesDecisionAndCancels() throws Exception {
		TransitFixture fixture = insertTransitFixture();
		Instant observedAt = Instant.now();
		insertArrival(fixture, observedAt);

		String createBody = objectMapper.writeValueAsString(Map.of(
				"anonymousKey", UUID.randomUUID(),
				"lineId", fixture.lineId,
				"directionId", fixture.directionId,
				"boardingStopId", fixture.boardingStopId,
				"alightingStopId", fixture.alightingStopId,
				"targetProbability", new BigDecimal("0.8000")));
		String createResponse = mockMvc.perform(post("/api/v1/journeys")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andReturn().getResponse().getContentAsString();
		UUID journeyId = UUID.fromString(
				objectMapper.readTree(createResponse).path("data").path("journeyId").asString());

		mockMvc.perform(post("/api/v1/journeys/{journeyId}/locations", journeyId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"latitude", new BigDecimal("37.100100"),
						"longitude", new BigDecimal("127.100100"),
						"accuracyM", new BigDecimal("8.00"),
						"speedMps", new BigDecimal("1.20"),
						"observedAt", observedAt))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.journeyId").value(journeyId.toString()))
				.andExpect(jsonPath("$.data.locationObservationId").isNumber());

		mockMvc.perform(get("/api/v1/journeys/{journeyId}/decision", journeyId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.decision").value("COMFORTABLE"))
				.andExpect(jsonPath("$.data.recommendedVehicleId").value("journey-e2e-vehicle"))
				.andExpect(jsonPath("$.data.vehicles.length()").value(1))
				.andExpect(jsonPath("$.data.vehicles[0].pacePredictions.length()").value(4));

		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM boarding_prediction_snapshot WHERE journey_id = ?",
				Integer.class, journeyId)).isEqualTo(4);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM boarding_prediction_snapshot WHERE journey_id = ? AND recommended = TRUE",
				Integer.class, journeyId)).isEqualTo(1);

		mockMvc.perform(delete("/api/v1/journeys/{journeyId}", journeyId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELLED"));
		mockMvc.perform(get("/api/v1/journeys/{journeyId}/decision", journeyId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOURNEY_NOT_ACTIVE"));
	}

	private TransitFixture insertTransitFixture() {
		long providerId = transitProviderMapper.findByCode("GBIS").orElseThrow().getId();
		Instant now = Instant.now();
		UUID lineId = UUID.randomUUID();
		UUID directionId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID alightingStopId = UUID.randomUUID();
		UUID stopPatternId = UUID.randomUUID();
		transitLineMapper.upsertTransitLine(TransitLineEntity.builder()
				.id(lineId).providerId(providerId).providerLineId("journey-e2e-" + lineId)
				.publicName("JOURNEY-E2E").operatorName("E2E").routeType("CITY_BUS")
				.active(true).sourceUpdatedAt(now).build());
		transitStopMapper.upsertTransitStop(stop(
				boardingStopId, providerId, "journey-e2e-boarding-" + boardingStopId,
				"37.100000", "127.100000"));
		transitStopMapper.upsertTransitStop(stop(
				alightingStopId, providerId, "journey-e2e-alighting-" + alightingStopId,
				"37.200000", "127.200000"));
		routeDirectionMapper.upsertRouteDirection(RouteDirectionEntity.builder()
				.id(directionId).lineId(lineId).providerDirectionId("outbound")
				.originStopId(boardingStopId).terminalStopId(alightingStopId)
				.representativeNextStopId(alightingStopId).displayName("E2E 방면").active(true).build());
		directedStopMapper.upsertDirectedStop(DirectedStopAssignmentEntity.builder()
				.id(UUID.randomUUID()).lineId(lineId).directionId(directionId)
				.stopId(boardingStopId).stopSequence(1).nextStopId(alightingStopId).build());
		directedStopMapper.upsertDirectedStop(DirectedStopAssignmentEntity.builder()
				.id(UUID.randomUUID()).lineId(lineId).directionId(directionId)
				.stopId(alightingStopId).stopSequence(2).build());
		stopPatternMapper.upsertStopPattern(StopPatternEntity.builder()
				.id(stopPatternId).lineId(lineId).providerPatternId("journey-e2e-pattern")
				.serviceType("LOCAL").validFrom(LocalDate.now()).active(true).build());
		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(stopPatternId, 1, boardingStopId, true, false),
				new StopPatternStopEntity(stopPatternId, 2, alightingStopId, false, true)));
		return new TransitFixture(lineId, directionId, boardingStopId, alightingStopId, stopPatternId);
	}

	private void insertArrival(TransitFixture fixture, Instant observedAt) {
		long vehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				VehicleRunObservationEntity.builder()
						.lineId(fixture.lineId)
						.directionId(fixture.directionId)
						.stopPatternId(fixture.stopPatternId)
						.providerVehicleId("journey-e2e-vehicle")
						.currentStopId(fixture.boardingStopId)
						.currentSequence(1)
						.serviceType("LOCAL")
						.movementStatus("APPROACHING")
						.positionSource("STOP_SEQUENCE")
						.observedAt(observedAt)
						.receivedAt(observedAt)
						.build());
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				ArrivalPredictionObservationEntity.builder()
						.vehicleRunObservationId(vehicleObservationId)
						.boardingStopId(fixture.boardingStopId)
						.requestedAlightingStopId(fixture.alightingStopId)
						.alightingStopConfirmed(true)
						.alightingStopStatus("STOPS")
						.expectedAt(observedAt.plusSeconds(300))
						.minExpectedAt(observedAt.plusSeconds(240))
						.maxExpectedAt(observedAt.plusSeconds(360))
						.remainingStops(2)
						.source("PROVIDER")
						.confidence("HIGH")
						.observedAt(observedAt)
						.receivedAt(observedAt)
						.build());
	}

	private TransitStopEntity stop(
			UUID id,
			long providerId,
			String providerStopId,
			String latitude,
			String longitude) {
		return TransitStopEntity.builder()
				.id(id).providerId(providerId).providerStopId(providerStopId)
				.publicName(providerStopId).latitude(new BigDecimal(latitude))
				.longitude(new BigDecimal(longitude)).sourceUpdatedAt(Instant.now()).build();
	}

	private static class TransitFixture {
		private final UUID lineId;
		private final UUID directionId;
		private final UUID boardingStopId;
		private final UUID alightingStopId;
		private final UUID stopPatternId;

		private TransitFixture(
				UUID lineId,
				UUID directionId,
				UUID boardingStopId,
				UUID alightingStopId,
				UUID stopPatternId) {
			this.lineId = lineId;
			this.directionId = directionId;
			this.boardingStopId = boardingStopId;
			this.alightingStopId = alightingStopId;
			this.stopPatternId = stopPatternId;
		}
	}
}
