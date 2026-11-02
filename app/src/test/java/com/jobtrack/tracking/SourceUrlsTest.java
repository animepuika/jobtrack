package com.jobtrack.tracking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SourceUrlsTest {

	@Test
	void removesTrackingParametersButKeepsOthers() {
		assertThat(SourceUrls.normalize("https://example.com/jobs/101?utm_source=claude&ref=7&UTM_CAMPAIGN=x"))
			.isEqualTo("https://example.com/jobs/101?ref=7");
	}

	@Test
	void lowerCasesSchemeAndHostOnly() {
		assertThat(SourceUrls.normalize("HTTPS://Example.COM/Jobs/101")).isEqualTo("https://example.com/Jobs/101");
	}

	@Test
	void dropsDefaultPortAndFragment() {
		assertThat(SourceUrls.normalize("https://example.com:443/jobs/101#apply")).isEqualTo("https://example.com/jobs/101");
		assertThat(SourceUrls.normalize("http://example.com:8080/jobs")).isEqualTo("http://example.com:8080/jobs");
	}

	@Test
	void sameListingWithDifferentTrackingIsEqual() {
		assertThat(SourceUrls.normalize("https://example.com/jobs/101?utm_medium=email"))
			.isEqualTo(SourceUrls.normalize(" https://example.com/jobs/101 "));
	}

	@Test
	void treatsBlankAsMissing() {
		assertThat(SourceUrls.normalize("  ")).isNull();
		assertThat(SourceUrls.normalize(null)).isNull();
	}

	@Test
	void rejectsNonWebLinks() {
		assertThatIllegalArgumentException().isThrownBy(() -> SourceUrls.normalize("javascript:alert(1)"));
		assertThatIllegalArgumentException().isThrownBy(() -> SourceUrls.normalize("example.com/jobs"));
		assertThatIllegalArgumentException().isThrownBy(() -> SourceUrls.normalize("https://exa mple.com"));
	}

}
