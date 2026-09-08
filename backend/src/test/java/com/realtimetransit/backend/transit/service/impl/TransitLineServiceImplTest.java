package com.realtimetransit.backend.transit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.provider.entity.TransitProviderEntity;
import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;

@ExtendWith(MockitoExtension.class)
class TransitLineServiceImplTest {

	@Mock
	private TransitProviderMapper transitProviderMapper;

	@Mock
	private TransitLineMapper transitLineMapper;

	@Mock
	private TransitExternalCollectionService externalCollectionService;

	@InjectMocks
	private TransitLineServiceImpl transitLineService;

	@Test
	void normalizesQueryAndReturnsExistingLinesWithoutExternalCollection() {
		TransitProviderEntity provider = provider();
		TransitLineEntity line = line(provider.getId(), "6601");
		when(transitProviderMapper.findByCode("GBIS")).thenReturn(Optional.of(provider));
		when(transitLineMapper.searchActiveLines(provider.getId(), "6601", 20)).thenReturn(List.of(line));

		assertThat(transitLineService.searchActiveLines("GBIS", " 6601 ", 20, null, null))
				.singleElement()
				.satisfies(response -> assertThat(response.getPublicName()).isEqualTo("6601"));
		verify(externalCollectionService, never()).searchAndSynchronizeLines("GBIS", "6601", 20, null, null);
	}

	@Test
	void synchronizesFromProviderWhenNoStoredLineMatches() {
		TransitProviderEntity provider = provider();
		TransitLineEntity synchronizedLine = line(provider.getId(), "6601");
		when(transitProviderMapper.findByCode("GBIS")).thenReturn(Optional.of(provider));
		when(transitLineMapper.searchActiveLines(provider.getId(), "6601", 100))
				.thenReturn(List.of(), List.of(synchronizedLine));

		assertThat(transitLineService.searchActiveLines("GBIS", "6601", 500, null, null)).hasSize(1);
		verify(externalCollectionService).searchAndSynchronizeLines("GBIS", "6601", 100, null, null);
	}

	@Test
	void synchronizesAndReturnsOnlyLocationScopedNationwideBusLines() {
		TransitProviderEntity provider = provider("NATIONAL_PRECISION_BUS");
		TransitLineEntity line = line(provider.getId(), "101");
		BigDecimal latitude = new BigDecimal("36.341858");
		BigDecimal longitude = new BigDecimal("126.586988");
		when(transitProviderMapper.findByCode("NATIONAL_PRECISION_BUS")).thenReturn(Optional.of(provider));
		when(externalCollectionService.searchAndSynchronizeLines(
				"NATIONAL_PRECISION_BUS", "101", 20, latitude, longitude))
				.thenReturn(List.of("TAGO:34030:CNB287000002"));
		when(transitLineMapper.findActiveLinesByProviderLineIds(
				provider.getId(), List.of("TAGO:34030:CNB287000002")))
				.thenReturn(List.of(line));

		assertThat(transitLineService.searchActiveLines(
				"NATIONAL_PRECISION_BUS", "101", 20, latitude, longitude))
				.singleElement()
				.satisfies(response -> assertThat(response.getPublicName()).isEqualTo("101"));
		verify(transitLineMapper, never()).searchActiveLines(provider.getId(), "101", 20);
	}

	@Test
	void requiresLocationForNationwideBusSearch() {
		TransitProviderEntity provider = provider("NATIONAL_PRECISION_BUS");
		when(transitProviderMapper.findByCode("NATIONAL_PRECISION_BUS")).thenReturn(Optional.of(provider));

		assertInvalidRequest(() -> transitLineService.searchActiveLines(
				"NATIONAL_PRECISION_BUS", "101", 20, null, null));
	}

	@Test
	void rejectsMissingProviderOrQueryBeforeMapperCalls() {
		assertInvalidRequest(() -> transitLineService.searchActiveLines(null, "6601", 20, null, null));
		assertInvalidRequest(() -> transitLineService.searchActiveLines(" ", "6601", 20, null, null));
		assertInvalidRequest(() -> transitLineService.searchActiveLines("GBIS", null, 20, null, null));
		assertInvalidRequest(() -> transitLineService.searchActiveLines("GBIS", " ", 20, null, null));
		verifyNoInteractions(transitProviderMapper, transitLineMapper, externalCollectionService);
	}

	private void assertInvalidRequest(Runnable invocation) {
		assertThatThrownBy(invocation::run)
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
	}

	private TransitProviderEntity provider() {
		return provider("GBIS");
	}

	private TransitProviderEntity provider(String code) {
		return TransitProviderEntity.builder()
				.id(1L)
				.code(code)
				.displayName("경기버스정보")
				.transportType("BUS")
				.active(true)
				.build();
	}

	private TransitLineEntity line(long providerId, String publicName) {
		return TransitLineEntity.builder()
				.id(UUID.randomUUID())
				.providerId(providerId)
				.providerLineId("232000137")
				.publicName(publicName)
				.operatorName("김포,서울")
				.routeType("직행좌석형시내버스")
				.active(true)
				.build();
	}
}
