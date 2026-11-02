package com.jobtrack.tracking;

/**
 * Thrown when an application does not exist or belongs to another user. Both cases
 * look the same, so nobody can find out which IDs other users have.
 */
public class ApplicationNotFoundException extends RuntimeException {

	public ApplicationNotFoundException(long id) {
		super("Application " + id + " was not found");
	}

}
