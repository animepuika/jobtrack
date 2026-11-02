package com.jobtrack.documents;

/**
 * The CV version does not exist or belongs to someone else. Both look the same.
 */
public class CvNotFoundException extends RuntimeException {

	public CvNotFoundException(long id) {
		super("CV version " + id + " was not found");
	}

}
