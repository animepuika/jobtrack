package com.jobtrack.reminderservice.reminder;

import java.time.Clock;
import java.time.Instant;

import com.jobtrack.reminderservice.reminder.ReminderEvent.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies events from the tracker. Safe to call more than once with the same event,
 * and safe when events arrive out of order:
 * <ul>
 * <li>an event ID seen before is a duplicate and changes nothing;</li>
 * <li>an event whose version is not newer than the reminder's is stale and changes
 * nothing, so a late "scheduled" cannot bring back a finished reminder;</li>
 * <li>a finishing event for an unknown reminder creates a finished placeholder, so
 * the creation event that arrives later is recognised as stale.</li>
 * </ul>
 */
@Service
public class ReminderEventHandler {

	private static final Logger logger = LoggerFactory.getLogger(ReminderEventHandler.class);

	private final ReminderRepository reminders;

	private final ProcessedEventRepository processedEvents;

	private final Clock clock;

	public ReminderEventHandler(ReminderRepository reminders, ProcessedEventRepository processedEvents, Clock clock) {
		this.reminders = reminders;
		this.processedEvents = processedEvents;
		this.clock = clock;
	}

	public enum Outcome {

		APPLIED, DUPLICATE, STALE

	}

	@Transactional
	public Outcome handle(ReminderEvent event) {
		event.validate();
		if (this.processedEvents.existsById(event.eventId())) {
			logger.info("Ignored duplicate event {} for reminder {}", event.eventId(), event.reminderId());
			return Outcome.DUPLICATE;
		}
		Instant now = Instant.now(this.clock);
		Reminder reminder = this.reminders.findById(event.reminderId())
			.orElseGet(() -> new Reminder(event.reminderId(), event.ownerId(), event.applicationId(), now));
		Outcome outcome = apply(reminder, event, now);
		this.reminders.save(reminder);
		this.processedEvents.save(new ProcessedEvent(event.eventId(), event.reminderId(), outcome.name(), now));
		logger.info("{} {} v{} for reminder {}", outcome, event.type(), event.version(), event.reminderId());
		return outcome;
	}

	private static Outcome apply(Reminder reminder, ReminderEvent event, Instant now) {
		if (event.type() == Type.REMINDER_SCHEDULED) {
			boolean newer = event.version() > reminder.getVersion();
			reminder.fillCreation(event.dueAt(), event.note(), event.version(), now);
			return newer ? Outcome.APPLIED : Outcome.STALE;
		}
		if (event.version() <= reminder.getVersion()) {
			return Outcome.STALE;
		}
		switch (event.type()) {
			case REMINDER_RESCHEDULED -> reminder.reschedule(event.dueAt(), event.version(), now);
			case REMINDER_COMPLETED -> reminder.finish(ReminderStatus.DONE, event.occurredAt(), event.version(), now);
			case REMINDER_CANCELLED ->
				reminder.finish(ReminderStatus.CANCELLED, event.occurredAt(), event.version(), now);
			case REMINDER_SCHEDULED -> throw new IllegalStateException("handled above");
		}
		return Outcome.APPLIED;
	}

}
