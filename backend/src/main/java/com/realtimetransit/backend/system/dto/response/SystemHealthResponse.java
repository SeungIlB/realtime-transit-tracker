package com.realtimetransit.backend.system.dto.response;

import java.time.Instant;

public record SystemHealthResponse(String status, Instant checkedAt) {
}
