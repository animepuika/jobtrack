package com.jobtrack.events;

import java.util.UUID;

import com.jobtrack.InMemoryReminderService;
import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.reminders.ReminderCommands;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens when the queue is down: events wait, are retried with a growing
 * delay, keep their order, and are all delivered once it is back, including after a
 * restart.
 */
@IntegrationTest
class OutboxPublisherIntegrationTest {

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private InMemoryReminderService queue;

	@Autowired
	private ReminderCommands commands;

	@Autowired
	private TrackingService tracking;

	@Autowired
	private TestUsers users;

	@Autowired
	private JdbcTemplate jdbc;

	private JobTrackUser user;

	private JobApplication application;

	@BeforeEach
	void setUp() {
		this.publisher.publishDue();
		this.user = this.users.create();
		this.application = this.tracking.saveJob(this.user.getId(),
				NewJob.manual("Junior Java Developer", "Example Logistics", null, null));
	}

	@AfterEach
	void reset() {
		this.queue.reset();
		makeEverythingDue();
		this.publisher.publishDue();
	}

	@Test
	void sendsWaitingEventsInOrder() {
		UUID id = add();
		this.commands.complete(this.user.getId(), id);
		int before = this.queue.sent().size();

		assertThat(this.publisher.publishDue()).isEqualTo(2);

		assertThat(this.queue.sent().subList(before, before + 2)).satisfiesExactly(
				(first) -> assertThat(first).contains("REMINDER_SCHEDULED"),
				(second) -> assertThat(second).contains("REMINDER_COMPLETED"));
		assertThat(this.commands.pendingChanges(this.user.getId())).isZero();
	}

	@Test
	void failedSendIsKeptAndRetriedLaterWithoutLaterEventsOvertakingIt() {
		UUID id = add();
		this.commands.complete(this.user.getId(), id);
		this.queue.failNextSends(1);

		assertThat(this.publisher.publishDue()).isZero();

		assertThat(this.commands.pendingChanges(this.user.getId())).isEqualTo(2);
		assertThat(this.jdbc.queryForMap(
				"select attempts, last_error, next_attempt_at > now() as waiting from outbox_event where aggregate_id = ? and event_type = 'REMINDER_SCHEDULED'",
				id))
			.containsEntry("attempts", 1)
			.containsEntry("last_error", "Queue is down (simulated)")
			.containsEntry("waiting", true);

		assertThat(this.publisher.publishDue()).as("not due again yet").isZero();

		makeEverythingDue();
		assertThat(this.publisher.publishDue()).isEqualTo(2);
		assertThat(this.commands.pendingChanges(this.user.getId())).isZero();
	}

	@Test
	void eventsWrittenWhileThePublisherWasStoppedAreSentWhenItRunsAgain() {
		add();
		add();
		add();
		assertThat(this.commands.pendingChanges(this.user.getId())).as("nothing sent while stopped").isEqualTo(3);

		this.publisher.publishDue();

		assertThat(this.commands.pendingChanges(this.user.getId())).isZero();
	}

	private UUID add() {
		return this.commands.add(this.user.getId(), this.application.getId(), this.tracking.today().plusDays(1), null,
				"Follow up");
	}

	private void makeEverythingDue() {
		this.jdbc.update("update outbox_event set next_attempt_at = now() - interval '1 second' where published_at is null");
	}

}
