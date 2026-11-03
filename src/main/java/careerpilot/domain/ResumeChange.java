package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

/** One proposed edit to a tailored resume, with its evidence and grounding result. */
public class ResumeChange {
    private String id;
    private int sectionIndex;
    private int entryIndex;
    private String before;
    private String after;
    private String reason;
    private List<String> requirementIds = new ArrayList<>();
    private List<EvidenceRef> evidence = new ArrayList<>();
    private boolean supported;
    private String unsupportedReason;
    private boolean accepted;

    private ResumeChange() { }

    public ResumeChange(String id, int sectionIndex, int entryIndex, String before, String after, String reason,
                        List<String> requirementIds, List<EvidenceRef> evidence) {
        this.id = id; this.sectionIndex = sectionIndex; this.entryIndex = entryIndex;
        this.before = before; this.after = after; this.reason = reason;
        this.requirementIds = new ArrayList<>(requirementIds);
        this.evidence = new ArrayList<>(evidence);
    }

    public String getId() { return id; }
    public int getSectionIndex() { return sectionIndex; }
    public int getEntryIndex() { return entryIndex; }
    public String getBefore() { return before; }
    public String getAfter() { return after; }
    public String getReason() { return reason; }
    public List<String> getRequirementIds() { return requirementIds; }
    public List<EvidenceRef> getEvidence() { return evidence; }
    public boolean isSupported() { return supported; }
    public String getUnsupportedReason() { return unsupportedReason; }
    public boolean isAccepted() { return accepted; }

    public void markGrounding(boolean supported, String unsupportedReason) {
        this.supported = supported;
        this.unsupportedReason = unsupportedReason;
        this.accepted = supported;
    }

    public void setAccepted(boolean accepted) { this.accepted = accepted; }

    public ResumeChange copy() {
        ResumeChange c = new ResumeChange(id, sectionIndex, entryIndex, before, after, reason, requirementIds, evidence);
        c.supported = supported; c.unsupportedReason = unsupportedReason; c.accepted = accepted;
        return c;
    }
}
