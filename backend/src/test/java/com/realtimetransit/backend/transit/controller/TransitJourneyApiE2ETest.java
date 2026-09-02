package com.realtimetransit.backend.transit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransitJourneyApiE2ETest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	@Autowired
	private MockMvc mockMvc;

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
	private VehicleRunObservationMapper vehicleRunObservationMapper;

	@Autowired
	private ArrivalPredictionObservationMapper arrivalPredictionObservationMapper;

	@Test
	void searchesLineSelectsStopsAndFindsUpcomingArrival() throws Exception {
		long providerId = transitProviderMapper.findByCode("GBIS").orElseThrow().getId();
		UUID lineId = UUID.randomUUID();
		UUID boardingStopId = UUID.randomUUID();
		UUID alightingStopId = UUID.randomUUID();
		UUID directionId = UUID.randomUUID();
		UUID stopPatternId = UUID.randomUUID();
		Instant now = Instant.now();

		transitLineMapper.upsertTransitLine(new TransitLineEntity(
				lineId, providerId, "e2e-line", "E2E-1000", "E2E 운수", "CITY_BUS",
				true, now, null, null));
		transitStopMapper.upsertTransitStop(new TransitStopEntity(
				boardingStopId, providerId, "e2e-stop-1", null, "E2E 승차 정류장",
				new BigDecimal("37.100000"), new BigDecimal("127.100000"), now, null, null));
		transitStopMapper.upsertTransitStop(new TransitStopEntity(
				alightingStopId, providerId, "e2e-stop-2", null, "E2E 하차 정류장",
				new BigDecimal("37.200000"), new BigDecimal("127.200000"), now, null, null));
		routeDirectionMapper.upsertRouteDirection(new RouteDirectionEntity(
				directionId, lineId, "outbound", boardingStopId, alightingStopId, alightingStopId,
				"E2E 하차 정류장 방면", true, null, null));
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), lineId, directionId, boardingStopId, 1, alightingStopId,
				"platform-1", "E2E 하차 정류장 방면", "segment-1"));
		directedStopMapper.upsertDirectedStop(new DirectedStopAssignmentEntity(
				UUID.randomUUID(), lineId, directionId, alightingStopId, 2, null,
				"platform-2", "E2E 하차 정류장 방면", "segment-2"));
		stopPatternMapper.upsertStopPattern(new StopPatternEntity(
				stopPatternId, lineId, "e2e-pattern", "LOCAL", LocalDate.now(), null,
				true, null, null));
		stopPatternMapper.upsertStopPatternStops(List.of(
				new StopPatternStopEntity(stopPatternId, 1, boardingStopId, true, false),
				new StopPatternStopEntity(stopPatternId, 2, alightingStopId, false, true)));

		long vehicleObservationId = vehicleRunObservationMapper.insertVehicleRunObservation(
				new VehicleRunObservationEntity(
						null, null, lineId, directionId, stopPatternId, "e2e-vehicle", "e2e-run",
						alightingStopId, boardingStopId, 1, "LOCAL", "APPROACHING",
						null, null, null, null, "STOP_SEQUENCE", now.minusSeconds(10), now.minusSeconds(5)));
		arrivalPredictionObservationMapper.insertArrivalPredictionObservation(
				new ArrivalPredictionObservationEntity(
						null, null, vehicleObservationId, boardingStopId, now.plusSeconds(300),
						now.plusSeconds(240), now.plusSeconds(360), 2, "PROVIDER", "HIGH",
						now.minusSeconds(10), now.minusSeconds(5)));

		mockMvc.perform(get("/api/v1/lines")
				.param("provider", "GBIS")
				.param("query", "E2E-1000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data[0].id").value(lineId.toString()))
				.andExpect(jsonPath("$.data[0].publicName").value("E2E-1000"));

		mockMvc.perform(get("/api/v1/lines/{lineId}/stops", lineId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].stopId").value(boardingStopId.toString()))
				.andExpect(jsonPath("$.data[1].stopId").value(alightingStopId.toString()));

		mockMvc.perform(get("/api/v1/lines/{lineId}/stops/{boardingStopId}/destinations",
				lineId, boardingStopId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].stopId").value(alightingStopId.toString()));

		mockMvc.perform(get("/api/v1/lines/{lineId}/arrivals", lineId)
				.param("boardingStopId", boardingStopId.toString())
				.param("alightingStopId", alightingStopId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].providerVehicleId").value("e2e-vehicle"))
				.andExpect(jsonPath("$.data[0].boardingStopId").value(boardingStopId.toString()));
	}
}
