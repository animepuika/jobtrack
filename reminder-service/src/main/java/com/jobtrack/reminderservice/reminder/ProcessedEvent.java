package com.jobtrack.reminderservice.reminder;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proof that an event was handled. It is saved in the same transaction as the
 * event's effect, so either both exist or neither does.
 */
@Entity
@Table(name = "processed_event")
class ProcessedEvent {

	@Id
	@Column(name = "event_id")
	private UUID eventId;

	@Column(name = "reminder_id", nullable = false)
	private UUID reminderId;

	@Column(nullable = false)
	private String outcome;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected ProcessedEvent() {
		// for JPA
	}

	ProcessedEvent(UUID eventId, UUID reminderId, String outcome, Instant processedAt) {
		this.eventId = eventId;
		this.reminderId = reminderId;
		this.outcome = outcome;
		this.processedAt = processedAt;
	}

}
