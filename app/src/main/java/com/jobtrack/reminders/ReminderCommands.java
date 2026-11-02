package com.jobtrack.reminders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.jobtrack.events.Outbox;
import com.jobtrack.tracking.ApplicationArchivedEvent;
import com.jobtrack.tracking.TrackingService;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes to reminders, sent to the reminder service as events. Each method checks
 * the rules, updates the tracker's reminder reference and records the event in one
 * transaction. The reminder service applies the event a moment later.
 */
@Service
@Transactional
public class ReminderCommands {

	private static final int MAX_NOTE_LENGTH = 500;

	private final ReminderRefRepository references;

	private final Outbox outbox;

	private final TrackingService tracking;

	private final Clock clock;

	public ReminderCommands(ReminderRefRepository references, Outbox outbox, TrackingService tracking, Clock clock) {
		this.references = references;
		this.outbox = outbox;
		this.tracking = tracking;
		this.clock = clock;
	}

	/**
	 * @return the new reminder's ID, chosen here so it can be referred to before the
	 * reminder service has seen it
	 */
	public UUID add(long ownerId, long applicationId, LocalDate date, LocalTime time, String note) {
		this.tracking.requireOwned(ownerId, applicationId);
		String text = (note == null) ? "" : note.strip();
		if (text.isEmpty() || text.length() > MAX_NOTE_LENGTH) {
			throw new IllegalArgumentException("Say what to do, in up to " + MAX_NOTE_LENGTH + " characters");
		}
		Instant dueAt = dueAt(date, time);
		Instant now = Instant.now(this.clock);
		ReminderRef reference = this.references.save(new ReminderRef(UUID.randomUUID(), ownerId, applicationId, now));
		record(reference, ReminderEventPayload.SCHEDULED, reference.getVersion(), now, dueAt, text);
		return reference.getId();
	}

	public void reschedule(long ownerId, UUID reminderId, LocalDate date, LocalTime time) {
		ReminderRef reference = findOwned(ownerId, reminderId);
		if (!reference.isOpen()) {
			throw new IllegalArgumentException("A finished reminder can't be moved");
		}
		Instant dueAt = dueAt(date, time);
		record(reference, ReminderEventPayload.RESCHEDULED, reference.nextVersion(), Instant.now(this.clock), dueAt,
				null);
	}

	/**
	 * Marks a reminder done. Doing it twice is harmless.
	 */
	public void complete(long ownerId, UUID reminderId) {
		ReminderRef reference = findOwned(ownerId, reminderId);
		if (reference.isOpen()) {
			reference.close();
			record(reference, ReminderEventPayload.COMPLETED, reference.nextVersion(), Instant.now(this.clock), null,
					null);
		}
	}

	/**
	 * Archiving an application cancels its open reminders, in the same transaction as
	 * the archiving itself.
	 */
	@EventListener
	public void onApplicationArchived(ApplicationArchivedEvent event) {
		Instant now = Instant.now(this.clock);
		for (ReminderRef reference : this.references.findByOwnerIdAndApplicationIdAndOpenTrue(event.ownerId(),
				event.applicationId())) {
			reference.close();
			record(reference, ReminderEventPayload.CANCELLED, reference.nextVersion(), now, null, null);
		}
	}

	/**
	 * How many of the user's changes have not reached the reminder service's queue.
	 */
	@Transactional(readOnly = true)
	public long pendingChanges(long ownerId) {
		return this.outbox.pendingFor(ownerId);
	}

	private void record(ReminderRef reference, String type, int version, Instant now, Instant dueAt, String note) {
		UUID eventId = UUID.randomUUID();
		this.outbox.add(eventId, reference.getOwnerId(), type, reference.getId(), new ReminderEventPayload(eventId,
				type, now, reference.getOwnerId(), reference.getApplicationId(), reference.getId(), version, dueAt, note));
	}

	private Instant dueAt(LocalDate date, LocalTime time) {
		if (date == null) {
			throw new IllegalArgumentException("Pick a date for the reminder");
		}
		if (date.isBefore(LocalDate.now(this.clock))) {
			throw new IllegalArgumentException("The reminder date can't be in the past.");
		}
		LocalTime at = (time != null) ? time : LocalTime.of(9, 0);
		return date.atTime(at).atZone(this.clock.getZone()).toInstant();
	}

	private ReminderRef findOwned(long ownerId, UUID reminderId) {
		return this.references.findByIdAndOwnerId(reminderId, ownerId)
			.orElseThrow(() -> new ReminderNotFoundException(reminderId));
	}

}
