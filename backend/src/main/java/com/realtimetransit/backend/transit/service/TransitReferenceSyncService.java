package com.realtimetransit.backend.transit.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.realtimetransit.backend.transit.dto.request.TransitLineSyncRequest;
import com.realtimetransit.backend.transit.dto.request.TransitStopSyncRequest;
import com.realtimetransit.backend.transit.dto.request.RouteDirectionSyncRequest;
import com.realtimetransit.backend.transit.dto.request.StopPatternSyncRequest;
import com.realtimetransit.backend.transit.dto.response.StopPatternSyncKey;

public interface TransitReferenceSyncService {

	Map<String, UUID> synchronizeTransitLines(
			long providerId,
			List<TransitLineSyncRequest> lines);

	Map<String, UUID> upsertTransitLines(
			long providerId,
			List<TransitLineSyncRequest> lines);

	Map<String, UUID> synchronizeTransitStops(
			long providerId,
			List<TransitStopSyncRequest> stops);

	Map<String, UUID> synchronizeRouteDirections(
			UUID lineId,
			Map<String, UUID> stopIdsByProviderStopId,
			List<RouteDirectionSyncRequest> directions);

	Map<StopPatternSyncKey, UUID> synchronizeStopPatterns(
			UUID lineId,
			Map<String, UUID> stopIdsByProviderStopId,
			List<StopPatternSyncRequest> patterns);
}
