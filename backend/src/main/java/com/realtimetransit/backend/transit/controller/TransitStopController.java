package com.realtimetransit.backend.transit.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetransit.backend.common.dto.ResponseDTO;
import com.realtimetransit.backend.transit.dto.response.DirectedStopResponse;
import com.realtimetransit.backend.transit.dto.response.DestinationStopResponse;
import com.realtimetransit.backend.transit.service.TransitStopService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/lines/{lineId}/stops")
@RequiredArgsConstructor
public class TransitStopController {

	private final TransitStopService transitStopService;

    @GetMapping
    public ResponseDTO<List<DirectedStopResponse>> findStops(
            @PathVariable UUID lineId) {

        return ResponseDTO.success(
                transitStopService.findActiveStopsByLineId(lineId));
    }

	@GetMapping("/{boardingStopId}/destinations")
	public ResponseDTO<List<DestinationStopResponse>> findDestinations(
			@PathVariable UUID lineId,
			@PathVariable UUID boardingStopId) {
		return ResponseDTO.success(transitStopService.findDestinationsAfterBoardingStop(
				lineId,
				boardingStopId));
	}
}
