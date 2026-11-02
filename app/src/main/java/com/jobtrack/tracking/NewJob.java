package com.jobtrack.tracking;

/**
 * The details needed to start tracking a job, whether typed in or imported.
 */
public record NewJob(String title, String company, String location, String sourceUrl, SourceProvider provider,
		String externalId) {

	public NewJob {
		if (provider == null) {
			throw new IllegalArgumentException("A job needs a source provider");
		}
		if (provider != SourceProvider.MANUAL && (externalId == null || externalId.isBlank())) {
			throw new IllegalArgumentException("An imported job needs the provider's job ID");
		}
		sourceUrl = SourceUrls.normalize(sourceUrl);
	}

	public static NewJob manual(String title, String company, String location, String sourceUrl) {
		return new NewJob(title, company, location, sourceUrl, SourceProvider.MANUAL, null);
	}

}
