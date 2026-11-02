package com.jobtrack.documents;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One uploaded CV file: its description and where storage keeps it. The storage key
 * is random and private; files are only ever served through an ownership check.
 */
@Entity
@Table(name = "cv_version")
public class CvVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(nullable = false)
	private String label;

	@Column(name = "original_filename", nullable = false, updatable = false)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, updatable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private long sizeBytes;

	@Column(name = "storage_key", nullable = false, unique = true, updatable = false)
	private String storageKey;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private Instant uploadedAt;

	protected CvVersion() {
		// for JPA
	}

	CvVersion(long ownerId, String label, String originalFilename, String contentType, long sizeBytes,
			String storageKey, Instant uploadedAt) {
		this.ownerId = ownerId;
		this.label = label;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.uploadedAt = uploadedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public String getLabel() {
		return label;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	String getStorageKey() {
		return storageKey;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

}
