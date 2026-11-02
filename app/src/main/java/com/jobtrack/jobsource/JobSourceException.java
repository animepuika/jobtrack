package com.jobtrack.jobsource;

import java.time.Duration;

/**
 * The provider could not answer. This says nothing about whether a job still
 * exists: a failed request is an unknown state, not a closed listing.
 */
public class JobSourceException extends RuntimeException {

	private final Duration retryAfter;

	private final boolean retryable;

	public JobSourceException(String message) {
		this(message, null, false, null);
	}

	public JobSourceException(String message, Duration retryAfter, boolean retryable, Throwable cause) {
		super(message, cause);
		this.retryAfter = retryAfter;
		this.retryable = retryable;
	}

	/**
	 * How long the provider asked us to wait, or {@code null} if it did not say.
	 */
	public Duration getRetryAfter() {
		return retryAfter;
	}

	boolean isRetryable() {
		return retryable;
	}

}
