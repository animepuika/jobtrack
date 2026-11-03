package com.jobtrack.tracking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The user's tracking card for one job: its current stage and its history. The
 * stage only changes through {@link #moveTo}, which applies the stage rules and
 * records the change in the same step.
 */
@Entity
@Table(name = "job_application")
public class JobApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
	@JoinColumn(name = "job_id", nullable = false, updatable = false)
	private TrackedJob job;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stage stage;

	@Column(name = "applied_on")
	private LocalDate appliedOn;

	@Column(nullable = false)
	private boolean archived;

	@Column(name = "archived_on")
	private LocalDate archivedOn;

	/** The CV version sent, by ID only: CVs belong to the documents module. */
	@Column(name = "cv_version_id")
	private Long cvVersionId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	@OneToMany(mappedBy = "application", cascade = CascadeType.ALL)
	@OrderBy("occurredOn ASC, id ASC")
	private List<HistoryEntry> history = new ArrayList<>();

	protected JobApplication() {
		// for JPA
	}

	JobApplication(TrackedJob job, LocalDate savedOn, Instant now) {
		this.job = job;
		this.stage = Stage.SAVED;
		this.createdAt = now;
		this.updatedAt = now;
		this.history.add(HistoryEntry.created(this, savedOn, now));
	}

	void moveTo(Stage next, LocalDate on, Instant now) {
		if (next == null || on == null) {
			throw new IllegalArgumentException("A stage change needs a stage and a date");
		}
		if (!this.stage.canMoveTo(next)) {
			throw new StageChangeNotAllowedException(this.stage, next);
		}
		LocalDate latest = latestStageDate();
		if (on.isBefore(latest)) {
			throw new IllegalArgumentException("A stage change cannot be dated before the previous one (" + latest + ")");
		}
		this.history.add(HistoryEntry.stageChange(this, this.stage, next, on, now));
		if (next == Stage.APPLIED && this.appliedOn == null) {
			this.appliedOn = on;
		}
		this.stage = next;
		this.updatedAt = now;
	}

	void addNote(String text, LocalDate on, Instant now) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("A note must not be blank");
		}
		if (on == null) {
			throw new IllegalArgumentException("A note needs a date");
		}
		this.history.add(HistoryEntry.note(this, text.strip(), on, now));
		this.updatedAt = now;
	}

	void setArchived(boolean archived, LocalDate today, Instant now) {
		if (archived == this.archived) {
			return;
		}
		this.archived = archived;
		this.archivedOn = archived ? today : null;
		this.updatedAt = now;
	}

	void useCvVersion(Long cvVersionId, Instant now) {
		this.cvVersionId = cvVersionId;
		this.updatedAt = now;
	}

	void touch(Instant now) {
		this.updatedAt = now;
	}

	private LocalDate latestStageDate() {
		return this.history.stream()
			.filter((entry) -> entry.getType() != EventType.NOTE)
			.map(HistoryEntry::getOccurredOn)
			.max(LocalDate::compareTo)
			.orElse(LocalDate.MIN);
	}

	/**
	 * The day the application entered its current stage.
	 */
	public LocalDate getStageSince() {
		return latestStageDate();
	}

	/**
	 * The latest day anything happened: a stage change or a note, such as a follow-up
	 * email.
	 */
	public LocalDate getLastActivityOn() {
		return this.history.stream()
			.map(HistoryEntry::getOccurredOn)
			.max(LocalDate::compareTo)
			.orElse(LocalDate.MIN);
	}

	public Long getId() {
		return id;
	}

	public TrackedJob getJob() {
		return job;
	}

	public Stage getStage() {
		return stage;
	}

	public LocalDate getAppliedOn() {
		return appliedOn;
	}

	public boolean isArchived() {
		return archived;
	}

	public LocalDate getArchivedOn() {
		return archivedOn;
	}

	public Long getCvVersionId() {
		return cvVersionId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

	public List<HistoryEntry> getHistory() {
		return Collections.unmodifiableList(history);
	}

}
