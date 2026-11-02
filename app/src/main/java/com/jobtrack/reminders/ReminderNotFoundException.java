package com.jobtrack.reminders;

import java.util.UUID;

public class ReminderNotFoundException extends RuntimeException {

	public ReminderNotFoundException(UUID id) {
		super("Reminder " + id + " was not found");
	}

}
