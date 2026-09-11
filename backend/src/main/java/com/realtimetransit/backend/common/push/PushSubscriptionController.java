package com.realtimetransit.backend.common.push;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtimetransit.backend.common.dto.ResponseDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/push/subscriptions")
@RequiredArgsConstructor
public class PushSubscriptionController {
	private final PushSubscriptionService pushSubscriptionService;

	@PostMapping
	public ResponseDTO<Void> save(@RequestBody PushSubscriptionRequest request) {
		pushSubscriptionService.save(request);
		return ResponseDTO.success(null);
	}

	@DeleteMapping
	public ResponseDTO<Void> remove(@RequestBody PushSubscriptionRequest request) {
		pushSubscriptionService.remove(request == null ? null : request.getEndpoint());
		return ResponseDTO.success(null);
	}
}
