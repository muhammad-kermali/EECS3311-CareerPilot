package careerpilot.domain;

/** A job requirement. sourceSpan is an exact quote from the posting, checked before saving. */
public class Requirement {
    private String id;
    private String text;
    private String category;
    private String priority;
    private String sourceSpan;

    private Requirement() { }

    public Requirement(String id, String text, String category, String priority, String sourceSpan) {
        this.id = id; this.text = text; this.category = category; this.priority = priority; this.sourceSpan = sourceSpan;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public String getSourceSpan() { return sourceSpan; }
    public boolean isRequired() { return !"preferred".equalsIgnoreCase(priority); }
}
