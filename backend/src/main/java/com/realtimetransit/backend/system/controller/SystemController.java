package com.realtimetransit.backend.system.controller;

import com.realtimetransit.backend.system.dto.response.SystemHealthResponse;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

	private final Clock clock;

	public SystemController(Clock clock) {
		this.clock = clock;
	}

	@GetMapping("/health")
	public SystemHealthResponse health() {
		return new SystemHealthResponse("UP", Instant.now(clock));
	}
}
