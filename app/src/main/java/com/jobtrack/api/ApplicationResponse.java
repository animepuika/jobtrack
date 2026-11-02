package com.jobtrack.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.SourceProvider;
import com.jobtrack.tracking.Stage;
import com.jobtrack.tracking.TrackedJob;

record ApplicationResponse(long id, String title, String company, String location, String sourceUrl,
		SourceProvider source, String externalId, Stage stage, List<Stage> nextStages, LocalDate appliedOn,
		boolean archived, Instant updatedAt) {

	static ApplicationResponse from(JobApplication application) {
		TrackedJob job = application.getJob();
		return new ApplicationResponse(application.getId(), job.getTitle(), job.getCompany(), job.getLocation(),
				job.getSourceUrl(), job.getSourceProvider(), job.getExternalId(), application.getStage(),
				application.getStage().nextStages(), application.getAppliedOn(), application.isArchived(),
				application.getUpdatedAt());
	}

}
