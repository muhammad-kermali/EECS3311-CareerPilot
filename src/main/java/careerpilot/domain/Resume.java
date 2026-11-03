package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A resume version. The base resume comes from the imported file; tailored versions are created with
 * {@link #deepCopy()} so editing a copy can never change the base resume (Prototype pattern).
 */
public class Resume extends Document implements ResumePrototype {
    private String profileId;
    private String jobId;
    private String sourceResumeId;
    private String title;
    private String sourceName;
    private List<ResumeSection> sections = new ArrayList<>();
    private List<ResumeChange> changes = new ArrayList<>();

    @Override public String idPrefix() { return "R"; }

    private Resume() { }

    public Resume(String profileId, String title, List<ResumeSection> sections) {
        this.profileId = profileId;
        this.title = title;
        this.sections = new ArrayList<>(sections);
    }

    /** Copies every mutable list. The copy is unsaved, unreviewed and remembers which resume it came from. */
    @Override
    public Resume deepCopy() {
        Resume copy = new Resume();
        copy.profileId = profileId;
        copy.jobId = jobId;
        copy.title = title;
        copy.sourceName = sourceName;
        copy.sourceResumeId = getId();
        for (ResumeSection s : sections) copy.sections.add(s.copy());
        for (ResumeChange c : changes) copy.changes.add(c.copy());
        copy.copySourceRevisions(this);
        copy.resetIdentity();
        copy.setReviewed(false);
        return copy;
    }

    public String getProfileId() { return profileId; }
    @Override public String getJobId() { return jobId; }
    public String getSourceResumeId() { return sourceResumeId; }
    public String getTitle() { return title; }
    public String getSourceName() { return sourceName; }
    public List<ResumeSection> getSections() { return sections; }
    public List<ResumeChange> getChanges() { return changes; }
    public boolean isBase() { return jobId == null; }

    public void setProfileId(String v) { profileId = v; }
    public void setJobId(String v) { jobId = v; }
    public void setTitle(String v) { title = v; }
    public void setSourceName(String v) { sourceName = v; }

    public String entry(int section, int entry) {
        if (section < 0 || section >= sections.size()) return null;
        List<String> entries = sections.get(section).getEntries();
        return entry < 0 || entry >= entries.size() ? null : entries.get(entry);
    }

    public void setEntry(int section, int entry, String text) { sections.get(section).getEntries().set(entry, text); }

    public String render() {
        StringBuilder sb = new StringBuilder();
        for (ResumeSection s : sections) {
            sb.append(s.getHeading().toUpperCase()).append('\n');
            for (String e : s.getEntries()) sb.append("- ").append(e).append('\n');
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    @Override public String describe() { return (isBase() ? "Base resume " : "Tailored resume ") + getId() + (jobId == null ? "" : " for " + jobId); }
}
