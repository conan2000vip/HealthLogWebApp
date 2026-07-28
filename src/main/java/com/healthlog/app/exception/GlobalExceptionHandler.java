package com.healthlog.app.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public String handleBusinessException(BusinessException ex, Model model) {
		model.addAttribute("error", ex.getMessage());
		return "weight/weight_logs"; //
	}
}