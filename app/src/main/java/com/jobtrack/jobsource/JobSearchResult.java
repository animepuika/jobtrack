package com.jobtrack.jobsource;

import java.util.List;

/**
 * A job found by a search. {@code location} is a best-effort value taken from the
 * provider's display text and may be missing. {@code highlights} are short display
 * strings such as workplace, pay and seniority, exactly as the provider sent them.
 * {@code summary} is the provider's one-paragraph requirements summary, used to
 * compare the job with the user's skills; it may be missing.
 */
public record JobSearchResult(String externalId, String title, String company, String location,
		List<String> highlights, String url, String summary) {

	public JobSearchResult(String externalId, String title, String company, String location, List<String> highlights,
			String url) {
		this(externalId, title, company, location, highlights, url, null);
	}

	/**
	 * Everything that describes the job, for matching against skills.
	 */
	public String matchingText() {
		return String.join("\n", this.title, String.join("\n", this.highlights),
				(this.summary != null) ? this.summary : "");
	}

}
