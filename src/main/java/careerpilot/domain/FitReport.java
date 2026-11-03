package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

/** Requirement-by-requirement comparison. It describes evidence; it does not predict hiring outcomes. */
public class FitReport extends Entity implements HasWarnings {
    private String profileId;
    private String jobId;
    private int profileRevision;
    private int jobRevision;
    private List<RequirementMatch> matches = new ArrayList<>();
    private String summary;
    private List<String> warnings = new ArrayList<>();

    @Override public String idPrefix() { return "F"; }

    private FitReport() { }

    public FitReport(CandidateProfile profile, JobPosting job, List<RequirementMatch> matches, String summary, List<String> warnings) {
        this.profileId = profile.getId(); this.jobId = job.getId();
        this.profileRevision = profile.getRevision(); this.jobRevision = job.getRevision();
        this.matches = new ArrayList<>(matches); this.summary = summary; this.warnings = new ArrayList<>(warnings);
    }

    public String getProfileId() { return profileId; }
    public String getJobId() { return jobId; }
    public int getProfileRevision() { return profileRevision; }
    public int getJobRevision() { return jobRevision; }
    public List<RequirementMatch> getMatches() { return matches; }
    public String getSummary() { return summary; }
    @Override public List<String> warnings() { return warnings; }

    public long count(MatchLevel level) { return matches.stream().filter(m -> m.getLevel() == level).count(); }

    /** Stale when the profile or job changed after this report was made. */
    public boolean isStaleFor(CandidateProfile profile, JobPosting job) {
        return profile.getRevision() != profileRevision || job.getRevision() != jobRevision;
    }
}
