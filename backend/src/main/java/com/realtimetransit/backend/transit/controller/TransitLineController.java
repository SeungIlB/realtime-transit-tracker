package com.realtimetransit.backend.transit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetransit.backend.transit.dto.response.TransitLineResponse;
import com.realtimetransit.backend.transit.service.TransitLineService;

@RestController
@RequestMapping("/api/v1/lines")
public class TransitLineController {

	private final TransitLineService transitLineService;

	public TransitLineController(TransitLineService transitLineService) {
		this.transitLineService = transitLineService;
	}

	@GetMapping
	public List<TransitLineResponse> searchLines(
			@RequestParam(defaultValue = "GBIS") String provider,
			@RequestParam String query,
			@RequestParam(defaultValue = "20") int limit) {
		return transitLineService.searchActiveLines(provider, query, limit);
	}
}
