package com.realtimetransit.backend.common.push;

import java.util.UUID;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.common.error.ErrorCode;
import com.realtimetransit.backend.journey.dto.response.BoardingDecisionResponse;
import com.realtimetransit.backend.journey.dto.response.VehicleBoardingPredictionResponse;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushSubscriptionService {
	private static final String REDIS_KEY = "rtt:push:subscriptions";
	private static final Duration DEDUPE_TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	@Value("${VAPID_PUBLIC_KEY:}")
	private String publicKey;
	@Value("${VAPID_PRIVATE_KEY:}")
	private String privateKey;
	@Value("${VAPID_SUBJECT:mailto:admin@realtime-transit.app}")
	private String subject;

	public void save(PushSubscriptionRequest request) {
		if (request == null || request.getEndpoint() == null || request.getEndpoint().isBlank()
				|| request.getKeys() == null || request.getKeys().getP256dh() == null
				|| request.getKeys().getAuth() == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "A valid push subscription is required");
		}
		try {
			redisTemplate.opsForHash().put(REDIS_KEY, request.getEndpoint(), objectMapper.writeValueAsString(request));
		} catch (JacksonException exception) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Push subscription could not be stored");
		}
	}

	public void remove(String endpoint) {
		if (endpoint != null && !endpoint.isBlank()) redisTemplate.opsForHash().delete(REDIS_KEY, endpoint);
	}

	public void notifyVehicleMovement(UUID journeyId, BoardingDecisionResponse decision) {
		if (publicKey.isBlank() || privateKey.isBlank() || decision == null || decision.getVehicles() == null) return;
		for (Object value : redisTemplate.opsForHash().values(REDIS_KEY)) {
			try {
				PushSubscriptionRequest request = objectMapper.readValue((String) value, PushSubscriptionRequest.class);
				if (request.getJourneyId() == null || !request.getJourneyId().equals(journeyId.toString())) continue;
				for (VehicleBoardingPredictionResponse vehicle : decision.getVehicles()) {
					String eventKey = journeyId + ":" + vehicle.getProviderVehicleId() + ":"
							+ (vehicle.getCurrentSequence() == null ? vehicle.getVehicleExpectedAt() : vehicle.getCurrentSequence());
					Boolean firstEvent = redisTemplate.opsForValue().setIfAbsent(
							"rtt:push:notified:" + eventKey, "1", DEDUPE_TTL);
					if (!Boolean.TRUE.equals(firstEvent)) continue;
					String payload = objectMapper.writeValueAsString(java.util.Map.of(
							"title", "탑승 알림",
							"body", vehicle.getCurrentStopName() == null
									? "차량 위치가 갱신됐어요. 탑승 가능성을 확인해 보세요."
									: vehicle.getCurrentStopName() + " 부근 차량이 이동 중이에요.",
							"tag", "vehicle-" + vehicle.getProviderVehicleId(),
							"url", "/"));
					Subscription subscription = new Subscription(request.getEndpoint(),
						new Subscription.Keys(request.getKeys().getP256dh(), request.getKeys().getAuth()));
					var response = new PushService(publicKey, privateKey, subject).send(
							new Notification(subscription, payload), Encoding.AES128GCM);
					int status = response.getStatusLine().getStatusCode();
					if (status == 404 || status == 410) remove(request.getEndpoint());
					else if (status >= 400) log.warn("Web push failed for journeyId={} with status={}", journeyId, status);
				}
			} catch (Exception exception) {
				log.warn("Web push delivery failed for journeyId={}: {}", journeyId, exception.getMessage());
			}
		}
	}
}
