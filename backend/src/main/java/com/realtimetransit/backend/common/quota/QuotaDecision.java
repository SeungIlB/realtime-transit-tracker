package com.realtimetransit.backend.common.quota;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaDecision {
	private boolean allowed;
	private long remaining;
	private Instant resetsAt;
}
