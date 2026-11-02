package com.jobtrack.reminders;

public class ReminderNotFoundException extends RuntimeException {

	public ReminderNotFoundException(long id) {
		super("Reminder " + id + " was not found");
	}

}
