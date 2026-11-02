package com.jobtrack.events;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An event waiting to be sent, or already sent. Saved in the same transaction as the
 * change it describes.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

	private static final int MAX_ERROR_LENGTH = 500;

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;

	@Column(nullable = false, updatable = false)
	private String payload;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "last_error")
	private String lastError;

	protected OutboxEvent() {
		// for JPA
	}

	OutboxEvent(UUID id, long ownerId, String eventType, UUID aggregateId, String payload, Instant now) {
		this.id = id;
		this.ownerId = ownerId;
		this.eventType = eventType;
		this.aggregateId = aggregateId;
		this.payload = payload;
		this.createdAt = now;
		this.nextAttemptAt = now;
	}

	void markPublished(Instant now) {
		this.publishedAt = now;
		this.attempts++;
		this.lastError = null;
	}

	/**
	 * Waits longer after each failure: 2, 4, 8 … seconds, at most five minutes.
	 */
	void markFailed(String error, Instant now) {
		this.attempts++;
		long seconds = Math.min(300, 1L << Math.min(this.attempts, 9));
		this.nextAttemptAt = now.plus(Duration.ofSeconds(seconds));
		String message = (error != null) ? error : "unknown error";
		this.lastError = (message.length() > MAX_ERROR_LENGTH) ? message.substring(0, MAX_ERROR_LENGTH) : message;
	}

	public UUID getId() {
		return id;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public String getLastError() {
		return lastError;
	}

}
