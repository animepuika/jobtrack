package com.jobtrack.reminderservice.reminder;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReminderQueries {

	private final ReminderRepository reminders;

	public ReminderQueries(ReminderRepository reminders) {
		this.reminders = reminders;
	}

	/**
	 * A user's reminders, soonest first. Placeholders whose creation event has not
	 * arrived yet have no due time and are left out.
	 */
	public List<Reminder> find(long ownerId, Long applicationId, ReminderStatus status, int limit) {
		return this.reminders.search(ownerId, applicationId, status, PageRequest.of(0, limit));
	}

}
