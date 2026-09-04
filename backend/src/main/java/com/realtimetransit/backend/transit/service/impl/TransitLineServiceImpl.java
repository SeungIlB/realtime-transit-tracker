package com.realtimetransit.backend.transit.service.impl;

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
	public List<TransitLineResponse> searchActiveLines(String providerCode, String query, int limit) {
		validateSearchInput(providerCode, query);
		String normalizedQuery = query.strip();
		var provider = transitProviderMapper.findByCode(providerCode)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Provider: " + providerCode));
		int safeLimit = Math.clamp(limit, 1, MAX_SEARCH_LIMIT);
		var lines = transitLineMapper.searchActiveLines(provider.getId(), normalizedQuery, safeLimit);
		if (lines.isEmpty()) {
			externalCollectionService.searchAndSynchronizeLines(providerCode, normalizedQuery, safeLimit);
			lines = transitLineMapper.searchActiveLines(provider.getId(), normalizedQuery, safeLimit);
		}
		return lines.stream()
				.map(TransitLineResponse::from)
				.toList();
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

