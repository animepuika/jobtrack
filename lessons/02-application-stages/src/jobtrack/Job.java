package jobtrack;

public class Job {
    private final String title;
    private final String company;
    private final String location;
    private final String sourceLink;

    public Job(String title, String company, String location, String sourceLink) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Job title must not be blank");
        }
        this.title = title;
        this.company = company;
        this.location = location;
        this.sourceLink = sourceLink;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getLocation() {
        return location;
    }

    public String getSourceLink() {
        return sourceLink;
    }

    @Override
    public String toString() {
        return title + " at " + company + ", " + location + " (" + sourceLink + ")";
    }
}
