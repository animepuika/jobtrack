package com.jobtrack.api;

import java.time.LocalDate;

import com.jobtrack.tracking.Stage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The JSON bodies the API accepts. Validation annotations describe what a valid
 * request looks like; Spring checks them before the controller method runs.
 */
final class ApiRequests {

	private ApiRequests() {
	}

	record CreateJobRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 200) String company,
			@Size(max = 200) String location, @Size(max = 2000) String sourceUrl) {
	}

	record UpdateJobRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 200) String company,
			@Size(max = 200) String location) {
	}

	record StageChangeRequest(@NotNull Stage stage, LocalDate date) {
	}

	record NoteRequest(@NotBlank @Size(max = 5000) String text, LocalDate date) {
	}

	record ImportRequest(@NotBlank @Size(max = 100) String externalId, @NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 200) String company, @Size(max = 200) String location,
			@Size(max = 2000) String url) {
	}

}
