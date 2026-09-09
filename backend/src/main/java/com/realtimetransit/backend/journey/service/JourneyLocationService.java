package com.realtimetransit.backend.journey.service;

import java.util.UUID;

import com.realtimetransit.backend.journey.dto.request.JourneyLocationCreateRequest;
import com.realtimetransit.backend.journey.dto.response.JourneyLocationResponse;

public interface JourneyLocationService {

	JourneyLocationResponse addLocation(UUID journeyId, UUID anonymousKey, JourneyLocationCreateRequest request);
}
