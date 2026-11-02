package com.jobtrack.jobsource;

public record JobSearchQuery(String keywords, String location, Workplace workplace, Seniority seniority) {

	public JobSearchQuery {
		if (keywords == null || keywords.isBlank()) {
			throw new IllegalArgumentException("Enter something to search for");
		}
		keywords = keywords.strip();
		location = (location == null || location.isBlank()) ? null : location.strip();
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

		NO_EXPERIENCE("No Prior Experience Required"), ENTRY("Entry Level"), MID("Mid Level"), SENIOR("Senior Level");

		private final String label;

		Seniority(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}

	}

}
