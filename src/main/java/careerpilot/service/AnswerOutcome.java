package careerpilot.service;

import careerpilot.domain.AnswerRecord;
import careerpilot.domain.HasWarnings;
import careerpilot.domain.InterviewQuestion;
import careerpilot.domain.InterviewSession;
import java.util.List;

/** Result of submitting an answer: the saved session, the answer, and the next question (if any). */
public record AnswerOutcome(InterviewSession session, AnswerRecord answer, InterviewQuestion nextQuestion, List<String> warnings)
        implements HasWarnings { }
