package careerpilot.agent;

import careerpilot.domain.Constraints;
import careerpilot.domain.Document;
import java.util.ArrayList;
import java.util.List;

/** Scope and working memory for one agent run: which records it may touch and what it has produced so far. */
public class RunContext {
    private final String profileId;
    private final String jobId;
    private final Constraints constraints;
    private final List<Document> drafts = new ArrayList<>();
    private final List<String> observations = new ArrayList<>();

    public RunContext(String profileId, String jobId, Constraints constraints) {
        this.profileId = profileId;
        this.jobId = jobId;
        this.constraints = constraints;
    }

    public String profileId() { return profileId; }
    public String jobId() { return jobId; }
    public Constraints constraints() { return constraints; }
    public List<Document> drafts() { return drafts; }
    public List<String> observations() { return observations; }

    public void addDraft(Document d) { drafts.add(d); }
    public void addObservation(String o) { observations.add(o); }
}
