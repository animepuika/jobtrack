package com.jobtrack.tracking;

/**
 * Optional filters for the application list. {@code null} means "any".
 */
public record ApplicationFilter(Stage stage, String text, boolean includeArchived) {

	public static ApplicationFilter all() {
		return new ApplicationFilter(null, null, false);
	}

}
