package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InterviewSession extends Entity {
    private String questionSetId;
    private String jobId;
    private int currentIndex;
    private int questionCount;
    private SessionStatus status = SessionStatus.IN_PROGRESS;
    private List<AnswerRecord> answers = new ArrayList<>();

    @Override public String idPrefix() { return "S"; }

    private InterviewSession() { }

    public InterviewSession(QuestionSet set) {
        this.questionSetId = set.getId(); this.jobId = set.getJobId(); this.questionCount = set.getQuestions().size();
    }

    public String getQuestionSetId() { return questionSetId; }
    public String getJobId() { return jobId; }
    public int getCurrentIndex() { return currentIndex; }
    public int getQuestionCount() { return questionCount; }
    public SessionStatus getStatus() { return status; }
    public List<AnswerRecord> getAnswers() { return answers; }

    public void setStatus(SessionStatus s) { status = s; }

    public void recordAnswer(AnswerRecord a) {
        answers.add(a);
        currentIndex++;
        if (currentIndex >= questionCount) status = SessionStatus.COMPLETED;
    }

    public Optional<AnswerRecord> lastPending() {
        for (int i = answers.size() - 1; i >= 0; i--) if (answers.get(i).isFeedbackPending()) return Optional.of(answers.get(i));
        return Optional.empty();
    }
}
