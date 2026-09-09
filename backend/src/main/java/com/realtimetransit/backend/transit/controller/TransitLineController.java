package com.realtimetransit.backend.transit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetransit.backend.common.dto.ResponseDTO;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.transit.dto.request.TransitLineSearchRequest;
import com.realtimetransit.backend.transit.dto.response.TransitLineResponse;
import com.realtimetransit.backend.transit.service.TransitLineService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/lines")
@RequiredArgsConstructor
public class TransitLineController {

	private final TransitLineService transitLineService;

	@PostMapping("/search")
	public ResponseDTO<List<TransitLineResponse>> searchLines(
			@RequestBody TransitLineSearchRequest request) {
		if (request == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "request is required");
		}
		return ResponseDTO.success(transitLineService.searchActiveLines(
				request.getProvider(),
				request.getQuery(),
				request.getLimit() == null ? 20 : request.getLimit(),
				request.getLatitude(),
				request.getLongitude()));
	}
}
