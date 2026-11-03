package com.jobtrack.jobsource;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import com.jobtrack.tracking.SourceProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * A search result the user has opened. Only the provider's job ID is stored. Rows are
 * written by {@link ViewedJobRepository#markViewed}.
 */
@Entity
@Table(name = "viewed_job")
@IdClass(ViewedJob.Key.class)
public class ViewedJob {

	@Id
	@Column(name = "owner_id")
	private Long ownerId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "source_provider")
	private SourceProvider sourceProvider;

	@Id
	@Column(name = "external_id")
	private String externalId;

	@Column(name = "first_viewed_at", nullable = false, updatable = false)
	private Instant firstViewedAt;

	@Column(name = "last_viewed_at", nullable = false)
	private Instant lastViewedAt;

	protected ViewedJob() {
		// for JPA
	}

	public String getExternalId() {
		return externalId;
	}

	public Instant getFirstViewedAt() {
		return firstViewedAt;
	}

	public Instant getLastViewedAt() {
		return lastViewedAt;
	}

	public static class Key implements Serializable {

		private Long ownerId;

		private SourceProvider sourceProvider;

		private String externalId;

		public Key() {
		}

		public Key(Long ownerId, SourceProvider sourceProvider, String externalId) {
			this.ownerId = ownerId;
			this.sourceProvider = sourceProvider;
			this.externalId = externalId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && Objects.equals(this.ownerId, key.ownerId)
					&& this.sourceProvider == key.sourceProvider && Objects.equals(this.externalId, key.externalId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.ownerId, this.sourceProvider, this.externalId);
		}

	}

}
