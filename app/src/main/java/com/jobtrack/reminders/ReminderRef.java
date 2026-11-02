package com.jobtrack.reminders;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The tracker's note that a reminder exists. The reminder itself lives in the
 * reminder service; this keeps only what the tracker needs to send correct events:
 * whose it is, which application, the latest event version, and whether it is open.
 */
@Entity
@Table(name = "reminder_ref")
class ReminderRef {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(name = "application_id", nullable = false, updatable = false)
	private Long applicationId;

	@Column(nullable = false)
	private int version;

	@Column(nullable = false)
	private boolean open;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ReminderRef() {
		// for JPA
	}

	ReminderRef(UUID id, long ownerId, long applicationId, Instant now) {
		this.id = id;
		this.ownerId = ownerId;
		this.applicationId = applicationId;
		this.version = 1;
		this.open = true;
		this.createdAt = now;
	}

	/**
	 * Every event about this reminder gets a higher version than the one before.
	 */
	int nextVersion() {
		return ++this.version;
	}

	void close() {
		this.open = false;
	}

	UUID getId() {
		return id;
	}

	Long getOwnerId() {
		return ownerId;
	}

	Long getApplicationId() {
		return applicationId;
	}

	int getVersion() {
		return version;
	}

	boolean isOpen() {
		return open;
	}

}
