package com.realtimetransit.backend.common.push;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PushSubscriptionRequest {
	private String endpoint;
	private Long expirationTime;
	private PushSubscriptionKeys keys;
	private String journeyId;

	@Getter
	@Setter
	@NoArgsConstructor
	public static class PushSubscriptionKeys {
		private String p256dh;
		private String auth;
	}
}
