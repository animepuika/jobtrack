package jobtrack;

import java.time.LocalDate;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Fictional job; example.com is reserved for documentation.
        Job job = new Job("Junior Java Developer", "Example Logistics", "Riga", "https://example.com/jobs/101");
        Application application = new Application(job, LocalDate.of(2026, 10, 20));

        System.out.println("Tracking: " + job);
        System.out.print("Possible stages:");
        for (Stage stage : Stage.values()) {
            System.out.print(" " + stage);
        }
        System.out.println();
        System.out.println("Starts at: " + application.getStage());

        tryMove(application, Stage.APPLIED, LocalDate.of(2026, 10, 21));
        tryMove(application, Stage.OFFER, LocalDate.of(2026, 10, 25));
        tryMove(application, Stage.INTERVIEW, LocalDate.of(2026, 10, 28));
        tryMove(application, Stage.OFFER, LocalDate.of(2026, 11, 5));
        tryMove(application, Stage.ACCEPTED, LocalDate.of(2026, 11, 7));
        tryMove(application, Stage.WITHDRAWN, LocalDate.of(2026, 11, 10));

        System.out.println("Final stage: " + application.getStage());

        List<StageChange> history = application.getHistory();
        System.out.println("History (" + history.size() + " entries):");
        for (StageChange change : history) {
            System.out.println("  " + change);
        }

        try {
            history.add(new StageChange(Stage.REJECTED, LocalDate.of(2026, 11, 12)));
        } catch (UnsupportedOperationException e) {
            System.out.println("Editing the history from outside was blocked");
        }
        System.out.println("History still has " + application.getHistory().size() + " entries");
    }

    private static void tryMove(Application application, Stage next, LocalDate date) {
        Stage before = application.getStage();
        try {
            application.moveTo(next, date);
            System.out.println("  " + before + " -> " + next + ": moved");
        } catch (IllegalStateException e) {
            System.out.println("  " + before + " -> " + next + ": refused (" + e.getMessage() + ")");
        }
    }
}
