package com.jobtrack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jobtrack.events.EventPublisher;
import com.jobtrack.reminders.ReminderReader;
import com.jobtrack.reminders.ReminderView;
import com.jobtrack.reminders.RemindersUnavailableException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plays the queue and the reminder service in tests: published events are applied at
 * once, in memory. It can also be told to fail sends or to be unreachable.
 */
public class InMemoryReminderService implements EventPublisher, ReminderReader {

	private final JsonMapper json = JsonMapper.builder().build();

	private final Map<UUID, ReminderView> reminders = new ConcurrentHashMap<>();

	private final Map<UUID, Integer> versions = new ConcurrentHashMap<>();

	private final Map<UUID, Long> owners = new ConcurrentHashMap<>();

	private final Set<UUID> handledEvents = ConcurrentHashMap.newKeySet();

	private final List<String> sent = Collections.synchronizedList(new ArrayList<>());

	private final AtomicInteger failuresToSimulate = new AtomicInteger();

	private volatile boolean unavailable;

	public void failNextSends(int count) {
		this.failuresToSimulate.set(count);
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public List<String> sent() {
		return List.copyOf(this.sent);
	}

	public void reset() {
		this.failuresToSimulate.set(0);
		this.unavailable = false;
	}

	@Override
	public void send(String message) {
		if (this.failuresToSimulate.getAndUpdate((n) -> Math.max(0, n - 1)) > 0) {
			throw new IllegalStateException("Queue is down (simulated)");
		}
		this.sent.add(message);
		apply(this.json.readValue(message, Event.class));
	}

	private synchronized void apply(Event event) {
		if (!this.handledEvents.add(event.eventId())) {
			return;
		}
		int current = this.versions.getOrDefault(event.reminderId(), 0);
		if (event.version() <= current) {
			return;
		}
		this.versions.put(event.reminderId(), event.version());
		this.owners.put(event.reminderId(), event.ownerId());
		ReminderView before = this.reminders.get(event.reminderId());
		ReminderView after = switch (event.type()) {
			case "REMINDER_SCHEDULED" -> new ReminderView(event.reminderId(), event.applicationId(), event.dueAt(),
					event.note(), "OPEN", null);
			case "REMINDER_RESCHEDULED" -> new ReminderView(event.reminderId(), event.applicationId(), event.dueAt(),
					(before != null) ? before.note() : null, "OPEN", null);
			case "REMINDER_COMPLETED" -> finished(before, event, "DONE");
			case "REMINDER_CANCELLED" -> finished(before, event, "CANCELLED");
			default -> throw new IllegalArgumentException("Unknown event type " + event.type());
		};
		this.reminders.put(event.reminderId(), after);
	}

	private static ReminderView finished(ReminderView before, Event event, String status) {
		return new ReminderView(event.reminderId(), event.applicationId(), (before != null) ? before.dueAt() : null,
				(before != null) ? before.note() : null, status, event.occurredAt());
	}

	@Override
	public List<ReminderView> forApplication(long ownerId, long applicationId) {
		return find(ownerId).stream().filter((r) -> r.applicationId() == applicationId).toList();
	}

	@Override
	public List<ReminderView> upcoming(long ownerId, int limit) {
		return find(ownerId).stream().filter((r) -> !r.isCompleted()).limit(limit).toList();
	}

	@Override
	public List<ReminderView> all(long ownerId) {
		return find(ownerId);
	}

	private List<ReminderView> find(long ownerId) {
		if (this.unavailable) {
			throw new RemindersUnavailableException(new IllegalStateException("Service is down (simulated)"));
		}
		return this.reminders.values()
			.stream()
			.filter((r) -> this.owners.get(r.id()) == ownerId)
			.sorted(Comparator.comparing(ReminderView::dueAt, Comparator.nullsLast(Comparator.naturalOrder())))
			.toList();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Event(UUID eventId, String type, Instant occurredAt, long ownerId, long applicationId, UUID reminderId,
			int version, Instant dueAt, String note) {
	}

}
