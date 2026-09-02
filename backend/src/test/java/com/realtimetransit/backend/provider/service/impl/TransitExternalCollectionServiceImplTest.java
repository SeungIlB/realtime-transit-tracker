package com.realtimetransit.backend.provider.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.TransitProviderClient;
import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.client.dto.ExternalDirection;
import com.realtimetransit.backend.provider.client.dto.ExternalRouteReference;
import com.realtimetransit.backend.provider.client.dto.ExternalStop;
import com.realtimetransit.backend.provider.dto.request.ArrivalPredictionObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.VehicleRunObservationSaveRequest;
import com.realtimetransit.backend.provider.entity.TransitProviderEntity;
import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.provider.service.ObservationService;
import com.realtimetransit.backend.provider.service.TransitProviderService;
import com.realtimetransit.backend.transit.dto.request.StopPatternSyncRequest;
import com.realtimetransit.backend.transit.entity.StopPatternEntity;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.repository.RouteDirectionMapper;
import com.realtimetransit.backend.transit.repository.StopPatternMapper;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;
import com.realtimetransit.backend.transit.service.TransitReferenceSyncService;

@ExtendWith(MockitoExtension.class)
class TransitExternalCollectionServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-09-02T06:00:00Z");
	private static final UUID LINE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID STOP_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID PATTERN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

	@Mock
	private TransitProviderService transitProviderService;
	@Mock
	private TransitProviderMapper transitProviderMapper;
	@Mock
	private TransitLineMapper transitLineMapper;
	@Mock
	private TransitStopMapper transitStopMapper;
	@Mock
	private RouteDirectionMapper routeDirectionMapper;
	@Mock
	private StopPatternMapper stopPatternMapper;
	@Mock
	private TransitReferenceSyncService referenceSyncService;
	@Mock
	private ObservationService observationService;
	@Mock
	private TransitProviderClient client;

	private TransitExternalCollectionServiceImpl collectionService;

	@BeforeEach
	void setUp() {
		collectionService = new TransitExternalCollectionServiceImpl(
				transitProviderService,
				transitProviderMapper,
				transitLineMapper,
				transitStopMapper,
				routeDirectionMapper,
				stopPatternMapper,
				referenceSyncService,
				observationService,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void synchronizesExternalRouteWithDatabaseCompatibleServiceType() {
		stubLineAndProvider();
		ExternalStop stop = ExternalStop.builder()
				.providerStopId("provider-stop")
				.publicName("정류장")
				.build();
		when(client.fetchRoute("provider-line")).thenReturn(ExternalRouteReference.builder()
				.providerLineId("provider-line")
				.directions(List.of(ExternalDirection.builder()
						.providerDirectionId("UP")
						.displayName("상행")
						.stops(List.of(stop))
						.build()))
				.build());
		when(referenceSyncService.synchronizeTransitStops(anyLong(), anyList()))
				.thenReturn(Map.of("provider-stop", STOP_ID));

		collectionService.synchronizeRoute(LINE_ID);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<StopPatternSyncRequest>> patterns = ArgumentCaptor.forClass(List.class);
		verify(referenceSyncService).synchronizeStopPatterns(any(), anyMap(), patterns.capture());
		assertThat(patterns.getValue())
				.singleElement()
				.extracting(StopPatternSyncRequest::getServiceType)
				.isEqualTo("UNKNOWN");
	}

	@Test
	void storesExternalArrivalUsingAllowedObservationCodes() {
		stubLineAndProvider();
		when(transitStopMapper.findById(STOP_ID)).thenReturn(Optional.of(TransitStopEntity.builder()
				.id(STOP_ID)
				.providerId(1L)
				.providerStopId("provider-stop")
				.build()));
		when(stopPatternMapper.findActiveStopPatternsByLineIdAndServiceDate(LINE_ID, LocalDate.of(2026, 9, 2)))
				.thenReturn(List.of(StopPatternEntity.builder()
						.id(PATTERN_ID)
						.providerPatternId("UP")
						.build()));
		when(client.fetchArrivals("provider-line", "provider-stop")).thenReturn(List.of(ExternalArrival.builder()
				.providerVehicleId("vehicle-1")
				.providerDirectionId("UP")
				.expectedAt(NOW.plusSeconds(300))
				.movementStatus("BETWEEN")
				.positionSource("ESTIMATED")
				.observedAt(NOW)
				.build()));
		when(observationService.saveVehicleRunObservation(any())).thenReturn(10L);

		collectionService.collectArrivals(LINE_ID, STOP_ID);

		ArgumentCaptor<VehicleRunObservationSaveRequest> vehicle =
				ArgumentCaptor.forClass(VehicleRunObservationSaveRequest.class);
		verify(observationService).saveVehicleRunObservation(vehicle.capture());
		assertThat(vehicle.getValue().getServiceType()).isEqualTo("UNKNOWN");
		assertThat(vehicle.getValue().getMovementStatus()).isEqualTo("BETWEEN");
		assertThat(vehicle.getValue().getPositionSource()).isEqualTo("ESTIMATED");

		ArgumentCaptor<ArrivalPredictionObservationSaveRequest> arrival =
				ArgumentCaptor.forClass(ArrivalPredictionObservationSaveRequest.class);
		verify(observationService).saveArrivalPredictionObservation(arrival.capture());
		assertThat(arrival.getValue().getSource()).isEqualTo("PROVIDER");
		assertThat(arrival.getValue().getConfidence()).isEqualTo("HIGH");
	}

	private void stubLineAndProvider() {
		when(transitLineMapper.findById(LINE_ID)).thenReturn(Optional.of(TransitLineEntity.builder()
				.id(LINE_ID)
				.providerId(1L)
				.providerLineId("provider-line")
				.build()));
		when(transitProviderMapper.findById(1L)).thenReturn(Optional.of(TransitProviderEntity.builder()
				.id(1L)
				.code(ExternalApiProvider.SEOUL_SUBWAY.name())
				.build()));
		when(transitProviderService.getClient(ExternalApiProvider.SEOUL_SUBWAY)).thenReturn(client);
	}
}
