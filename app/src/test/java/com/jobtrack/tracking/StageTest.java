package com.jobtrack.tracking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class StageTest {

	@Test
	void followsTheNormalPath() {
		assertThat(Stage.SAVED.canMoveTo(Stage.APPLIED)).isTrue();
		assertThat(Stage.APPLIED.canMoveTo(Stage.INTERVIEW)).isTrue();
		assertThat(Stage.INTERVIEW.canMoveTo(Stage.OFFER)).isTrue();
		assertThat(Stage.OFFER.canMoveTo(Stage.ACCEPTED)).isTrue();
	}

	@Test
	void cannotSkipAhead() {
		assertThat(Stage.SAVED.canMoveTo(Stage.OFFER)).isFalse();
		assertThat(Stage.APPLIED.canMoveTo(Stage.OFFER)).isFalse();
		assertThat(Stage.SAVED.canMoveTo(Stage.INTERVIEW)).isFalse();
	}

	@Test
	void savedJobCannotBeRejectedBecauseNothingWasSent() {
		assertThat(Stage.SAVED.canMoveTo(Stage.REJECTED)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = Stage.class, names = { "ACCEPTED", "REJECTED", "WITHDRAWN" })
	void finalStagesHaveNoWayOut(Stage stage) {
		assertThat(stage.isFinal()).isTrue();
		assertThat(stage.nextStages()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = Stage.class, names = { "SAVED", "APPLIED", "INTERVIEW", "OFFER" })
	void openStagesCanAlwaysBeWithdrawn(Stage stage) {
		assertThat(stage.canMoveTo(Stage.WITHDRAWN)).isTrue();
	}

	@ParameterizedTest
	@EnumSource(Stage.class)
	void neverMovesToItself(Stage stage) {
		assertThat(stage.canMoveTo(stage)).isFalse();
	}

	@Test
	void hasReadableLabels() {
		assertThat(Stage.INTERVIEW.getLabel()).isEqualTo("Interview");
	}

}
