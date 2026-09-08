package com.realtimetransit.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.realtimetransit.backend.provider.client.dto.ExternalTransitLine;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RedisCacheConfigTest {

	@Test
	void roundTripsCachedDtoLists() {
		var serializer = RedisCacheConfig.jsonSerializer();
		var source = List.of(ExternalTransitLine.builder()
				.providerLineId("241439006")
				.publicName("033")
				.operatorName("파주")
				.sourceUpdatedAt(Instant.parse("2026-09-08T00:00:00Z"))
				.build());

		Object restored = serializer.deserialize(serializer.serialize(source));

		assertThat(restored).isInstanceOf(List.class);
		assertThat((List<?>) restored)
				.singleElement()
				.isInstanceOfSatisfying(ExternalTransitLine.class, line -> {
					assertThat(line.getPublicName()).isEqualTo("033");
					assertThat(line.getOperatorName()).isEqualTo("파주");
				});
	}

	@Test
	void roundTripsCachedJsonNodeLists() {
		var serializer = RedisCacheConfig.jsonSerializer();
		JsonNode node = new ObjectMapper().readTree("""
				{"citycode":31200,"cityname":"파주시"}
				""");

		Object restored = serializer.deserialize(serializer.serialize(List.of(node)));

		assertThat(restored).isInstanceOf(List.class);
		assertThat((List<?>) restored)
				.singleElement()
				.isInstanceOfSatisfying(JsonNode.class,
						restoredNode -> assertThat(restoredNode.get("cityname").asString()).isEqualTo("파주시"));
	}
}
