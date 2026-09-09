package com.realtimetransit.backend.common.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ApiRateLimitService {

	private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
			local current = tonumber(redis.call('GET', KEYS[1]) or '0')
			local limit = tonumber(ARGV[1])
			if current >= limit then
			  return 0
			end
			current = redis.call('INCR', KEYS[1])
			if current == 1 then
			  redis.call('EXPIRE', KEYS[1], 120)
			end
			return 1
			""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final Clock clock;
	private final ConcurrentMap<String, FallbackWindow> fallbackWindows = new ConcurrentHashMap<>();

	public boolean tryAcquire(String scope, String identityHash, int limit) {
		Instant minute = clock.instant().truncatedTo(ChronoUnit.MINUTES);
		String key = "rtt:rate:" + scope + ":" + identityHash + ":" + minute.getEpochSecond();
		try {
			Long allowed = redisTemplate.execute(
					ACQUIRE_SCRIPT,
					List.of(key),
					Integer.toString(limit));
			return Long.valueOf(1).equals(allowed);
		} catch (DataAccessException exception) {
			return tryAcquireLocally(key, limit, minute.plus(2, ChronoUnit.MINUTES));
		}
	}

	private boolean tryAcquireLocally(String key, int limit, Instant expiresAt) {
		Instant now = clock.instant();
		FallbackWindow window = fallbackWindows.compute(key, (ignored, current) -> {
			if (current == null || !current.expiresAt().isAfter(now)) {
				return new FallbackWindow(1, expiresAt);
			}
			return new FallbackWindow(current.count() + 1, current.expiresAt());
		});
		if (fallbackWindows.size() > 10_000) {
			fallbackWindows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
		}
		return window.count() <= limit;
	}

	private record FallbackWindow(int count, Instant expiresAt) {
	}
}
