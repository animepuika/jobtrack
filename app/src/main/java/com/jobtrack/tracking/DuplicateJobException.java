package com.jobtrack.tracking;

public class DuplicateJobException extends RuntimeException {

	private final long existingApplicationId;

	public DuplicateJobException(long existingApplicationId) {
		super("This job is already tracked");
		this.existingApplicationId = existingApplicationId;
	}

	public long getExistingApplicationId() {
		return existingApplicationId;
	}

}
