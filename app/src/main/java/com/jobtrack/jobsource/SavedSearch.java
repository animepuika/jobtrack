package com.jobtrack.jobsource;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A search saved under a name. {@code query} holds the search page's address
 * parameters, so the provider-specific filters stay out of the database schema.
 */
@Entity
@Table(name = "saved_search")
public class SavedSearch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String query;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SavedSearch() {
		// for JPA
	}

	SavedSearch(long ownerId, String name, String query, Instant createdAt) {
		this.ownerId = ownerId;
		this.name = name;
		this.query = query;
		this.createdAt = createdAt;
	}

	void replaceQuery(String query) {
		this.query = query;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getQuery() {
		return query;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
