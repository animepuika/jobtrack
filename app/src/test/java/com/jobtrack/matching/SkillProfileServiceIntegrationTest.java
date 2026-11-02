package com.jobtrack.matching;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.documents.CvNotFoundException;
import com.jobtrack.documents.CvService;
import com.jobtrack.documents.CvVersion;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@IntegrationTest
class SkillProfileServiceIntegrationTest {

	@Autowired
	private SkillProfileService skills;

	@Autowired
	private CvService cvs;

	@Autowired
	private TestUsers users;

	private JobTrackUser user;

	@BeforeEach
	void createUser() {
		this.user = this.users.create();
	}

	@Test
	void readsSkillsFromAnUploadedCvStrongestFirst() throws IOException {
		CvVersion cv = upload(List.of("Fictional Person", "Java developer. Java, Spring Boot, Docker.",
				"Projects in Java with PostgreSQL"));

		List<UserSkill> found = this.skills.readFromCv(this.user.getId(), cv.getId());

		assertThat(found).extracting(UserSkill::getName).startsWith("Java")
			.containsExactlyInAnyOrder("Java", "Spring Boot", "Docker", "PostgreSQL");
		assertThat(found.getFirst().getWeight()).isEqualTo(3);
		assertThat(this.skills.profileOf(this.user.getId())).hasValueSatisfying(
				(profile) -> assertThat(profile.getCvLabel()).isEqualTo("Backend CV"));
	}

	@Test
	void readingAgainKeepsSkillsAddedByHand() throws IOException {
		CvVersion cv = upload(List.of("Java and Docker"));
		this.skills.add(this.user.getId(), "Figma");
		this.skills.add(this.user.getId(), "postgres");

		this.skills.readFromCv(this.user.getId(), cv.getId());

		assertThat(this.skills.list(this.user.getId())).extracting(UserSkill::getName)
			.containsExactlyInAnyOrder("Java", "Docker", "Figma", "PostgreSQL");
	}

	@Test
	void removingASkill() {
		this.skills.add(this.user.getId(), "Java");
		this.skills.remove(this.user.getId(), "java");

		assertThat(this.skills.list(this.user.getId())).isEmpty();
	}

	@Test
	void aCvWithoutKnownSkillsIsExplained() throws IOException {
		CvVersion cv = upload(List.of("Fictional Person", "Friendly and reliable"));

		assertThatIllegalArgumentException().isThrownBy(() -> this.skills.readFromCv(this.user.getId(), cv.getId()))
			.withMessageContaining("No known skills");
	}

	@Test
	void anotherUsersCvCannotBeRead() throws IOException {
		CvVersion cv = upload(List.of("Java"));
		JobTrackUser other = this.users.create();

		assertThatExceptionOfType(CvNotFoundException.class)
			.isThrownBy(() -> this.skills.readFromCv(other.getId(), cv.getId()));
	}

	private CvVersion upload(List<String> lines) throws IOException {
		byte[] pdf = TestCvFiles.pdf(lines);
		return this.cvs.upload(this.user.getId(), "Backend CV", "cv.pdf", pdf.length, new ByteArrayInputStream(pdf));
	}

}
