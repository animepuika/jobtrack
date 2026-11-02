package com.jobtrack.tracking;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One line in an application's history: when it was created, a stage change, or a
 * note. Entries are only ever added, never edited.
 */
@Entity
@Table(name = "application_event")
public class HistoryEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false, updatable = false)
	private JobApplication application;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private EventType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_stage", updatable = false)
	private Stage fromStage;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_stage", updatable = false)
	private Stage toStage;

	@Column(updatable = false)
	private String note;

	@Column(name = "occurred_on", nullable = false, updatable = false)
	private LocalDate occurredOn;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected HistoryEntry() {
		// for JPA
	}

	private HistoryEntry(JobApplication application, EventType type, Stage fromStage, Stage toStage, String note,
			LocalDate occurredOn, Instant createdAt) {
		this.application = application;
		this.type = type;
		this.fromStage = fromStage;
		this.toStage = toStage;
		this.note = note;
		this.occurredOn = occurredOn;
		this.createdAt = createdAt;
	}

	static HistoryEntry created(JobApplication application, LocalDate on, Instant now) {
		return new HistoryEntry(application, EventType.CREATED, null, Stage.SAVED, null, on, now);
	}

	static HistoryEntry stageChange(JobApplication application, Stage from, Stage to, LocalDate on, Instant now) {
		return new HistoryEntry(application, EventType.STAGE_CHANGE, from, to, null, on, now);
	}

	static HistoryEntry note(JobApplication application, String text, LocalDate on, Instant now) {
		return new HistoryEntry(application, EventType.NOTE, null, null, text, on, now);
	}

	public Long getId() {
		return id;
	}

	public JobApplication getApplication() {
		return application;
	}

	public EventType getType() {
		return type;
	}

	public Stage getFromStage() {
		return fromStage;
	}

	public Stage getToStage() {
		return toStage;
	}

	public String getNote() {
		return note;
	}

	public LocalDate getOccurredOn() {
		return occurredOn;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
