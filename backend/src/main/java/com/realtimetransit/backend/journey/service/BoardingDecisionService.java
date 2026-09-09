package com.realtimetransit.backend.journey.service;

import java.util.UUID;

import com.realtimetransit.backend.journey.dto.response.BoardingDecisionResponse;

public interface BoardingDecisionService {

	BoardingDecisionResponse calculateDecision(UUID journeyId, UUID anonymousKey);
}
