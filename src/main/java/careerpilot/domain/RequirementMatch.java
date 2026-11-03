package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class RequirementMatch {
    private String requirementId;
    private MatchLevel level;
    private List<EvidenceRef> evidence = new ArrayList<>();
    private String explanation;

    private RequirementMatch() { }

    public RequirementMatch(String requirementId, MatchLevel level, List<EvidenceRef> evidence, String explanation) {
        this.requirementId = requirementId; this.level = level;
        this.evidence = new ArrayList<>(evidence); this.explanation = explanation;
    }

    public String getRequirementId() { return requirementId; }
    public MatchLevel getLevel() { return level; }
    public List<EvidenceRef> getEvidence() { return evidence; }
    public String getExplanation() { return explanation; }
}
