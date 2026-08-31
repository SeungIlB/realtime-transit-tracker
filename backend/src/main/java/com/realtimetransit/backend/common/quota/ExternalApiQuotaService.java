package com.realtimetransit.backend.common.quota;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(ExternalApiQuotaProperties.class)
public class ExternalApiQuotaService {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
			local current = tonumber(redis.call('GET', KEYS[1]) or '0')
			local limit = tonumber(ARGV[1])
			if current >= limit then
			  return -1
			end
			current = redis.call('INCR', KEYS[1])
			if current == 1 then
			  redis.call('EXPIREAT', KEYS[1], tonumber(ARGV[2]))
			end
			return limit - current
			""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final ExternalApiQuotaProperties properties;
	private final Clock clock;

	public ExternalApiQuotaService(
			StringRedisTemplate redisTemplate,
			ExternalApiQuotaProperties properties,
			Clock clock) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.clock = clock;
	}

	public QuotaDecision tryAcquire(ExternalApiProvider provider) {
		long dailyLimit = properties.dailyLimit(provider);
		if (dailyLimit <= 0) {
			throw new IllegalStateException("Daily API limit must be positive for " + provider);
		}

		var today = LocalDate.now(clock.withZone(KOREA_ZONE));
		Instant resetsAt = today.plusDays(1).atStartOfDay(KOREA_ZONE).toInstant();
		String key = "rtt:quota:" + provider.name().toLowerCase() + ":" + today;
		Long remaining = redisTemplate.execute(
				ACQUIRE_SCRIPT,
				List.of(key),
				Long.toString(dailyLimit),
				Long.toString(resetsAt.getEpochSecond()));

		if (remaining == null) {
			throw new IllegalStateException("Redis did not return an API quota decision");
		}
		return new QuotaDecision(remaining >= 0, Math.max(remaining, 0), resetsAt);
	}
}
