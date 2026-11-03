package careerpilot.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** A generated document (resume version or cover letter) that the applicant must review before it is saved. */
public abstract class Document extends Entity {
    private boolean reviewed;
    private Map<String, Integer> sourceRevisions = new LinkedHashMap<>();

    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }
    public Map<String, Integer> getSourceRevisions() { return sourceRevisions; }

    public void recordSource(Entity source) { sourceRevisions.put(source.sourceKey(), source.getRevision()); }

    protected void copySourceRevisions(Document from) { sourceRevisions = new LinkedHashMap<>(from.sourceRevisions); }

    public abstract String getJobId();
    public abstract String describe();
}
