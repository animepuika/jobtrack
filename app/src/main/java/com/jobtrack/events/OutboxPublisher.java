package com.jobtrack.events;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends waiting events to the queue in the order they were created.
 * <p>
 * Delivery is at least once: if the app stops after sending but before saving
 * "published", the event is sent again after restart. The reminder service ignores
 * event IDs it has already handled, so a repeat has no effect.
 */
@Component
public class OutboxPublisher {

	private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

	private static final int BATCH = 50;

	private final OutboxRepository events;

	private final EventPublisher publisher;

	private final Clock clock;

	public OutboxPublisher(OutboxRepository events, EventPublisher publisher, Clock clock) {
		this.events = events;
		this.publisher = publisher;
		this.clock = clock;
	}

	/**
	 * Sends one batch of due events. Stops at the first failure so later events do
	 * not overtake earlier ones; the failed event is retried after a growing delay.
	 * @return how many events were sent
	 */
	@Transactional
	public int publishDue() {
		Instant now = Instant.now(this.clock);
		List<OutboxEvent> due = this.events.lockDue(now, BATCH);
		int sent = 0;
		for (OutboxEvent event : due) {
			try {
				this.publisher.send(event.getPayload());
				event.markPublished(now);
				sent++;
			}
			catch (RuntimeException ex) {
				event.markFailed(ex.getMessage(), now);
				logger.warn("Could not send event {} (attempt {}), will retry after {}: {}", event.getId(),
						event.getAttempts(), event.getNextAttemptAt(), ex.getMessage());
				break;
			}
		}
		return sent;
	}

	@Transactional(readOnly = true)
	public long pendingCount() {
		return this.events.countByPublishedAtIsNull();
	}

}
