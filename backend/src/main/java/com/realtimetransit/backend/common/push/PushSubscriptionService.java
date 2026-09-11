package com.realtimetransit.backend.common.push;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {
	private static final String REDIS_KEY = "rtt:push:subscriptions";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public void save(PushSubscriptionRequest request) {
		if (request == null || request.getEndpoint() == null || request.getEndpoint().isBlank()
				|| request.getKeys() == null || request.getKeys().getP256dh() == null
				|| request.getKeys().getAuth() == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "A valid push subscription is required");
		}
		try {
			redisTemplate.opsForHash().put(REDIS_KEY, request.getEndpoint(), objectMapper.writeValueAsString(request));
		} catch (JacksonException exception) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Push subscription could not be stored");
		}
	}

	public void remove(String endpoint) {
		if (endpoint != null && !endpoint.isBlank()) redisTemplate.opsForHash().delete(REDIS_KEY, endpoint);
	}
}
