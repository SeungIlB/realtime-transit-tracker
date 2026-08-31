package com.realtimetransit.backend.provider.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.realtimetransit.backend.provider.entity.RawObservationEntity;

@SpringBootTest
@Testcontainers
class TransitProviderMapperIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	@Autowired
	private TransitProviderMapper transitProviderMapper;

	@Autowired
	private RawObservationMapper rawObservationMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void readsProvidersInsertedByFlyway() {
		var providers = transitProviderMapper.findAll();

		assertThat(providers).extracting(provider -> provider.code())
				.containsExactly("GBIS", "NATIONAL_PRECISION_BUS", "SEOUL_SUBWAY");
		assertThat(transitProviderMapper.findByCode("GBIS")).isPresent();
	}

	@Test
	@Transactional
	void findsOnlyActiveProvidersInIdOrder() {
		jdbcTemplate.update("""
			INSERT INTO transit_provider (code, display_name, transport_type, active)
			VALUES (?, ?, ?, FALSE)
			""", "INACTIVE_TEST", "비활성 테스트", "BUS");

		var activeProviders = transitProviderMapper.findAllActive();
		assertThat(activeProviders)
				.extracting(provider -> provider.code())
				.containsExactly("GBIS", "NATIONAL_PRECISION_BUS", "SEOUL_SUBWAY");
		assertThat(activeProviders)
				.extracting(provider -> provider.id())
				.isSorted();
	}

	@Test
	void insertsRawObservationAndReturnsGeneratedId() {
		long providerId = transitProviderMapper.findByCode("GBIS").orElseThrow().id();
		Instant receivedAt = Instant.parse("2026-08-31T01:00:00Z");
		long observationId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null,
				providerId,
				"/bus-arrivals",
				"route-1000:stop-1",
				receivedAt,
				Instant.parse("2026-08-31T00:59:55Z"),
				200,
				"{\"vehicles\":[{\"id\":\"bus-1\"}]}",
				Instant.parse("2026-08-31T01:00:30Z")));

		assertThat(jdbcTemplate.queryForMap(
				"SELECT provider_id, endpoint, request_key, response_status, "
						+ "payload -> 'vehicles' -> 0 ->> 'id' AS vehicle_id "
						+ "FROM raw_observation WHERE id = ?",
				observationId))
				.containsEntry("provider_id", providerId)
				.containsEntry("endpoint", "/bus-arrivals")
				.containsEntry("request_key", "route-1000:stop-1")
				.containsEntry("response_status", 200)
				.containsEntry("vehicle_id", "bus-1");

		long nullPayloadObservationId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, "/bus-arrivals", "route-1000:stop-2", receivedAt,
				null, 204, null, null));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT payload IS NULL AND provider_observed_at IS NULL AND expires_at IS NULL "
						+ "FROM raw_observation WHERE id = ?",
				Boolean.class, nullPayloadObservationId))
				.isTrue();
	}

	@Test
	void findsLatestRawObservationByRequestIdentity() {
		long gbisProviderId = transitProviderMapper.findByCode("GBIS").orElseThrow().id();
		long nationalProviderId = transitProviderMapper.findByCode("NATIONAL_PRECISION_BUS")
				.orElseThrow().id();
		Instant receivedAt = Instant.parse("2026-08-31T02:00:00Z");

		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, gbisProviderId, "/latest-test", "route-1:stop-1", receivedAt,
				null, 200, "{\"result\":\"success\"}", null));
		long latestId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, gbisProviderId, "/latest-test", "route-1:stop-1", receivedAt,
				null, 503, "{\"result\":\"failure\"}", null));
		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, gbisProviderId, "/other-endpoint", "route-1:stop-1", receivedAt.plusSeconds(60),
				null, 200, null, null));
		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, gbisProviderId, "/latest-test", "other-request", receivedAt.plusSeconds(60),
				null, 200, null, null));
		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, nationalProviderId, "/latest-test", "route-1:stop-1", receivedAt.plusSeconds(60),
				null, 200, null, null));

		assertThat(rawObservationMapper.findLatestByProviderIdAndEndpointAndRequestKey(
				gbisProviderId, "/latest-test", "route-1:stop-1"))
				.get()
				.satisfies(observation -> {
					assertThat(observation.id()).isEqualTo(latestId);
					assertThat(observation.responseStatus()).isEqualTo(503);
					assertThat(observation.payload()).contains("failure");
				});
		assertThat(rawObservationMapper.findLatestByProviderIdAndEndpointAndRequestKey(
				gbisProviderId, "/latest-test", "missing-request"))
				.isEmpty();
	}

	@Test
	void findsLatestSuccessfulUnexpiredRawObservationForFallback() {
		long providerId = transitProviderMapper.findByCode("GBIS").orElseThrow().id();
		Instant asOf = Instant.parse("2026-08-31T03:00:00Z");
		String endpoint = "/fallback-test";
		String requestKey = "route-2:stop-3";

		long usableObservationId = rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, endpoint, requestKey, asOf.minusSeconds(40),
				null, 299, "{\"source\":\"usable\"}", asOf.plusSeconds(60)));
		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, endpoint, requestKey, asOf.minusSeconds(30),
				null, 200, "{\"source\":\"expired\"}", asOf));
		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, endpoint, requestKey, asOf.minusSeconds(20),
				null, 200, null, asOf.plusSeconds(60)));
		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, providerId, endpoint, requestKey, asOf.minusSeconds(10),
				null, 503, "{\"source\":\"failure\"}", asOf.plusSeconds(60)));

		assertThat(rawObservationMapper.findLatestSuccessfulUnexpiredByRequestIdentity(
				providerId, endpoint, requestKey, asOf))
				.get()
				.satisfies(observation -> {
					assertThat(observation.id()).isEqualTo(usableObservationId);
					assertThat(observation.responseStatus()).isEqualTo(299);
					assertThat(observation.payload()).contains("usable");
				});
		assertThat(rawObservationMapper.findLatestSuccessfulUnexpiredByRequestIdentity(
				providerId, endpoint, "missing-request", asOf))
				.isEmpty();
	}

	@Test
	@Transactional
	void aggregatesCollectionStatusesByProviderAndEndpoint() {
		long gbisProviderId = transitProviderMapper.findByCode("GBIS").orElseThrow().id();
		long nationalProviderId = transitProviderMapper.findByCode("NATIONAL_PRECISION_BUS")
				.orElseThrow().id();
		Instant receivedAfter = Instant.parse("2030-01-01T00:00:00Z");

		for (int index = 0; index < 4; index++) {
			int status = new int[] {199, 200, 299, 300}[index];
			rawObservationMapper.insertRawObservation(new RawObservationEntity(
					null, gbisProviderId, "/collect-a", "request-" + index,
					receivedAfter.plusSeconds(index), null, status, null, null));
		}
		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, gbisProviderId, "/collect-b", "request-1",
				receivedAfter, null, 204, null, null));
		rawObservationMapper.insertRawObservation(new RawObservationEntity(
				null, nationalProviderId, "/collect-a", "request-1",
				receivedAfter, null, 503, null, null));

		assertThat(rawObservationMapper.findCollectionStatusesReceivedAfter(receivedAfter))
				.satisfiesExactly(
						gbisFirstEndpoint -> {
							assertThat(gbisFirstEndpoint.providerCode()).isEqualTo("GBIS");
							assertThat(gbisFirstEndpoint.endpoint()).isEqualTo("/collect-a");
							assertThat(gbisFirstEndpoint.totalCalls()).isEqualTo(4);
							assertThat(gbisFirstEndpoint.successfulCalls()).isEqualTo(2);
							assertThat(gbisFirstEndpoint.failedCalls()).isEqualTo(2);
							assertThat(gbisFirstEndpoint.latestReceivedAt())
									.isEqualTo(receivedAfter.plusSeconds(3));
						},
						gbisSecondEndpoint -> {
							assertThat(gbisSecondEndpoint.providerCode()).isEqualTo("GBIS");
							assertThat(gbisSecondEndpoint.endpoint()).isEqualTo("/collect-b");
							assertThat(gbisSecondEndpoint.successfulCalls()).isEqualTo(1);
						},
						nationalEndpoint -> {
							assertThat(nationalEndpoint.providerCode()).isEqualTo("NATIONAL_PRECISION_BUS");
							assertThat(nationalEndpoint.endpoint()).isEqualTo("/collect-a");
							assertThat(nationalEndpoint.failedCalls()).isEqualTo(1);
						});
	}
}
