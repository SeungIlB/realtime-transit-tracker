package com.realtimetransit.backend.provider.service;

import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.TransitProviderClient;

public interface TransitProviderService {

	TransitProviderClient getClient(ExternalApiProvider provider);
}
