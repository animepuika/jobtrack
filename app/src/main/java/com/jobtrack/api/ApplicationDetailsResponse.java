package com.jobtrack.api;

import java.time.LocalDate;
import java.util.List;

import com.jobtrack.tracking.ApplicationDetails;
import com.jobtrack.tracking.EventType;
import com.jobtrack.tracking.HistoryEntry;
import com.jobtrack.tracking.Stage;

record ApplicationDetailsResponse(ApplicationResponse application, List<HistoryEntryResponse> history) {

	static ApplicationDetailsResponse from(ApplicationDetails details) {
		return new ApplicationDetailsResponse(ApplicationResponse.from(details.application()),
				details.history().stream().map(HistoryEntryResponse::from).toList());
	}

	record HistoryEntryResponse(long id, EventType type, Stage fromStage, Stage toStage, String note,
			LocalDate occurredOn) {

		static HistoryEntryResponse from(HistoryEntry entry) {
			return new HistoryEntryResponse(entry.getId(), entry.getType(), entry.getFromStage(), entry.getToStage(),
					entry.getNote(), entry.getOccurredOn());
		}

	}

}
