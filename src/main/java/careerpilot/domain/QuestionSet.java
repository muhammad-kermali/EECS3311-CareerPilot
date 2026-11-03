package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class QuestionSet extends Entity {
    private String profileId;
    private String jobId;
    private String focus;
    private List<InterviewQuestion> questions = new ArrayList<>();

    @Override public String idPrefix() { return "Q"; }

    private QuestionSet() { }

    public QuestionSet(String profileId, String jobId, String focus, List<InterviewQuestion> questions) {
        this.profileId = profileId; this.jobId = jobId; this.focus = focus; this.questions = new ArrayList<>(questions);
    }

    public String getProfileId() { return profileId; }
    public String getJobId() { return jobId; }
    public String getFocus() { return focus; }
    public List<InterviewQuestion> getQuestions() { return questions; }
}
