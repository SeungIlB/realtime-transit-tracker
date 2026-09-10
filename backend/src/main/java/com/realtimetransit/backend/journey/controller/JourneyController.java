package com.realtimetransit.backend.journey.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetransit.backend.common.dto.ResponseDTO;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.dto.request.JourneyCreateRequest;
import com.realtimetransit.backend.journey.dto.request.JourneyLocationCreateRequest;
import com.realtimetransit.backend.journey.dto.request.RouteDecisionRequest;
import com.realtimetransit.backend.journey.dto.response.BoardingDecisionResponse;
import com.realtimetransit.backend.journey.dto.response.JourneyLocationResponse;
import com.realtimetransit.backend.journey.dto.response.JourneySessionResponse;
import com.realtimetransit.backend.journey.dto.response.RouteDecisionResponse;
import com.realtimetransit.backend.journey.service.BoardingDecisionService;
import com.realtimetransit.backend.journey.service.JourneyLocationService;
import com.realtimetransit.backend.journey.service.JourneyService;
import com.realtimetransit.backend.journey.service.RouteDecisionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
public class JourneyController {

	private final JourneyService journeyService;
	private final JourneyLocationService journeyLocationService;
	private final BoardingDecisionService boardingDecisionService;
	private final RouteDecisionService routeDecisionService;

	@PostMapping("/route-decisions")
	public ResponseDTO<RouteDecisionResponse> calculateRouteDecision(
			@RequestHeader("X-Anonymous-Key") UUID anonymousKey,
			@RequestBody RouteDecisionRequest request) {
		return ResponseDTO.success(routeDecisionService.calculateRouteDecision(anonymousKey, request));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseDTO<JourneySessionResponse> createJourney(
			@RequestHeader("X-Anonymous-Key") UUID anonymousKey,
			@RequestBody JourneyCreateRequest request) {
		if (request == null || !anonymousKey.equals(request.getAnonymousKey())) {
			throw new BusinessException(ErrorCode.INVALID_JOURNEY_REQUEST, "anonymous key does not match request");
		}
		return ResponseDTO.success(journeyService.createJourney(request));
	}

	@PostMapping("/{journeyId}/locations")
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseDTO<JourneyLocationResponse> addLocation(
			@PathVariable UUID journeyId,
			@RequestHeader("X-Anonymous-Key") UUID anonymousKey,
			@RequestBody JourneyLocationCreateRequest request) {
		return ResponseDTO.success(journeyLocationService.addLocation(journeyId, anonymousKey, request));
	}

	@GetMapping("/{journeyId}/decision")
	public ResponseDTO<BoardingDecisionResponse> calculateDecision(
			@PathVariable UUID journeyId,
			@RequestHeader("X-Anonymous-Key") UUID anonymousKey) {
		return ResponseDTO.success(boardingDecisionService.calculateDecision(journeyId, anonymousKey));
	}

	@DeleteMapping("/{journeyId}")
	public ResponseDTO<JourneySessionResponse> cancelJourney(
			@PathVariable UUID journeyId,
			@RequestHeader("X-Anonymous-Key") UUID anonymousKey) {
		return ResponseDTO.success(journeyService.cancelJourney(journeyId, anonymousKey));
	}
}
