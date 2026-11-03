package com.jobtrack.tracking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.jobtrack.tracking.ProgressService.Board;
import com.jobtrack.tracking.ProgressService.FunnelStep;
import com.jobtrack.tracking.ProgressService.Progress;
import com.jobtrack.tracking.ProgressService.WeekCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Progress figures from histories built in memory, with a fixed "today": Wednesday
 * 4 November 2026.
 */
class ProgressServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Europe/Riga");

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

	private static final Instant NOW = TODAY.atTime(12, 0).atZone(ZONE).toInstant();

	private final JobApplicationRepository repository = mock(JobApplicationRepository.class);

	private final List<JobApplication> applications = new ArrayList<>();

	private ProgressService progress;

	@BeforeEach
	void setUp() {
		this.progress = new ProgressService(this.repository, Clock.fixed(NOW, ZONE));
		given(this.repository.findAllWithHistory(1L)).willReturn(this.applications);
	}

	@Test
	void noApplicationsGiveEmptyFiguresWithoutDividingByZero() {
		Progress result = this.progress.progress(1L);

		assertThat(result.total()).isZero();
		assertThat(result.responsePercent()).isNull();
		assertThat(result.averageDaysToReply()).isNull();
		assertThat(result.weeks()).hasSize(8).allSatisfy((week) -> assertThat(week.applied()).isZero());
		assertThat(result.barPercent(0)).isZero();
	}

	@Test
	void funnelCountsEveryStageAnApplicationReachedEvenAfterARejection() {
		job("Saved only", TODAY.minusDays(30));
		job("Rejected after an interview", TODAY.minusDays(30)).moveTo(Stage.APPLIED, TODAY.minusDays(20), NOW);
		this.applications.getLast().moveTo(Stage.INTERVIEW, TODAY.minusDays(10), NOW);
		this.applications.getLast().moveTo(Stage.REJECTED, TODAY.minusDays(5), NOW);
		job("Rejected straight away", TODAY.minusDays(30)).moveTo(Stage.APPLIED, TODAY.minusDays(12), NOW);
		this.applications.getLast().moveTo(Stage.REJECTED, TODAY.minusDays(8), NOW);
		job("Waiting", TODAY.minusDays(3)).moveTo(Stage.APPLIED, TODAY.minusDays(1), NOW);

		Progress result = this.progress.progress(1L);

		assertThat(result.funnel()).extracting(FunnelStep::stage, FunnelStep::reached)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(Stage.SAVED, 4L),
					org.assertj.core.groups.Tuple.tuple(Stage.APPLIED, 3L),
					org.assertj.core.groups.Tuple.tuple(Stage.INTERVIEW, 1L),
					org.assertj.core.groups.Tuple.tuple(Stage.OFFER, 0L),
					org.assertj.core.groups.Tuple.tuple(Stage.ACCEPTED, 0L));
		assertThat(result.funnel().get(1).percentOfPrevious()).isEqualTo(75);
		assertThat(result.funnel().get(2).percentOfPrevious()).isEqualTo(33);
		assertThat(result.heardBack()).isEqualTo(2);
		assertThat(result.responsePercent()).isEqualTo(67);
		assertThat(result.averageDaysToReply()).isEqualTo(7.0);
		assertThat(result.open()).isEqualTo(2);
		assertThat(result.outcomes().get(Stage.REJECTED)).isEqualTo(2);
	}

	@Test
	void weeksRunMondayToSundayAndEndWithThisWeek() {
		job("This week", TODAY.minusDays(20)).moveTo(Stage.APPLIED, TODAY.minusDays(2), NOW);
		job("Last Sunday", TODAY.minusDays(20)).moveTo(Stage.APPLIED, TODAY.minusDays(3), NOW);
		job("Too old", TODAY.minusDays(90)).moveTo(Stage.APPLIED, TODAY.minusDays(70), NOW);

		Progress result = this.progress.progress(1L);

		assertThat(result.weeks().getLast().weekStart()).isEqualTo(LocalDate.of(2026, 11, 2));
		assertThat(result.weeks().getFirst().weekStart()).isEqualTo(LocalDate.of(2026, 9, 14));
		assertThat(result.weeks()).extracting(WeekCount::applied).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 1L, 1L);
		assertThat(result.appliedThisWeek()).isEqualTo(1);
		assertThat(result.barPercent(1)).isEqualTo(100);
	}

	@Test
	void followUpsAreOpenApplicationsWithoutNewsForTwoWeeksAndANoteCountsAsNews() {
		job("Quiet for 20 days", TODAY.minusDays(30)).moveTo(Stage.APPLIED, TODAY.minusDays(20), NOW);
		job("Quiet for exactly 14", TODAY.minusDays(30)).moveTo(Stage.APPLIED, TODAY.minusDays(14), NOW);
		job("Quiet for 13", TODAY.minusDays(30)).moveTo(Stage.APPLIED, TODAY.minusDays(13), NOW);
		job("Followed up", TODAY.minusDays(30)).moveTo(Stage.APPLIED, TODAY.minusDays(20), NOW);
		this.applications.getLast().addNote("Followed up", TODAY.minusDays(2), NOW);
		job("Saved, not applied", TODAY.minusDays(30));
		job("Archived", TODAY.minusDays(30)).moveTo(Stage.APPLIED, TODAY.minusDays(20), NOW);
		this.applications.getLast().setArchived(true, TODAY, NOW);

		assertThat(this.progress.followUps(1L)).extracting((followUp) -> followUp.application().getJob().getTitle())
			.containsExactly("Quiet for 20 days", "Quiet for exactly 14");
		assertThat(this.progress.followUps(1L).getFirst().daysQuiet()).isEqualTo(20);
	}

	@Test
	void boardGroupsOpenApplicationsByStageAndFinishedOnesTogether() {
		job("New", TODAY.minusDays(1));
		job("Applied long ago", TODAY.minusDays(40)).moveTo(Stage.APPLIED, TODAY.minusDays(30), NOW);
		job("Applied recently", TODAY.minusDays(40)).moveTo(Stage.APPLIED, TODAY.minusDays(2), NOW);
		job("Withdrawn", TODAY.minusDays(40)).moveTo(Stage.WITHDRAWN, TODAY.minusDays(4), NOW);
		job("Hidden", TODAY.minusDays(40)).setArchived(true, TODAY, NOW);

		Board board = this.progress.board(1L);

		assertThat(board.columns()).containsOnlyKeys(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER);
		assertThat(board.columns().get(Stage.SAVED)).extracting((card) -> card.application().getJob().getTitle())
			.containsExactly("New");
		assertThat(board.columns().get(Stage.APPLIED)).extracting((card) -> card.application().getJob().getTitle())
			.containsExactly("Applied long ago", "Applied recently");
		assertThat(board.columns().get(Stage.APPLIED).getFirst().daysInStage()).isEqualTo(30);
		assertThat(board.columns().get(Stage.APPLIED).getFirst().needsFollowUp()).isTrue();
		assertThat(board.columns().get(Stage.APPLIED).getLast().needsFollowUp()).isFalse();
		assertThat(board.closed()).extracting((card) -> card.application().getJob().getTitle())
			.containsExactly("Withdrawn");
		assertThat(board.closedTotal()).isEqualTo(1);
	}

	private JobApplication job(String title, LocalDate savedOn) {
		TrackedJob job = new TrackedJob(1L, NewJob.manual(title, "Example Logistics", null, null), NOW);
		JobApplication application = new JobApplication(job, savedOn, NOW);
		ReflectionTestUtils.setField(application, "id", (long) this.applications.size() + 1);
		this.applications.add(application);
		return application;
	}

}
