package com.jobtrack.jobsource;

import java.util.List;

/**
 * A job search. {@code excludedCompanies} leaves out jobs from those companies:
 * HiringCafe has no page number, so the next page is the same search without the
 * companies already shown.
 */
public record JobSearchQuery(String keywords, String location, Workplace workplace, Seniority seniority,
		List<String> excludedCompanies) {

	public JobSearchQuery {
		if (keywords == null || keywords.isBlank()) {
			throw new IllegalArgumentException("Enter something to search for");
		}
		keywords = keywords.strip();
		location = (location == null || location.isBlank()) ? null : location.strip();
		excludedCompanies = (excludedCompanies == null) ? List.of()
				: excludedCompanies.stream()
					.filter((company) -> company != null && !company.isBlank())
					.map(String::strip)
					.distinct()
					.toList();
	}

	public JobSearchQuery(String keywords, String location, Workplace workplace, Seniority seniority) {
		this(keywords, location, workplace, seniority, List.of());
	}

	public enum Workplace {

		REMOTE("Remote"), HYBRID("Hybrid"), ONSITE("Onsite");

		private final String label;

		Workplace(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}

	}

	public enum Seniority {

		NO_EXPERIENCE("No Prior Experience Required", "No prior experience"), ENTRY("Entry Level", "Entry"),
		MID("Mid Level", "Mid"), SENIOR("Senior Level", "Senior");

		private final String label;

		private final String shortLabel;

		Seniority(String label, String shortLabel) {
			this.label = label;
			this.shortLabel = shortLabel;
		}

		/**
		 * The value HiringCafe expects.
		 */
		public String label() {
			return this.label;
		}

		/**
		 * The name shown on the search form.
		 */
		public String shortLabel() {
			return this.shortLabel;
		}

	}

}
