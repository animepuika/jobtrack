package com.jobtrack.tracking;

import java.time.LocalDate;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@IntegrationTest
class TrackingServiceIntegrationTest {

	@Autowired
	private TrackingService tracking;

	@Autowired
	private TestUsers users;

	@Autowired
	private JdbcTemplate jdbc;

	private JobTrackUser user;

	@BeforeEach
	void createUser() {
		this.user = this.users.create();
	}

	@Test
	void savedJobStartsAsSavedApplicationWithHistory() {
		JobApplication saved = save("https://example.com/jobs/101");

		ApplicationDetails details = this.tracking.get(this.user.getId(), saved.getId());
		assertThat(details.application().getStage()).isEqualTo(Stage.SAVED);
		assertThat(details.application().getJob().getTitle()).isEqualTo("Junior Java Developer");
		assertThat(details.history()).singleElement()
			.satisfies((entry) -> assertThat(entry.getType()).isEqualTo(EventType.CREATED));
	}

	@Test
	void sameLinkWithDifferentTrackingParametersIsADuplicate() {
		JobApplication first = save("https://example.com/jobs/101?utm_source=newsletter");

		assertThatExceptionOfType(DuplicateJobException.class)
			.isThrownBy(() -> save("https://EXAMPLE.com/jobs/101"))
			.satisfies((ex) -> assertThat(ex.getExistingApplicationId()).isEqualTo(first.getId()));
	}

	@Test
	void anotherUserCanTrackTheSameJob() {
		save("https://example.com/jobs/101");
		JobTrackUser other = this.users.create();

		JobApplication theirs = this.tracking.saveJob(other.getId(),
				NewJob.manual("Junior Java Developer", "Example Logistics", "Riga", "https://example.com/jobs/101"));

		assertThat(theirs.getId()).isNotNull();
	}

	@Test
	void anotherUserCannotReadOrChangeTheApplication() {
		JobApplication mine = save("https://example.com/jobs/101");
		JobTrackUser other = this.users.create();

		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.tracking.get(other.getId(), mine.getId()));
		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.tracking.changeStage(other.getId(), mine.getId(), Stage.APPLIED, null));
		assertThat(this.tracking.list(other.getId(), ApplicationFilter.all())).isEmpty();
	}

	@Test
	void stageChangeIsStoredTogetherWithItsHistoryEntry() {
		JobApplication saved = save("https://example.com/jobs/101");
		LocalDate today = this.tracking.today();

		this.tracking.changeStage(this.user.getId(), saved.getId(), Stage.APPLIED, today);

		ApplicationDetails details = this.tracking.get(this.user.getId(), saved.getId());
		assertThat(details.application().getStage()).isEqualTo(Stage.APPLIED);
		assertThat(details.application().getAppliedOn()).isEqualTo(today);
		assertThat(details.history()).extracting(HistoryEntry::getType)
			.containsExactly(EventType.CREATED, EventType.STAGE_CHANGE);
	}

	@Test
	void refusedStageChangeIsNotStored() {
		JobApplication saved = save("https://example.com/jobs/101");

		assertThatExceptionOfType(StageChangeNotAllowedException.class)
			.isThrownBy(() -> this.tracking.changeStage(this.user.getId(), saved.getId(), Stage.OFFER, null));

		ApplicationDetails details = this.tracking.get(this.user.getId(), saved.getId());
		assertThat(details.application().getStage()).isEqualTo(Stage.SAVED);
		assertThat(details.history()).hasSize(1);
	}

	@Test
	void stageChangeCannotBeDatedInTheFuture() {
		JobApplication saved = save("https://example.com/jobs/101");

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> this.tracking
			.changeStage(this.user.getId(), saved.getId(), Stage.APPLIED, this.tracking.today().plusDays(1)));
	}

	@Test
	void listFiltersByStageTextAndArchive() {
		JobApplication java = save("https://example.com/jobs/101");
		JobApplication intern = this.tracking.saveJob(this.user.getId(),
				NewJob.manual("Backend Intern", "Sample Health", "Remote", null));
		this.tracking.changeStage(this.user.getId(), intern.getId(), Stage.APPLIED, null);
		JobApplication archived = this.tracking.saveJob(this.user.getId(),
				NewJob.manual("Old role", "Contoso Retail", null, null));
		this.tracking.setArchived(this.user.getId(), archived.getId(), true);

		assertThat(ids(new ApplicationFilter(Stage.APPLIED, null, false))).containsExactly(intern.getId());
		assertThat(ids(new ApplicationFilter(null, "LOGISTICS", false))).containsExactly(java.getId());
		assertThat(ids(new ApplicationFilter(null, "remote", false))).containsExactly(intern.getId());
		assertThat(ids(ApplicationFilter.all())).containsExactlyInAnyOrder(java.getId(), intern.getId());
		assertThat(ids(new ApplicationFilter(null, null, true))).contains(archived.getId());
	}

	@Test
	void countsOpenApplicationsByStage() {
		save("https://example.com/jobs/101");
		JobApplication applied = save("https://example.com/jobs/102");
		this.tracking.changeStage(this.user.getId(), applied.getId(), Stage.APPLIED, null);

		assertThat(this.tracking.countByStage(this.user.getId())).containsEntry(Stage.SAVED, 1L)
			.containsEntry(Stage.APPLIED, 1L)
			.containsEntry(Stage.OFFER, 0L);
	}

	@Test
	void databaseRejectsADuplicateImportEvenWithoutTheServiceCheck() {
		String insert = """
				insert into tracked_job (owner_id, title, company, source_provider, external_id)
				values (?, 'Junior Java Developer', 'Example Logistics', 'HIRINGCAFE', 'fixture00000001')
				""";
		this.jdbc.update(insert, this.user.getId());

		assertThatExceptionOfType(DuplicateKeyException.class)
			.isThrownBy(() -> this.jdbc.update(insert, this.user.getId()));
	}

	private JobApplication save(String url) {
		return this.tracking.saveJob(this.user.getId(),
				NewJob.manual("Junior Java Developer", "Example Logistics", "Riga", url));
	}

	private java.util.List<Long> ids(ApplicationFilter filter) {
		return this.tracking.list(this.user.getId(), filter).stream().map(JobApplication::getId).toList();
	}

}
