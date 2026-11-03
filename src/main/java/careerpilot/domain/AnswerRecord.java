package careerpilot.domain;

import java.time.Instant;

public class AnswerRecord {
    private String questionId;
    private String answer;
    private Feedback feedback;
    private boolean feedbackPending;
    private Instant answeredAt;

    private AnswerRecord() { }

    public AnswerRecord(String questionId, String answer, Instant answeredAt) {
        this.questionId = questionId; this.answer = answer; this.answeredAt = answeredAt; this.feedbackPending = true;
    }

    public String getQuestionId() { return questionId; }
    public String getAnswer() { return answer; }
    public Feedback getFeedback() { return feedback; }
    public boolean isFeedbackPending() { return feedbackPending; }
    public Instant getAnsweredAt() { return answeredAt; }

    public void completeFeedback(Feedback f) { feedback = f; feedbackPending = false; }
}
