package com.realtimetransit.backend.provider.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.realtimetransit.backend.transit.entity.TransitLineEntity;

public interface TransitExternalCollectionService {
	List<String> searchAndSynchronizeLines(
			String providerCode,
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude);

	List<TransitLineEntity> searchAndSynchronizeNearbyBusLines(
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude);

	void synchronizeRoute(UUID lineId);

	void collectArrivals(UUID lineId, UUID boardingStopId, UUID alightingStopId);
}
