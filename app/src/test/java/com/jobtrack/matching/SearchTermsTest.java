package com.jobtrack.matching;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTermsTest {

	@Test
	void prefersLanguagesAndFrameworksOverToolsEvenWhenToolsAreMentionedMore() {
		List<UserSkill> skills = List.of(skill("Git", 5), skill("SQL", 4), skill("Docker", 3), skill("Spring Boot", 2),
				skill("Java", 2), skill("Spring", 2));

		assertThat(SkillCatalog.searchTerms(skills, 2)).containsExactly("Java", "Spring Boot");
	}

	@Test
	void theMostMentionedLanguageComesFirst() {
		List<UserSkill> skills = List.of(skill("Java", 1), skill("Python", 6), skill("React", 2));

		assertThat(SkillCatalog.searchTerms(skills, 2)).containsExactly("Python", "React");
	}

	@Test
	void fallsBackToOtherSkillsWhenThereIsNoLanguageOrFramework() {
		List<UserSkill> skills = List.of(skill("Git", 1), skill("Docker", 3), skill("Kubernetes", 3));

		assertThat(SkillCatalog.searchTerms(skills, 2)).containsExactly("Docker", "Kubernetes");
	}

	private static UserSkill skill(String name, int weight) {
		return new UserSkill(1L, name, weight, "CV");
	}

}
