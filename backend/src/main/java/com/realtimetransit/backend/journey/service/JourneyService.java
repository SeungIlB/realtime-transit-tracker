package com.realtimetransit.backend.journey.service;

import java.util.UUID;

import com.realtimetransit.backend.journey.dto.request.JourneyCreateRequest;
import com.realtimetransit.backend.journey.dto.response.JourneySessionResponse;

public interface JourneyService {

	JourneySessionResponse createJourney(JourneyCreateRequest request);

	JourneySessionResponse cancelJourney(UUID journeyId);
}
