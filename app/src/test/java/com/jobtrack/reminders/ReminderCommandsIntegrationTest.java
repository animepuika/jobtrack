package com.jobtrack.reminders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.jobtrack.InMemoryReminderService;
import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.events.OutboxPublisher;
import com.jobtrack.tracking.ApplicationNotFoundException;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@IntegrationTest
class ReminderCommandsIntegrationTest {

	@Autowired
	private ReminderCommands commands;

	@Autowired
	private ReminderReader reader;

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private InMemoryReminderService reminderService;

	@Autowired
	private TrackingService tracking;

	@Autowired
	private TestUsers users;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactions;

	private JobTrackUser user;

	private JobApplication application;

	@BeforeEach
	void setUp() {
		this.user = this.users.create();
		this.application = this.tracking.saveJob(this.user.getId(),
				NewJob.manual("Junior Java Developer", "Example Logistics", "Riga", null));
	}

	@AfterEach
	void resetStandIn() {
		this.reminderService.reset();
		this.publisher.publishDue();
	}

	@Test
	void addingARecordsAVersionOneEventWithTheDueTimeInTheConfiguredZone() {
		LocalDate date = this.tracking.today().plusDays(3);

		UUID id = this.commands.add(this.user.getId(), this.application.getId(), date, LocalTime.of(14, 30),
				"Email the recruiter");

		assertThat(events(id)).singleElement().satisfies((payload) -> {
			assertThat(payload).contains("\"type\":\"REMINDER_SCHEDULED\"", "\"version\":1",
					"\"note\":\"Email the recruiter\"");
			assertThat(payload).contains("\"dueAt\":\"" + date.atTime(14, 30).atZone(ZoneId.of("Europe/Riga")).toInstant() + "\"");
		});
	}

	@Test
	void eachChangeGetsAHigherVersion() {
		UUID id = add();
		this.commands.reschedule(this.user.getId(), id, this.tracking.today().plusDays(5), null);
		this.commands.complete(this.user.getId(), id);
		this.commands.complete(this.user.getId(), id);

		assertThat(events(id)).hasSize(3)
			.satisfiesExactly((first) -> assertThat(first).contains("REMINDER_SCHEDULED", "\"version\":1"),
					(second) -> assertThat(second).contains("REMINDER_RESCHEDULED", "\"version\":2"),
					(third) -> assertThat(third).contains("REMINDER_COMPLETED", "\"version\":3"));
	}

	@Test
	void finishedReminderCannotBeMoved() {
		UUID id = add();
		this.commands.complete(this.user.getId(), id);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.commands.reschedule(this.user.getId(), id, this.tracking.today().plusDays(2), null));
	}

	@Test
	void archivingCancelsOpenRemindersInTheSameTransaction() {
		UUID open = add();
		UUID done = add();
		this.commands.complete(this.user.getId(), done);

		this.tracking.setArchived(this.user.getId(), this.application.getId(), true);

		assertThat(events(open)).last().asString().contains("REMINDER_CANCELLED", "\"version\":2");
		assertThat(events(done)).noneMatch((payload) -> payload.contains("REMINDER_CANCELLED"));
	}

	@Test
	void noEventIsKeptWhenTheChangeIsRolledBack() {
		long before = countEvents();

		assertThatIllegalStateException().isThrownBy(() -> this.transactions.executeWithoutResult((status) -> {
			add();
			throw new IllegalStateException("something later in the same transaction failed");
		}));

		assertThat(countEvents()).isEqualTo(before);
	}

	@Test
	void cannotTouchAnotherUsersApplicationOrReminder() {
		UUID id = add();
		JobTrackUser other = this.users.create();

		assertThatExceptionOfType(ApplicationNotFoundException.class).isThrownBy(() -> this.commands
			.add(other.getId(), this.application.getId(), this.tracking.today(), null, "Sneaky"));
		assertThatExceptionOfType(ReminderNotFoundException.class)
			.isThrownBy(() -> this.commands.complete(other.getId(), id));
	}

	@Test
	void cannotBeInThePast() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.commands.add(this.user.getId(), this.application.getId(),
					this.tracking.today().minusDays(1), null, "Too late"))
			.withMessage("The reminder date can't be in the past.");
	}

	@Test
	void reminderShowsUpOnceTheEventIsDelivered() {
		UUID id = add();
		assertThat(this.reader.forApplication(this.user.getId(), this.application.getId())).isEmpty();
		assertThat(this.commands.pendingChanges(this.user.getId())).isEqualTo(1);

		this.publisher.publishDue();

		assertThat(this.reader.forApplication(this.user.getId(), this.application.getId())).singleElement()
			.satisfies((reminder) -> assertThat(reminder.id()).isEqualTo(id));
		assertThat(this.commands.pendingChanges(this.user.getId())).isZero();
	}

	private UUID add() {
		return this.commands.add(this.user.getId(), this.application.getId(), this.tracking.today().plusDays(1), null,
				"Follow up");
	}

	private List<String> events(UUID reminderId) {
		return this.jdbc.queryForList("select payload from outbox_event where aggregate_id = ? order by created_at, id",
				String.class, reminderId);
	}

	private long countEvents() {
		return this.jdbc.queryForObject("select count(*) from outbox_event where owner_id = ?", Long.class,
				this.user.getId());
	}

}
