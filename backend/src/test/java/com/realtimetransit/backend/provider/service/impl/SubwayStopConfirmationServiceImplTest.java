package com.realtimetransit.backend.provider.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.kric.client.KricRailwayTimetableClient;
import com.realtimetransit.backend.provider.kric.dto.KricStation;
import com.realtimetransit.backend.provider.kric.dto.KricTimetableCall;
import com.realtimetransit.backend.transit.entity.AlightingStopStatus;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;

@ExtendWith(MockitoExtension.class)
class SubwayStopConfirmationServiceImplTest {

	private static final Instant EXPECTED_AT = Instant.parse("2026-09-03T07:20:00Z");
	private static final KricStation BOARDING_STATION = station("KR", "1", "149", "송내");
	private static final KricStation ALIGHTING_STATION = station("KR", "1", "135", "용산");

	@Mock
	private KricRailwayTimetableClient timetableClient;
	@Mock
	private TransitStopMapper transitStopMapper;

	private SubwayStopConfirmationServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new SubwayStopConfirmationServiceImpl(timetableClient, transitStopMapper);
	}

	@Test
	void confirmsExpressTrainWhenSameRunStopsAtDestinationAfterBoarding() {
		stubStationsAndConfiguration();
		when(timetableClient.findTimetable(BOARDING_STATION, 8))
				.thenReturn(List.of(call("1096", "16:20:00")));
		when(timetableClient.findTimetable(ALIGHTING_STATION, 8))
				.thenReturn(List.of(call("1096", "16:48:00")));

		assertThat(service.confirmAlightingStop(line(), boardingStop(), alightingStop(), arrival("EXPRESS")))
				.isEqualTo(AlightingStopStatus.STOPS);
	}

	@Test
	void rejectsExpressTrainOnlyWhenDestinationTimetableIsCompleteAndRunIsAbsent() {
		stubStationsAndConfiguration();
		when(timetableClient.findTimetable(BOARDING_STATION, 8))
				.thenReturn(List.of(call("1096", "16:20:00")));
		when(timetableClient.findTimetable(ALIGHTING_STATION, 8))
				.thenReturn(List.of(call("1100", "16:45:00")));

		assertThat(service.confirmAlightingStop(line(), boardingStop(), alightingStop(), arrival("EXPRESS")))
				.isEqualTo(AlightingStopStatus.SKIPS);
	}

	@Test
	void leavesLimitedStopTrainUnknownWhenTimetableIsNotConfigured() {
		when(timetableClient.isConfigured()).thenReturn(false);

		assertThat(service.confirmAlightingStop(line(), boardingStop(), alightingStop(), arrival("EXPRESS")))
				.isEqualTo(AlightingStopStatus.UNKNOWN);
	}

	@Test
	void leavesLocalTrainUnknownWhenTerminalAndTimetableAreUnavailable() {
		when(timetableClient.isConfigured()).thenReturn(false);

		assertThat(service.confirmAlightingStop(line(), boardingStop(), alightingStop(), arrival("LOCAL")))
				.isEqualTo(AlightingStopStatus.UNKNOWN);
	}

	@Test
	void rejectsLocalTrainWhoseTerminalIsBeforeRequestedAlightingStop() {
		ExternalArrival arrival = arrival("LOCAL");
		arrival.setProviderDirectionId("UP");
		arrival.setDestinationProviderStopId("terminal");
		when(transitStopMapper.canReachAlightingBeforeTerminal(
				line().getId(), "UP", boardingStop().getId(), alightingStop().getId(), "terminal"))
				.thenReturn(java.util.Optional.of(false));

		assertThat(service.confirmAlightingStop(line(), boardingStop(), alightingStop(), arrival))
				.isEqualTo(AlightingStopStatus.SKIPS);
	}

	@Test
	void confirmsLocalTrainWhenRequestedStopIsBeforeItsTerminal() {
		ExternalArrival arrival = arrival("LOCAL");
		arrival.setProviderDirectionId("UP");
		arrival.setDestinationProviderStopId("terminal");
		when(transitStopMapper.canReachAlightingBeforeTerminal(
				line().getId(), "UP", boardingStop().getId(), alightingStop().getId(), "terminal"))
				.thenReturn(java.util.Optional.of(true));

		assertThat(service.confirmAlightingStop(line(), boardingStop(), alightingStop(), arrival))
				.isEqualTo(AlightingStopStatus.STOPS);
	}

	@Test
	void parsesRailwayTimesBeyondMidnightAgainstNearestServiceDate() {
		KricTimetableCall call = call("1096", "24:15:00");
		Instant reference = Instant.parse("2026-09-03T15:10:00Z");

		assertThat(SubwayStopConfirmationServiceImpl.scheduledInstant(call, reference))
				.isEqualTo(Instant.parse("2026-09-03T15:15:00Z"));
	}

	private void stubStationsAndConfiguration() {
		when(timetableClient.isConfigured()).thenReturn(true);
		when(timetableClient.findStations("송내")).thenReturn(List.of(BOARDING_STATION));
		when(timetableClient.findStations("용산")).thenReturn(List.of(ALIGHTING_STATION));
	}

	private static KricStation station(String operatorCode, String lineCode, String stationCode, String stationName) {
		return KricStation.builder().operatorCode(operatorCode).lineCode(lineCode)
				.stationCode(stationCode).stationName(stationName).build();
	}

	private static KricTimetableCall call(String trainNumber, String arrivalTime) {
		return KricTimetableCall.builder().operatorCode("KR").lineCode("1").stationCode("station")
				.trainNumber(trainNumber).arrivalTime(arrivalTime).dayCode(8).build();
	}

	private static TransitLineEntity line() {
		return TransitLineEntity.builder().id(java.util.UUID.fromString("10000000-0000-0000-0000-000000000001"))
				.providerLineId("01호선").publicName("1호선").build();
	}

	private static TransitStopEntity boardingStop() {
		return TransitStopEntity.builder().id(java.util.UUID.fromString("20000000-0000-0000-0000-000000000001"))
				.publicName("송내").build();
	}

	private static TransitStopEntity alightingStop() {
		return TransitStopEntity.builder().id(java.util.UUID.fromString("30000000-0000-0000-0000-000000000001"))
				.publicName("용산").build();
	}

	private static ExternalArrival arrival(String serviceType) {
		return ExternalArrival.builder().providerVehicleId("1096").serviceType(serviceType)
				.expectedAt(EXPECTED_AT).build();
	}
}
