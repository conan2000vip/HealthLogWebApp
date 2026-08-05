package com.healthlog.app.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

	private final HttpStatus status;
	private final String field;

	public BusinessException(HttpStatus status, String message) {
		this(status, null, message);
	}

	public BusinessException(HttpStatus status, String field, String message) {
		super(message);
		this.status = status;
		this.field = field;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getField() {
		return field;
	}
}