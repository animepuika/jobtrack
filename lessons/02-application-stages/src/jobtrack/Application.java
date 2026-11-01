package jobtrack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Application {
    private final Job job;
    private Stage stage;
    private final List<StageChange> history = new ArrayList<>();

    public Application(Job job, LocalDate savedOn) {
        if (job == null) {
            throw new IllegalArgumentException("An application needs a job");
        }
        this.job = job;
        this.stage = Stage.SAVED;
        history.add(new StageChange(Stage.SAVED, savedOn));
    }

    public Job getJob() {
        return job;
    }

    public Stage getStage() {
        return stage;
    }

    public List<StageChange> getHistory() {
        return List.copyOf(history);
    }

    public void moveTo(Stage next, LocalDate date) {
        if (!stage.canMoveTo(next)) {
            throw new IllegalStateException("Cannot move from " + stage + " to " + next);
        }
        history.add(new StageChange(next, date));
        stage = next;
    }
}
