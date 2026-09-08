package com.realtimetransit.backend.provider.client;

import java.math.BigDecimal;
import java.util.List;

import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.client.dto.ExternalRouteReference;
import com.realtimetransit.backend.provider.client.dto.ExternalTransitLine;

public interface TransitProviderClient {

	ExternalApiProvider provider();

	List<ExternalTransitLine> searchLines(String query, int limit);

	default List<ExternalTransitLine> searchLines(
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude) {
		return searchLines(query, limit);
	}

	ExternalRouteReference fetchRoute(String providerLineId);

	List<ExternalArrival> fetchArrivals(String providerLineId, String providerStopId);
}
