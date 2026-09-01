package com.realtimetransit.backend.common.quota;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "transit.api-quota.seoul-subway-daily-limit=2")
@Testcontainers
class ExternalApiQuotaServiceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine")
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "");
	}

	@Autowired
	private ExternalApiQuotaService quotaService;

	@Test
	void atomicallyRejectsCallsAfterTheDailyBudgetIsConsumed() {
		var first = quotaService.tryAcquire(ExternalApiProvider.SEOUL_SUBWAY);
		var second = quotaService.tryAcquire(ExternalApiProvider.SEOUL_SUBWAY);
		var rejected = quotaService.tryAcquire(ExternalApiProvider.SEOUL_SUBWAY);

		assertThat(first.isAllowed()).isTrue();
		assertThat(first.getRemaining()).isEqualTo(1);
		assertThat(second.isAllowed()).isTrue();
		assertThat(second.getRemaining()).isZero();
		assertThat(rejected.isAllowed()).isFalse();
		assertThat(rejected.getRemaining()).isZero();
		assertThat(first.getResetsAt()).isEqualTo(second.getResetsAt());
	}
}
