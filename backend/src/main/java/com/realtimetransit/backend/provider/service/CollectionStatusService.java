package com.realtimetransit.backend.provider.service;

import java.time.Duration;
import java.util.List;

import com.realtimetransit.backend.provider.dto.response.ProviderCollectionStatusResponse;

public interface CollectionStatusService {

	List<ProviderCollectionStatusResponse> findCollectionStatuses(Duration lookback);
}
