package com.jobtrack.web;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.documents.CvService;
import com.jobtrack.documents.CvVersion;
import com.jobtrack.InMemoryReminderService;
import com.jobtrack.events.OutboxPublisher;
import com.jobtrack.reminders.ReminderCommands;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class FilesAndRemindersIntegrationTest {

	private static final byte[] PDF = "%PDF-1.7\nfictional test CV\n%%EOF".getBytes(StandardCharsets.US_ASCII);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TestUsers users;

	@Autowired
	private TrackingService tracking;

	@Autowired
	private CvService cvs;

	@Autowired
	private ReminderCommands reminders;

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private InMemoryReminderService reminderService;

	private JobTrackUser user;

	private JobApplication application;

	@BeforeEach
	void setUp() {
		this.user = this.users.create();
		this.application = this.tracking.saveJob(this.user.getId(),
				NewJob.manual("Junior Java Developer", "Example Logistics", "Riga", null));
	}

	@AfterEach
	void resetStandIn() {
		this.reminderService.reset();
	}

	@Test
	void uploadingACvNeedsACsrfToken() throws Exception {
		this.mvc.perform(multipart("/cvs").file(pdf()).param("label", "Backend CV").with(user(this.user)))
			.andExpect(status().isForbidden());
	}

	@Test
	void uploadsAndDownloadsACvAsAnAttachment() throws Exception {
		this.mvc.perform(multipart("/cvs").file(pdf()).param("label", "Backend CV").with(user(this.user)).with(csrf()))
			.andExpect(redirectedUrl("/cvs"))
			.andExpect(flash().attribute("success", "CV uploaded. You can now pick it on an application."));
		CvVersion cv = this.cvs.list(this.user.getId()).getFirst();

		this.mvc.perform(get("/cvs/{id}/download", cv.getId()).with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("Content-Disposition", containsString("attachment")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(content().bytes(PDF));
	}

	@Test
	void wrongFileTypeIsExplained() throws Exception {
		MockMultipartFile text = new MockMultipartFile("file", "cv.txt", "text/plain", PDF);

		this.mvc.perform(multipart("/cvs").file(text).param("label", "CV").with(user(this.user)).with(csrf()))
			.andExpect(flash().attribute("error", "Upload a PDF or a Word (.docx) file"));
	}

	@Test
	void anotherUsersCvCannotBeDownloadedOrLinked() throws Exception {
		CvVersion cv = upload();
		JobTrackUser other = this.users.create();
		JobApplication theirs = this.tracking.saveJob(other.getId(), NewJob.manual("Intern", "Sample Health", null, null));

		this.mvc.perform(get("/cvs/{id}/download", cv.getId()).with(user(other))).andExpect(status().isNotFound());
		this.mvc
			.perform(post("/applications/{id}/cv", theirs.getId()).param("cvVersionId", cv.getId().toString())
				.with(user(other))
				.with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void applicationPageShowsTheChosenCv() throws Exception {
		CvVersion cv = upload();

		this.mvc
			.perform(post("/applications/{id}/cv", this.application.getId()).param("cvVersionId", cv.getId().toString())
				.with(user(this.user))
				.with(csrf()))
			.andExpect(redirectedUrl("/applications/" + this.application.getId()));

		this.mvc.perform(get("/applications/{id}", this.application.getId()).with(user(this.user)))
			.andExpect(content().string(containsString("/cvs/" + cv.getId() + "/download")));
	}

	@Test
	void remindersShowOnTheApplicationAndTheDashboard() throws Exception {
		this.mvc
			.perform(post("/applications/{id}/reminders", this.application.getId())
				.param("date", this.tracking.today().plusDays(2).toString())
				.param("time", "10:30")
				.param("note", "Email the recruiter")
				.with(user(this.user))
				.with(csrf()))
			.andExpect(redirectedUrl("/applications/" + this.application.getId()));
		this.mvc.perform(get("/applications/{id}", this.application.getId()).with(user(this.user)))
			.andExpect(content().string(containsString("reminder change is on its way")));

		this.publisher.publishDue();

		this.mvc.perform(get("/applications/{id}", this.application.getId()).with(user(this.user)))
			.andExpect(content().string(containsString("Email the recruiter")))
			.andExpect(content().string(containsString("10:30")));
		this.mvc.perform(get("/").with(user(this.user)))
			.andExpect(content().string(containsString("Coming up")))
			.andExpect(content().string(containsString("Email the recruiter")));
	}

	@Test
	void completingAReminderOnlyRedirectsToLocalPages() throws Exception {
		UUID id = this.reminders.add(this.user.getId(), this.application.getId(), this.tracking.today().plusDays(1),
				null, "Call back");

		this.mvc
			.perform(post("/reminders/{id}/complete", id).param("returnTo", "https://evil.example/phish")
				.with(user(this.user))
				.with(csrf()))
			.andExpect(redirectedUrl("/"));
	}

	@Test
	void archivedBannerShowsTheDate() throws Exception {
		this.tracking.setArchived(this.user.getId(), this.application.getId(), true);

		this.mvc.perform(get("/applications/{id}", this.application.getId()).with(user(this.user)))
			.andExpect(content().string(containsString("Archived on ")));
	}

	@Test
	void exportContainsOnlyTheUsersOwnRecords() throws Exception {
		upload();
		this.reminders.add(this.user.getId(), this.application.getId(), this.tracking.today().plusDays(1), null,
				"Call back");
		this.publisher.publishDue();
		JobTrackUser other = this.users.create();
		this.tracking.saveJob(other.getId(), NewJob.manual("Someone else's job", "Contoso Retail", null, null));

		this.mvc.perform(get("/api/export").with(httpBasic(this.user.getUsername(), TestUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("jobtrack-export-")))
			.andExpect(jsonPath("$.format").value("jobtrack-export-v1"))
			.andExpect(jsonPath("$.remindersIncluded").value(true))
			.andExpect(jsonPath("$.applications.length()").value(1))
			.andExpect(jsonPath("$.applications[0].title").value("Junior Java Developer"))
			.andExpect(jsonPath("$.applications[0].history[0].type").value("CREATED"))
			.andExpect(jsonPath("$.applications[0].reminders[0].note").value("Call back"))
			.andExpect(jsonPath("$.cvVersions[0].label").value("Backend CV"));
	}

	@Test
	void pagesStillWorkWhenTheReminderServiceIsDown() throws Exception {
		this.reminderService.setUnavailable(true);

		this.mvc.perform(get("/applications/{id}", this.application.getId()).with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Reminders can't be loaded right now")))
			.andExpect(content().string(containsString("Junior Java Developer")));
		this.mvc.perform(get("/").with(user(this.user)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Reminders can't be loaded right now")));
		this.mvc.perform(get("/api/export").with(httpBasic(this.user.getUsername(), TestUsers.PASSWORD)))
			.andExpect(jsonPath("$.remindersIncluded").value(false));
	}

	private CvVersion upload() {
		return this.cvs.upload(this.user.getId(), "Backend CV", "cv.pdf", PDF.length, new ByteArrayInputStream(PDF));
	}

	private static MockMultipartFile pdf() {
		return new MockMultipartFile("file", "Backend CV.pdf", "application/pdf", PDF);
	}

}
