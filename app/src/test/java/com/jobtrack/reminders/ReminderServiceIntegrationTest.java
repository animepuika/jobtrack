package com.jobtrack.reminders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.tracking.ApplicationNotFoundException;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@IntegrationTest
class ReminderServiceIntegrationTest {

	@Autowired
	private ReminderService reminders;

	@Autowired
	private TrackingService tracking;

	@Autowired
	private TestUsers users;

	private JobTrackUser user;

	private JobApplication application;

	@BeforeEach
	void setUp() {
		this.user = this.users.create();
		this.application = this.tracking.saveJob(this.user.getId(),
				NewJob.manual("Junior Java Developer", "Example Logistics", "Riga", null));
	}

	@Test
	void storesTheDueTimeInTheConfiguredTimeZone() {
		LocalDate date = this.tracking.today().plusDays(3);

		Reminder reminder = this.reminders.add(this.user.getId(), this.application.getId(), date, LocalTime.of(14, 30),
				"Email the recruiter");

		assertThat(reminder.getDueAt().atZone(ZoneId.of("Europe/Riga")).toLocalDateTime())
			.isEqualTo(date.atTime(14, 30));
	}

	@Test
	void defaultsToNineInTheMorning() {
		LocalDate date = this.tracking.today().plusDays(1);

		Reminder reminder = this.reminders.add(this.user.getId(), this.application.getId(), date, null, "Call back");

		assertThat(reminder.getDueAt().atZone(ZoneId.of("Europe/Riga")).toLocalTime()).isEqualTo(LocalTime.of(9, 0));
	}

	@Test
	void cannotBeAddedToAnotherUsersApplication() {
		JobTrackUser other = this.users.create();

		assertThatExceptionOfType(ApplicationNotFoundException.class).isThrownBy(() -> this.reminders.add(other.getId(),
				this.application.getId(), this.tracking.today(), null, "Sneaky"));
	}

	@Test
	void cannotBeInThePast() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.reminders.add(this.user.getId(), this.application.getId(),
					this.tracking.today().minusDays(1), null, "Too late"))
			.withMessage("The reminder date can't be in the past.");
	}

	@Test
	void upcomingListsOpenRemindersSoonestFirst() {
		LocalDate today = this.tracking.today();
		Reminder later = add(today.plusDays(5), "Later");
		Reminder sooner = add(today.plusDays(1), "Sooner");
		Reminder done = add(today.plusDays(2), "Already done");
		this.reminders.complete(this.user.getId(), done.getId());

		assertThat(this.reminders.upcoming(this.user.getId(), 10)).extracting(Reminder::getId)
			.containsExactly(sooner.getId(), later.getId());
	}

	@Test
	void rescheduleMovesTheDueTime() {
		Reminder reminder = add(this.tracking.today().plusDays(1), "Follow up");
		LocalDate newDate = this.tracking.today().plusDays(10);

		Reminder moved = this.reminders.reschedule(this.user.getId(), reminder.getId(), newDate, LocalTime.of(10, 0));

		assertThat(moved.getDueAt().atZone(ZoneId.of("Europe/Riga")).toLocalDate()).isEqualTo(newDate);
	}

	@Test
	void finishedReminderCannotBeMoved() {
		Reminder reminder = add(this.tracking.today().plusDays(1), "Follow up");
		this.reminders.complete(this.user.getId(), reminder.getId());

		assertThatIllegalArgumentException().isThrownBy(() -> this.reminders.reschedule(this.user.getId(),
				reminder.getId(), this.tracking.today().plusDays(2), null));
	}

	@Test
	void anotherUserCannotCompleteIt() {
		Reminder reminder = add(this.tracking.today().plusDays(1), "Follow up");
		JobTrackUser other = this.users.create();

		assertThatExceptionOfType(ReminderNotFoundException.class)
			.isThrownBy(() -> this.reminders.complete(other.getId(), reminder.getId()));
	}

	private Reminder add(LocalDate date, String note) {
		return this.reminders.add(this.user.getId(), this.application.getId(), date, null, note);
	}

}
