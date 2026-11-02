package com.jobtrack.reminders;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A reminder as the reminder service reports it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReminderView(UUID id, long applicationId, Instant dueAt, String note, String status,
		Instant completedAt) {

	public boolean isCompleted() {
		return !"OPEN".equals(this.status);
	}

	public boolean isCancelled() {
		return "CANCELLED".equals(this.status);
	}

	public boolean isOverdue(Instant now) {
		return !isCompleted() && this.dueAt != null && this.dueAt.isBefore(now);
	}

}
