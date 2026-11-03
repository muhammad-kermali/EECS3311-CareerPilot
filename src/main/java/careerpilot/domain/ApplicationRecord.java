package careerpilot.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** At most one record per job. Status is always set explicitly by the applicant. */
public class ApplicationRecord extends Entity {
    private String jobId;
    private ApplicationStatus status = ApplicationStatus.SAVED;
    private String notes = "";
    private LocalDate deadline;
    private LocalDate appliedOn;

    @Override public String idPrefix() { return "A"; }

    private ApplicationRecord() { }

    public ApplicationRecord(String jobId) { this.jobId = jobId; }

    public String getJobId() { return jobId; }
    public ApplicationStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public LocalDate getDeadline() { return deadline; }
    public LocalDate getAppliedOn() { return appliedOn; }

    public void setStatus(ApplicationStatus s) { status = s; }
    public void setNotes(String n) { notes = n == null ? "" : n; }
    public void setDeadline(LocalDate d) { deadline = d; }
    public void setAppliedOn(LocalDate d) { appliedOn = d; }

    /** Overdue: still not submitted and the deadline has passed. */
    public boolean isOverdue(LocalDate today) {
        return deadline != null && status == ApplicationStatus.SAVED && deadline.isBefore(today);
    }

    public Long daysLeft(LocalDate today) { return deadline == null ? null : ChronoUnit.DAYS.between(today, deadline); }
}
