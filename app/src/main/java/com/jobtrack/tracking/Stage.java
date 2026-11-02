package com.jobtrack.tracking;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum Stage {

	SAVED,
	APPLIED,
	INTERVIEW,
	OFFER,
	ACCEPTED,
	REJECTED,
	WITHDRAWN;

	public boolean canMoveTo(Stage next) {
		return switch (this) {
			case SAVED -> next == APPLIED || next == WITHDRAWN;
			case APPLIED -> next == INTERVIEW || next == REJECTED || next == WITHDRAWN;
			case INTERVIEW -> next == OFFER || next == REJECTED || next == WITHDRAWN;
			case OFFER -> next == ACCEPTED || next == REJECTED || next == WITHDRAWN;
			case ACCEPTED, REJECTED, WITHDRAWN -> false;
		};
	}

	public List<Stage> nextStages() {
		return Arrays.stream(values()).filter(this::canMoveTo).toList();
	}

	public boolean isFinal() {
		return nextStages().isEmpty();
	}

	/**
	 * The name shown on pages, for example {@code "Interview"}.
	 */
	public String getLabel() {
		return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
	}

}
