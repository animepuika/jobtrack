package com.jobtrack.jobsource;

import com.jobtrack.tracking.SourceProvider;

/**
 * A place to search for jobs. The tracker only sees these neutral types, so
 * provider-specific responses stay inside the adapter that implements this.
 */
public interface JobSource {

	SourceProvider provider();

	boolean isEnabled();

	/**
	 * Searches the provider.
	 * @throws JobSourceException if the provider is disabled, unavailable or answers
	 * with an error
	 */
	JobSearchPage search(JobSearchQuery query);

}
