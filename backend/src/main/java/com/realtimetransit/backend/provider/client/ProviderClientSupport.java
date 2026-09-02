package com.realtimetransit.backend.provider.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.JsonNode;
import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.common.quota.ExternalApiProvider;
import com.realtimetransit.backend.common.quota.ExternalApiQuotaService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class ProviderClientSupport {
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private final ExternalApiQuotaService quotaService;

	protected void acquireQuota(ExternalApiProvider provider) {
		if (!quotaService.tryAcquire(provider).isAllowed()) {
			throw new BusinessException(ErrorCode.EXTERNAL_API_QUOTA_EXCEEDED, provider.name());
		}
	}

	protected static void requireKey(String key, ExternalApiProvider provider) {
		if (key == null || key.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION, provider.name() + " service key");
		}
	}

	protected static String text(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value == null || value.isNull() ? null : value.asString().strip();
	}

	protected static Integer integer(JsonNode node, String field) {
		String value = text(node, field);
		try {
			return value == null || value.isBlank() ? null : Integer.valueOf(value);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	protected static BigDecimal decimal(JsonNode node, String field) {
		String value = text(node, field);
		try {
			return value == null || value.isBlank() ? null : new BigDecimal(value);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	protected static Instant koreaInstant(String value, String pattern) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value.strip(), DateTimeFormatter.ofPattern(pattern))
					.atZone(KOREA_ZONE)
					.toInstant();
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	protected static String decodeServiceKey(String value) {
		return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
