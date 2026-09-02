package com.realtimetransit.backend.transit.service.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.transit.dto.request.DirectedStopSyncRequest;
import com.realtimetransit.backend.transit.dto.request.RouteDirectionSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitLineSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitStopSyncRequest;
import com.realtimetransit.backend.transit.dto.request.StopPatternSyncRequest;
import com.realtimetransit.backend.transit.dto.request.StopPatternStopSyncRequest;
import com.realtimetransit.backend.transit.dto.response.StopPatternSyncKey;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransitReferenceSyncValidator {

	private final Validator validator;

	public void validateLines(long providerId, List<TransitLineSyncRequest> lines) {
		validateProviderId(providerId);
		validateList(lines, "lines");
		Set<String> ids = new HashSet<>();
		for (TransitLineSyncRequest line : lines) {
			validateBean(line, "line");
			addUnique(ids, line.getProviderLineId(), "providerLineId");
		}
	}

	public void validateStops(long providerId, List<TransitStopSyncRequest> stops) {
		validateProviderId(providerId);
		validateList(stops, "stops");
		Set<String> ids = new HashSet<>();
		for (TransitStopSyncRequest stop : stops) {
			validateBean(stop, "stop");
			addUnique(ids, stop.getProviderStopId(), "providerStopId");
		}
		for (TransitStopSyncRequest stop : stops) {
			String parentId = stop.getParentProviderStopId();
			if (parentId == null) {
				continue;
			}
			if (parentId.equals(stop.getProviderStopId())) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "A stop cannot be its own parent: " + parentId);
			}
			if (!ids.contains(parentId)) {
				throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Parent stop: " + parentId);
			}
		}
	}

	public void validateDirections(
			UUID lineId,
			Map<String, UUID> stopIdsByProviderStopId,
			List<RouteDirectionSyncRequest> directions) {
		if (lineId == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "lineId must not be null");
		}
		if (stopIdsByProviderStopId == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "stopIdsByProviderStopId must not be null");
		}
		validateList(directions, "directions");
		Set<String> directionIds = new HashSet<>();
		for (RouteDirectionSyncRequest direction : directions) {
			validateBean(direction, "direction");
			addUnique(directionIds, direction.getProviderDirectionId(), "providerDirectionId");
			validateStopReference(stopIdsByProviderStopId, direction.getOriginProviderStopId());
			validateStopReference(stopIdsByProviderStopId, direction.getTerminalProviderStopId());
			validateStopReference(stopIdsByProviderStopId, direction.getRepresentativeNextProviderStopId());
			Set<Integer> sequences = new HashSet<>();
			for (DirectedStopSyncRequest stop : direction.getDirectedStops()) {
				validateBean(stop, "directedStop");
				if (!sequences.add(stop.getStopSequence())) {
					throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "stopSequence: " + stop.getStopSequence());
				}
				validateStopReference(stopIdsByProviderStopId, stop.getProviderStopId());
				validateStopReference(stopIdsByProviderStopId, stop.getNextProviderStopId());
			}
		}
	}

	public void validateStopPatterns(
			UUID lineId,
			Map<String, UUID> stopIdsByProviderStopId,
			List<StopPatternSyncRequest> patterns) {
		if (lineId == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "lineId must not be null");
		}
		if (stopIdsByProviderStopId == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "stopIdsByProviderStopId must not be null");
		}
		validateList(patterns, "patterns");
		Set<StopPatternSyncKey> patternKeys = new HashSet<>();
		for (StopPatternSyncRequest pattern : patterns) {
			validateBean(pattern, "pattern");
			StopPatternSyncKey key = new StopPatternSyncKey(
					pattern.getProviderPatternId(), pattern.getValidFrom());
			addUnique(patternKeys, key, "stopPattern business key");
			if (pattern.getValidTo() != null && pattern.getValidTo().isBefore(pattern.getValidFrom())) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "validTo must not be before validFrom");
			}
			Set<Integer> sequences = new HashSet<>();
			for (StopPatternStopSyncRequest stop : pattern.getPatternStops()) {
				validateBean(stop, "patternStop");
				addUnique(sequences, stop.getStopSequence(), "stopSequence");
				validateStopReference(stopIdsByProviderStopId, stop.getProviderStopId());
			}
		}
	}

	private void validateBean(Object value, String name) {
		if (value == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, name + " must not be null");
		}
		Set<ConstraintViolation<Object>> violations = validator.validate(value);
		if (!violations.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, violations.iterator().next().getMessage());
		}
	}

	private static void validateProviderId(long providerId) {
		if (providerId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "providerId must be positive");
		}
	}

	private static void validateList(List<?> values, String name) {
		if (values == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, name + " must not be null");
		}
	}

	private static <T> void addUnique(Set<T> values, T value, String name) {
		if (!values.add(value)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, name + ": " + value);
		}
	}

	private static void validateStopReference(Map<String, UUID> stopIds, String providerStopId) {
		if (providerStopId != null && !stopIds.containsKey(providerStopId)) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Stop: " + providerStopId);
		}
	}
}
