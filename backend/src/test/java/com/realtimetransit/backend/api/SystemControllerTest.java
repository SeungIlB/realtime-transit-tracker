package com.realtimetransit.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemController.class)
@Import(SystemControllerTest.FixedClockConfig.class)
class SystemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsApplicationHealth() throws Exception {
		mockMvc.perform(get("/api/v1/system/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.checkedAt").value("2026-08-27T00:00:00Z"));
	}

	static class FixedClockConfig {
		@Bean
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
		}
	}
}
