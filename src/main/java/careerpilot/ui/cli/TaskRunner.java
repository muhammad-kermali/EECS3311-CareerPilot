package careerpilot.ui.cli;

import careerpilot.agent.AgentStep;
import careerpilot.app.CareerAssistantFacade;
import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import careerpilot.app.Operation;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.CoverLetter;
import careerpilot.domain.Document;
import careerpilot.domain.FitReport;
import careerpilot.domain.GapAction;
import careerpilot.domain.GapReport;
import careerpilot.domain.InterviewQuestion;
import careerpilot.domain.JobPosting;
import careerpilot.domain.JobPreview;
import careerpilot.domain.MatchLevel;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import careerpilot.domain.ProfilePreview;
import careerpilot.domain.QuestionSet;
import careerpilot.domain.Resume;
import careerpilot.domain.ResumeChange;
import careerpilot.domain.RequirementMatch;
import careerpilot.service.PreparationPreview;
import careerpilot.storage.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a task file without any questions to the user (standalone CLI mode). Every step is a FeatureRequest sent to
 * the same facade the GUI and interactive CLI use, so the behaviour and validation are identical.
 */
public class TaskRunner {
    private final CareerAssistantFacade facade;
    private final PrintStream out;
    private final Path baseDir;
    private final List<Map<String, Object>> steps = new ArrayList<>();

    /** @param baseDir folder of the task file; relative paths are tried against the working folder first, then this one */
    public TaskRunner(CareerAssistantFacade facade, PrintStream out, Path baseDir) {
        this.facade = facade;
        this.out = out;
        this.baseDir = baseDir;
    }

    /** Runs the task and returns the exit code: 0 completed, 1 a step failed, 2 the task file is invalid. */
    public int run(String taskText, boolean json) {
        TaskFile task;
        try {
            task = TaskFile.parse(taskText);
        } catch (IllegalArgumentException e) {
            return finish(json, "INVALID_TASK", e.getMessage(), null, 2);
        }
        if (task.isCommandScript()) return runCommands(task, json);
        try {
            String profileId = profile(task);
            String jobId = job(task);
            Map<String, Object> result = switch (task.task()) {
                case "prepare-application" -> prepare(task, profileId, jobId);
                case "fit" -> fit(profileId, jobId);
                case "gaps" -> gaps(profileId, jobId);
                case "tailor-resume" -> tailor(task, profileId, jobId);
                case "cover-letter" -> letter(task, profileId, jobId);
                case "interview-questions" -> questions(task, profileId, jobId);
                default -> throw new StepFailed("unknown task " + task.task());
            };
            Object agent = result.get("agentStatus");
            if ("TIMED_OUT".equals(agent) || "LIMIT_REACHED".equals(agent) || "FAILED".equals(agent)) {
                return finish(json, "FAILED", "Task " + task.task() + " did not finish: the agent stopped with " + agent, result, 1);
            }
            return finish(json, "COMPLETED", "Task " + task.task() + " finished", result, 0);
        } catch (StepFailed e) {
            return finish(json, "FAILED", e.getMessage(), null, 1);
        }
    }

    // ---------- shared setup steps ----------

    private String profile(TaskFile task) {
        if (task.has("resume")) {
            ProfilePreview preview = call("import resume", FeatureRequest.of(Operation.IMPORT_RESUME).with("path", path(task.get("resume")))).payload(ProfilePreview.class);
            CandidateProfile saved = call("save profile", FeatureRequest.of(Operation.CONFIRM_PROFILE).with("preview", preview)).payload(CandidateProfile.class);
            say("Imported resume as profile " + saved.getId() + " (" + saved.getFullName() + ", "
                    + preview.resume().getSections().size() + " resume sections)");
            return saved.getId();
        }
        CandidateProfile p = call("load profile", FeatureRequest.of(Operation.GET_PROFILE)).payload(CandidateProfile.class);
        say("Using saved profile " + p.getId() + " (" + p.getFullName() + ")");
        return p.getId();
    }

    private String job(TaskFile task) {
        if (task.has("job-id")) {
            JobPosting j = call("load job", FeatureRequest.of(Operation.GET_JOB).with("jobId", task.get("job-id"))).payload(JobPosting.class);
            say("Using saved job " + j.label());
            return j.getId();
        }
        JobPreview preview = call("analyze job", FeatureRequest.of(Operation.ANALYZE_JOB_FILE).with("path", path(task.get("job")))
                .with("company", task.get("company")).with("deadline", task.get("deadline"))).payload(JobPreview.class);
        JobPosting saved = call("save job", FeatureRequest.of(Operation.SAVE_JOB).with("job", preview.job())).payload(JobPosting.class);
        say("Analyzed job as " + saved.label() + " (" + saved.getRequirements().size() + " requirements)");
        return saved.getId();
    }

    // ---------- tasks ----------

    private Map<String, Object> prepare(TaskFile task, String profileId, String jobId) {
        say("Agent is preparing the application (up to 8 tool calls; this can take a minute or two)...");
        PreparationPreview p = call("run agent", FeatureRequest.of(Operation.PREPARE_APPLICATION).with("profileId", profileId).with("jobId", jobId)
                .with("minutes", task.get("minutes")).with("deadline", task.get("deadline")).with("preferences", task.get("preferences")))
                .payload(PreparationPreview.class);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("agentStatus", p.status().name());
        r.put("summary", p.summary());
        if (p.question() != null) r.put("question", p.question());
        List<String> trace = new ArrayList<>();
        for (AgentStep s : p.trace()) trace.add(s.toString());
        r.put("toolCalls", trace);
        say("Agent steps:");
        trace.forEach(t -> say("  " + t));
        say("Agent result: " + p.status() + (p.summary() == null ? "" : ". " + p.summary()));
        if (p.question() != null) say("The agent needs input: " + p.question());
        if (p.plan() != null) {
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (PreparationTask t : p.plan().getTasks()) {
                tasks.add(Map.of("id", t.getId(), "description", t.getDescription(), "minutes", t.getMinutes(), "dependsOn", t.getDependsOn()));
                say("  " + t.getId() + ". " + t.getDescription() + " (" + t.getMinutes() + " min"
                        + (t.getDependsOn().isEmpty() ? "" : ", after " + String.join(", ", t.getDependsOn())) + ")");
            }
            r.put("plan", tasks);
            r.put("totalMinutes", p.plan().totalMinutes());
            r.put("availableMinutes", p.constraints().availableMinutes());
            if (p.validation() != null) {
                r.put("planValid", p.validation().feasible());
                r.put("planProblems", p.validation().errors());
                p.validation().errors().forEach(e -> say("  problem: " + e));
            }
            if (task.flag("save", true) && p.validation() != null && p.validation().feasible()) {
                PreparationPlan saved = call("save plan", FeatureRequest.of(Operation.SAVE_PLAN).with("plan", p.plan())
                        .with("minutes", p.constraints().availableMinutes())
                        .with("deadline", p.constraints().deadline() == null ? null : p.constraints().deadline().toString())).payload(PreparationPlan.class);
                r.put("savedPlanId", saved.getId());
                say("Saved plan " + saved.getId() + " (" + p.plan().totalMinutes() + " of " + p.constraints().availableMinutes() + " minutes)");
            }
        }
        List<String> drafts = new ArrayList<>();
        for (Document d : p.drafts()) {
            boolean ok = !(d instanceof CoverLetter l && l.unsupportedCount() > 0);
            if (d instanceof Resume res) res.getChanges().forEach(c -> c.setAccepted(c.isSupported()));
            if (task.flag("save-drafts", false) && ok) {
                Document saved = call("save draft", FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", d)).payload(Document.class);
                drafts.add(saved.describe() + " (saved)");
            } else {
                drafts.add((d instanceof Resume ? "tailored resume" : "cover letter") + " draft (not saved)");
            }
        }
        if (!drafts.isEmpty()) {
            r.put("drafts", drafts);
            drafts.forEach(d -> say("Draft: " + d));
        }
        r.put("warnings", p.warnings());
        return r;
    }

    private Map<String, Object> fit(String profileId, String jobId) {
        FitReport f = call("fit analysis", FeatureRequest.of(Operation.ANALYZE_FIT).with("profileId", profileId).with("jobId", jobId)).payload(FitReport.class);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fitReportId", f.getId());
        r.put("summary", f.getSummary());
        List<Map<String, Object>> matches = new ArrayList<>();
        for (RequirementMatch m : f.getMatches()) {
            matches.add(Map.of("requirementId", m.getRequirementId(), "level", m.getLevel().name(),
                    "evidence", m.getEvidence().stream().map(Object::toString).toList()));
            say("  [" + m.getLevel() + "] " + m.getRequirementId() + (m.getEvidence().isEmpty() ? "" : "  evidence: " + m.getEvidence().get(0)));
        }
        r.put("matches", matches);
        r.put("supported", f.count(MatchLevel.SUPPORTED));
        r.put("partial", f.count(MatchLevel.PARTIAL));
        r.put("notEvidenced", f.count(MatchLevel.NOT_EVIDENCED));
        say("Fit report " + f.getId() + ": " + f.count(MatchLevel.SUPPORTED) + " supported, " + f.count(MatchLevel.PARTIAL)
                + " partial, " + f.count(MatchLevel.NOT_EVIDENCED) + " not evidenced");
        return r;
    }

    private Map<String, Object> gaps(String profileId, String jobId) {
        GapReport g = call("gap analysis", FeatureRequest.of(Operation.IDENTIFY_GAPS).with("profileId", profileId).with("jobId", jobId)).payload(GapReport.class);
        List<Map<String, Object>> actions = new ArrayList<>();
        for (GapAction a : g.getActions()) {
            actions.add(Map.of("requirementId", a.getRequirementId(), "type", a.getGapType().name(), "action", a.getAction(), "hours", a.getEstimatedHours()));
            say("  " + a.getRequirementId() + " [" + a.getGapType() + ", ~" + a.getEstimatedHours() + "h] " + a.getAction());
        }
        say("Gap report " + g.getId() + ": " + actions.size() + " action(s)");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("gapReportId", g.getId());
        r.put("note", g.getNote());
        r.put("actions", actions);
        return r;
    }

    private Map<String, Object> tailor(TaskFile task, String profileId, String jobId) {
        Resume draft = call("tailor resume", FeatureRequest.of(Operation.TAILOR_RESUME).with("profileId", profileId).with("jobId", jobId)).payload(Resume.class);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (ResumeChange c : draft.getChanges()) {
            c.setAccepted(c.isSupported());
            changes.add(Map.of("id", c.getId(), "before", c.getBefore(), "after", c.getAfter(), "supported", c.isSupported()));
            say("  " + c.getId() + (c.isSupported() ? " [kept] " : " [left out: " + c.getUnsupportedReason() + "] ") + c.getAfter());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("changes", changes);
        if (task.flag("save", true)) {
            Document saved = call("save resume", FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", draft)).payload(Document.class);
            r.put("savedResumeId", saved.getId());
            say("Saved " + saved.describe() + " with only the supported changes");
        }
        return r;
    }

    private Map<String, Object> letter(TaskFile task, String profileId, String jobId) {
        CoverLetter l = call("draft cover letter", FeatureRequest.of(Operation.DRAFT_LETTER).with("profileId", profileId).with("jobId", jobId)
                .with("tone", task.get("tone")).with("motivation", task.get("motivation"))).payload(CoverLetter.class);
        say("");
        say(l.getText());
        say("");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("text", l.getText());
        r.put("unsupportedParagraphs", l.unsupportedCount());
        r.put("warnings", l.getWarnings());
        if (l.unsupportedCount() > 0) {
            say("Not saved: " + l.unsupportedCount() + " paragraph(s) have unsupported claims. " + String.join(" ", l.getWarnings()));
        } else if (task.flag("save", true)) {
            Document saved = call("save letter", FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", l)).payload(Document.class);
            r.put("savedLetterId", saved.getId());
            say("Saved " + saved.describe());
        }
        return r;
    }

    private Map<String, Object> questions(TaskFile task, String profileId, String jobId) {
        QuestionSet q = call("generate questions", FeatureRequest.of(Operation.GENERATE_QUESTIONS).with("profileId", profileId).with("jobId", jobId)
                .with("count", task.get("count")).with("focus", task.get("focus"))).payload(QuestionSet.class);
        List<Map<String, Object>> list = new ArrayList<>();
        for (InterviewQuestion iq : q.getQuestions()) {
            list.add(Map.of("id", iq.getId(), "prompt", iq.getPrompt(), "requirementIds", iq.getRequirementIds()));
            say("  " + iq.getId() + ". " + iq.getPrompt());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("questionSetId", q.getId());
        r.put("questions", list);
        say("Saved question set " + q.getId());
        return r;
    }

    // ---------- command scripts ----------

    private int runCommands(TaskFile task, boolean json) {
        int worst = 0;
        for (List<String> cmd : task.commands()) {
            List<String> args = new ArrayList<>(cmd);
            if (!args.contains("--yes")) args.add("--yes");
            if (!json) out.println("$ career " + String.join(" ", cmd));
            PrintStream target = json ? new PrintStream(java.io.OutputStream.nullOutputStream()) : out;
            int code = new CareerCLI(facade, new ByteArrayInputStream(new byte[0]), target).run(args.toArray(new String[0]));
            steps.add(Map.of("command", String.join(" ", cmd), "exitCode", code));
            worst = Math.max(worst, code);
        }
        return finish(json, worst == 0 ? "COMPLETED" : "FAILED", worst == 0 ? "All commands succeeded" : "At least one command failed", null, worst == 0 ? 0 : 1);
    }

    // ---------- helpers ----------

    private FeatureResult call(String step, FeatureRequest request) {
        FeatureResult r = facade.execute(request);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", step);
        s.put("ok", r.isOk());
        if (!r.isOk()) s.put("error", r.error());
        if (!r.warnings().isEmpty()) s.put("warnings", r.warnings());
        steps.add(s);
        if (!r.isOk()) throw new StepFailed(step + " failed: " + r.error());
        r.warnings().forEach(w -> say("Warning: " + w));
        return r;
    }

    private String path(String value) {
        Path p = Path.of(value);
        if (p.isAbsolute() || Files.exists(p) || baseDir == null) return p.toString();
        Path beside = baseDir.resolve(value);
        return Files.exists(beside) ? beside.toString() : p.toString();
    }

    private boolean quiet;

    private void say(String line) { if (!quiet) out.println(line); }

    private int finish(boolean json, String status, String message, Map<String, Object> result, int code) {
        if (json) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("status", status);
            doc.put("message", message);
            doc.put("steps", steps);
            if (result != null) doc.put("result", result);
            try {
                out.println(JsonMapper.get().writeValueAsString(doc));
            } catch (JsonProcessingException e) {
                out.println("{\"status\":\"FAILED\",\"message\":\"could not write JSON\"}");
                return 1;
            }
        } else {
            out.println((code == 0 ? "Done: " : "Error: ") + message);
        }
        return code;
    }

    /** Lets json mode suppress the step-by-step text so standard output holds only the JSON document. */
    public TaskRunner quietFor(boolean json) {
        this.quiet = json;
        return this;
    }

    private static final class StepFailed extends RuntimeException {
        StepFailed(String m) { super(m); }
    }
}
