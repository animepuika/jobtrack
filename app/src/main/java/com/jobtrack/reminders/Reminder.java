package com.jobtrack.reminders;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A follow-up to do by a certain time. It refers to its application by ID only, so
 * this module does not depend on the tracker's tables.
 */
@Entity
@Table(name = "reminder")
public class Reminder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(name = "application_id", nullable = false, updatable = false)
	private Long applicationId;

	@Column(name = "due_at", nullable = false)
	private Instant dueAt;

	@Column(nullable = false, updatable = false)
	private String note;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	protected Reminder() {
		// for JPA
	}

	Reminder(long ownerId, long applicationId, Instant dueAt, String note, Instant createdAt) {
		this.ownerId = ownerId;
		this.applicationId = applicationId;
		this.dueAt = dueAt;
		this.note = note;
		this.createdAt = createdAt;
	}

	void complete(Instant now) {
		if (this.completedAt == null) {
			this.completedAt = now;
		}
	}

	void reschedule(Instant newDueAt) {
		if (this.completedAt != null) {
			throw new IllegalArgumentException("A finished reminder can't be moved");
		}
		this.dueAt = newDueAt;
	}

	public boolean isOverdue(Instant now) {
		return this.completedAt == null && this.dueAt.isBefore(now);
	}

	public Long getId() {
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

	public Instant getCompletedAt() {
		return completedAt;
	}

	public boolean isCompleted() {
		return completedAt != null;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
