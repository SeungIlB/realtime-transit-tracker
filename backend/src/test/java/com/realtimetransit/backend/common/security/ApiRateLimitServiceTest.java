package com.realtimetransit.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class ApiRateLimitServiceTest {

	@Test
	void preservesRateLimitingWhenRedisIsUnavailable() {
		StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
		when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
				.thenThrow(new RedisConnectionFailureException("unavailable"));
		ApiRateLimitService service = new ApiRateLimitService(
				redisTemplate,
				Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC));

		assertThat(service.tryAcquire("test", "identity", 2)).isTrue();
		assertThat(service.tryAcquire("test", "identity", 2)).isTrue();
		assertThat(service.tryAcquire("test", "identity", 2)).isFalse();
	}
}
