package com.jobtrack.tracking;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A job listing the user chose to track: what the job is and where it came from.
 * The user's own progress lives in {@link JobApplication}, so provider data and
 * personal data never overwrite each other.
 */
@Entity
@Table(name = "tracked_job")
public class TrackedJob {

	static final int MAX_TEXT_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String company;

	private String location;

	@Column(name = "source_url")
	private String sourceUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_provider", nullable = false, updatable = false)
	private SourceProvider sourceProvider;

	@Column(name = "external_id", updatable = false)
	private String externalId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TrackedJob() {
		// for JPA
	}

	TrackedJob(long ownerId, NewJob job, Instant createdAt) {
		this.ownerId = ownerId;
		this.sourceProvider = job.provider();
		this.externalId = job.externalId();
		this.sourceUrl = job.sourceUrl();
		this.createdAt = createdAt;
		updateDetails(job.title(), job.company(), job.location());
	}

	void updateDetails(String title, String company, String location) {
		this.title = limit(requireText(title, "Job title"), "Job title");
		this.company = limit(requireText(company, "Company"), "Company");
		this.location = (location == null || location.isBlank()) ? null : limit(location.strip(), "Location");
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.strip();
	}

	private static String limit(String value, String name) {
		if (value.length() > MAX_TEXT_LENGTH) {
			throw new IllegalArgumentException(name + " must be at most " + MAX_TEXT_LENGTH + " characters");
		}
		return value;
	}

	public Long getId() {
		return id;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public String getTitle() {
		return title;
	}

	public String getCompany() {
		return company;
	}

	public String getLocation() {
		return location;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public SourceProvider getSourceProvider() {
		return sourceProvider;
	}

	public String getExternalId() {
		return externalId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
