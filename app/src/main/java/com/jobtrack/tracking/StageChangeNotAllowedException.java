package com.jobtrack.tracking;

public class StageChangeNotAllowedException extends RuntimeException {

	private final Stage from;

	private final Stage to;

	public StageChangeNotAllowedException(Stage from, Stage to) {
		super("Cannot move from " + from + " to " + to);
		this.from = from;
		this.to = to;
	}

	public Stage getFrom() {
		return from;
	}

	public Stage getTo() {
		return to;
	}

}
