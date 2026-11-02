package com.jobtrack.reminders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.jobtrack.tracking.TrackingService;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schedules, moves and completes follow-up reminders. Times are entered and shown in
 * the configured time zone and stored as instants, so they mean the same moment
 * wherever the app runs. Reminders show up in the app; nothing is sent by email or
 * text message.
 */
@Service
@Transactional
public class ReminderService {

	private static final int MAX_NOTE_LENGTH = 500;

	private final ReminderRepository reminders;

	private final TrackingService tracking;

	private final Clock clock;

	public ReminderService(ReminderRepository reminders, TrackingService tracking, Clock clock) {
		this.reminders = reminders;
		this.tracking = tracking;
		this.clock = clock;
	}

	public Reminder add(long ownerId, long applicationId, LocalDate date, LocalTime time, String note) {
		this.tracking.requireOwned(ownerId, applicationId);
		String text = (note == null) ? "" : note.strip();
		if (text.isEmpty() || text.length() > MAX_NOTE_LENGTH) {
			throw new IllegalArgumentException("Say what to do, in up to " + MAX_NOTE_LENGTH + " characters");
		}
		return this.reminders.save(new Reminder(ownerId, applicationId, dueAt(date, time), text, Instant.now(this.clock)));
	}

	public Reminder reschedule(long ownerId, long reminderId, LocalDate date, LocalTime time) {
		Reminder reminder = findOwned(ownerId, reminderId);
		reminder.reschedule(dueAt(date, time));
		return reminder;
	}

	public Reminder complete(long ownerId, long reminderId) {
		Reminder reminder = findOwned(ownerId, reminderId);
		reminder.complete(Instant.now(this.clock));
		return reminder;
	}

	@Transactional(readOnly = true)
	public List<Reminder> forApplication(long ownerId, long applicationId) {
		return this.reminders.findByOwnerIdAndApplicationIdOrderByDueAtAscIdAsc(ownerId, applicationId);
	}

	/**
	 * Open reminders, soonest first, overdue ones included.
	 */
	@Transactional(readOnly = true)
	public List<Reminder> upcoming(long ownerId, int limit) {
		return this.reminders.findByOwnerIdAndCompletedAtIsNullOrderByDueAtAscIdAsc(ownerId, PageRequest.of(0, limit));
	}

	@Transactional(readOnly = true)
	public List<Reminder> all(long ownerId) {
		return this.reminders.findByOwnerIdOrderByDueAtAscIdAsc(ownerId);
	}

	public Instant now() {
		return Instant.now(this.clock);
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

	private Reminder findOwned(long ownerId, long reminderId) {
		return this.reminders.findByIdAndOwnerId(reminderId, ownerId)
			.orElseThrow(() -> new ReminderNotFoundException(reminderId));
	}

}
