package com.jobtrack.jobsource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the provider says about one job. {@code facts} are labelled values such
 * as "Compensation" or "Technical tools", in the provider's order. The description is
 * split into paragraphs of plain text; nothing in it is treated as HTML.
 */
public record JobDetails(String externalId, String title, String company, String url, LocalDate postedOn,
		Map<String, String> facts, String requirementsSummary, List<String> description) {

	/**
	 * The text used to compare the job with the user's skills.
	 */
	public String matchingText() {
		return String.join("\n", this.title, String.join("\n", this.facts.values()),
				(this.requirementsSummary != null) ? this.requirementsSummary : "");
	}

}
