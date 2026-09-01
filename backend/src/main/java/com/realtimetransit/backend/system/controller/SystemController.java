package com.realtimetransit.backend.system.controller;

import com.realtimetransit.backend.system.dto.response.SystemHealthResponse;
import com.realtimetransit.backend.common.dto.ResponseDTO;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

	private final Clock clock;

	@GetMapping("/health")
	public ResponseDTO<SystemHealthResponse> health() {
		return ResponseDTO.success(SystemHealthResponse.builder()
				.status("UP")
				.checkedAt(Instant.now(clock))
				.build());
	}
}
