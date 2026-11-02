package com.jobtrack.web;

import java.time.Duration;
import java.util.List;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.jobsource.JobSearchPage;
import com.jobtrack.jobsource.JobSearchQuery;
import com.jobtrack.jobsource.JobSearchResult;
import com.jobtrack.jobsource.JobSource;
import com.jobtrack.jobsource.JobSourceException;
import com.jobtrack.matching.SkillProfileService;
import com.jobtrack.tracking.SourceProvider;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The search page with a fake job source, so no live provider is called.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SearchPageIntegrationTest {

	private static final JobSearchResult RESULT = new JobSearchResult("fixture00000001", "Junior Java Developer",
			"Example Logistics", "Riga, Riga", List.of("Riga, Riga · Hybrid", "Full Time · Entry Level"),
			"https://hiringcafe.com/job/junior-java-developer-fixture00000001");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TestUsers users;

	@Autowired
	private SkillProfileService skills;

	@MockitoBean
	private JobSource jobSource;

	private JobTrackUser user;

	@BeforeEach
	void setUp() {
		this.user = this.users.create();
		given(this.jobSource.isEnabled()).willReturn(true);
		given(this.jobSource.provider()).willReturn(SourceProvider.HIRINGCAFE);
	}

	@Test
	void showsResultsWithATrackButton() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 12));

		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Junior Java Developer")))
			.andExpect(content().string(containsString("Riga, Riga · Hybrid")))
			.andExpect(content().string(containsString("</svg>Track</button>")));
	}

	@Test
	void withSkillsResultsShowTheirFitAndTheBestFitComesFirst() throws Exception {
		this.skills.add(this.user.getId(), "Java");
		this.skills.add(this.user.getId(), "Spring Boot");
		JobSearchResult pythonJob = new JobSearchResult("fixture00000002", "Backend Intern", "Sample Health", "Europe",
				List.of("Europe · Remote"), "https://hiringcafe.com/job/backend-intern-fixture00000002",
				"Python, Django and AWS");
		JobSearchResult javaJob = new JobSearchResult(RESULT.externalId(), RESULT.title(), RESULT.company(),
				RESULT.location(), RESULT.highlights(), RESULT.url(), "Java and Spring Boot, Kubernetes is a plus");
		given(this.jobSource.search(any(JobSearchQuery.class)))
			.willReturn(new JobSearchPage(List.of(pythonJob, javaJob), 2));

		String html = this.mvc.perform(get("/search").param("keywords", "developer").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Strong fit")))
			.andExpect(content().string(containsString("Low fit")))
			.andExpect(content().string(containsString("Kubernetes")))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html.indexOf("Junior Java Developer")).isLessThan(html.indexOf("Backend Intern"));
	}

	@Test
	void withoutSkillsThePageSuggestsAddingThem() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 1));

		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(content().string(containsString("Add your skills")))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("fit-level"))));
	}

	@Test
	void trackedResultLinksToTheExistingApplication() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 1));
		track();

		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(content().string(containsString("Tracked · open")));
	}

	@Test
	void trackingTwiceSaysItIsAlreadyTracked() throws Exception {
		track();

		this.mvc
			.perform(post("/search/track").with(user(this.user))
				.with(csrf())
				.param("externalId", RESULT.externalId())
				.param("title", RESULT.title())
				.param("company", RESULT.company())
				.param("url", RESULT.url()))
			.andExpect(flash().attribute("success", "You already track this job."));
	}

	@Test
	void providerFailureKeepsManualEntryAvailable() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class)))
			.willThrow(new JobSourceException("HiringCafe is limiting requests. Try again later.",
					Duration.ofSeconds(30), false, null));

		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("HiringCafe is limiting requests")))
			.andExpect(content().string(containsString("wait <span>30</span> seconds")))
			.andExpect(content().string(containsString("Add a job by hand")));
	}

	private void track() throws Exception {
		this.mvc
			.perform(post("/search/track").with(user(this.user))
				.with(csrf())
				.param("externalId", RESULT.externalId())
				.param("title", RESULT.title())
				.param("company", RESULT.company())
				.param("location", RESULT.location())
				.param("url", RESULT.url()))
			.andExpect(redirectedUrlPattern("/applications/*"))
			.andExpect(flash().attribute("success", "Saved from HiringCafe. It starts in the Saved stage."));
	}

}
