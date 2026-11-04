package com.jobtrack.jobsource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageMentionsTest {

	@Test
	void findsCapitalizedLanguageNamesInOrderWithoutDuplicates() {
		assertThat(LanguageMentions.in("Fluent German and English; English documentation; Farsi is a plus."))
			.containsExactly("German", "English", "Persian");
	}

	@Test
	void ignoresLowercaseWordsAndProgrammingLanguages() {
		assertThat(LanguageMentions.in("You polish code in Java, Go and Swift; english is assumed.")).isEmpty();
		assertThat(LanguageMentions.in(null)).isEmpty();
	}

	@Test
	void doesNotMatchInsideOtherWords() {
		assertThat(LanguageMentions.in("Englishness and Germany")).isEmpty();
	}

}
