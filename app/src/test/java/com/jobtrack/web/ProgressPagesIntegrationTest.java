package com.jobtrack.web;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.Stage;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The progress page, the board, and moving applications from them.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ProgressPagesIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TestUsers users;

	@Autowired
	private TrackingService tracking;

	private JobTrackUser user;

	@BeforeEach
	void setUp() {
		this.user = this.users.create();
	}

	@Test
	void progressPageWithoutApplicationsExplainsWhatItWillShow() throws Exception {
		this.mvc.perform(get("/progress").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Nothing to measure yet")));
	}

	@Test
	void progressPageShowsTheFunnelAndTheWeeks() throws Exception {
		JobApplication application = save("Junior Java Developer");
		this.tracking.changeStage(this.user.getId(), application.getId(), Stage.APPLIED, null);
		save("Backend Intern");

		this.mvc.perform(get("/progress").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("How far applications get")))
			.andExpect(content().string(containsString("of 2 tracked jobs")))
			.andExpect(content().string(containsString("This week")))
			.andExpect(content().string(containsString("Show as a table")))
			.andExpect(content().string(containsString("Nothing is waiting on you.")));
	}

	@Test
	void boardShowsOpenApplicationsWithMoveButtonsThatComeBackToTheBoard() throws Exception {
		JobApplication application = save("Junior Java Developer");

		this.mvc.perform(get("/board").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Junior Java Developer")))
			.andExpect(content().string(containsString("aria-label=\"Move Junior Java Developer to Applied\"")))
			.andExpect(content().string(containsString("name=\"returnTo\" value=\"/board\"")));

		this.mvc
			.perform(post("/applications/{id}/stage", application.getId()).with(user(this.user))
				.with(csrf())
				.param("stage", "APPLIED")
				.param("returnTo", "/board"))
			.andExpect(redirectedUrl("/board"))
			.andExpect(flash().attribute("success", "Moved “Junior Java Developer” to Applied."));
		assertThat(this.tracking.get(this.user.getId(), application.getId()).application().getStage())
			.isEqualTo(Stage.APPLIED);
	}

	@Test
	void loggingAFollowUpReturnsOnlyToKnownPages() throws Exception {
		JobApplication application = save("Junior Java Developer");

		this.mvc
			.perform(post("/applications/{id}/notes", application.getId()).with(user(this.user))
				.with(csrf())
				.param("text", "Followed up")
				.param("returnTo", "/"))
			.andExpect(redirectedUrl("/"))
			.andExpect(flash().attribute("success",
					"Noted on “Junior Java Developer”. The follow-up clock starts again."));

		this.mvc
			.perform(post("/applications/{id}/notes", application.getId()).with(user(this.user))
				.with(csrf())
				.param("text", "Followed up")
				.param("returnTo", "https://example.com/"))
			.andExpect(redirectedUrl("/applications/" + application.getId()));
	}

	private JobApplication save(String title) {
		return this.tracking.saveJob(this.user.getId(), NewJob.manual(title, "Example Logistics", "Riga", null));
	}

}
