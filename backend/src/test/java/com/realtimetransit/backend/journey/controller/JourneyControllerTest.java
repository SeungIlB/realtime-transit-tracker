package com.realtimetransit.backend.journey.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.dto.request.JourneyCreateRequest;
import com.realtimetransit.backend.journey.dto.request.JourneyLocationCreateRequest;
import com.realtimetransit.backend.journey.dto.response.BoardingDecisionResponse;
import com.realtimetransit.backend.journey.dto.response.JourneyLocationResponse;
import com.realtimetransit.backend.journey.dto.response.JourneySessionResponse;
import com.realtimetransit.backend.journey.service.BoardingDecisionService;
import com.realtimetransit.backend.journey.service.JourneyLocationService;
import com.realtimetransit.backend.journey.service.JourneyService;

@WebMvcTest(JourneyController.class)
class JourneyControllerTest {

	@MockitoBean private JourneyService journeyService;
	@MockitoBean private JourneyLocationService journeyLocationService;
	@MockitoBean private BoardingDecisionService boardingDecisionService;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void exposesJourneyLifecycleEndpointsWithResponseDto() throws Exception {
		UUID journeyId = UUID.randomUUID();
		when(journeyService.createJourney(any(JourneyCreateRequest.class)))
				.thenReturn(JourneySessionResponse.builder().journeyId(journeyId).status("ACTIVE").build());
		when(journeyLocationService.addLocation(any(UUID.class), any(JourneyLocationCreateRequest.class)))
				.thenReturn(JourneyLocationResponse.builder().locationObservationId(10L).journeyId(journeyId).build());
		when(boardingDecisionService.calculateDecision(journeyId))
				.thenReturn(BoardingDecisionResponse.builder()
						.journeyId(journeyId).decision("NO_VEHICLE").vehicles(List.of()).build());
		when(journeyService.cancelJourney(journeyId))
				.thenReturn(JourneySessionResponse.builder().journeyId(journeyId).status("CANCELLED").build());

		mockMvc.perform(post("/api/v1/journeys")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"anonymousKey":"%s","lineId":"%s","directionId":"%s","boardingStopId":"%s"}
						""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.journeyId").value(journeyId.toString()))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		mockMvc.perform(post("/api/v1/journeys/{journeyId}/locations", journeyId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"latitude":37.5,"longitude":127.0,"accuracyM":10,"observedAt":"%s"}
						""".formatted(Instant.parse("2026-09-03T01:00:00Z"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.locationObservationId").value(10));

		mockMvc.perform(get("/api/v1/journeys/{journeyId}/decision", journeyId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.decision").value("NO_VEHICLE"));

		mockMvc.perform(delete("/api/v1/journeys/{journeyId}", journeyId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELLED"));
	}

	@Test
	void convertsBusinessExceptionToCommonFailureResponse() throws Exception {
		UUID journeyId = UUID.randomUUID();
		when(boardingDecisionService.calculateDecision(journeyId))
				.thenThrow(new BusinessException(ErrorCode.JOURNEY_NOT_FOUND, "journeyId=" + journeyId));

		mockMvc.perform(get("/api/v1/journeys/{journeyId}/decision", journeyId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("JOURNEY_NOT_FOUND"));
	}

	@Test
	void rejectsMalformedJourneyUuid() throws Exception {
		mockMvc.perform(get("/api/v1/journeys/not-a-uuid/decision"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
