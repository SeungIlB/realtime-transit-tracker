package com.realtimetransit.backend.provider.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.provider.dto.response.ProviderCollectionStatusResponse;
import com.realtimetransit.backend.provider.repository.RawObservationMapper;
import com.realtimetransit.backend.provider.service.CollectionStatusService;
import com.realtimetransit.backend.provider.service.validation.ObservationValidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CollectionStatusServiceImpl implements CollectionStatusService {

	private final RawObservationMapper rawObservationMapper;
	private final ObservationValidator observationValidator;
	private final Clock clock;

	@Override
	public List<ProviderCollectionStatusResponse> findCollectionStatuses(Duration lookback) {
		observationValidator.validateRetention(lookback);
        Instant receivedAfter = Instant.now(clock).minus(lookback);

        return rawObservationMapper.findCollectionStatusesReceivedAfter(receivedAfter).stream()
                .map(ProviderCollectionStatusResponse::from)
                .toList();
	}
}
