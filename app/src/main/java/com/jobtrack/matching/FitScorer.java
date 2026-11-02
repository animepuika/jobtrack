package com.jobtrack.matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares a job's text with the user's skills. The job's requirements are the known
 * skills its text mentions, plus any of the user's own typed skills it mentions. The
 * fit is the share of those requirements the user has. Simple on purpose: every
 * result shows exactly which skills matched and which are missing.
 */
public final class FitScorer {

	private FitScorer() {
	}

	public enum Level {

		STRONG("Strong fit"), SOME("Some fit"), LOW("Low fit"), UNKNOWN("Not enough detail");

		private final String label;

		Level(String label) {
			this.label = label;
		}

		public String getLabel() {
			return this.label;
		}

	}

	/**
	 * @param matched the job's skills the user has
	 * @param missing the job's skills the user has not listed
	 * @param percent matched share of the job's skills, 0 to 100
	 */
	public record Fit(Level level, List<String> matched, List<String> missing, int percent) {

		public boolean isKnown() {
			return this.level != Level.UNKNOWN;
		}

	}

	public static Fit score(String jobText, Collection<String> userSkills) {
		Set<String> required = new LinkedHashSet<>(SkillCatalog.find(jobText).keySet());
		for (String skill : userSkills) {
			if (SkillCatalog.canonicalName(skill).isEmpty() && SkillCatalog.mentions(jobText, skill)) {
				required.add(skill);
			}
		}
		if (required.isEmpty()) {
			return new Fit(Level.UNKNOWN, List.of(), List.of(), 0);
		}
		List<String> matched = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (String skill : required) {
			if (userSkills.stream().anyMatch(skill::equalsIgnoreCase)) {
				matched.add(skill);
			}
			else {
				missing.add(skill);
			}
		}
		int percent = Math.round(100f * matched.size() / required.size());
		Level level = (percent >= 60) ? Level.STRONG : (percent >= 30) ? Level.SOME : Level.LOW;
		return new Fit(level, List.copyOf(matched), List.copyOf(missing), percent);
	}

}
