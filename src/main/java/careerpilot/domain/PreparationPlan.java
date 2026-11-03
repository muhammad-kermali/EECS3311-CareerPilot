package careerpilot.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PreparationPlan extends Entity {
    private String profileId;
    private String jobId;
    private int availableMinutes;
    private LocalDate deadline;
    private String summary;
    private List<PreparationTask> tasks = new ArrayList<>();
    private boolean reviewed;

    @Override public String idPrefix() { return "PL"; }

    private PreparationPlan() { }

    public PreparationPlan(String profileId, String jobId, Constraints constraints, String summary, List<PreparationTask> tasks) {
        this.profileId = profileId; this.jobId = jobId;
        this.availableMinutes = constraints.availableMinutes(); this.deadline = constraints.deadline();
        this.summary = summary; this.tasks = new ArrayList<>(tasks);
    }

    public String getProfileId() { return profileId; }
    public String getJobId() { return jobId; }
    public int getAvailableMinutes() { return availableMinutes; }
    public LocalDate getDeadline() { return deadline; }
    public String getSummary() { return summary; }
    public List<PreparationTask> getTasks() { return tasks; }
    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean r) { reviewed = r; }

    public int totalMinutes() { return tasks.stream().mapToInt(PreparationTask::getMinutes).sum(); }
}
