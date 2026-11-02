package com.jobtrack.web;

import com.jobtrack.tracking.ApplicationNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice(basePackageClasses = WebExceptionHandler.class)
class WebExceptionHandler {

	@ExceptionHandler(ApplicationNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound() {
		return "not-found";
	}

}
