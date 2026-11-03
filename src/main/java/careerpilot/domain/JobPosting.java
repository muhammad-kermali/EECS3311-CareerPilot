package careerpilot.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JobPosting extends Entity {
    private String title;
    private String company;
    private String url;
    private LocalDate deadline;
    private String sourceText;
    private List<Requirement> requirements = new ArrayList<>();
    private boolean reviewed;

    @Override public String idPrefix() { return "J"; }

    private JobPosting() { }

    public JobPosting(String title, String company, String url, LocalDate deadline, String sourceText, List<Requirement> requirements) {
        this.title = title; this.company = company; this.url = url; this.deadline = deadline;
        this.sourceText = sourceText; this.requirements = new ArrayList<>(requirements);
    }

    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getUrl() { return url; }
    public LocalDate getDeadline() { return deadline; }
    public String getSourceText() { return sourceText; }
    public List<Requirement> getRequirements() { return requirements; }
    public boolean isReviewed() { return reviewed; }

    public void setTitle(String v) { title = v; }
    public void setCompany(String v) { company = v; }
    public void setDeadline(LocalDate v) { deadline = v; }
    public void setReviewed(boolean v) { reviewed = v; }

    public Optional<Requirement> requirement(String id) {
        return requirements.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    public String label() {
        return getId() + ": " + (title == null ? "Untitled role" : title) + (company == null || company.isBlank() ? "" : " at " + company);
    }
}
