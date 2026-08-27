package com.realtimetransit.backend.api;

import java.time.Instant;

public record SystemHealthResponse(String status, Instant checkedAt) {
}
