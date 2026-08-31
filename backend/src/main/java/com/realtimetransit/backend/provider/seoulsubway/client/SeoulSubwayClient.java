package com.realtimetransit.backend.provider.seoulsubway.client;

import org.springframework.stereotype.Component;

import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.TransitProviderClient;

@Component
public class SeoulSubwayClient implements TransitProviderClient {

	@Override
	public ExternalApiProvider provider() {
		return ExternalApiProvider.SEOUL_SUBWAY;
	}
}
