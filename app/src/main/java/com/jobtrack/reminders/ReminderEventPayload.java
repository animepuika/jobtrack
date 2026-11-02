package com.jobtrack.reminders;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The message sent to the reminder service. The contract is described in
 * docs/events.md; the reminder service has its own copy of this shape and accepts
 * unknown fields, so either side can add fields first.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ReminderEventPayload(UUID eventId, String type, Instant occurredAt, long ownerId, long applicationId,
		UUID reminderId, int version, Instant dueAt, String note) {

	static final String SCHEDULED = "REMINDER_SCHEDULED";

	static final String RESCHEDULED = "REMINDER_RESCHEDULED";

	static final String COMPLETED = "REMINDER_COMPLETED";

	static final String CANCELLED = "REMINDER_CANCELLED";

}
