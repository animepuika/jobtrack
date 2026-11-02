package com.jobtrack.api;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.jobtrack.reminders.Reminder;
import com.jobtrack.reminders.ReminderService;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reminders, and which CV version an application used.
 */
@RestController
class ReminderApiController {

	private final ReminderService reminders;

	private final TrackingService tracking;

	ReminderApiController(ReminderService reminders, TrackingService tracking) {
		this.reminders = reminders;
		this.tracking = tracking;
	}

	@GetMapping("/api/applications/{id}/reminders")
	List<ReminderResponse> list(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id) {
		this.tracking.requireOwned(user.getId(), id);
		return this.reminders.forApplication(user.getId(), id).stream().map(ReminderResponse::from).toList();
	}

	@PostMapping("/api/applications/{id}/reminders")
	ResponseEntity<ReminderResponse> add(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			@Valid @RequestBody NewReminderRequest request) {
		Reminder reminder = this.reminders.add(user.getId(), id, request.date(), request.time(), request.note());
		return ResponseEntity.created(URI.create("/api/reminders/" + reminder.getId()))
			.body(ReminderResponse.from(reminder));
	}

	@PatchMapping("/api/reminders/{id}")
	ReminderResponse reschedule(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			@Valid @RequestBody RescheduleRequest request) {
		return ReminderResponse.from(this.reminders.reschedule(user.getId(), id, request.date(), request.time()));
	}

	@PostMapping("/api/reminders/{id}/complete")
	ReminderResponse complete(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id) {
		return ReminderResponse.from(this.reminders.complete(user.getId(), id));
	}

	@PutMapping("/api/applications/{id}/cv-version")
	ApplicationResponse useCv(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			@RequestBody CvChoiceRequest request) {
		return ApplicationResponse.from(this.tracking.useCvVersion(user.getId(), id, request.cvVersionId()));
	}

	record NewReminderRequest(@NotNull LocalDate date, LocalTime time, @NotBlank @Size(max = 500) String note) {
	}

	record RescheduleRequest(@NotNull LocalDate date, LocalTime time) {
	}

	record CvChoiceRequest(Long cvVersionId) {
	}

	record ReminderResponse(long id, long applicationId, Instant dueAt, String note, Instant completedAt) {

		static ReminderResponse from(Reminder reminder) {
			return new ReminderResponse(reminder.getId(), reminder.getApplicationId(), reminder.getDueAt(),
					reminder.getNote(), reminder.getCompletedAt());
		}

	}

}
