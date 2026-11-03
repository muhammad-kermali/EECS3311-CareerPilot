package careerpilot.service;

import careerpilot.common.DateText;
import careerpilot.common.TextUtil;
import careerpilot.common.ValidationException;
import careerpilot.domain.ApplicationRecord;
import careerpilot.domain.ApplicationStatus;
import careerpilot.domain.JobPosting;
import careerpilot.storage.WorkspaceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** F10 track applications, notes and deadlines. Fully deterministic. */
public class ApplicationService {
    private final WorkspaceRepository repo;
    private final Clock clock;

    public ApplicationService(WorkspaceRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Creates or updates the single record for a job. expectedRevision (if given) must match the saved record. */
    public ApplicationRecord updateApplication(String jobId, ApplicationChanges changes, Integer expectedRevision) {
        JobPosting job = repo.load(JobPosting.class, jobId);
        ApplicationRecord record = find(jobId).orElseGet(() -> {
            ApplicationRecord r = new ApplicationRecord(jobId);
            r.setDeadline(job.getDeadline());
            return r;
        });
        LocalDate today = LocalDate.now(clock);
        if (changes.status() != null) record.setStatus(ApplicationStatus.parse(changes.status()));
        if (changes.notes() != null) {
            if (changes.notes().length() > 4000) throw new ValidationException("Notes must be 4000 characters or fewer");
            record.setNotes(changes.notes());
        }
        if (changes.deadline() != null) record.setDeadline(DateText.parseIsoDate(changes.deadline(), "Deadline"));
        if (changes.appliedOn() != null) {
            LocalDate applied = DateText.parseIsoDate(changes.appliedOn(), "Applied date");
            if (applied != null && applied.isAfter(today)) throw new ValidationException("Applied date cannot be in the future");
            record.setAppliedOn(applied);
        }
        if (record.getStatus() != ApplicationStatus.SAVED && record.getStatus() != ApplicationStatus.WITHDRAWN && record.getAppliedOn() == null) {
            record.setAppliedOn(today);
        }
        int expected = expectedRevision == null ? record.getRevision() : expectedRevision;
        return repo.save(record, expected);
    }

    public Optional<ApplicationRecord> find(String jobId) {
        return repo.list(ApplicationRecord.class, r -> r.getJobId().equals(jobId)).stream().findFirst();
    }

    public List<ApplicationView> listApplications(String statusFilter, boolean overdueOnly) {
        ApplicationStatus status = TextUtil.isBlank(statusFilter) || statusFilter.equalsIgnoreCase("all") ? null : ApplicationStatus.parse(statusFilter);
        LocalDate today = LocalDate.now(clock);
        List<ApplicationView> out = new ArrayList<>();
        for (ApplicationRecord r : repo.list(ApplicationRecord.class, r -> status == null || r.getStatus() == status)) {
            JobPosting job = repo.find(JobPosting.class, r.getJobId()).orElse(null);
            boolean overdue = r.isOverdue(today);
            if (overdueOnly && !overdue) continue;
            out.add(new ApplicationView(r, job, overdue, r.daysLeft(today)));
        }
        out.sort(Comparator.comparing((ApplicationView v) -> v.record().getDeadline(), Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }
}
