package com.realtimetransit.backend.transit.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.provider.service.TransitExternalCollectionService;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.transit.dto.response.TransitLineResponse;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.service.TransitLineService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransitLineServiceImpl implements TransitLineService {

	private static final int MAX_SEARCH_LIMIT = 100;

	private final TransitProviderMapper transitProviderMapper;
	private final TransitLineMapper transitLineMapper;
	private final TransitExternalCollectionService externalCollectionService;

	@Override
	public List<TransitLineResponse> searchActiveLines(
			String providerCode,
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude) {
		validateSearchInput(providerCode, query);
		String normalizedQuery = query.strip();
		var provider = transitProviderMapper.findByCode(providerCode)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Provider: " + providerCode));
		int safeLimit = Math.clamp(limit, 1, MAX_SEARCH_LIMIT);
		if ("NATIONAL_PRECISION_BUS".equals(providerCode)) {
			validateLocation(latitude, longitude);
			List<String> providerLineIds = externalCollectionService.searchAndSynchronizeLines(
					providerCode, normalizedQuery, safeLimit, latitude, longitude);
			if (providerLineIds.isEmpty()) return List.of();
			return transitLineMapper.findActiveLinesByProviderLineIds(provider.getId(), providerLineIds).stream()
					.map(TransitLineResponse::from)
					.toList();
		}
		var lines = transitLineMapper.searchActiveLines(provider.getId(), normalizedQuery, safeLimit);
		if (lines.isEmpty()) {
			externalCollectionService.searchAndSynchronizeLines(
					providerCode, normalizedQuery, safeLimit, null, null);
			lines = transitLineMapper.searchActiveLines(provider.getId(), normalizedQuery, safeLimit);
		}
		return lines.stream()
				.map(TransitLineResponse::from)
				.toList();
	}

	private static void validateLocation(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null
				|| latitude.compareTo(BigDecimal.valueOf(-90)) < 0
				|| latitude.compareTo(BigDecimal.valueOf(90)) > 0
				|| longitude.compareTo(BigDecimal.valueOf(-180)) < 0
				|| longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
			throw new BusinessException(
					ErrorCode.INVALID_REQUEST,
					"latitude and longitude are required for nationwide bus search");
		}
	}

	private static void validateSearchInput(String providerCode, String query) {
		if (providerCode == null || providerCode.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "provider is required");
		}
		if (query == null || query.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "query is required");
		}
	}
}

