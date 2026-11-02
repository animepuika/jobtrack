package com.jobtrack.web;

import com.jobtrack.documents.CvNotFoundException;
import com.jobtrack.reminders.ReminderNotFoundException;
import com.jobtrack.tracking.ApplicationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.support.RequestContextUtils;

@ControllerAdvice(basePackageClasses = WebExceptionHandler.class)
class WebExceptionHandler {

	@ExceptionHandler(ApplicationNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String applicationNotFound(Model model) {
		model.addAttribute("thing", "application");
		return "not-found";
	}

	@ExceptionHandler(CvNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String cvNotFound(Model model) {
		model.addAttribute("thing", "CV");
		return "not-found";
	}

	@ExceptionHandler(ReminderNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String reminderNotFound(Model model) {
		model.addAttribute("thing", "reminder");
		return "not-found";
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	String uploadTooLarge(HttpServletRequest request) {
		RequestContextUtils.getOutputFlashMap(request).put("error", "The file is too large. CVs can be up to 5 MB.");
		return "redirect:/cvs";
	}

}
