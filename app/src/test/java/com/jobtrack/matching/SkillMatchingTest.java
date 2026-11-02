package com.jobtrack.matching;

import java.io.IOException;
import java.util.List;

import com.jobtrack.matching.FitScorer.Fit;
import com.jobtrack.matching.FitScorer.Level;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillMatchingTest {

	@Test
	void javaIsNotJavaScript() {
		assertThat(SkillCatalog.find("Frontend role: JavaScript and TypeScript")).containsOnlyKeys("JavaScript",
				"TypeScript");
		assertThat(SkillCatalog.find("Backend in Java 21")).containsOnlyKeys("Java");
	}

	@Test
	void recognisesSymbolsAndVariants() {
		assertThat(SkillCatalog.find("C#, .NET 8 and C++ experience; Postgres; k8s")).containsKeys("C#", ".NET",
				"C++", "PostgreSQL", "Kubernetes");
	}

	@Test
	void wordsOnlyCountInATechnicalPhrase() {
		assertThat(SkillCatalog.find("Start in spring 2027. The rest of the team")).isEmpty();
		assertThat(SkillCatalog.find("Spring Boot microservices with REST APIs")).containsOnlyKeys("Spring Boot",
				"REST APIs", "Microservices");
	}

	@Test
	void countsHowOftenASkillAppears() {
		assertThat(SkillCatalog.find("Java here, Java there, and Docker")).containsEntry("Java", 2)
			.containsEntry("Docker", 1);
	}

	@Test
	void typedSkillsUseTheCatalogName() {
		assertThat(SkillCatalog.canonicalName("postgres")).contains("PostgreSQL");
		assertThat(SkillCatalog.canonicalName("spring boot")).contains("Spring Boot");
		assertThat(SkillCatalog.canonicalName("Figma")).isEmpty();
	}

	@Test
	void fitListsMatchedAndMissingSkills() {
		Fit fit = FitScorer.score("Java, Spring Boot, Kubernetes and Kafka; SQL", List.of("Java", "Spring Boot", "SQL"));

		assertThat(fit.matched()).containsExactly("Java", "SQL", "Spring Boot");
		assertThat(fit.missing()).containsExactly("Kafka", "Kubernetes");
		assertThat(fit.percent()).isEqualTo(60);
		assertThat(fit.level()).isEqualTo(Level.STRONG);
	}

	@Test
	void fitLevels() {
		assertThat(FitScorer.score("Java and Python", List.of("Java")).level()).isEqualTo(Level.SOME);
		assertThat(FitScorer.score("Python, Django, AWS", List.of("Java")).level()).isEqualTo(Level.LOW);
		assertThat(FitScorer.score("A friendly team in Riga", List.of("Java")).level()).isEqualTo(Level.UNKNOWN);
	}

	@Test
	void typedSkillsOutsideTheCatalogAlsoCount() {
		Fit fit = FitScorer.score("Experience with Figma and Java", List.of("Figma", "Java"));

		assertThat(fit.matched()).containsExactlyInAnyOrder("Figma", "Java");
	}

	@Test
	void readsTextFromPdfAndWordFiles() throws IOException {
		byte[] pdf = TestCvFiles.pdf(List.of("Fictional Person", "Skills: Java, Spring Boot, PostgreSQL, Docker"));
		byte[] docx = TestCvFiles.docx(List.of("Fictional Person", "Skills: Kotlin & Git"));

		assertThat(SkillCatalog.find(CvTextExtractor.extract(pdf, "application/pdf"))).containsOnlyKeys("Java",
				"Spring Boot", "PostgreSQL", "Docker");
		assertThat(SkillCatalog.find(CvTextExtractor.extract(docx,
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
			.containsOnlyKeys("Kotlin", "Git");
	}

}
