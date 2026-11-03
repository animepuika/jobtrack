package com.jobtrack.web;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.jobsource.JobDetails;
import com.jobtrack.jobsource.JobSearchPage;
import com.jobtrack.jobsource.JobSearchQuery;
import com.jobtrack.jobsource.JobSearchQuery.Industry;
import com.jobtrack.jobsource.JobSearchQuery.PayPeriod;
import com.jobtrack.jobsource.JobSearchQuery.Workplace;
import com.jobtrack.jobsource.JobSearchResult;
import com.jobtrack.jobsource.JobSource;
import com.jobtrack.jobsource.JobSourceException;
import com.jobtrack.jobsource.SavedSearch;
import com.jobtrack.jobsource.SavedSearchService;
import com.jobtrack.matching.SkillProfileService;
import com.jobtrack.tracking.SourceProvider;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

	@Autowired
	private SavedSearchService savedSearches;

	@Autowired
	private Clock clock;

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
	void filtersFromTheAddressReachTheQueryAndAreListed() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 3));

		this.mvc
			.perform(get("/search").param("keywords", "java")
				.param("workplace", "REMOTE", "HYBRID")
				.param("posted", "7")
				.param("pay", "2500")
				.param("payPeriod", "MONTHLY")
				.param("skipIndustry", "OIL_GAS")
				.param("hideCompanies", "Sample Staffing, Other Agency")
				.param("distance", "")
				.with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<li>Remote or Hybrid</li>")))
			.andExpect(content().string(containsString("<li>Posted: past week</li>")))
			.andExpect(content().string(containsString("<li>At least $2,500 a month</li>")))
			.andExpect(content().string(containsString("More filters")))
			.andExpect(content().string(containsString(
					"value=\"keywords=java&amp;workplace=REMOTE&amp;workplace=HYBRID&amp;posted=7&amp;pay=2500&amp;payPeriod=MONTHLY&amp;skipIndustry=OIL_GAS&amp;hideCompanies=Sample+Staffing%2C+Other+Agency\"")));
		ArgumentCaptor<JobSearchQuery> query = ArgumentCaptor.forClass(JobSearchQuery.class);
		verify(this.jobSource).search(query.capture());
		assertThat(query.getValue().workplaces()).containsExactly(Workplace.REMOTE, Workplace.HYBRID);
		assertThat(query.getValue().postedWithinDays()).isEqualTo(7);
		assertThat(query.getValue().minPay()).isEqualTo(2500);
		assertThat(query.getValue().payPeriod()).isEqualTo(PayPeriod.MONTHLY);
		assertThat(query.getValue().excludedIndustries()).containsExactly(Industry.OIL_GAS);
		assertThat(query.getValue().excludedCompanies()).containsExactly("Sample Staffing", "Other Agency");
		assertThat(query.getValue().radiusKm()).isNull();
	}

	@Test
	void filtersAloneAreEnoughToSearch() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 3));

		this.mvc.perform(get("/search").param("workplace", "REMOTE").with(user(this.user)))
			.andExpect(content().string(containsString("Junior Java Developer")));
	}

	@Test
	void aFilterThatIsNotUnderstoodIsReportedWithoutSearching() throws Exception {
		this.mvc.perform(get("/search").param("keywords", "java").param("posted", "soon").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("understood: posted. Check them")));
		verify(this.jobSource, times(0)).search(any(JobSearchQuery.class));
	}

	@Test
	void savedSearchesCanBeSavedRenamedRunAndRemoved() throws Exception {
		String query = "keywords=java&workplace=REMOTE";
		this.mvc
			.perform(post("/search/saved").with(user(this.user))
				.with(csrf())
				.param("name", "Remote Java")
				.param("query", query))
			.andExpect(redirectedUrl("/search?" + query))
			.andExpect(flash().attribute("success",
					"Saved as “Remote Java”. Run it any time from the top of this page."));
		this.mvc
			.perform(post("/search/saved").with(user(this.user))
				.with(csrf())
				.param("name", "remote java")
				.param("query", "keywords=java&workplace=HYBRID"))
			.andExpect(flash().attribute("success", "Updated the saved search “remote java”."));

		List<SavedSearch> saved = this.savedSearches.list(this.user.getId());
		assertThat(saved).singleElement().satisfies((search) -> {
			assertThat(search.getName()).isEqualTo("Remote Java");
			assertThat(search.getQuery()).isEqualTo("keywords=java&workplace=HYBRID");
		});
		this.mvc.perform(get("/search").with(user(this.user)))
			.andExpect(content().string(containsString("href=\"/search?keywords=java&amp;workplace=HYBRID\"")));

		JobTrackUser other = this.users.create();
		this.mvc
			.perform(post("/search/saved/{id}/delete", saved.getFirst().getId()).with(user(other)).with(csrf()))
			.andExpect(redirectedUrl("/search"));
		assertThat(this.savedSearches.list(this.user.getId())).hasSize(1);

		this.mvc
			.perform(post("/search/saved/{id}/delete", saved.getFirst().getId()).with(user(this.user)).with(csrf()))
			.andExpect(flash().attribute("success", "Saved search removed."));
		assertThat(this.savedSearches.list(this.user.getId())).isEmpty();
	}

	@Test
	void aSavedQueryMustBePlainAddressParameters() throws Exception {
		this.mvc
			.perform(post("/search/saved").with(user(this.user))
				.with(csrf())
				.param("name", "Odd")
				.param("query", "keywords=java\r\nLocation: https://example.com"))
			.andExpect(redirectedUrl("/search"))
			.andExpect(flash().attribute("error", "This search can't be saved: run a search first"));
		assertThat(this.savedSearches.list(this.user.getId())).isEmpty();
	}

	@Test
	void resultsSayHowOldThePostIsAndCanPutTheNewestFirst() throws Exception {
		LocalDate today = LocalDate.now(this.clock);
		JobSearchResult older = withPosted(RESULT, today.minusDays(20));
		JobSearchResult newer = new JobSearchResult("fixture00000004", "Graduate Developer", "Sample & Sons", "Riga",
				List.of("Riga · Onsite"), "https://hiringcafe.com/job/graduate-developer-fixture00000004",
				"Fictional summary: Java and SQL.", today.minusDays(2));
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(older, newer), 2));

		String html = this.mvc.perform(get("/search").param("keywords", "java").param("sort", "newest")
				.with(user(this.user)))
			.andExpect(content().string(containsString(">2 days ago</time>")))
			.andExpect(content().string(containsString(">2 weeks ago</time>")))
			.andExpect(content().string(containsString("<strong aria-current=\"true\">Newest first</strong>")))
			.andExpect(content().string(containsString("Fictional summary: Java and SQL.")))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html.indexOf("Graduate Developer")).isLessThan(html.indexOf("Junior Java Developer"));
	}

	@Test
	void openingAResultRemembersItAndLaterSearchesMarkIt() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 1));
		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Opened today"))));

		this.mvc
			.perform(get("/search/open").param("id", RESULT.externalId())
				.param("url", RESULT.url())
				.with(user(this.user)))
			.andExpect(redirectedUrl(RESULT.url()));

		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(content().string(containsString("Opened today")))
			.andExpect(content().string(containsString("class=\"result viewed\"")));
		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.users.create())))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Opened today"))));
	}

	@Test
	void openOnlyFollowsHiringCafeLinks() throws Exception {
		this.mvc
			.perform(get("/search/open").param("id", RESULT.externalId())
				.param("url", "https://example.com/job/fixture00000001")
				.with(user(this.user)))
			.andExpect(status().isBadRequest());
		this.mvc
			.perform(get("/search/open").param("id", RESULT.externalId())
				.param("url", "https://hiringcafe.com.example.com/job")
				.with(user(this.user)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void detailsPageShowsEverythingAndCountsAsOpened() throws Exception {
		given(this.jobSource.details(RESULT.externalId())).willReturn(new JobDetails(RESULT.externalId(),
				RESULT.title(), RESULT.company(), RESULT.url(), LocalDate.now(this.clock).minusDays(1),
				new LinkedHashMap<>(Map.of("Compensation", "€2,000 – €2,800/mo")),
				"Fictional summary: Java and Spring Boot.", List.of("About the team", "• Remote Fridays")));

		this.mvc
			.perform(get("/search/jobs/{id}", RESULT.externalId()).param("back", "/search?keywords=java")
				.with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1 id=\"job-title\">Junior Java Developer</h1>")))
			.andExpect(content().string(containsString("€2,000 – €2,800/mo")))
			.andExpect(content().string(containsString("Fictional summary: Java and Spring Boot.")))
			.andExpect(content().string(containsString("• Remote Fridays")))
			.andExpect(content().string(containsString(">yesterday</time>")))
			.andExpect(content().string(containsString("href=\"/search?keywords=java\"")))
			.andExpect(content().string(containsString("Track this job")));

		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 1));
		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(content().string(containsString("Opened today")));
	}

	@Test
	void detailsPageIgnoresABackLinkToAnotherSite() throws Exception {
		given(this.jobSource.details(RESULT.externalId()))
			.willThrow(new JobSourceException("HiringCafe could not be reached."));

		this.mvc
			.perform(get("/search/jobs/{id}", RESULT.externalId()).param("back", "https://example.com/")
				.with(user(this.user)))
			.andExpect(content().string(containsString("The details can't be loaded right now")))
			.andExpect(content().string(containsString("class=\"back-link\" href=\"/search\"")));
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
	void nextPageLeavesOutTheCompaniesAlreadyShown() throws Exception {
		JobSearchResult other = new JobSearchResult("fixture00000003", "Graduate Developer", "Sample & Sons", "Riga",
				List.of("Riga · Onsite"), "https://hiringcafe.com/job/graduate-developer-fixture00000003");
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT, other), 40));

		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(content().string(containsString("of <span>40</span> matches</h2>")))
			.andExpect(content().string(containsString(
					"href=\"/search?keywords=java&amp;seen=Example+Logistics&amp;seen=Sample+%26+Sons&amp;per=2\" rel=\"next\"")))
			.andExpect(content().string(containsString("<span aria-disabled=\"true\">")));

		this.mvc
			.perform(get("/search").param("keywords", "java")
				.param("seen", "Example Logistics", "Sample & Sons")
				.param("per", "2")
				.with(user(this.user)))
			.andExpect(content().string(containsString("matches not shown yet")))
			.andExpect(content().string(containsString("href=\"/search?keywords=java\" rel=\"prev\"")));
		ArgumentCaptor<JobSearchQuery> queries = ArgumentCaptor.forClass(JobSearchQuery.class);
		verify(this.jobSource, times(2)).search(queries.capture());
		assertThat(queries.getAllValues().get(0).excludedCompanies()).isEmpty();
		assertThat(queries.getAllValues().get(1).excludedCompanies()).containsExactly("Example Logistics",
				"Sample & Sons");
	}

	@Test
	void pagingThatDoesNotAddUpStartsAgainAtPageOne() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 40));

		this.mvc
			.perform(get("/search").param("keywords", "java").param("seen", "A", "B").param("per", "5")
				.with(user(this.user)))
			.andExpect(content().string(containsString("of <span>40</span> matches</h2>")));
		ArgumentCaptor<JobSearchQuery> query = ArgumentCaptor.forClass(JobSearchQuery.class);
		verify(this.jobSource).search(query.capture());
		assertThat(query.getValue().excludedCompanies()).isEmpty();
	}

	@Test
	void lastPageHasNoNextLink() throws Exception {
		given(this.jobSource.search(any(JobSearchQuery.class))).willReturn(new JobSearchPage(List.of(RESULT), 1));

		this.mvc.perform(get("/search").param("keywords", "java").with(user(this.user)))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("rel=\"next\""))))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("class=\"pager\""))));
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

	private static JobSearchResult withPosted(JobSearchResult result, LocalDate postedOn) {
		return new JobSearchResult(result.externalId(), result.title(), result.company(), result.location(),
				result.highlights(), result.url(), result.summary(), postedOn);
	}

}
