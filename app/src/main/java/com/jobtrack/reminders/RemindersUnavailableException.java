package com.jobtrack.reminders;

public class RemindersUnavailableException extends RuntimeException {

	public RemindersUnavailableException(Throwable cause) {
		super("The reminder service could not be reached", cause);
	}

}
