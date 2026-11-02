package com.jobtrack.events;

/**
 * Sends one event's JSON to the queue. Throws if it could not be sent; the outbox
 * keeps the event and tries again later.
 */
public interface EventPublisher {

	void send(String json);

}
