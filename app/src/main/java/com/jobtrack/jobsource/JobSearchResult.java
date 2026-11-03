package com.jobtrack.jobsource;

import java.time.LocalDate;
import java.util.List;

/**
 * A job found by a search. {@code location} is a best-effort value taken from the
 * provider's display text and may be missing. {@code highlights} are short display
 * strings such as workplace, pay and seniority, exactly as the provider sent them.
 * {@code summary} is the provider's one-paragraph requirements summary, used to
 * compare the job with the user's skills; it may be missing. {@code postedOn} is the
 * day the job was posted, when the provider says.
 */
public record JobSearchResult(String externalId, String title, String company, String location,
		List<String> highlights, String url, String summary, LocalDate postedOn) {

	public JobSearchResult(String externalId, String title, String company, String location, List<String> highlights,
			String url) {
		this(externalId, title, company, location, highlights, url, null, null);
	}

	public JobSearchResult(String externalId, String title, String company, String location, List<String> highlights,
			String url, String summary) {
		this(externalId, title, company, location, highlights, url, summary, null);
	}

	/**
	 * Everything that describes the job, for matching against skills.
	 */
	public String matchingText() {
		return String.join("\n", this.title, String.join("\n", this.highlights),
				(this.summary != null) ? this.summary : "");
	}

}
