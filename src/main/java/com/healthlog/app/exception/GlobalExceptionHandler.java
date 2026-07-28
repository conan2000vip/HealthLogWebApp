package com.healthlog.app.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public String handleResponseStatusException(
			ResponseStatusException ex,
			HttpServletRequest request,
			Model model) {
		model.addAttribute("error", ex.getReason());
		return "error/error";
	}

	@ExceptionHandler(Exception.class)
	public String handleException(
			Exception ex,
			Model model) {
		model.addAttribute("error", "システムエラーが発生しました。");
		return "error/error";
	}

}