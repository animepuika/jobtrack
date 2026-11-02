package com.jobtrack.api;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jobtrack.reminders.ReminderCommands;
import com.jobtrack.reminders.ReminderReader;
import com.jobtrack.reminders.ReminderView;
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
 * Reminders, and which CV version an application used. Reminder changes are applied
 * by the reminder service a moment later, so they answer 202 Accepted.
 */
@RestController
class ReminderApiController {

	private final ReminderCommands commands;

	private final ReminderReader reader;

	private final TrackingService tracking;

	ReminderApiController(ReminderCommands commands, ReminderReader reader, TrackingService tracking) {
		this.commands = commands;
		this.reader = reader;
		this.tracking = tracking;
	}

	@GetMapping("/api/applications/{id}/reminders")
	List<ReminderView> list(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id) {
		this.tracking.requireOwned(user.getId(), id);
		return this.reader.forApplication(user.getId(), id);
	}

	@PostMapping("/api/applications/{id}/reminders")
	ResponseEntity<Map<String, Object>> add(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			@Valid @RequestBody NewReminderRequest request) {
		UUID reminderId = this.commands.add(user.getId(), id, request.date(), request.time(), request.note());
		return ResponseEntity.accepted()
			.location(URI.create("/api/applications/" + id + "/reminders"))
			.body(Map.of("id", reminderId, "status", "PENDING"));
	}

	@PatchMapping("/api/reminders/{id}")
	ResponseEntity<Void> reschedule(@AuthenticationPrincipal JobTrackUser user, @PathVariable UUID id,
			@Valid @RequestBody RescheduleRequest request) {
		this.commands.reschedule(user.getId(), id, request.date(), request.time());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/api/reminders/{id}/complete")
	ResponseEntity<Void> complete(@AuthenticationPrincipal JobTrackUser user, @PathVariable UUID id) {
		this.commands.complete(user.getId(), id);
		return ResponseEntity.accepted().build();
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

}
