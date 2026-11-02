package com.jobtrack.tracking;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JobApplicationTest {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

	private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);

	private final TrackedJob job = new TrackedJob(1L,
			NewJob.manual("Junior Java Developer", "Example Logistics", "Riga", "https://example.com/jobs/101"), NOW);

	@Test
	void startsSavedWithOneHistoryEntry() {
		JobApplication application = new JobApplication(this.job, OCT_20, NOW);

		assertThat(application.getStage()).isEqualTo(Stage.SAVED);
		assertThat(application.getHistory()).singleElement().satisfies((entry) -> {
			assertThat(entry.getType()).isEqualTo(EventType.CREATED);
			assertThat(entry.getOccurredOn()).isEqualTo(OCT_20);
		});
	}

	@Test
	void recordsEachStageChange() {
		JobApplication application = new JobApplication(this.job, OCT_20, NOW);
		application.moveTo(Stage.APPLIED, OCT_20.plusDays(1), NOW);
		application.moveTo(Stage.INTERVIEW, OCT_20.plusDays(8), NOW);

		assertThat(application.getStage()).isEqualTo(Stage.INTERVIEW);
		assertThat(application.getHistory()).extracting(HistoryEntry::getToStage)
			.containsExactly(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW);
		assertThat(application.getAppliedOn()).isEqualTo(OCT_20.plusDays(1));
	}

	@Test
	void refusedChangeLeavesStageAndHistoryUntouched() {
		JobApplication application = new JobApplication(this.job, OCT_20, NOW);

		assertThatExceptionOfType(StageChangeNotAllowedException.class)
			.isThrownBy(() -> application.moveTo(Stage.OFFER, OCT_20, NOW))
			.withMessage("Cannot move from SAVED to OFFER");
		assertThat(application.getStage()).isEqualTo(Stage.SAVED);
		assertThat(application.getHistory()).hasSize(1);
	}

	@Test
	void stageChangeCannotGoBackInTime() {
		JobApplication application = new JobApplication(this.job, OCT_20, NOW);
		application.moveTo(Stage.APPLIED, OCT_20.plusDays(5), NOW);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> application.moveTo(Stage.INTERVIEW, OCT_20.plusDays(2), NOW))
			.withMessageContaining("before the previous one");
		assertThat(application.getStage()).isEqualTo(Stage.APPLIED);
	}

	@Test
	void notesDoNotChangeTheStage() {
		JobApplication application = new JobApplication(this.job, OCT_20, NOW);
		application.addNote("  Recruiter called back  ", OCT_20, NOW);

		assertThat(application.getStage()).isEqualTo(Stage.SAVED);
		assertThat(application.getHistory()).last().satisfies((entry) -> {
			assertThat(entry.getType()).isEqualTo(EventType.NOTE);
			assertThat(entry.getNote()).isEqualTo("Recruiter called back");
		});
	}

	@Test
	void blankNoteIsRejected() {
		JobApplication application = new JobApplication(this.job, OCT_20, NOW);

		assertThatIllegalArgumentException().isThrownBy(() -> application.addNote("   ", OCT_20, NOW));
	}

	@Test
	void historyCannotBeEditedFromOutside() {
		JobApplication application = new JobApplication(this.job, OCT_20, NOW);

		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> application.getHistory().clear());
	}

	@Test
	void jobRejectsBlankTitle() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new TrackedJob(1L, NewJob.manual("  ", "Example Logistics", null, null), NOW))
			.withMessage("Job title must not be blank");
	}

}
