package com.realtimetransit.backend.common.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request value is invalid"),
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "Resource is duplicated"),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found"),
	SYNC_RESULT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "SYNC_RESULT_NOT_FOUND", "Synchronized resource could not be read back"),
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER", "Transit provider is not supported"),
	INVALID_CONFIGURATION(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_CONFIGURATION", "Application configuration is invalid"),
	EXTERNAL_STORAGE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_STORAGE_ERROR", "External storage operation failed"),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
