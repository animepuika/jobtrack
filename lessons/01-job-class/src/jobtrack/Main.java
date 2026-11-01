package jobtrack;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Fictional jobs; example.com is reserved for documentation.
        List<Job> jobs = new ArrayList<>();
        jobs.add(new Job("Junior Java Developer", "Example Logistics", "Riga", "https://example.com/jobs/101"));
        jobs.add(new Job("Backend Intern", "Sample Health", "Remote", "https://example.com/jobs/202"));

        for (Job job : jobs) {
            System.out.println(job);
        }
        System.out.println("Tracked jobs: " + jobs.size());

        try {
            jobs.add(new Job("   ", "Blank Title Ltd", "Riga", "https://example.com/jobs/303"));
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected job: " + e.getMessage());
        }
        System.out.println("Tracked jobs after rejected one: " + jobs.size());
    }
}
