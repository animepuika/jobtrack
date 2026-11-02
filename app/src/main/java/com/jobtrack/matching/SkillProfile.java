package com.jobtrack.matching;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Which CV the skills were last read from, so the page can say so.
 */
@Entity
@Table(name = "skill_profile")
public class SkillProfile {

	@Id
	@Column(name = "owner_id")
	private Long ownerId;

	@Column(name = "cv_version_id")
	private Long cvVersionId;

	@Column(name = "cv_label")
	private String cvLabel;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SkillProfile() {
		// for JPA
	}

	SkillProfile(long ownerId, Instant now) {
		this.ownerId = ownerId;
		this.updatedAt = now;
	}

	void readFrom(long cvVersionId, String cvLabel, Instant now) {
		this.cvVersionId = cvVersionId;
		this.cvLabel = cvLabel;
		this.updatedAt = now;
	}

	void touch(Instant now) {
		this.updatedAt = now;
	}

	public Long getCvVersionId() {
		return cvVersionId;
	}

	public String getCvLabel() {
		return cvLabel;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
