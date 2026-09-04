package com.realtimetransit.backend.provider.gbis.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GbisClientTest {

	@Test
	void resolvesDirectionFromRouteOccurrenceAndTurnaroundProgress() {
		assertThat(GbisClient.directionId(9, 8, 10)).isEqualTo("OUTBOUND");
		assertThat(GbisClient.directionId(11, 8, 10)).isEqualTo("INBOUND");
		assertThat(GbisClient.directionId(10, 10, 10)).isEqualTo("OUTBOUND");
		assertThat(GbisClient.directionId(10, 11, 10)).isEqualTo("INBOUND");
	}
}
