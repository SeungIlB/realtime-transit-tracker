package com.realtimetransit.backend.provider.service;

import java.util.UUID;

public interface TransitExternalCollectionService {
	void searchAndSynchronizeLines(String providerCode, String query, int limit);
	void synchronizeRoute(UUID lineId);
	void collectArrivals(UUID lineId, UUID boardingStopId, UUID alightingStopId);
}
