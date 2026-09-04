package com.realtimetransit.backend.provider.kric.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class KricRailwayTimetableClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void extractsRowsFromNestedKricResponseWithoutDependingOnWrapperShape() {
		var response = objectMapper.readTree("""
				{
				  "body": {
				    "items": [
				      {
				        "railOprIsttCd": "KR",
				        "lnCd": "1",
				        "stinCd": "152",
				        "trnNo": "1096",
				        "exptCd": "1"
				      }
				    ]
				  }
				}
				""");

		assertThat(KricRailwayTimetableClient.objectsContaining(
				response, "trnNo", "railOprIsttCd", "lnCd", "stinCd"))
				.singleElement()
				.satisfies(row -> assertThat(row.get("trnNo").asString()).isEqualTo("1096"));
	}
}
