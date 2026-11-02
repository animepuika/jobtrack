package com.jobtrack.tracking;

import java.util.List;

/**
 * An application together with its history, loaded while the database session is
 * still open so pages and API responses can read both.
 */
public record ApplicationDetails(JobApplication application, List<HistoryEntry> history) {

}
