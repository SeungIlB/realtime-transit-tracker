package com.realtimetransit.backend.provider.service.validation;

import java.time.Instant;
import java.util.UUID;
import java.time.Duration;

import com.realtimetransit.backend.provider.dto.request.ArrivalPredictionObservationSaveRequest;
import org.springframework.stereotype.Component;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.provider.dto.request.RawObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.VehicleRunObservationSaveRequest;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ObservationValidator {

	private final Validator validator;

	public void validateRawObservation(
			RawObservationSaveRequest request,
			Instant receivedAt) {
		validateBean(request, "request");
		validateReceivedAt(receivedAt);

		if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(receivedAt)) {
			throw new BusinessException(
					ErrorCode.INVALID_REQUEST,
					"expiresAt must not be before receivedAt");
		}
	}

	public void validateVehicleRunObservation(
			VehicleRunObservationSaveRequest request,
			Instant receivedAt) {
        validateBean(request, "request");
        validateReceivedAt(receivedAt);
	}

	public void validateRequestIdentity(
			long providerId,
			String endpoint,
			String requestKey) {
		if (providerId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "providerId must be positive");
		}
		if (endpoint == null || endpoint.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "endpoint must not be blank");
		}
		if (requestKey == null || requestKey.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "requestKey must not be blank");
		}
	}

	public void validateCleanupLimit(int limit) {
		if (limit <= 0) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "limit must be positive");
		}
	}

	public void validateVehicleHistoryQuery(
			UUID lineId,
			String providerVehicleId,
			Duration lookback,
			int limit) {
		if (lineId == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "lineId must not be null");
		}
		if (providerVehicleId == null || providerVehicleId.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "providerVehicleId must not be blank");
		}
		validateRetention(lookback);
		validateCleanupLimit(limit);
	}

	public void validateRetention(Duration retention) {
		if (retention == null || retention.isZero() || retention.isNegative()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "retention must be positive");
		}
	}

	private void validateBean(Object value, String name) {
		if (value == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, name + " must not be null");
		}
		var violations = validator.validate(value);
		if (!violations.isEmpty()) {
			throw new BusinessException(
					ErrorCode.INVALID_REQUEST,
					violations.iterator().next().getMessage());
		}
	}

	private void validateReceivedAt(Instant receivedAt) {
		if (receivedAt == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "receivedAt must not be null");
		}
	}

    public void validateArrivalPredictionObservation(
            ArrivalPredictionObservationSaveRequest request,
            Instant receivedAt) {
        validateBean(request, "request");
        validateReceivedAt(receivedAt);
        validateExpectedAtRange(request);
    }

	private void validateExpectedAtRange(
			ArrivalPredictionObservationSaveRequest request) {
		boolean minimumAfterExpected = request.getMinExpectedAt() != null
				&& request.getExpectedAt() != null
				&& request.getMinExpectedAt().isAfter(request.getExpectedAt());
		boolean expectedAfterMaximum = request.getExpectedAt() != null
				&& request.getMaxExpectedAt() != null
				&& request.getExpectedAt().isAfter(request.getMaxExpectedAt());
		boolean minimumAfterMaximum = request.getMinExpectedAt() != null
				&& request.getMaxExpectedAt() != null
				&& request.getMinExpectedAt().isAfter(request.getMaxExpectedAt());

		if (minimumAfterExpected || expectedAfterMaximum || minimumAfterMaximum) {
			throw new BusinessException(
					ErrorCode.INVALID_REQUEST,
					"Expected arrival time range is invalid");
		}
	}
}
