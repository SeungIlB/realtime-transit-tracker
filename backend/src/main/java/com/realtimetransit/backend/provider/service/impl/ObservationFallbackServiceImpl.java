package com.realtimetransit.backend.provider.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetransit.backend.provider.dto.response.RawObservationFallbackResponse;
import com.realtimetransit.backend.provider.dto.response.RawObservationStatusResponse;
import com.realtimetransit.backend.provider.repository.RawObservationMapper;
import com.realtimetransit.backend.provider.service.ObservationFallbackService;
import com.realtimetransit.backend.provider.service.validation.ObservationValidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ObservationFallbackServiceImpl implements ObservationFallbackService {

	private final RawObservationMapper rawObservationMapper;
	private final ObservationValidator observationValidator;
	private final Clock clock;

	@Override
	public Optional<RawObservationFallbackResponse> findLatestSuccessfulFallback(
			long providerId,
			String endpoint,
			String requestKey) {
        observationValidator.validateRequestIdentity(providerId, endpoint, requestKey);

        Instant asOf = Instant.now(clock);
		return rawObservationMapper
				.findLatestSuccessfulUnexpiredByRequestIdentity(
						providerId,
						endpoint,
						requestKey,
						asOf)
				.map(RawObservationFallbackResponse::from);
	}

	@Override
	public Optional<RawObservationStatusResponse> findLatestObservationStatus(
			long providerId,
			String endpoint,
			String requestKey) {
		observationValidator.validateRequestIdentity(providerId, endpoint, requestKey);

        return rawObservationMapper.findLatestByProviderIdAndEndpointAndRequestKey(providerId, endpoint, requestKey)
                .map(RawObservationStatusResponse::from);
	}
}
