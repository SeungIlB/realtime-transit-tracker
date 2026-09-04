package com.realtimetransit.backend.transit.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetransit.backend.common.dto.ResponseDTO;
import com.realtimetransit.backend.transit.dto.response.UpcomingArrivalResponse;
import com.realtimetransit.backend.transit.service.ArrivalService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/lines/{lineId}/arrivals")
@RequiredArgsConstructor
public class ArrivalController {

	private final ArrivalService arrivalService;

	@GetMapping
	public ResponseDTO<List<UpcomingArrivalResponse>> findUpcomingArrivals(
			@PathVariable UUID lineId,
			@RequestParam UUID directionId,
			@RequestParam UUID boardingStopId,
			@RequestParam UUID alightingStopId) {
		return ResponseDTO.success(arrivalService.findUpcomingArrivals(
				lineId,
				directionId,
				boardingStopId,
				alightingStopId));
	}
}
