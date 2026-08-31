package com.realtimetransit.backend.provider.gbis.client;

import org.springframework.stereotype.Component;

import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.TransitProviderClient;

@Component
public class GbisClient implements TransitProviderClient {

	@Override
	public ExternalApiProvider provider() {
		return ExternalApiProvider.GBIS;
	}
}
