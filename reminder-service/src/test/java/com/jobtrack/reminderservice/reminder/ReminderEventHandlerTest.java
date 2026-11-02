package com.jobtrack.reminderservice.reminder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.jobtrack.reminderservice.TestcontainersConfiguration;
import com.jobtrack.reminderservice.reminder.ReminderEvent.Type;
import com.jobtrack.reminderservice.reminder.ReminderEventHandler.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The delivery problems a queue can cause: the same message twice, messages out of
 * order, and old messages arriving after newer ones.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "reminders.queue.enabled=false")
class ReminderEventHandlerTest {

	private static final AtomicLong OWNERS = new AtomicLong(System.nanoTime());

	private static final Instant DUE = Instant.parse("2026-11-09T08:00:00Z");

	@Autowired
	private ReminderEventHandler handler;

	@Autowired
	private ReminderQueries queries;

	private long owner;

	private UUID reminderId;

	@BeforeEach
	void setUp() {
		this.owner = OWNERS.incrementAndGet();
		this.reminderId = UUID.randomUUID();
	}

	@Test
	void scheduleCreatesAnOpenReminder() {
		assertThat(this.handler.handle(scheduled(1))).isEqualTo(Outcome.APPLIED);

		assertThat(open()).singleElement().satisfies((reminder) -> {
			assertThat(reminder.getNote()).isEqualTo("Email the recruiter");
			assertThat(reminder.getDueAt()).isEqualTo(DUE);
			assertThat(reminder.getVersion()).isEqualTo(1);
		});
	}

	@Test
	void sameEventTwiceHasOneEffect() {
		ReminderEvent event = scheduled(1);

		assertThat(this.handler.handle(event)).isEqualTo(Outcome.APPLIED);
		assertThat(this.handler.handle(event)).isEqualTo(Outcome.DUPLICATE);

		assertThat(all()).hasSize(1);
	}

	@Test
	void olderRescheduleArrivingLateIsIgnored() {
		this.handler.handle(scheduled(1));
		this.handler.handle(event(Type.REMINDER_RESCHEDULED, 3, DUE.plusSeconds(7200), null));

		assertThat(this.handler.handle(event(Type.REMINDER_RESCHEDULED, 2, DUE.plusSeconds(3600), null)))
			.isEqualTo(Outcome.STALE);

		assertThat(open()).singleElement().satisfies((reminder) -> {
			assertThat(reminder.getDueAt()).isEqualTo(DUE.plusSeconds(7200));
			assertThat(reminder.getVersion()).isEqualTo(3);
		});
	}

	@Test
	void lateScheduleCannotResurrectACompletedReminder() {
		this.handler.handle(event(Type.REMINDER_COMPLETED, 2, null, null));

		assertThat(this.handler.handle(scheduled(1))).isEqualTo(Outcome.STALE);

		assertThat(open()).isEmpty();
		assertThat(all()).singleElement().satisfies((reminder) -> {
			assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DONE);
			assertThat(reminder.getNote()).isEqualTo("Email the recruiter");
		});
	}

	@Test
	void rescheduleBeforeScheduleKeepsTheNewTimeAndTheNote() {
		this.handler.handle(event(Type.REMINDER_RESCHEDULED, 2, DUE.plusSeconds(3600), null));
		this.handler.handle(scheduled(1));

		assertThat(open()).singleElement().satisfies((reminder) -> {
			assertThat(reminder.getDueAt()).isEqualTo(DUE.plusSeconds(3600));
			assertThat(reminder.getNote()).isEqualTo("Email the recruiter");
		});
	}

	@Test
	void cancelledReminderLeavesTheOpenList() {
		this.handler.handle(scheduled(1));
		this.handler.handle(event(Type.REMINDER_CANCELLED, 2, null, null));

		assertThat(open()).isEmpty();
		assertThat(all()).singleElement()
			.satisfies((reminder) -> assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.CANCELLED));
	}

	@Test
	void usersOnlySeeTheirOwnReminders() {
		this.handler.handle(scheduled(1));

		assertThat(this.queries.find(this.owner + 1_000_000, null, null, 10)).isEmpty();
	}

	@Test
	void incompleteEventsAreRejected() {
		ReminderEvent noDueTime = event(Type.REMINDER_SCHEDULED, 1, null, "Note");

		assertThatIllegalArgumentException().isThrownBy(() -> this.handler.handle(noDueTime));
	}

	private List<Reminder> open() {
		return this.queries.find(this.owner, null, ReminderStatus.OPEN, 10);
	}

	private List<Reminder> all() {
		return this.queries.find(this.owner, null, null, 10);
	}

	private ReminderEvent scheduled(int version) {
		return event(Type.REMINDER_SCHEDULED, version, DUE, "Email the recruiter");
	}

	private ReminderEvent event(Type type, int version, Instant dueAt, String note) {
		return new ReminderEvent(UUID.randomUUID(), type, Instant.now(), this.owner, 7, this.reminderId, version,
				dueAt, note);
	}

}
