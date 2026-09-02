package com.realtimetransit.backend.provider.service;

import java.util.Optional;

import com.realtimetransit.backend.provider.dto.response.RawObservationFallbackResponse;
import com.realtimetransit.backend.provider.dto.response.RawObservationStatusResponse;

public interface ObservationFallbackService {

	Optional<RawObservationFallbackResponse> findLatestSuccessfulFallback(
			long providerId,
			String endpoint,
			String requestKey);

	Optional<RawObservationStatusResponse> findLatestObservationStatus(
			long providerId,
			String endpoint,
			String requestKey);
}
