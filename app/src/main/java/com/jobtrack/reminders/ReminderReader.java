package com.jobtrack.reminders;

import java.util.List;

/**
 * Reads reminders from the reminder service. Every method throws
 * {@link RemindersUnavailableException} when the service cannot be reached; pages
 * then show the rest of their content without reminders.
 */
public interface ReminderReader {

	List<ReminderView> forApplication(long ownerId, long applicationId);

	/**
	 * Open reminders, soonest first, overdue ones included.
	 */
	List<ReminderView> upcoming(long ownerId, int limit);

	List<ReminderView> all(long ownerId);

}
