package com.realtimetransit.backend.journey.service;

import java.util.UUID;

import com.realtimetransit.backend.journey.dto.request.RouteDecisionRequest;
import com.realtimetransit.backend.journey.dto.response.RouteDecisionResponse;

public interface RouteDecisionService {

	RouteDecisionResponse calculateRouteDecision(UUID anonymousKey, RouteDecisionRequest request);
}
