package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class InterviewQuestion {
    private String id;
    private String prompt;
    private List<String> requirementIds = new ArrayList<>();
    private List<String> criteria = new ArrayList<>();

    private InterviewQuestion() { }

    public InterviewQuestion(String id, String prompt, List<String> requirementIds, List<String> criteria) {
        this.id = id; this.prompt = prompt;
        this.requirementIds = new ArrayList<>(requirementIds); this.criteria = new ArrayList<>(criteria);
    }

    public String getId() { return id; }
    public String getPrompt() { return prompt; }
    public List<String> getRequirementIds() { return requirementIds; }
    public List<String> getCriteria() { return criteria; }
}
