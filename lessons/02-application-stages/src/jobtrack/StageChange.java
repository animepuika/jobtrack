package jobtrack;

import java.time.LocalDate;

public record StageChange(Stage stage, LocalDate date) {
    public StageChange {
        if (stage == null || date == null) {
            throw new IllegalArgumentException("A stage change needs a stage and a date");
        }
    }

    @Override
    public String toString() {
        return date + " " + stage;
    }
}
