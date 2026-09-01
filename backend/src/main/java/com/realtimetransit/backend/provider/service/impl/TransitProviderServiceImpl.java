package com.realtimetransit.backend.provider.service.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.provider.client.TransitProviderClient;
import com.realtimetransit.backend.provider.service.TransitProviderService;

@Service
public class TransitProviderServiceImpl implements TransitProviderService {

	private final Map<ExternalApiProvider, TransitProviderClient> clients;

	public TransitProviderServiceImpl(List<TransitProviderClient> clients) {
		var clientsByProvider = new EnumMap<ExternalApiProvider, TransitProviderClient>(ExternalApiProvider.class);
		for (var client : clients) {
			if (clientsByProvider.put(client.provider(), client) != null) {
				throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Provider client: " + client.provider());
			}
		}
		this.clients = Map.copyOf(clientsByProvider);
	}

	@Override
	public TransitProviderClient getClient(ExternalApiProvider provider) {
		var client = clients.get(provider);
		if (client == null) {
			throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER, String.valueOf(provider));
		}
		return client;
	}
}
