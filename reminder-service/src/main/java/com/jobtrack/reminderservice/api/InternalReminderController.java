package com.jobtrack.reminderservice.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jobtrack.reminderservice.reminder.Reminder;
import com.jobtrack.reminderservice.reminder.ReminderQueries;
import com.jobtrack.reminderservice.reminder.ReminderStatus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read access for the tracker. The tracker has already checked who the user is and
 * always says whose reminders it wants.
 */
@RestController
class InternalReminderController {

	private static final int MAX_LIMIT = 500;

	private final ReminderQueries queries;

	InternalReminderController(ReminderQueries queries) {
		this.queries = queries;
	}

	@GetMapping("/internal/reminders")
	List<ReminderView> list(@RequestParam long ownerId, @RequestParam(required = false) Long applicationId,
			@RequestParam(required = false) ReminderStatus status, @RequestParam(defaultValue = "100") int limit) {
		return this.queries.find(ownerId, applicationId, status, Math.clamp(limit, 1, MAX_LIMIT))
			.stream()
			.map(ReminderView::from)
			.toList();
	}

	record ReminderView(UUID id, long applicationId, Instant dueAt, String note, ReminderStatus status,
			Instant completedAt, int version) {

		static ReminderView from(Reminder reminder) {
			return new ReminderView(reminder.getId(), reminder.getApplicationId(), reminder.getDueAt(),
					reminder.getNote(), reminder.getStatus(), reminder.getCompletedAt(), reminder.getVersion());
		}

	}

}
