package com.jobtrack.reminderservice.reminder;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One message from the tracker, as described in docs/events.md. {@code dueAt} and
 * {@code note} are only present where the event type needs them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReminderEvent(UUID eventId, Type type, Instant occurredAt, long ownerId, long applicationId,
		UUID reminderId, int version, Instant dueAt, String note) {

	public enum Type {

		REMINDER_SCHEDULED, REMINDER_RESCHEDULED, REMINDER_COMPLETED, REMINDER_CANCELLED

	}

	/**
	 * Rejects messages that cannot be applied, so they go to the poison queue instead
	 * of being retried forever.
	 */
	public void validate() {
		if (this.eventId == null || this.type == null || this.reminderId == null || this.occurredAt == null
				|| this.version < 1) {
			throw new IllegalArgumentException("Event is missing required fields");
		}
		if ((this.type == Type.REMINDER_SCHEDULED || this.type == Type.REMINDER_RESCHEDULED) && this.dueAt == null) {
			throw new IllegalArgumentException(this.type + " needs a due time");
		}
	}

}
