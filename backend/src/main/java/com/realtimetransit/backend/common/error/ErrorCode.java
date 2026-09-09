package com.realtimetransit.backend.common.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request value is invalid"),
	INVALID_JOURNEY_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_JOURNEY_REQUEST", "Journey request is invalid"),
	INVALID_JOURNEY_STOPS(HttpStatus.BAD_REQUEST, "INVALID_JOURNEY_STOPS", "Journey stops do not match the selected route direction"),
	JOURNEY_NOT_FOUND(HttpStatus.NOT_FOUND, "JOURNEY_NOT_FOUND", "Journey session was not found"),
	JOURNEY_NOT_ACTIVE(HttpStatus.CONFLICT, "JOURNEY_NOT_ACTIVE", "Journey session is not active"),
	JOURNEY_STATUS_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "JOURNEY_STATUS_UPDATE_FAILED", "Journey status could not be updated"),
	ACTIVE_JOURNEY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "ACTIVE_JOURNEY_LIMIT_EXCEEDED", "Too many active journey sessions"),
	INVALID_JOURNEY_LOCATION(HttpStatus.BAD_REQUEST, "INVALID_JOURNEY_LOCATION", "Journey location is invalid"),
	JOURNEY_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "JOURNEY_PROFILE_NOT_FOUND", "Journey traveler profile was not found"),
	BOARDING_STOP_COORDINATES_MISSING(HttpStatus.UNPROCESSABLE_CONTENT, "BOARDING_STOP_COORDINATES_MISSING", "Boarding stop coordinates are missing"),
	PREDICTION_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PREDICTION_SERIALIZATION_FAILED", "Prediction factors could not be serialized"),
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "Resource is duplicated"),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found"),
	SYNC_RESULT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "SYNC_RESULT_NOT_FOUND", "Synchronized resource could not be read back"),
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER", "Transit provider is not supported"),
	INVALID_CONFIGURATION(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_CONFIGURATION", "Application configuration is invalid"),
	EXTERNAL_STORAGE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_STORAGE_ERROR", "External storage operation failed"),
	EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "EXTERNAL_API_ERROR", "External transit API request failed"),
	EXTERNAL_API_AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "EXTERNAL_API_AUTHENTICATION_FAILED", "External transit API authentication failed"),
	EXTERNAL_API_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "EXTERNAL_API_QUOTA_EXCEEDED", "External transit API quota was exhausted"),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
