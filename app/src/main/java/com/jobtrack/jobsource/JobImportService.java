package com.jobtrack.jobsource;

import java.util.Optional;
import java.util.regex.Pattern;

import com.jobtrack.tracking.DuplicateJobException;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.SourceProvider;
import com.jobtrack.tracking.TrackingService;

import org.springframework.stereotype.Service;

/**
 * Saves a search result the user chose to track. Saving the same job again returns
 * the existing application instead of creating a second one.
 */
@Service
public class JobImportService {

	private static final Pattern EXTERNAL_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

	private final TrackingService tracking;

	public JobImportService(TrackingService tracking) {
		this.tracking = tracking;
	}

	/**
	 * Not transactional on purpose: each tracking call runs in its own transaction,
	 * so a duplicate found by the database can be caught here and answered with the
	 * existing record.
	 */
	public ImportResult importJob(long ownerId, JobSearchResult selection) {
		if (selection.externalId() == null || !EXTERNAL_ID.matcher(selection.externalId()).matches()) {
			throw new IllegalArgumentException("The selected job has an invalid ID");
		}
		if (selection.url() != null && !selection.url().isBlank()
				&& !selection.url().startsWith("https://hiringcafe.com/")) {
			throw new IllegalArgumentException("The selected job link must point to hiringcafe.com");
		}
		Optional<Long> existing = this.tracking.findTrackedApplicationId(ownerId, SourceProvider.HIRINGCAFE,
				selection.externalId());
		if (existing.isPresent()) {
			return new ImportResult(existing.get(), false);
		}
		try {
			JobApplication application = this.tracking.saveJob(ownerId, new NewJob(selection.title(),
					selection.company(), selection.location(), selection.url(), SourceProvider.HIRINGCAFE,
					selection.externalId()));
			return new ImportResult(application.getId(), true);
		}
		catch (DuplicateJobException ex) {
			return new ImportResult(ex.getExistingApplicationId(), false);
		}
	}

	public record ImportResult(long applicationId, boolean created) {
	}

}
