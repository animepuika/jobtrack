package com.jobtrack.documents;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.jobtrack.IntegrationTest;
import com.jobtrack.TestUsers;
import com.jobtrack.documents.CvService.CvFile;
import com.jobtrack.user.JobTrackUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@IntegrationTest
class CvServiceIntegrationTest {

	static final byte[] PDF = "%PDF-1.7\nfictional test CV\n%%EOF".getBytes(StandardCharsets.US_ASCII);

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
	void storesAndReturnsTheSameBytes() throws IOException {
		CvVersion cv = upload("Backend CV, Nov 2026", "Backend CV.pdf", PDF);

		assertThat(cv.getContentType()).isEqualTo("application/pdf");
		assertThat(cv.getSizeBytes()).isEqualTo(PDF.length);
		assertThat(this.cvs.list(this.user.getId())).extracting(CvVersion::getLabel).containsExactly("Backend CV, Nov 2026");
		CvFile file = this.cvs.open(this.user.getId(), cv.getId());
		try (InputStream content = file.content()) {
			assertThat(content.readAllBytes()).isEqualTo(PDF);
		}
	}

	@Test
	void anotherUserCannotSeeOrOpenIt() {
		CvVersion cv = upload("Backend CV", "cv.pdf", PDF);
		JobTrackUser other = this.users.create();

		assertThat(this.cvs.list(other.getId())).isEmpty();
		assertThatExceptionOfType(CvNotFoundException.class).isThrownBy(() -> this.cvs.open(other.getId(), cv.getId()));
	}

	@Test
	void onlyAcceptsPdfAndWordFiles() {
		assertThatIllegalArgumentException().isThrownBy(() -> upload("CV", "cv.txt", PDF))
			.withMessage("Upload a PDF or a Word (.docx) file");
	}

	@Test
	void checksTheFileContentNotJustTheName() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> upload("CV", "cv.pdf", "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8)))
			.withMessage("That file doesn't look like a real PDF");
	}

	@Test
	void rejectsFilesOverTheLimit() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.cvs.upload(this.user.getId(), "CV", "cv.pdf", 6 * 1024 * 1024,
					new ByteArrayInputStream(PDF)))
			.withMessage("The file is larger than 5 MB");
	}

	@Test
	void requiresAName() {
		assertThatIllegalArgumentException().isThrownBy(() -> upload("  ", "cv.pdf", PDF));
	}

	@Test
	void keepsOnlyASafeFileName() {
		assertThat(CvService.safeFilename("C:\\Users\\someone\\..\\My CV (final).pdf", CvService.FileType.PDF))
			.isEqualTo("My CV _final_.pdf");
		assertThat(CvService.safeFilename("", CvService.FileType.DOCX)).isEqualTo("cv.docx");
	}

	private CvVersion upload(String label, String filename, byte[] bytes) {
		return this.cvs.upload(this.user.getId(), label, filename, bytes.length, new ByteArrayInputStream(bytes));
	}

}
