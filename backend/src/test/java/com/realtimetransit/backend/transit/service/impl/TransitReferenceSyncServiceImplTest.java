package com.realtimetransit.backend.transit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.transit.dto.request.TransitLineSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitStopSyncRequest;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;
import com.realtimetransit.backend.transit.service.validation.TransitReferenceSyncValidator;

@ExtendWith(MockitoExtension.class)
class TransitReferenceSyncServiceImplTest {

	@Mock
	private TransitLineMapper transitLineMapper;

	@Mock
	private TransitStopMapper transitStopMapper;

	@Mock
	private TransitReferenceSyncValidator referenceSyncValidator;

	@InjectMocks
	private TransitReferenceSyncServiceImpl syncService;

	@Test
	void synchronizesLinesAndReturnsActualDatabaseIds() {
		long providerId = 1L;
		UUID actualLineId = UUID.randomUUID();
		Instant sourceUpdatedAt = Instant.parse("2026-08-31T00:00:00Z");
		var request = new TransitLineSyncRequest(
				"route-1000", "1000", "operator", "CITY_BUS", true, sourceUpdatedAt);
		when(transitLineMapper.findByProviderResourceId(providerId, "route-1000"))
				.thenReturn(Optional.of(new TransitLineEntity(
						actualLineId, providerId, "route-1000", "1000", "operator", "CITY_BUS",
						true, sourceUpdatedAt, null, null)));

		assertThat(syncService.synchronizeTransitLines(providerId, List.of(request)))
				.containsExactlyEntriesOf(java.util.Map.of("route-1000", actualLineId));

		var entityCaptor = ArgumentCaptor.forClass(TransitLineEntity.class);
		var ordered = inOrder(transitLineMapper);
		ordered.verify(transitLineMapper).upsertTransitLine(entityCaptor.capture());
		ordered.verify(transitLineMapper).findByProviderResourceId(providerId, "route-1000");
		ordered.verify(transitLineMapper)
				.deactivateTransitLinesNotInProviderLineIds(providerId, List.of("route-1000"));
		assertThat(entityCaptor.getValue()).satisfies(entity -> {
			assertThat(entity.getId()).isNotNull();
			assertThat(entity.getProviderId()).isEqualTo(providerId);
			assertThat(entity.getProviderLineId()).isEqualTo("route-1000");
			assertThat(entity.getCreatedAt()).isNull();
			assertThat(entity.getUpdatedAt()).isNull();
		});
	}

	@Test
	void deactivatesAllProviderLinesWhenInputIsEmpty() {
		assertThat(syncService.synchronizeTransitLines(1L, List.of())).isEmpty();
		verify(transitLineMapper).deactivateTransitLinesNotInProviderLineIds(1L, List.of());
	}

	@Test
	void rejectsDuplicateProviderLineIdsBeforeCallingMapper() {
		var first = new TransitLineSyncRequest("duplicate", "1000", null, null, true, null);
		var second = new TransitLineSyncRequest("duplicate", "1000-1", null, null, true, null);
		doThrow(new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "providerLineId: duplicate"))
				.when(referenceSyncValidator).validateLines(1L, List.of(first, second));

		assertThatThrownBy(
				() -> syncService.synchronizeTransitLines(1L, List.of(first, second)))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
		verifyNoInteractions(transitLineMapper, transitStopMapper);
	}

	@Test
	void failsWhenUpsertedLineCannotBeReadBack() {
		var request = new TransitLineSyncRequest("missing", "1000", null, null, true, null);
		when(transitLineMapper.findByProviderResourceId(1L, "missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> syncService.synchronizeTransitLines(1L, List.of(request)))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.SYNC_RESULT_NOT_FOUND));
		verify(transitLineMapper).upsertTransitLine(any(TransitLineEntity.class));
	}

	@Test
	void synchronizesStopsAndConnectsParentAfterIdsAreResolved() {
		long providerId = 1L;
		UUID parentId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();
		var parent = new TransitStopSyncRequest("station", null, "Station", null, null, null);
		var child = new TransitStopSyncRequest("platform", "station", "Platform", null, null, null);
		when(transitStopMapper.findByProviderResourceId(providerId, "station"))
				.thenReturn(Optional.of(TransitStopEntity.builder().id(parentId).build()));
		when(transitStopMapper.findByProviderResourceId(providerId, "platform"))
				.thenReturn(Optional.of(TransitStopEntity.builder().id(childId).build()));

		assertThat(syncService.synchronizeTransitStops(providerId, List.of(parent, child)))
				.containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
						"station", parentId,
						"platform", childId));

		var captor = ArgumentCaptor.forClass(TransitStopEntity.class);
		verify(transitStopMapper, org.mockito.Mockito.times(3)).upsertTransitStop(captor.capture());
		assertThat(captor.getAllValues().get(2)).satisfies(entity -> {
			assertThat(entity.getId()).isEqualTo(childId);
			assertThat(entity.getParentStationId()).isEqualTo(parentId);
		});
	}
}

