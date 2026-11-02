package com.jobtrack.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jobtrack.documents.CvNotFoundException;
import com.jobtrack.jobsource.JobSourceException;
import com.jobtrack.reminders.ReminderNotFoundException;
import com.jobtrack.tracking.ApplicationNotFoundException;
import com.jobtrack.tracking.DuplicateJobException;
import com.jobtrack.tracking.StageChangeNotAllowedException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions from the API into RFC 9457 "problem details" JSON, so every
 * error has the same shape: {@code status}, {@code title}, {@code detail}, and
 * sometimes extra fields.
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler
	ProblemDetail handle(ApplicationNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler
	ProblemDetail handle(CvNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler
	ProblemDetail handle(ReminderNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler
	ProblemDetail handle(DuplicateJobException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "Already tracked", ex.getMessage());
		problem.setProperty("existingApplicationId", ex.getExistingApplicationId());
		return problem;
	}

	@ExceptionHandler
	ProblemDetail handle(StageChangeNotAllowedException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "Stage change not allowed", ex.getMessage());
		problem.setProperty("allowedStages", ex.getFrom().nextStages());
		return problem;
	}

	@ExceptionHandler
	ProblemDetail handle(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
	}

	@ExceptionHandler
	ProblemDetail handle(ObjectOptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Changed by another request",
				"The application was changed at the same time. Reload it and try again.");
	}

	@ExceptionHandler
	ProblemDetail handle(DataIntegrityViolationException ex) {
		return problem(HttpStatus.CONFLICT, "Conflicting data", "The change conflicts with an existing record.");
	}

	@ExceptionHandler
	ResponseEntity<ProblemDetail> handle(JobSourceException ex) {
		ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "Job search unavailable", ex.getMessage());
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
		if (ex.getRetryAfter() != null) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()));
		}
		return response.body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Some fields are not valid.");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
