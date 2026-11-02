package com.jobtrack.jobsource;

import java.util.List;

/**
 * One page of search results. {@code total} is the provider's count of all matches.
 */
public record JobSearchPage(List<JobSearchResult> results, int total) {

}
