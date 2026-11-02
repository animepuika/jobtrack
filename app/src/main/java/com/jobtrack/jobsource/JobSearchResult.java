package com.jobtrack.jobsource;

import java.util.List;

/**
 * A job found by a search. {@code location} is a best-effort value taken from the
 * provider's display text and may be missing. {@code highlights} are short display
 * strings such as workplace, pay and seniority, exactly as the provider sent them.
 */
public record JobSearchResult(String externalId, String title, String company, String location,
		List<String> highlights, String url) {

}
