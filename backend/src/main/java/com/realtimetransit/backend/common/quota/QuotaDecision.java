package com.realtimetransit.backend.common.quota;

import java.time.Instant;

public record QuotaDecision(boolean allowed, long remaining, Instant resetsAt) {
}
