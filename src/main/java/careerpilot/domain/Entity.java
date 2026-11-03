package careerpilot.domain;

import java.time.Instant;

/** Common identity and optimistic-locking data for every saved record. */
public abstract class Entity {
    private String id;
    private int revision;
    private Instant createdAt;
    private Instant updatedAt;

    /** Short prefix used for readable IDs such as J1, P1 or Q3. */
    public abstract String idPrefix();

    public String getId() { return id; }
    public int getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Called by the repository only. */
    public void assignId(String id) {
        if (this.id != null && !this.id.equals(id)) throw new IllegalStateException("ID already assigned");
        this.id = id;
    }

    /** Called by the repository after a successful write. */
    public void markSaved(int newRevision, Instant now) {
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        revision = newRevision;
    }

    /** Used by Prototype copies: the copy is a new, unsaved record. */
    protected void resetIdentity() {
        id = null;
        revision = 0;
        createdAt = null;
        updatedAt = null;
    }

    /** Key used in sourceRevisions maps, e.g. "JobPosting:J1". */
    public String sourceKey() { return getClass().getSimpleName() + ":" + id; }
}
