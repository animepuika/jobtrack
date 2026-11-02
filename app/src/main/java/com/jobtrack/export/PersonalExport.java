package com.jobtrack.export;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.jobtrack.tracking.EventType;
import com.jobtrack.tracking.SourceProvider;
import com.jobtrack.tracking.Stage;

/**
 * Everything one user has stored, as plain data for backup or moving elsewhere. CV
 * files themselves are not included, only their details.
 */
public record PersonalExport(String format, Instant exportedAt, String username, List<ApplicationRecord> applications,
		List<CvRecord> cvVersions) {

	public static final String FORMAT = "jobtrack-export-v1";

	public record ApplicationRecord(long id, String title, String company, String location, String sourceUrl,
			SourceProvider source, String externalId, Stage stage, LocalDate appliedOn, boolean archived,
			LocalDate archivedOn, Long cvVersionId, Instant createdAt, List<HistoryRecord> history,
			List<ReminderRecord> reminders) {
	}

	public record HistoryRecord(EventType type, Stage fromStage, Stage toStage, String note, LocalDate occurredOn) {
	}

	public record ReminderRecord(Instant dueAt, String note, Instant completedAt) {
	}

	public record CvRecord(long id, String label, String originalFilename, String contentType, long sizeBytes,
			Instant uploadedAt) {
	}

}
