package com.realtimetransit.backend.transit.service;

import java.math.BigDecimal;
import java.util.List;

import com.realtimetransit.backend.transit.dto.response.TransitLineResponse;

public interface TransitLineService {

	List<TransitLineResponse> searchActiveLines(
			String providerCode,
			String query,
			int limit,
			BigDecimal latitude,
			BigDecimal longitude);
}
