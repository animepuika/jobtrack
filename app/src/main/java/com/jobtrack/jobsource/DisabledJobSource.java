package com.jobtrack.jobsource;

import com.jobtrack.tracking.SourceProvider;

/**
 * Used when live search is turned off, for example in the public demo profile.
 */
class DisabledJobSource implements JobSource {

	@Override
	public SourceProvider provider() {
		return SourceProvider.HIRINGCAFE;
	}

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public JobSearchPage search(JobSearchQuery query) {
		throw new JobSourceException("Live job search is turned off in this environment. You can still add jobs by hand.");
	}

}
