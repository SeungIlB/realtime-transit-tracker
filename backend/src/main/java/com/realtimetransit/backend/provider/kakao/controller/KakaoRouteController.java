package com.realtimetransit.backend.provider.kakao.controller;

import tools.jackson.databind.JsonNode;
import com.realtimetransit.backend.common.dto.ResponseDTO;
import com.realtimetransit.backend.provider.kakao.client.KakaoPublicTransitClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class KakaoRouteController {
	private final KakaoPublicTransitClient client;

	@GetMapping("/search")
	public ResponseDTO<JsonNode> search(
			@RequestParam double startLongitude,
			@RequestParam double startLatitude,
			@RequestParam double endLongitude,
			@RequestParam double endLatitude) {
		return ResponseDTO.success(client.search(startLongitude, startLatitude, endLongitude, endLatitude));
	}
}
