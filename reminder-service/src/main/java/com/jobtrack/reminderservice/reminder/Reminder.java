package com.jobtrack.reminderservice.reminder;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A reminder as this service knows it. {@code version} is the version of the last
 * event applied; events with a lower or equal version are stale and change nothing.
 * The tracker creates the ID, so a reminder can be referred to before this service
 * has seen it.
 */
@Entity
@Table(name = "reminder")
public class Reminder {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(name = "application_id", nullable = false, updatable = false)
	private Long applicationId;

	@Column(name = "due_at")
	private Instant dueAt;

	private String note;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReminderStatus status;

	@Column(nullable = false)
	private int version;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Reminder() {
		// for JPA
	}

	Reminder(UUID id, long ownerId, long applicationId, Instant now) {
		this.id = id;
		this.ownerId = ownerId;
		this.applicationId = applicationId;
		this.status = ReminderStatus.OPEN;
		this.updatedAt = now;
	}

	/**
	 * Fills in what a creation event carries, without undoing anything newer. The
	 * note never changes after creation, so it is always safe to set when missing.
	 */
	void fillCreation(Instant dueAt, String note, int eventVersion, Instant now) {
		if (this.note == null) {
			this.note = note;
		}
		if (eventVersion > this.version) {
			this.dueAt = dueAt;
			this.version = eventVersion;
		}
		else if (this.dueAt == null) {
			this.dueAt = dueAt;
		}
		this.updatedAt = now;
	}

	void reschedule(Instant dueAt, int eventVersion, Instant now) {
		this.dueAt = dueAt;
		this.version = eventVersion;
		this.updatedAt = now;
	}

	void finish(ReminderStatus finalStatus, Instant occurredAt, int eventVersion, Instant now) {
		this.status = finalStatus;
		this.completedAt = occurredAt;
		this.version = eventVersion;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public Long getApplicationId() {
		return applicationId;
	}

	public Instant getDueAt() {
		return dueAt;
	}

	public String getNote() {
		return note;
	}

	public ReminderStatus getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
