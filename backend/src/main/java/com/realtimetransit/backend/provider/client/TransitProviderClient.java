package com.realtimetransit.backend.provider.client;

import java.util.List;

import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.client.dto.ExternalRouteReference;
import com.realtimetransit.backend.provider.client.dto.ExternalTransitLine;

public interface TransitProviderClient {

	ExternalApiProvider provider();

	List<ExternalTransitLine> searchLines(String query, int limit);

	ExternalRouteReference fetchRoute(String providerLineId);

	List<ExternalArrival> fetchArrivals(String providerLineId, String providerStopId);
}
