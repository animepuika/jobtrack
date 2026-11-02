package com.jobtrack.export;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobtrack.documents.CvService;
import com.jobtrack.export.PersonalExport.ApplicationRecord;
import com.jobtrack.export.PersonalExport.CvRecord;
import com.jobtrack.export.PersonalExport.HistoryRecord;
import com.jobtrack.export.PersonalExport.ReminderRecord;
import com.jobtrack.reminders.Reminder;
import com.jobtrack.reminders.ReminderService;
import com.jobtrack.tracking.ApplicationDetails;
import com.jobtrack.tracking.ApplicationFilter;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.TrackedJob;
import com.jobtrack.tracking.TrackingService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collects one user's records from each module into a single export.
 */
@Service
public class ExportService {

	private final TrackingService tracking;

	private final ReminderService reminders;

	private final CvService cvVersions;

	private final Clock clock;

	public ExportService(TrackingService tracking, ReminderService reminders, CvService cvVersions, Clock clock) {
		this.tracking = tracking;
		this.reminders = reminders;
		this.cvVersions = cvVersions;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PersonalExport export(long ownerId, String username) {
		Map<Long, List<Reminder>> remindersByApplication = this.reminders.all(ownerId)
			.stream()
			.collect(Collectors.groupingBy(Reminder::getApplicationId));
		List<ApplicationRecord> applications = this.tracking.list(ownerId, new ApplicationFilter(null, null, true))
			.stream()
			.map((application) -> this.tracking.get(ownerId, application.getId()))
			.map((details) -> toRecord(details,
					remindersByApplication.getOrDefault(details.application().getId(), List.of())))
			.toList();
		List<CvRecord> cvs = this.cvVersions.list(ownerId)
			.stream()
			.map((cv) -> new CvRecord(cv.getId(), cv.getLabel(), cv.getOriginalFilename(), cv.getContentType(),
					cv.getSizeBytes(), cv.getUploadedAt()))
			.toList();
		return new PersonalExport(PersonalExport.FORMAT, Instant.now(this.clock), username, applications, cvs);
	}

	private static ApplicationRecord toRecord(ApplicationDetails details, List<Reminder> reminders) {
		JobApplication application = details.application();
		TrackedJob job = application.getJob();
		return new ApplicationRecord(application.getId(), job.getTitle(), job.getCompany(), job.getLocation(),
				job.getSourceUrl(), job.getSourceProvider(), job.getExternalId(), application.getStage(),
				application.getAppliedOn(), application.isArchived(), application.getArchivedOn(),
				application.getCvVersionId(), application.getCreatedAt(),
				details.history()
					.stream()
					.map((entry) -> new HistoryRecord(entry.getType(), entry.getFromStage(), entry.getToStage(),
							entry.getNote(), entry.getOccurredOn()))
					.toList(),
				reminders.stream()
					.map((reminder) -> new ReminderRecord(reminder.getDueAt(), reminder.getNote(),
							reminder.getCompletedAt()))
					.toList());
	}

}
