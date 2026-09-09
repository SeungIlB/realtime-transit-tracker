package com.realtimetransit.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class ApiSecurityFilterTest {

	private ApiRateLimitService rateLimitService;
	private ApiSecurityProperties properties;
	private ApiSecurityFilter filter;

	@BeforeEach
	void setUp() {
		rateLimitService = mock(ApiRateLimitService.class);
		properties = new ApiSecurityProperties();
		filter = new ApiSecurityFilter(rateLimitService, properties);
	}

	@Test
	void rejectsRateLimitedRequestsAndAppliesPrivacyHeaders() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/journeys");
		request.addHeader("X-Anonymous-Key", "00000000-0000-0000-0000-000000000001");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);
		when(rateLimitService.tryAcquire(eq("journey-create"), anyString(), eq(10))).thenReturn(false);

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(429);
		assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
		assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
		verifyNoInteractions(chain);
	}

	@Test
	void rejectsOversizedRequestBeforeRateLimitLookup() throws Exception {
		properties.setMaxRequestBytes(4);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lines/search");
		request.setContent("12345".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(413);
		assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
		verifyNoInteractions(rateLimitService, chain);
	}

	@Test
	void allowsCorsPreflightWithoutConsumingRequestQuota() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/lines/search");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		verifyNoInteractions(rateLimitService);
	}
}
