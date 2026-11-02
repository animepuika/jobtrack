package com.jobtrack.events;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records events for later sending. It must run inside the caller's transaction, so
 * an event is stored if and only if the change it describes is stored.
 */
@Component
public class Outbox {

	private final OutboxRepository events;

	private final JsonMapper json;

	private final Clock clock;

	public Outbox(OutboxRepository events, JsonMapper json, Clock clock) {
		this.events = events;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * @param payload a record with an {@code eventId} equal to {@code eventId}
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void add(UUID eventId, long ownerId, String eventType, UUID aggregateId, Object payload) {
		this.events.save(new OutboxEvent(eventId, ownerId, eventType, aggregateId, this.json.writeValueAsString(payload),
				Instant.now(this.clock)));
	}

	/**
	 * How many of the user's changes have not reached the queue yet.
	 */
	@Transactional(readOnly = true)
	public long pendingFor(long ownerId) {
		return this.events.countByOwnerIdAndPublishedAtIsNull(ownerId);
	}

}
