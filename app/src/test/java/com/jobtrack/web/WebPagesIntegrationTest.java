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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class WebPagesIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TestUsers users;

	@Autowired
	private TrackingService tracking;

	private JobTrackUser user;

	@BeforeEach
	void createUser() {
		this.user = this.users.create();
	}

	@Test
	void pagesRedirectToLoginWhenSignedOut() throws Exception {
		this.mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	void loginPageAndStylesheetArePublic() throws Exception {
		this.mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Sign in")));
		this.mvc.perform(get("/css/app.css")).andExpect(status().isOk());
	}

	@Test
	void signsInWithTheRightPasswordOnly() throws Exception {
		this.mvc.perform(formLogin().user(this.user.getUsername()).password(TestUsers.PASSWORD))
			.andExpect(redirectedUrl("/"))
			.andExpect(authenticated());
		this.mvc.perform(formLogin().user(this.user.getUsername()).password("wrong-password"))
			.andExpect(redirectedUrl("/login?error"))
			.andExpect(unauthenticated());
	}

	@Test
	void formsWithoutACsrfTokenAreRejected() throws Exception {
		this.mvc.perform(post("/applications").with(user(this.user)).param("title", "X").param("company", "Y"))
			.andExpect(status().isForbidden());
	}

	@Test
	void addingAJobRedirectsToItsPage() throws Exception {
		this.mvc
			.perform(post("/applications").with(user(this.user))
				.with(csrf())
				.param("title", "Junior Java Developer")
				.param("company", "Example Logistics")
				.param("location", "Riga")
				.param("sourceUrl", "https://example.com/jobs/101"))
			.andExpect(redirectedUrlPattern("/applications/*"))
			.andExpect(flash().attributeExists("success"));

		this.mvc.perform(get("/").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Junior Java Developer")));
	}

	@Test
	void missingTitleShowsTheFormAgainWithLinkedErrors() throws Exception {
		this.mvc
			.perform(post("/applications").with(user(this.user)).with(csrf()).param("title", " ").param("company", "Y"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("One thing needs fixing")))
			.andExpect(content().string(containsString("href=\"#title\"")))
			.andExpect(content().string(containsString("aria-invalid=\"true\"")))
			.andExpect(content().string(containsString("Enter a job title")));
	}

	@Test
	void linkWithoutSchemeIsAFieldError() throws Exception {
		this.mvc
			.perform(post("/applications").with(user(this.user))
				.with(csrf())
				.param("title", "Junior Java Developer")
				.param("company", "Example Logistics")
				.param("sourceUrl", "example.com/jobs/101"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Enter the full link, starting with https://")))
			.andExpect(content().string(containsString("href=\"#sourceUrl\"")));
	}

	@Test
	void duplicateNamesTheExistingJobAndItsStage() throws Exception {
		JobApplication existing = save();

		this.mvc
			.perform(post("/applications").with(user(this.user))
				.with(csrf())
				.param("title", "Junior Java Developer")
				.param("company", "Example Logistics")
				.param("sourceUrl", "https://example.com/jobs/101?utm_source=mail"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("You already track this job.")))
			.andExpect(content().string(containsString("and now at <span>Saved</span>")))
			.andExpect(content().string(containsString("/applications/" + existing.getId())));
	}

	@Test
	void detailPageShowsProgressHistoryAndOnlyAllowedMoves() throws Exception {
		JobApplication application = save();

		this.mvc.perform(get("/applications/{id}", application.getId()).with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Started tracking. Added by hand.")))
			.andExpect(content().string(containsString("<dd>Not yet</dd>")))
			.andExpect(content().string(containsString("aria-current=\"step\"")))
			.andExpect(content().string(containsString("name=\"stage\" value=\"APPLIED\"")))
			.andExpect(content().string(containsString("You sent your application")))
			.andExpect(content().string(not(containsString("value=\"OFFER\""))));
	}

	@Test
	void finalStageExplainsThatNoMoreMovesArePossible() throws Exception {
		JobApplication application = save();
		this.tracking.changeStage(this.user.getId(), application.getId(), Stage.WITHDRAWN, null);

		this.mvc.perform(get("/applications/{id}", application.getId()).with(user(this.user)))
			.andExpect(content().string(containsString("No more moves.")))
			.andExpect(content().string(not(containsString("Update stage"))));
	}

	@Test
	void refusedStageChangeIsExplainedInPlainWords() throws Exception {
		JobApplication application = save();

		this.mvc
			.perform(post("/applications/{id}/stage", application.getId()).with(user(this.user))
				.with(csrf())
				.param("stage", "OFFER"))
			.andExpect(redirectedUrl("/applications/" + application.getId()))
			.andExpect(flash().attribute("error",
					"That move isn't possible: this application is already at Saved, and stages only move forward. Nothing was changed."));
	}

	@Test
	void anotherUsersApplicationIsNotFound() throws Exception {
		JobApplication application = save();
		JobTrackUser other = this.users.create();

		this.mvc.perform(get("/applications/{id}", application.getId()).with(user(other)))
			.andExpect(status().isNotFound())
			.andExpect(content().string(containsString("We can't find that <span>application</span>")));
	}

	@Test
	void newUserSeesTheWelcomePanel() throws Exception {
		this.mvc.perform(get("/").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Welcome to JobTrack")))
			.andExpect(content().string(not(containsString("Recent activity"))));
	}

	@Test
	void dashboardPagesTwentyApplicationsAtATime() throws Exception {
		for (int i = 1; i <= 23; i++) {
			this.tracking.saveJob(this.user.getId(), NewJob.manual("Job " + i, "Example Logistics", null, null));
		}

		this.mvc.perform(get("/").param("page", "2").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Showing <span>21</span> to")))
			.andExpect(content().string(containsString("rel=\"prev\"")))
			.andExpect(content().string(not(containsString("rel=\"next\""))));
		this.mvc.perform(get("/").param("page", "99").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Showing <span>21</span> to")));
	}

	@Test
	void noMatchPanelRepeatsTheFilters() throws Exception {
		save();

		this.mvc.perform(get("/").param("q", "designer").param("stage", "INTERVIEW").with(user(this.user)))
			.andExpect(content().string(containsString("No applications match these filters")))
			.andExpect(content().string(containsString("Nothing in <span>Interview</span> contains “<span>designer</span>”.")));
	}

	@Test
	void currentPageIsMarkedInTheNavigation() throws Exception {
		this.mvc.perform(get("/search").with(user(this.user)))
			.andExpect(content().string(containsString("href=\"/search\" aria-current=\"page\"")));
	}

	@Test
	void searchPageExplainsWhenLiveSearchIsOff() throws Exception {
		this.mvc.perform(get("/search").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Live search is turned off here")));
	}

	private JobApplication save() {
		return this.tracking.saveJob(this.user.getId(),
				NewJob.manual("Junior Java Developer", "Example Logistics", "Riga", "https://example.com/jobs/101"));
	}

}
