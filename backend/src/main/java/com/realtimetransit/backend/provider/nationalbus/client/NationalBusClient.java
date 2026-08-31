package com.realtimetransit.backend.provider.nationalbus.client;

import org.springframework.stereotype.Component;

import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.provider.client.TransitProviderClient;

@Component
public class NationalBusClient implements TransitProviderClient {

	@Override
	public ExternalApiProvider provider() {
		return ExternalApiProvider.NATIONAL_PRECISION_BUS;
	}
}
