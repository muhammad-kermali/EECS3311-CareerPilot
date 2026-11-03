package careerpilot.service;

import careerpilot.ai.Json;
import careerpilot.ai.LLMRequest;
import careerpilot.ai.OutputFormatException;
import careerpilot.ai.Prompts;
import careerpilot.ai.StructuredOutput;
import careerpilot.common.CareerPilotException;
import careerpilot.common.TextUtil;
import careerpilot.common.ValidationException;
import careerpilot.domain.AnswerRecord;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.Feedback;
import careerpilot.domain.InterviewQuestion;
import careerpilot.domain.InterviewSession;
import careerpilot.domain.JobPosting;
import careerpilot.domain.QuestionSet;
import careerpilot.domain.SessionStatus;
import careerpilot.storage.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** F08 interview questions, F09 mock interview with feedback. */
public class InterviewService {
    public static final Set<String> FOCUSES = Set.of("technical", "behavioural", "mixed");

    private final WorkspaceRepository repo;
    private final StructuredOutput ai;
    private final Clock clock;

    public InterviewService(WorkspaceRepository repo, StructuredOutput ai, Clock clock) {
        this.repo = repo;
        this.ai = ai;
        this.clock = clock;
    }

    public QuestionSet generateQuestions(String profileId, String jobId, int count, String focus) {
        if (count < 1 || count > 20) throw new ValidationException("Question count must be between 1 and 20");
        String f = focus == null || focus.isBlank() ? "mixed" : focus.trim().toLowerCase(Locale.ROOT).replace("behavioral", "behavioural");
        if (!FOCUSES.contains(f)) throw new ValidationException("Focus must be one of " + FOCUSES);
        CandidateProfile profile = repo.load(CandidateProfile.class, profileId);
        JobPosting job = repo.load(JobPosting.class, jobId);
        String user = "Number of questions: " + count + "\nFocus: " + f + "\nRole: " + job.getTitle()
                + "\n\nJob requirements:\n" + DocumentService.requirementsText(job)
                + "\nCandidate background (for context only):\n<profile>\nSkills: " + String.join(", ", profile.getSkills())
                + "\nExperience: " + profile.getExperience().stream().map(e -> e.describe()).toList() + "\n</profile>";
        LLMRequest req = LLMRequest.json("interview.questions", Prompts.questionSystem(), user).withTemperature(0.6);
        List<InterviewQuestion> questions = ai.generate(req, json -> parseQuestions(json, job, count));
        return repo.save(new QuestionSet(profileId, jobId, f, questions));
    }

    private List<InterviewQuestion> parseQuestions(JsonNode json, JobPosting job, int count) throws OutputFormatException {
        List<InterviewQuestion> out = new ArrayList<>();
        Set<String> prompts = new HashSet<>();
        int n = 1;
        for (JsonNode q : Json.array(json, "questions")) {
            String prompt = Json.text(q, "prompt", true);
            if (!prompts.add(TextUtil.normalize(prompt))) throw new OutputFormatException("duplicate question: " + TextUtil.truncate(prompt, 60));
            List<String> reqIds = Json.strings(q, "requirementIds");
            for (String id : reqIds) if (job.requirement(id).isEmpty()) throw new OutputFormatException("unknown requirementId '" + id + "'");
            if (reqIds.isEmpty()) throw new OutputFormatException("every question needs at least one requirementId");
            out.add(new InterviewQuestion("IQ" + n++, prompt, reqIds, Json.strings(q, "criteria")));
        }
        if (out.size() != count) throw new OutputFormatException("exactly " + count + " questions are required, got " + out.size());
        return out;
    }

    public List<QuestionSet> listQuestionSets(String jobId) {
        return repo.list(QuestionSet.class, q -> jobId == null || jobId.equals(q.getJobId()));
    }

    public InterviewSession startInterview(String questionSetId) {
        QuestionSet set = repo.load(QuestionSet.class, questionSetId);
        if (set.getQuestions().isEmpty()) throw new ValidationException("This question set is empty");
        return repo.save(new InterviewSession(set));
    }

    public InterviewSession getSession(String sessionId) { return repo.load(InterviewSession.class, sessionId); }

    public InterviewQuestion currentQuestion(InterviewSession session) {
        QuestionSet set = repo.load(QuestionSet.class, session.getQuestionSetId());
        int i = session.getCurrentIndex();
        return i < set.getQuestions().size() ? set.getQuestions().get(i) : null;
    }

    /** Saves the answer first, then asks for feedback. If feedback fails the answer stays saved as pending. */
    public AnswerOutcome submitAnswer(String sessionId, String answer) {
        if (TextUtil.isBlank(answer)) throw new ValidationException("The answer is blank");
        InterviewSession session = repo.load(InterviewSession.class, sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) throw new ValidationException("This interview session is already complete");
        QuestionSet set = repo.load(QuestionSet.class, session.getQuestionSetId());
        InterviewQuestion question = set.getQuestions().get(session.getCurrentIndex());
        AnswerRecord record = new AnswerRecord(question.getId(), answer.trim(), clock.instant());
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.recordAnswer(record);
        repo.save(session);

        List<String> warnings = new ArrayList<>();
        try {
            record.completeFeedback(requestFeedback(question, record.getAnswer()));
            repo.save(session);
        } catch (CareerPilotException e) {
            warnings.add("Your answer was saved, but feedback is pending (" + e.getMessage() + "). Use Retry Feedback later.");
        }
        InterviewQuestion next = session.getStatus() == SessionStatus.COMPLETED ? null : set.getQuestions().get(session.getCurrentIndex());
        return new AnswerOutcome(session, record, next, warnings);
    }

    public AnswerOutcome retryFeedback(String sessionId) {
        InterviewSession session = repo.load(InterviewSession.class, sessionId);
        AnswerRecord pending = session.lastPending().orElseThrow(() -> new ValidationException("No answer in this session is waiting for feedback"));
        QuestionSet set = repo.load(QuestionSet.class, session.getQuestionSetId());
        InterviewQuestion question = set.getQuestions().stream().filter(q -> q.getId().equals(pending.getQuestionId())).findFirst().orElseThrow();
        pending.completeFeedback(requestFeedback(question, pending.getAnswer()));
        repo.save(session);
        InterviewQuestion next = session.getStatus() == SessionStatus.COMPLETED ? null : set.getQuestions().get(session.getCurrentIndex());
        return new AnswerOutcome(session, pending, next, List.of());
    }

    public InterviewSession pauseInterview(String sessionId) {
        InterviewSession session = repo.load(InterviewSession.class, sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) throw new ValidationException("A completed session cannot be paused");
        session.setStatus(SessionStatus.PAUSED);
        return repo.save(session);
    }

    private Feedback requestFeedback(InterviewQuestion q, String answer) {
        String user = "Question: " + q.getPrompt() + "\nCriteria for a strong answer: " + String.join("; ", q.getCriteria())
                + "\n<answer>\n" + answer + "\n</answer>";
        LLMRequest req = LLMRequest.json("interview.feedback", Prompts.feedbackSystem(), user);
        return ai.generate(req, json -> new Feedback(Json.text(json, "summary", true), Json.strings(json, "strengths"),
                Json.strings(json, "improvements"), Json.integer(json, "rating", 1, 5)));
    }
}
