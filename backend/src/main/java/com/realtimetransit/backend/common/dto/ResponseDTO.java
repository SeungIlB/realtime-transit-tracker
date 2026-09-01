package com.realtimetransit.backend.common.dto;

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
public class ResponseDTO<T> {
	private boolean success;
	private String code;
	private String message;
	private T data;

	public static <T> ResponseDTO<T> success(T data) {
		return ResponseDTO.<T>builder()
				.success(true)
				.code("SUCCESS")
				.message("Success")
				.data(data)
				.build();
	}

	public static <T> ResponseDTO<T> failure(String code, String message) {
		return ResponseDTO.<T>builder()
				.success(false)
				.code(code)
				.message(message)
				.build();
	}
}
