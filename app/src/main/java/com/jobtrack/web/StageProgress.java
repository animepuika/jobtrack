package com.jobtrack.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.jobtrack.tracking.EventType;
import com.jobtrack.tracking.HistoryEntry;
import com.jobtrack.tracking.Stage;

/**
 * The steps shown above the "Move to" form: the usual path from Saved to Accepted,
 * with the date each step was reached. An application that ended as Rejected or
 * Withdrawn shows the steps it reached and then its end.
 */
final class StageProgress {

	private static final List<Stage> PATH = List.of(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER,
			Stage.ACCEPTED);

	/** What each move means, shown under the choice. */
	static final Map<Stage, String> MOVE_HELP = Map.of(Stage.APPLIED, "You sent your application", Stage.INTERVIEW,
			"They invited you to talk", Stage.OFFER, "They made you an offer", Stage.ACCEPTED, "You said yes",
			Stage.REJECTED, "They said no", Stage.WITHDRAWN, "You stopped");

	private StageProgress() {
	}

	/**
	 * @param status {@code done}, {@code current}, {@code end} or {@code todo}, matching
	 * the stepper's CSS classes
	 */
	record Step(Stage stage, String status, LocalDate reached) {
	}

	static List<Step> of(Stage current, List<HistoryEntry> history) {
		Map<Stage, LocalDate> reached = reachedDates(history);
		List<Step> steps = new ArrayList<>();
		if (current == Stage.REJECTED || current == Stage.WITHDRAWN) {
			for (Stage stage : PATH) {
				if (reached.containsKey(stage)) {
					steps.add(new Step(stage, "done", reached.get(stage)));
				}
			}
			steps.add(new Step(current, "end", reached.get(current)));
			return steps;
		}
		int currentIndex = PATH.indexOf(current);
		for (int i = 0; i < PATH.size(); i++) {
			Stage stage = PATH.get(i);
			String status = (i < currentIndex) ? "done" : (i == currentIndex) ? "current" : "todo";
			steps.add(new Step(stage, status, reached.get(stage)));
		}
		return steps;
	}

	/**
	 * The date the application was first saved.
	 */
	static LocalDate savedOn(List<HistoryEntry> history) {
		return reachedDates(history).get(Stage.SAVED);
	}

	private static Map<Stage, LocalDate> reachedDates(List<HistoryEntry> history) {
		Map<Stage, LocalDate> reached = new EnumMap<>(Stage.class);
		for (HistoryEntry entry : history) {
			if (entry.getType() != EventType.NOTE && entry.getToStage() != null) {
				reached.putIfAbsent(entry.getToStage(), entry.getOccurredOn());
			}
		}
		return reached;
	}

}
