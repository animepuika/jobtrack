package com.jobtrack.tracking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Builds an application history in memory for tests outside this package, which
 * cannot call the package-private constructors directly.
 */
public final class ApplicationTestData {

	private ApplicationTestData() {
	}

	/**
	 * Saves a fictional job on {@code savedOn} and moves it through {@code stages},
	 * one day apart.
	 */
	public static List<HistoryEntry> history(LocalDate savedOn, Instant now, Stage... stages) {
		TrackedJob job = new TrackedJob(1L, NewJob.manual("Junior Java Developer", "Example Logistics", null, null),
				now);
		JobApplication application = new JobApplication(job, savedOn, now);
		LocalDate date = savedOn;
		for (Stage stage : stages) {
			date = date.plusDays(1);
			application.moveTo(stage, date, now);
		}
		return application.getHistory();
	}

}
