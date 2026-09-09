package com.realtimetransit.backend.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@EnableConfigurationProperties(ApiSecurityProperties.class)
public class ApiSecurityFilter extends OncePerRequestFilter {

	private static final String ANONYMOUS_KEY_HEADER = "X-Anonymous-Key";

	private final ApiRateLimitService rateLimitService;
	private final ApiSecurityProperties properties;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		applySecurityHeaders(request, response);
		String path = request.getRequestURI();
		if (!path.startsWith("/api/") || path.equals("/api/v1/system/health")) {
			filterChain.doFilter(request, response);
			return;
		}
		if (request.getContentLengthLong() > properties.getMaxRequestBytes()) {
			writeError(response, HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large");
			return;
		}
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}

		RateLimit limit = resolveLimit(request);
		try {
			if (!rateLimitService.tryAcquire(
					limit.scope(),
					identityHash(request, limit.includeAnonymousKey()),
					limit.requestsPerMinute())) {
				response.setHeader(HttpHeaders.RETRY_AFTER, "60");
				writeError(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests");
				return;
			}
		} catch (RuntimeException exception) {
			writeError(response, HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", "Request protection is temporarily unavailable");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private void applySecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
		response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
		if (request.isSecure()) {
			response.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
		}
		if (request.getRequestURI().startsWith("/api/v1/journeys")) {
			response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		}
	}

	private RateLimit resolveLimit(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (HttpMethod.POST.matches(request.getMethod()) && path.equals("/api/v1/journeys")) {
			return new RateLimit("journey-create", properties.getJourneyCreatesPerMinute(), false);
		}
		if (path.equals("/api/v1/lines/search")) {
			return new RateLimit("line-search", properties.getLineSearchesPerMinute(), false);
		}
		if (path.endsWith("/locations")) {
			return new RateLimit("location", properties.getLocationUpdatesPerMinute(), true);
		}
		if (path.endsWith("/decision")) {
			return new RateLimit("decision", properties.getDecisionsPerMinute(), true);
		}
		return new RateLimit("default", properties.getDefaultRequestsPerMinute(), false);
	}

	private String identityHash(HttpServletRequest request, boolean includeAnonymousKey) {
		String identity = request.getRemoteAddr();
		if (includeAnonymousKey) {
			String anonymousKey = request.getHeader(ANONYMOUS_KEY_HEADER);
			if (anonymousKey == null || anonymousKey.length() > 64) anonymousKey = "none";
			identity += ':' + anonymousKey;
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(identity.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private void writeError(
			HttpServletResponse response,
			HttpStatus status,
			String code,
			String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType("application/json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"success\":false,\"code\":\"" + code
				+ "\",\"message\":\"" + message + "\",\"data\":null}");
	}

	private record RateLimit(String scope, int requestsPerMinute, boolean includeAnonymousKey) {
	}
}
