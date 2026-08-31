package com.realtimetransit.backend.transit.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.provider.repository.TransitProviderMapper;
import com.realtimetransit.backend.transit.dto.response.TransitLineResponse;
import com.realtimetransit.backend.transit.repository.TransitLineMapper;
import com.realtimetransit.backend.transit.service.TransitLineService;

@Service
@Transactional(readOnly = true)
public class TransitLineServiceImpl implements TransitLineService {

	private static final int MAX_SEARCH_LIMIT = 100;

	private final TransitProviderMapper transitProviderMapper;
	private final TransitLineMapper transitLineMapper;

	public TransitLineServiceImpl(
			TransitProviderMapper transitProviderMapper,
			TransitLineMapper transitLineMapper) {
		this.transitProviderMapper = transitProviderMapper;
		this.transitLineMapper = transitLineMapper;
	}

	@Override
	public List<TransitLineResponse> searchActiveLines(String providerCode, String query, int limit) {
		var provider = transitProviderMapper.findByCode(providerCode)
				.orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + providerCode));
		int safeLimit = Math.clamp(limit, 1, MAX_SEARCH_LIMIT);

		return transitLineMapper.searchActiveLines(provider.id(), query.strip(), safeLimit).stream()
				.map(TransitLineResponse::from)
				.toList();
	}
}
