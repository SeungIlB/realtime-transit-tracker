package com.realtimetransit.backend.system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.realtimetransit.backend.common.config.WebCorsConfig;
import com.realtimetransit.backend.common.security.ApiRateLimitService;

@WebMvcTest(
		value = SystemController.class,
		properties = "app.cors.allowed-origins=https://frontend.example.com"
)
@Import({SystemControllerTest.FixedClockConfig.class, WebCorsConfig.class})
class SystemControllerTest {
	@MockitoBean private ApiRateLimitService apiRateLimitService;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsApplicationHealth() throws Exception {
		mockMvc.perform(get("/api/v1/system/health"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.status").value("UP"))
				.andExpect(jsonPath("$.data.checkedAt").value("2026-08-27T00:00:00Z"));
	}

	@Test
	void allowsConfiguredFrontendOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/system/health")
				.header(HttpHeaders.ORIGIN, "https://frontend.example.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://frontend.example.com"));
	}

	static class FixedClockConfig {
		@Bean
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
		}
	}
}
