package com.realtimetransit.backend.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.realtimetransit.backend.common.dto.ResponseDTO;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ResponseDTO<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus())
				.body(ResponseDTO.failure(errorCode.getCode(), exception.getMessage()));
	}

	@ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ResponseDTO<Void>> handleInvalidWebRequest(Exception exception) {
		ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ResponseDTO.failure(errorCode.getCode(), errorCode.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ResponseDTO<Void>> handleUnexpectedException(Exception exception) {
		log.error("Unhandled request exception", exception);
		ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ResponseDTO.failure(errorCode.getCode(), errorCode.getMessage()));
	}
}
