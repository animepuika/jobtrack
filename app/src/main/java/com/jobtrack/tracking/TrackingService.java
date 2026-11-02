package com.jobtrack.tracking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tracker's use cases. Every method takes the ID of the signed-in user and only
 * ever reads or changes that user's records.
 */
@Service
@Transactional
public class TrackingService {

	private final TrackedJobRepository jobs;

	private final JobApplicationRepository applications;

	private final Clock clock;

	public TrackingService(TrackedJobRepository jobs, JobApplicationRepository applications, Clock clock) {
		this.jobs = jobs;
		this.applications = applications;
		this.clock = clock;
	}

	public JobApplication saveJob(long ownerId, NewJob newJob) {
		Optional<TrackedJob> existing = findExisting(ownerId, newJob);
		if (existing.isPresent()) {
			JobApplication application = this.applications.findByJobId(existing.get().getId()).orElseThrow();
			throw new DuplicateJobException(application.getId());
		}
		Instant now = Instant.now(this.clock);
		TrackedJob job = new TrackedJob(ownerId, newJob, now);
		return this.applications.save(new JobApplication(job, today(), now));
	}

	@Transactional(readOnly = true)
	public Optional<Long> findTrackedApplicationId(long ownerId, SourceProvider provider, String externalId) {
		return this.jobs.findByOwnerIdAndSourceProviderAndExternalId(ownerId, provider, externalId)
			.flatMap((job) -> this.applications.findByJobId(job.getId()))
			.map(JobApplication::getId);
	}

	@Transactional(readOnly = true)
	public Map<String, Long> findTrackedApplicationIds(long ownerId, SourceProvider provider,
			Collection<String> externalIds) {
		if (externalIds.isEmpty()) {
			return Map.of();
		}
		return this.applications.findTrackedExternalJobs(ownerId, provider, externalIds)
			.stream()
			.collect(Collectors.toMap(TrackedExternalJob::externalId, TrackedExternalJob::applicationId));
	}

	@Transactional(readOnly = true)
	public List<JobApplication> list(long ownerId, ApplicationFilter filter) {
		return this.applications.search(ownerId, filter.stage(), pattern(filter), filter.includeArchived());
	}

	/**
	 * One page of the filtered list, newest change first. {@code page} counts from 0;
	 * a page past the end returns the last page instead.
	 */
	@Transactional(readOnly = true)
	public Page<JobApplication> listPage(long ownerId, ApplicationFilter filter, int page, int size) {
		Page<JobApplication> result = searchPage(ownerId, filter, Math.max(page, 0), size);
		if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
			result = searchPage(ownerId, filter, result.getTotalPages() - 1, size);
		}
		return result;
	}

	/**
	 * How many applications the user has, archived ones included.
	 */
	@Transactional(readOnly = true)
	public long countAll(long ownerId) {
		return this.applications.countByJobOwnerId(ownerId);
	}

	private Page<JobApplication> searchPage(long ownerId, ApplicationFilter filter, int page, int size) {
		return this.applications.searchPage(ownerId, filter.stage(), pattern(filter), filter.includeArchived(),
				PageRequest.of(page, size));
	}

	private static String pattern(ApplicationFilter filter) {
		return (filter.text() == null || filter.text().isBlank()) ? null
				: "%" + filter.text().strip().toLowerCase(Locale.ROOT) + "%";
	}

	@Transactional(readOnly = true)
	public ApplicationDetails get(long ownerId, long applicationId) {
		JobApplication application = findOwned(ownerId, applicationId);
		return new ApplicationDetails(application, List.copyOf(application.getHistory()));
	}

	public JobApplication changeStage(long ownerId, long applicationId, Stage next, LocalDate on) {
		LocalDate date = (on != null) ? on : today();
		if (date.isAfter(today())) {
			throw new IllegalArgumentException("The date can't be in the future.");
		}
		JobApplication application = findOwned(ownerId, applicationId);
		application.moveTo(next, date, Instant.now(this.clock));
		return application;
	}

	public JobApplication addNote(long ownerId, long applicationId, String text, LocalDate on) {
		JobApplication application = findOwned(ownerId, applicationId);
		application.addNote(text, (on != null) ? on : today(), Instant.now(this.clock));
		return application;
	}

	public JobApplication updateJobDetails(long ownerId, long applicationId, String title, String company,
			String location) {
		JobApplication application = findOwned(ownerId, applicationId);
		application.getJob().updateDetails(title, company, location);
		application.touch(Instant.now(this.clock));
		return application;
	}

	public JobApplication setArchived(long ownerId, long applicationId, boolean archived) {
		JobApplication application = findOwned(ownerId, applicationId);
		application.setArchived(archived, Instant.now(this.clock));
		return application;
	}

	@Transactional(readOnly = true)
	public Map<Stage, Long> countByStage(long ownerId) {
		Map<Stage, Long> counts = new EnumMap<>(Stage.class);
		for (Stage stage : Stage.values()) {
			counts.put(stage, 0L);
		}
		this.applications.countByStage(ownerId).forEach((count) -> counts.put(count.stage(), count.count()));
		return counts;
	}

	@Transactional(readOnly = true)
	public List<HistoryEntry> recentActivity(long ownerId, int limit) {
		return this.applications.findRecentActivity(ownerId, PageRequest.of(0, limit));
	}

	public LocalDate today() {
		return LocalDate.now(this.clock);
	}

	private Optional<TrackedJob> findExisting(long ownerId, NewJob newJob) {
		if (newJob.externalId() != null) {
			return this.jobs.findByOwnerIdAndSourceProviderAndExternalId(ownerId, newJob.provider(),
					newJob.externalId());
		}
		if (newJob.sourceUrl() != null) {
			return this.jobs.findByOwnerIdAndSourceProviderAndSourceUrl(ownerId, newJob.provider(),
					newJob.sourceUrl());
		}
		return Optional.empty();
	}

	private JobApplication findOwned(long ownerId, long applicationId) {
		return this.applications.findOwned(applicationId, ownerId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

}
