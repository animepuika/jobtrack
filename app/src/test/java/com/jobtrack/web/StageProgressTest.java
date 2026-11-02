package com.jobtrack.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.jobtrack.tracking.ApplicationTestData;
import com.jobtrack.tracking.HistoryEntry;
import com.jobtrack.tracking.Stage;
import com.jobtrack.web.StageProgress.Step;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StageProgressTest {

	private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);

	@Test
	void openApplicationShowsTheWholePathWithReachedDates() {
		List<HistoryEntry> history = ApplicationTestData.history(OCT_12, Instant.now(), Stage.APPLIED);

		List<Step> steps = StageProgress.of(Stage.APPLIED, history);

		assertThat(steps).extracting(Step::stage)
			.containsExactly(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER, Stage.ACCEPTED);
		assertThat(steps).extracting(Step::status).containsExactly("done", "current", "todo", "todo", "todo");
		assertThat(steps.get(0).reached()).isEqualTo(OCT_12);
		assertThat(steps.get(1).reached()).isEqualTo(OCT_12.plusDays(1));
		assertThat(steps.get(2).reached()).isNull();
	}

	@Test
	void rejectedApplicationShowsReachedStepsThenTheEnd() {
		List<HistoryEntry> history = ApplicationTestData.history(OCT_12, Instant.now(), Stage.APPLIED,
				Stage.INTERVIEW, Stage.REJECTED);

		List<Step> steps = StageProgress.of(Stage.REJECTED, history);

		assertThat(steps).extracting(Step::stage)
			.containsExactly(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.REJECTED);
		assertThat(steps).extracting(Step::status).containsExactly("done", "done", "done", "end");
		assertThat(steps.getLast().reached()).isEqualTo(OCT_12.plusDays(3));
	}

	@Test
	void savedOnIsTheCreationDate() {
		assertThat(StageProgress.savedOn(ApplicationTestData.history(OCT_12, Instant.now()))).isEqualTo(OCT_12);
	}

}
