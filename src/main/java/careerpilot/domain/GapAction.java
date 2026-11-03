package careerpilot.domain;

public class GapAction {
    /** MISSING_EVIDENCE: the applicant may have the skill but the profile does not show it. LEARNING_NEED: a skill to build. */
    public enum GapType { MISSING_EVIDENCE, LEARNING_NEED }

    private String requirementId;
    private GapType gapType;
    private String action;
    private int estimatedHours;

    private GapAction() { }

    public GapAction(String requirementId, GapType gapType, String action, int estimatedHours) {
        this.requirementId = requirementId; this.gapType = gapType; this.action = action; this.estimatedHours = estimatedHours;
    }

    public String getRequirementId() { return requirementId; }
    public GapType getGapType() { return gapType; }
    public String getAction() { return action; }
    public int getEstimatedHours() { return estimatedHours; }
}
