package careerpilot.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.ai.LLMRequest;
import careerpilot.ai.LLMResponse;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.CoverLetter;
import careerpilot.domain.DocumentStatus;
import careerpilot.domain.FitReport;
import careerpilot.domain.JobPosting;
import careerpilot.domain.JobPreview;
import careerpilot.domain.MatchLevel;
import careerpilot.domain.ProfilePreview;
import careerpilot.domain.Resume;
import careerpilot.service.AnswerOutcome;
import careerpilot.service.ExportResult;
import careerpilot.storage.JsonWorkspaceRepository;
import careerpilot.testsupport.Fixtures;
import careerpilot.testsupport.MockLLMClient;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives the main features through the facade, exactly as the GUI and CLI do, with a scripted model. */
class FacadeFlowTest {
    @TempDir Path dir;
    private CareerAssistantFacade facade;
    private MockLLMClient llm;
    private JsonWorkspaceRepository repo;

    private static final String RESUME_JSON = "{\"fullName\":\"Jordan Lee\",\"location\":\"Toronto\",\"summary\":\"\",\"skills\":[\"Java\",\"SQL\"],"
            + "\"education\":[{\"school\":\"York University\",\"credential\":\"BSc\",\"start\":\"Sep 2022\",\"end\":\"Present\"}],"
            + "\"experience\":[{\"title\":\"Intern\",\"organization\":\"Maple Leaf Logistics\",\"start\":\"May 2025\",\"end\":\"Aug 2025\","
            + "\"bullets\":[\"Built a Java REST API used by 3 teams\"]}],"
            + "\"sections\":[{\"heading\":\"Experience\",\"entries\":[\"Built a Java REST API used by 3 teams\"]}]}";
    private static final String JOB_JSON = "{\"title\":\"Junior Developer\",\"company\":\"Northwind\",\"requirements\":["
            + "{\"text\":\"Java REST APIs\",\"category\":\"technical\",\"priority\":\"required\",\"sourceSpan\":\"REST APIs in Java\"},"
            + "{\"text\":\"Docker\",\"category\":\"technical\",\"priority\":\"preferred\",\"sourceSpan\":\"Docker is an asset\"}]}";
    private static final String FIT_JSON = "{\"summary\":\"Java shown; Docker not shown.\",\"matches\":["
            + "{\"requirementId\":\"REQ1\",\"level\":\"SUPPORTED\",\"explanation\":\"\",\"evidence\":[{\"path\":\"experience:E1:bullet:0\",\"quote\":\"Java REST API\"}]},"
            + "{\"requirementId\":\"REQ2\",\"level\":\"NOT_EVIDENCED\",\"explanation\":\"\",\"evidence\":[]}]}";

    @BeforeEach
    void setUp() {
        llm = new MockLLMClient();
        repo = new JsonWorkspaceRepository(dir.resolve("ws"), Fixtures.clock());
        facade = AppFactory.create(repo, llm, Fixtures.clock());
    }

    private FeatureResult ok(FeatureRequest r) {
        FeatureResult res = facade.execute(r);
        assertTrue(res.isOk(), () -> "Expected OK but got: " + res.error());
        return res;
    }

    private void seedProfileAndJob() throws Exception {
        Path resume = dir.resolve("resume.txt");
        Files.writeString(resume, "Jordan Lee\njordan.lee@example.com\nBuilt a Java REST API used by 3 teams\nSkills: Java, SQL");
        llm.thenText(RESUME_JSON);
        ProfilePreview preview = ok(FeatureRequest.of(Operation.IMPORT_RESUME).with("path", resume.toString())).payload(ProfilePreview.class);
        assertEquals("jordan.lee@example.com", preview.profile().getEmail());
        assertEquals("2022-09", preview.profile().getEducation().get(0).getStart());
        assertTrue(repo.list(CandidateProfile.class, null).isEmpty(), "preview must not be saved");
        ok(FeatureRequest.of(Operation.CONFIRM_PROFILE).with("preview", preview));

        llm.thenText(JOB_JSON);
        JobPreview job = ok(FeatureRequest.of(Operation.ANALYZE_JOB).with("text", "We build REST APIs in Java. Docker is an asset. Apply by November 30, 2026."))
                .payload(JobPreview.class);
        assertEquals(java.time.LocalDate.of(2026, 11, 30), job.job().getDeadline());
        ok(FeatureRequest.of(Operation.SAVE_JOB).with("job", job.job()));
    }

    @Test
    void importAnalyzeAndDetectStaleFit() throws Exception {
        seedProfileAndJob();
        llm.thenText(FIT_JSON);
        FitReport fit = ok(FeatureRequest.of(Operation.ANALYZE_FIT).with("jobId", "J1")).payload(FitReport.class);
        assertEquals(1, fit.count(MatchLevel.SUPPORTED));

        CandidateProfile p = ok(FeatureRequest.of(Operation.GET_PROFILE)).payload(CandidateProfile.class);
        p.setSummary("Backend focused");
        ok(FeatureRequest.of(Operation.UPDATE_PROFILE).with("profile", p));
        DocumentStatusCheck.assertFitState(repo, "stale");

        llm.thenText(FIT_JSON).thenText("{\"note\":\"\",\"actions\":[{\"requirementId\":\"REQ2\",\"gapType\":\"LEARNING_NEED\",\"action\":\"Try Docker\",\"estimatedHours\":5}]}");
        ok(FeatureRequest.of(Operation.IDENTIFY_GAPS).with("jobId", "J1"));
        assertEquals("analysis.fit", llm.requests.get(llm.requests.size() - 2).purpose(), "a stale fit must be recomputed before gaps");
    }

    @Test
    void unsupportedResumeChangesCannotBeSavedAndTheBaseResumeStaysUnchanged() throws Exception {
        seedProfileAndJob();
        llm.thenText("{\"changes\":[{\"sectionIndex\":0,\"entryIndex\":0,\"newText\":\"Built a Java REST API used by 30 teams\","
                + "\"reason\":\"\",\"requirementIds\":[\"REQ1\"],\"evidence\":[{\"path\":\"experience:E1:bullet:0\",\"quote\":\"Java REST API\"}]}]}");
        Resume draft = ok(FeatureRequest.of(Operation.TAILOR_RESUME).with("jobId", "J1")).payload(Resume.class);
        assertFalse(draft.getChanges().get(0).isSupported());

        draft.getChanges().get(0).setAccepted(true);
        assertFalse(facade.execute(FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", draft)).isOk());
        draft.getChanges().get(0).setAccepted(false);
        Resume saved = (Resume) ok(FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", draft)).payload();
        assertEquals("Built a Java REST API used by 3 teams", saved.entry(0, 0));
        assertEquals("Built a Java REST API used by 3 teams", repo.load(Resume.class, "R1").entry(0, 0));
    }

    @Test
    void letterWithUnsupportedClaimMustBeEditedBeforeSaving() throws Exception {
        seedProfileAndJob();
        llm.thenText("{\"paragraphs\":[{\"text\":\"Dear Hiring Manager,\",\"evidence\":[]},"
                + "{\"text\":\"I led 12 engineers building Java REST APIs.\",\"evidence\":[{\"path\":\"experience:E1:bullet:0\",\"quote\":\"Java REST API\"}]},"
                + "{\"text\":\"Sincerely, Jordan\",\"evidence\":[]}]}");
        CoverLetter letter = ok(FeatureRequest.of(Operation.DRAFT_LETTER).with("jobId", "J1")).payload(CoverLetter.class);
        assertEquals(1, letter.unsupportedCount());
        assertFalse(facade.execute(FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", letter)).isOk());
        letter.setText(letter.getText().replace("I led 12 engineers building", "I built"));
        ok(FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", letter));
    }

    @Test
    void interviewAnswerIsKeptWhenFeedbackFailsAndCanBeRetried() throws Exception {
        seedProfileAndJob();
        llm.thenText("{\"questions\":[{\"prompt\":\"Describe a REST API you built.\",\"requirementIds\":[\"REQ1\"],\"criteria\":[\"Specific\"]},"
                + "{\"prompt\":\"How would you learn Docker?\",\"requirementIds\":[\"REQ2\"],\"criteria\":[\"Plan\"]}]}");
        ok(FeatureRequest.of(Operation.GENERATE_QUESTIONS).with("jobId", "J1").with("count", 2));
        ok(FeatureRequest.of(Operation.START_INTERVIEW).with("questionSetId", "Q1"));

        llm.thenFail("AI service timed out");
        FeatureResult pending = ok(FeatureRequest.of(Operation.SUBMIT_ANSWER).with("sessionId", "S1").with("answer", "I built a shipment API."));
        assertTrue(pending.payload(AnswerOutcome.class).answer().isFeedbackPending());
        assertFalse(pending.warnings().isEmpty());

        llm.thenText("{\"summary\":\"Good\",\"strengths\":[\"Concrete\"],\"improvements\":[],\"rating\":4}");
        AnswerOutcome retried = ok(FeatureRequest.of(Operation.RETRY_FEEDBACK).with("sessionId", "S1")).payload(AnswerOutcome.class);
        assertEquals(4, retried.answer().getFeedback().getRating());
        assertEquals(1, retried.session().getCurrentIndex());
    }

    @Test
    void agentPlanCanBeSavedAndExported() throws Exception {
        seedProfileAndJob();
        llm.then(LLMResponse.tools(java.util.List.of(new careerpilot.ai.ToolCall("c1", "inspect_documents", "{\"profileId\":\"P1\",\"jobId\":\"J1\"}"))))
                .thenText("{\"status\":\"PLAN\",\"summary\":\"Short plan\",\"tasks\":[{\"id\":\"T1\",\"description\":\"Tailor resume\",\"minutes\":30,\"dependsOn\":[]}]}");
        var preview = ok(FeatureRequest.of(Operation.PREPARE_APPLICATION).with("jobId", "J1").with("minutes", 60))
                .payload(careerpilot.service.PreparationPreview.class);
        assertEquals("inspect_documents", preview.trace().get(0).tool());
        assertNotNull(preview.plan());
        ok(FeatureRequest.of(Operation.SAVE_PLAN).with("plan", preview.plan()).with("minutes", 60));

        ExportResult export = ok(FeatureRequest.of(Operation.EXPORT_PACKAGE).with("jobId", "J1").with("out", dir.resolve("pkg").toString()))
                .payload(ExportResult.class);
        assertTrue(Files.exists(export.folder().resolve("plan.md")));
        assertTrue(export.warnings().stream().anyMatch(w -> w.contains("resume")), "missing resume should be reported");
    }

    @Test
    void errorsComeBackAsResultsNotExceptions() {
        FeatureResult r = facade.execute(FeatureRequest.of(Operation.GET_JOB).with("jobId", "J404"));
        assertFalse(r.isOk());
        assertTrue(r.error().contains("not found"));
        FeatureResult noProfile = facade.execute(FeatureRequest.of(Operation.ANALYZE_FIT).with("jobId", "J1"));
        assertTrue(noProfile.error().contains("Import a resume first"));
    }

    /** Small helper so the stale check reads clearly in the test above. */
    static final class DocumentStatusCheck {
        static void assertFitState(JsonWorkspaceRepository repo, String expected) {
            CandidateProfile p = repo.list(CandidateProfile.class, null).get(0);
            JobPosting j = repo.list(JobPosting.class, null).get(0);
            FitReport f = repo.list(FitReport.class, null).get(0);
            assertEquals(expected, f.isStaleFor(p, j) ? DocumentStatus.STALE : DocumentStatus.CURRENT);
        }
    }
}
