package com.realtimetransit.backend.provider.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.common.maintenance.TransitMaintenanceProperties;
import com.realtimetransit.backend.provider.dto.request.ArrivalPredictionObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.RawObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.VehicleRunObservationSaveRequest;
import com.realtimetransit.backend.provider.entity.ArrivalPredictionObservationEntity;
import com.realtimetransit.backend.provider.entity.RawObservationEntity;
import com.realtimetransit.backend.provider.entity.VehicleRunObservationEntity;
import com.realtimetransit.backend.provider.repository.ArrivalPredictionObservationMapper;
import com.realtimetransit.backend.provider.repository.RawObservationMapper;
import com.realtimetransit.backend.provider.repository.VehicleRunObservationMapper;
import com.realtimetransit.backend.provider.service.validation.ObservationValidator;

@ExtendWith(MockitoExtension.class)
class ObservationServiceImplTest {

	private static final Instant RECEIVED_AT = Instant.parse("2026-09-02T00:00:00Z");

	@Mock
	private RawObservationMapper rawObservationMapper;
	@Mock
	private VehicleRunObservationMapper vehicleRunObservationMapper;
	@Mock
	private ArrivalPredictionObservationMapper arrivalPredictionObservationMapper;
	@Mock
	private ObservationValidator observationValidator;

	private ObservationServiceImpl observationService;

	@BeforeEach
	void setUp() {
		TransitMaintenanceProperties maintenanceProperties = new TransitMaintenanceProperties();
		maintenanceProperties.setObservationRetention(Duration.ofHours(2));
		observationService = new ObservationServiceImpl(
				rawObservationMapper,
				vehicleRunObservationMapper,
				arrivalPredictionObservationMapper,
				Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
				observationValidator,
				maintenanceProperties);
	}

	@Test
	void savesRawObservation() {
		var request = RawObservationSaveRequest.builder()
				.providerId(1L).endpoint("vehicles").requestKey("line-1")
				.responseStatus(200).build();
		when(rawObservationMapper.insertRawObservation(any())).thenReturn(11L);

		assertThat(observationService.saveRawObservation(request)).isEqualTo(11L);
		verify(observationValidator).validateRawObservation(request, RECEIVED_AT);
		var captor = ArgumentCaptor.forClass(RawObservationEntity.class);
		verify(rawObservationMapper).insertRawObservation(captor.capture());
		assertThat(captor.getValue().getReceivedAt()).isEqualTo(RECEIVED_AT);
		assertThat(captor.getValue().getExpiresAt()).isEqualTo(RECEIVED_AT.plus(Duration.ofHours(2)));
	}

	@Test
	void limitsRawObservationRetentionToTwoHours() {
		var request = RawObservationSaveRequest.builder()
				.providerId(1L).endpoint("vehicles").requestKey("line-1")
				.responseStatus(200).expiresAt(RECEIVED_AT.plus(Duration.ofDays(1))).build();
		when(rawObservationMapper.insertRawObservation(any())).thenReturn(11L);

		observationService.saveRawObservation(request);

		var captor = ArgumentCaptor.forClass(RawObservationEntity.class);
		verify(rawObservationMapper).insertRawObservation(captor.capture());
		assertThat(captor.getValue().getExpiresAt()).isEqualTo(RECEIVED_AT.plus(Duration.ofHours(2)));
	}

	@Test
	void savesVehicleRunObservation() {
		var request = VehicleRunObservationSaveRequest.builder()
				.lineId(UUID.randomUUID()).providerVehicleId("vehicle-1")
				.serviceType("LOCAL").movementStatus("BETWEEN")
				.positionSource("GPS").observedAt(RECEIVED_AT).build();
		when(vehicleRunObservationMapper.insertVehicleRunObservation(any())).thenReturn(12L);

		assertThat(observationService.saveVehicleRunObservation(request)).isEqualTo(12L);
		verify(observationValidator).validateVehicleRunObservation(request, RECEIVED_AT);
		var captor = ArgumentCaptor.forClass(VehicleRunObservationEntity.class);
		verify(vehicleRunObservationMapper).insertVehicleRunObservation(captor.capture());
		assertThat(captor.getValue().getReceivedAt()).isEqualTo(RECEIVED_AT);
	}

	@Test
	void savesArrivalPredictionObservation() {
		var request = ArrivalPredictionObservationSaveRequest.builder()
				.boardingStopId(UUID.randomUUID()).source("PROVIDER")
				.alightingStopStatus("NOT_REQUESTED")
				.confidence("HIGH").observedAt(RECEIVED_AT).build();
		when(arrivalPredictionObservationMapper.insertArrivalPredictionObservation(any())).thenReturn(13L);

		assertThat(observationService.saveArrivalPredictionObservation(request)).isEqualTo(13L);
		verify(observationValidator).validateArrivalPredictionObservation(request, RECEIVED_AT);
		var captor = ArgumentCaptor.forClass(ArrivalPredictionObservationEntity.class);
		verify(arrivalPredictionObservationMapper).insertArrivalPredictionObservation(captor.capture());
		assertThat(captor.getValue().getReceivedAt()).isEqualTo(RECEIVED_AT);
	}
}
