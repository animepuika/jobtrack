package com.jobtrack.jobsource;

import java.util.List;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.jobsource.JobImportService.ImportResult;
import com.jobtrack.tracking.ApplicationFilter;
import com.jobtrack.tracking.SourceProvider;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@IntegrationTest
class JobImportServiceIntegrationTest {

	private static final JobSearchResult SELECTION = new JobSearchResult("fixture00000001", "Junior Java Developer",
			"Example Logistics", "Riga, Riga", List.of("Riga, Riga · Hybrid"),
			"https://hiringcafe.com/job/junior-java-developer-fixture00000001");

	@Autowired
	private JobImportService imports;

	@Autowired
	private TrackingService tracking;

	@Autowired
	private TestUsers users;

	private JobTrackUser user;

	@BeforeEach
	void createUser() {
		this.user = this.users.create();
	}

	@Test
	void importsASelectedJob() {
		ImportResult result = this.imports.importJob(this.user.getId(), SELECTION);

		assertThat(result.created()).isTrue();
		var job = this.tracking.get(this.user.getId(), result.applicationId()).application().getJob();
		assertThat(job.getSourceProvider()).isEqualTo(SourceProvider.HIRINGCAFE);
		assertThat(job.getExternalId()).isEqualTo("fixture00000001");
		assertThat(job.getLocation()).isEqualTo("Riga, Riga");
	}

	@Test
	void importingTwiceReturnsTheExistingApplication() {
		ImportResult first = this.imports.importJob(this.user.getId(), SELECTION);
		ImportResult second = this.imports.importJob(this.user.getId(), SELECTION);

		assertThat(second.created()).isFalse();
		assertThat(second.applicationId()).isEqualTo(first.applicationId());
		assertThat(this.tracking.list(this.user.getId(), ApplicationFilter.all())).hasSize(1);
	}

	@Test
	void importDoesNotTouchNotesOrStage() {
		ImportResult first = this.imports.importJob(this.user.getId(), SELECTION);
		this.tracking.addNote(this.user.getId(), first.applicationId(), "My private note", null);

		this.imports.importJob(this.user.getId(), SELECTION);

		assertThat(this.tracking.get(this.user.getId(), first.applicationId()).history()).hasSize(2);
	}

	@Test
	void rejectsLinksOutsideHiringCafe() {
		JobSearchResult forged = new JobSearchResult("fixture00000002", "Title", "Company", null, List.of(),
				"https://example.com/elsewhere");

		assertThatIllegalArgumentException().isThrownBy(() -> this.imports.importJob(this.user.getId(), forged));
	}

	@Test
	void rejectsMalformedIds() {
		JobSearchResult forged = new JobSearchResult("../../etc", "Title", "Company", null, List.of(), null);

		assertThatIllegalArgumentException().isThrownBy(() -> this.imports.importJob(this.user.getId(), forged));
	}

}
