package com.jobtrack.api;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ApplicationApiIntegrationTest {

	private static final String NEW_JOB = """
			{"title": "Junior Java Developer", "company": "Example Logistics", "location": "Riga",
			 "sourceUrl": "https://example.com/jobs/101?utm_source=test"}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TestUsers users;

	private JobTrackUser user;

	@BeforeEach
	void createUser() {
		this.user = this.users.create();
	}

	@Test
	void requiresAuthentication() throws Exception {
		this.mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsAWrongPassword() throws Exception {
		this.mvc.perform(get("/api/applications").with(httpBasic(this.user.getUsername(), "wrong-password")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createsAndReadsAnApplication() throws Exception {
		long id = create();

		this.mvc.perform(get("/api/applications/{id}", id).with(auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.application.title").value("Junior Java Developer"))
			.andExpect(jsonPath("$.application.sourceUrl").value("https://example.com/jobs/101"))
			.andExpect(jsonPath("$.application.stage").value("SAVED"))
			.andExpect(jsonPath("$.application.nextStages[0]").value("APPLIED"))
			.andExpect(jsonPath("$.history[0].type").value("CREATED"));
	}

	@Test
	void invalidInputGetsAProblemDetailWithFieldErrors() throws Exception {
		this.mvc
			.perform(post("/api/applications").with(auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"  \", \"company\": \"Example Logistics\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.title").value("Invalid request"))
			.andExpect(jsonPath("$.errors.title").exists());
	}

	@Test
	void duplicateGetsConflictWithTheExistingId() throws Exception {
		long id = create();

		this.mvc.perform(post("/api/applications").with(auth()).contentType(MediaType.APPLICATION_JSON).content(NEW_JOB))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.existingApplicationId").value(id));
	}

	@Test
	void invalidStageChangeGetsConflictWithAllowedStages() throws Exception {
		long id = create();

		this.mvc
			.perform(patch("/api/applications/{id}/stage", id).with(auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stage\": \"OFFER\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Cannot move from SAVED to OFFER"))
			.andExpect(jsonPath("$.allowedStages[0]").value("APPLIED"));
	}

	@Test
	void unknownStageNameIsABadRequest() throws Exception {
		long id = create();

		this.mvc
			.perform(patch("/api/applications/{id}/stage", id).with(auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stage\": \"GHOSTED\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void validStageChangeAndNoteAreRecorded() throws Exception {
		long id = create();

		this.mvc
			.perform(patch("/api/applications/{id}/stage", id).with(auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stage\": \"APPLIED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stage").value("APPLIED"));
		this.mvc
			.perform(post("/api/applications/{id}/notes", id).with(auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\": \"Sent the CV by email\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.history.length()").value(3))
			.andExpect(jsonPath("$.history[2].note").value("Sent the CV by email"));
	}

	@Test
	void anotherUsersApplicationLooksMissing() throws Exception {
		long id = create();
		JobTrackUser other = this.users.create();

		this.mvc.perform(get("/api/applications/{id}", id).with(httpBasic(other.getUsername(), TestUsers.PASSWORD)))
			.andExpect(status().isNotFound());
	}

	@Test
	void searchWhileProviderIsDisabledIsServiceUnavailable() throws Exception {
		this.mvc.perform(get("/api/sources/hiringcafe/jobs").param("keywords", "java").with(auth()))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.title").value("Job search unavailable"));
	}

	private long create() throws Exception {
		MvcResult result = this.mvc
			.perform(post("/api/applications").with(auth()).contentType(MediaType.APPLICATION_JSON).content(NEW_JOB))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andReturn();
		String location = result.getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
		return httpBasic(this.user.getUsername(), TestUsers.PASSWORD);
	}

}
