package careerpilot.ui.cli;

import careerpilot.agent.AgentStep;
import careerpilot.app.CareerAssistantFacade;
import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import careerpilot.app.Operation;
import careerpilot.domain.AnswerRecord;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.CoverLetter;
import careerpilot.domain.Document;
import careerpilot.domain.EducationItem;
import careerpilot.domain.ExperienceItem;
import careerpilot.domain.FitReport;
import careerpilot.domain.GapAction;
import careerpilot.domain.GapReport;
import careerpilot.domain.InterviewQuestion;
import careerpilot.domain.InterviewSession;
import careerpilot.domain.JobPosting;
import careerpilot.domain.JobPreview;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import careerpilot.domain.ProfilePreview;
import careerpilot.domain.QuestionSet;
import careerpilot.domain.Requirement;
import careerpilot.domain.RequirementMatch;
import careerpilot.domain.Resume;
import careerpilot.domain.ResumeChange;
import careerpilot.domain.SessionStatus;
import careerpilot.service.AnswerOutcome;
import careerpilot.service.ApplicationView;
import careerpilot.service.ExportResult;
import careerpilot.service.PreparationPreview;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Command-line boundary. Every command becomes a FeatureRequest sent to the shared facade. */
public class CareerCLI {
    private final CareerAssistantFacade facade;
    private final BufferedReader in;
    private final PrintStream out;
    private boolean assumeYes;

    public CareerCLI(CareerAssistantFacade facade, InputStream in, PrintStream out) {
        this.facade = facade;
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
    }

    /** Sends a request through the facade (same path as the GUI). */
    public FeatureResult submit(FeatureRequest request) { return facade.execute(request); }

    public int run(String[] argv) {
        CliArgs a = new CliArgs(argv);
        assumeYes = a.flag("yes");
        String cmd = (a.positional(0) + " " + a.positional(1)).trim();
        try {
            return switch (cmd) {
                case "resume import" -> resumeImport(a);
                case "profile show" -> profileShow(a);
                case "profile edit" -> profileEdit(a);
                case "job import" -> jobImport(a);
                case "job list" -> jobList();
                case "job show" -> jobShow(a);
                case "job fit" -> jobFit(a);
                case "job gaps" -> jobGaps(a);
                case "resume tailor" -> resumeTailor(a);
                case "letter draft" -> letterDraft(a);
                case "documents list" -> documentsList(a);
                case "interview questions" -> interviewQuestions(a);
                case "interview sets" -> interviewSets(a);
                case "interview start" -> interviewStart(a);
                case "interview resume" -> interviewLoop(require(a, "session"));
                case "interview answer" -> interviewAnswer(a);
                case "interview retry" -> interviewRetry(a);
                case "interview pause" -> interviewPause(a);
                case "application update" -> applicationUpdate(a);
                case "application list" -> applicationList(a);
                case "plan prepare" -> planPrepare(a);
                default -> {
                    if (a.positional(0).equals("export")) yield export(a);
                    if (a.positional(0).isEmpty() || a.positional(0).equals("help")) { help(); yield 0; }
                    out.println("Unknown command: " + cmd);
                    help();
                    yield 2;
                }
            };
        } catch (UsageException e) {
            out.println("Error: " + e.getMessage());
            return 2;
        }
    }

    // ---------- F01 / F02 ----------

    private int resumeImport(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.IMPORT_RESUME).with("path", require(a, "file")), "Reading resume and extracting profile");
        if (r == null) return 1;
        ProfilePreview p = r.payload(ProfilePreview.class);
        printProfile(p.profile());
        out.println("\nResume sections: " + p.resume().getSections().stream().map(s -> s.getHeading()).toList());
        printWarnings(r);
        if (!confirm("Save this profile and base resume?")) { out.println("Not saved."); return 0; }
        FeatureResult saved = call(FeatureRequest.of(Operation.CONFIRM_PROFILE).with("preview", p), null);
        if (saved == null) return 1;
        out.println("Saved profile " + saved.payload(CandidateProfile.class).getId() + ".");
        return 0;
    }

    private int profileShow(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.GET_PROFILE).with("profileId", a.opt("profile")), null);
        if (r == null) return 1;
        printProfile(r.payload(CandidateProfile.class));
        return 0;
    }

    private int profileEdit(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.GET_PROFILE).with("profileId", a.opt("profile")), null);
        if (r == null) return 1;
        CandidateProfile p = r.payload(CandidateProfile.class);
        if (a.has("name")) p.setFullName(a.opt("name"));
        if (a.has("email")) p.setEmail(a.opt("email"));
        if (a.has("phone")) p.setPhone(a.opt("phone"));
        if (a.has("location")) p.setLocation(a.opt("location"));
        if (a.has("summary")) p.setSummary(a.opt("summary"));
        List<String> skills = new ArrayList<>(p.getSkills());
        if (a.has("add-skill")) for (String s : a.opt("add-skill").split(",")) if (!s.isBlank() && skills.stream().noneMatch(x -> x.equalsIgnoreCase(s.trim()))) skills.add(s.trim());
        if (a.has("remove-skill")) for (String s : a.opt("remove-skill").split(",")) skills.removeIf(x -> x.equalsIgnoreCase(s.trim()));
        p.setSkills(skills);
        if (a.has("entry")) {
            String id = a.opt("entry");
            boolean found = false;
            for (ExperienceItem e : p.getExperience()) if (e.getId().equals(id)) { found = true; if (a.has("start")) e.setStart(a.opt("start")); if (a.has("end")) e.setEnd(a.opt("end")); }
            for (EducationItem e : p.getEducation()) if (e.getId().equals(id)) { found = true; if (a.has("start")) e.setStart(a.opt("start")); if (a.has("end")) e.setEnd(a.opt("end")); }
            if (!found) { out.println("Error: no experience or education entry " + id); return 1; }
        }
        FeatureResult saved = call(FeatureRequest.of(Operation.UPDATE_PROFILE).with("profile", p), null);
        if (saved == null) return 1;
        out.println("Profile " + p.getId() + " saved (revision " + saved.payload(CandidateProfile.class).getRevision() + ").");
        return 0;
    }

    // ---------- F03 / F04 / F05 ----------

    private int jobImport(CliArgs a) {
        FeatureRequest req = a.has("file")
                ? FeatureRequest.of(Operation.ANALYZE_JOB_FILE).with("path", a.opt("file"))
                : FeatureRequest.of(Operation.ANALYZE_JOB).with("text", require(a, "text"));
        req.with("company", a.opt("company")).with("url", a.opt("url")).with("deadline", a.opt("deadline"));
        FeatureResult r = call(req, "Analyzing job posting");
        if (r == null) return 1;
        JobPreview preview = r.payload(JobPreview.class);
        printJob(preview.job());
        printWarnings(r);
        if (!confirm("Save this job?")) { out.println("Not saved."); return 0; }
        FeatureResult saved = call(FeatureRequest.of(Operation.SAVE_JOB).with("job", preview.job()), null);
        if (saved == null) return 1;
        out.println("Saved job " + saved.payload(JobPosting.class).getId() + ".");
        return 0;
    }

    private int jobList() {
        FeatureResult r = call(FeatureRequest.of(Operation.LIST_JOBS), null);
        if (r == null) return 1;
        List<?> jobs = (List<?>) r.payload();
        if (jobs.isEmpty()) out.println("No jobs yet. Add one with: career job import --file posting.txt");
        for (Object o : jobs) {
            JobPosting j = (JobPosting) o;
            out.println(j.label() + "  (" + j.getRequirements().size() + " requirements, deadline " + (j.getDeadline() == null ? "unknown" : j.getDeadline()) + ")");
        }
        return 0;
    }

    private int jobShow(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.GET_JOB).with("jobId", require(a, "job")), null);
        if (r == null) return 1;
        printJob(r.payload(JobPosting.class));
        return 0;
    }

    private int jobFit(CliArgs a) {
        String jobId = require(a, "job");
        FeatureResult r = call(FeatureRequest.of(Operation.ANALYZE_FIT).with("jobId", jobId).with("profileId", a.opt("profile")), "Comparing your profile with the job");
        if (r == null) return 1;
        FitReport fit = r.payload(FitReport.class);
        JobPosting job = call(FeatureRequest.of(Operation.GET_JOB).with("jobId", jobId), null).payload(JobPosting.class);
        out.println("Fit report " + fit.getId() + " for " + job.label());
        out.println(fit.getSummary());
        for (RequirementMatch m : fit.getMatches()) {
            Requirement req = job.requirement(m.getRequirementId()).orElse(null);
            out.println("\n[" + m.getLevel() + "] " + m.getRequirementId() + ": " + (req == null ? "" : req.getText()));
            if (m.getExplanation() != null && !m.getExplanation().isBlank()) out.println("   " + m.getExplanation());
            m.getEvidence().forEach(e -> out.println("   evidence: " + e));
        }
        out.println("\nSupported " + fit.count(careerpilot.domain.MatchLevel.SUPPORTED) + ", partial " + fit.count(careerpilot.domain.MatchLevel.PARTIAL)
                + ", not evidenced " + fit.count(careerpilot.domain.MatchLevel.NOT_EVIDENCED)
                + ". (Not evidenced means your profile does not show it, not that you lack it.)");
        printWarnings(r);
        return 0;
    }

    private int jobGaps(CliArgs a) {
        String jobId = require(a, "job");
        FeatureResult r = call(FeatureRequest.of(Operation.IDENTIFY_GAPS).with("jobId", jobId).with("profileId", a.opt("profile")), "Finding gaps and next steps");
        if (r == null) return 1;
        GapReport g = r.payload(GapReport.class);
        out.println("Gap report " + g.getId() + " (based on fit report " + g.getFitReportId() + ")");
        if (g.getNote() != null && !g.getNote().isBlank()) out.println(g.getNote());
        for (GapAction act : g.getActions()) {
            out.println("- " + act.getRequirementId() + " [" + act.getGapType() + ", ~" + act.getEstimatedHours() + "h] " + act.getAction());
        }
        return 0;
    }

    // ---------- F06 / F07 ----------

    private int resumeTailor(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.TAILOR_RESUME).with("jobId", require(a, "job")).with("profileId", a.opt("profile")), "Tailoring your resume");
        if (r == null) return 1;
        Resume draft = r.payload(Resume.class);
        if (draft.getChanges().isEmpty()) out.println("No truthful changes were suggested for this job.");
        for (ResumeChange c : draft.getChanges()) {
            out.println("\n" + c.getId() + (c.isSupported() ? " [supported]" : " [NOT SUPPORTED: " + c.getUnsupportedReason() + "]"));
            out.println("  before: " + c.getBefore());
            out.println("  after:  " + c.getAfter());
            out.println("  why:    " + c.getReason() + " " + c.getRequirementIds());
            c.getEvidence().forEach(e -> out.println("  evidence: " + e));
        }
        for (ResumeChange c : draft.getChanges()) {
            if (!c.isSupported()) { c.setAccepted(false); continue; }
            c.setAccepted(assumeYes || confirm("Accept " + c.getId() + "?"));
        }
        long rejected = draft.getChanges().stream().filter(c -> !c.isSupported()).count();
        if (rejected > 0) out.println(rejected + " unsupported change(s) were left out.");
        if (!confirm("Save this tailored resume?")) { out.println("Discarded. Your base resume is unchanged."); return 0; }
        return saveDraft(draft);
    }

    private int letterDraft(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.DRAFT_LETTER).with("jobId", require(a, "job")).with("profileId", a.opt("profile"))
                .with("tone", a.opt("tone")).with("motivation", a.opt("motivation")), "Drafting cover letter");
        if (r == null) return 1;
        CoverLetter letter = r.payload(CoverLetter.class);
        out.println("\n" + letter.getText() + "\n");
        printWarnings(r);
        if (letter.unsupportedCount() > 0) {
            out.println("This letter has unsupported claims. Edit it in the GUI, or redraft. It cannot be saved unedited.");
            return 0;
        }
        if (!confirm("Save this letter?")) { out.println("Not saved."); return 0; }
        return saveDraft(letter);
    }

    private int saveDraft(Document d) {
        FeatureResult saved = call(FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", d), null);
        if (saved == null) return 1;
        out.println("Saved " + saved.payload(Document.class).describe() + ".");
        return 0;
    }

    private int documentsList(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.LIST_DOCUMENTS).with("jobId", a.opt("job")), null);
        if (r == null) return 1;
        List<?> docs = (List<?>) r.payload();
        if (docs.isEmpty()) out.println("No saved documents yet.");
        for (Object o : docs) out.println(((Document) o).describe());
        return 0;
    }

    // ---------- F08 / F09 ----------

    private int interviewQuestions(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.GENERATE_QUESTIONS).with("jobId", require(a, "job")).with("profileId", a.opt("profile"))
                .with("count", a.opt("count")).with("focus", a.opt("focus")), "Generating questions");
        if (r == null) return 1;
        QuestionSet set = r.payload(QuestionSet.class);
        out.println("Question set " + set.getId() + " (" + set.getFocus() + "):");
        for (InterviewQuestion q : set.getQuestions()) out.println(q.getId() + ". " + q.getPrompt() + "  " + q.getRequirementIds());
        out.println("\nPractice with: career interview start --questions " + set.getId());
        return 0;
    }

    private int interviewSets(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.LIST_QUESTION_SETS).with("jobId", a.opt("job")), null);
        if (r == null) return 1;
        for (Object o : (List<?>) r.payload()) {
            QuestionSet s = (QuestionSet) o;
            out.println(s.getId() + " for " + s.getJobId() + ": " + s.getQuestions().size() + " " + s.getFocus() + " questions");
        }
        return 0;
    }

    private int interviewStart(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.START_INTERVIEW).with("questionSetId", require(a, "questions")), null);
        if (r == null) return 1;
        InterviewSession s = r.payload(InterviewSession.class);
        out.println("Started session " + s.getId() + ". Type your answer on one line. Type /pause to stop and continue later.");
        return interviewLoop(s.getId());
    }

    private int interviewLoop(String sessionId) {
        while (true) {
            FeatureResult qr = call(FeatureRequest.of(Operation.CURRENT_QUESTION).with("sessionId", sessionId), null);
            if (qr == null) return 1;
            InterviewQuestion q = (InterviewQuestion) qr.payload();
            if (q == null) { out.println("Session complete. Well done."); return 0; }
            out.println("\nQ: " + q.getPrompt());
            out.print("> ");
            out.flush();
            String line = readLine();
            if (line == null || line.trim().equals("/pause")) {
                call(FeatureRequest.of(Operation.PAUSE_INTERVIEW).with("sessionId", sessionId), null);
                out.println("Paused. Continue with: career interview resume --session " + sessionId);
                return 0;
            }
            if (line.isBlank()) { out.println("Please type an answer (or /pause)."); continue; }
            FeatureResult ar = call(FeatureRequest.of(Operation.SUBMIT_ANSWER).with("sessionId", sessionId).with("answer", line), "Getting feedback");
            if (ar == null) continue;
            printOutcome(ar);
        }
    }

    private int interviewAnswer(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.SUBMIT_ANSWER).with("sessionId", require(a, "session")).with("answer", a.opt("text")), "Getting feedback");
        if (r == null) return 1;
        printOutcome(r);
        return 0;
    }

    private int interviewRetry(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.RETRY_FEEDBACK).with("sessionId", require(a, "session")), "Retrying feedback");
        if (r == null) return 1;
        printOutcome(r);
        return 0;
    }

    private int interviewPause(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.PAUSE_INTERVIEW).with("sessionId", require(a, "session")), null);
        if (r == null) return 1;
        out.println("Session " + r.payload(InterviewSession.class).getId() + " paused.");
        return 0;
    }

    private void printOutcome(FeatureResult r) {
        AnswerOutcome o = r.payload(AnswerOutcome.class);
        AnswerRecord ans = o.answer();
        if (ans.getFeedback() != null) {
            out.println("Feedback (" + ans.getFeedback().getRating() + "/5): " + ans.getFeedback().getSummary());
            ans.getFeedback().getStrengths().forEach(s -> out.println("  + " + s));
            ans.getFeedback().getImprovements().forEach(s -> out.println("  - " + s));
        }
        printWarnings(r);
        InterviewSession s = o.session();
        out.println("Progress: " + s.getCurrentIndex() + "/" + s.getQuestionCount() + (s.getStatus() == SessionStatus.COMPLETED ? " (complete)" : ""));
    }

    // ---------- F10 ----------

    private int applicationUpdate(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.UPDATE_APPLICATION).with("jobId", require(a, "job")).with("status", a.opt("status"))
                .with("notes", a.opt("notes")).with("deadline", a.opt("deadline")).with("appliedOn", a.opt("applied-on"))
                .with("expectedRevision", a.opt("revision")), null);
        if (r == null) return 1;
        var rec = r.payload(careerpilot.domain.ApplicationRecord.class);
        out.println("Application " + rec.getId() + " for " + rec.getJobId() + ": " + rec.getStatus().name().toLowerCase()
                + (rec.getDeadline() == null ? "" : ", deadline " + rec.getDeadline()) + " (revision " + rec.getRevision() + ")");
        return 0;
    }

    private int applicationList(CliArgs a) {
        FeatureResult r = call(FeatureRequest.of(Operation.LIST_APPLICATIONS).with("status", a.opt("status")).with("overdueOnly", a.flag("overdue")), null);
        if (r == null) return 1;
        List<?> views = (List<?>) r.payload();
        if (views.isEmpty()) out.println("No matching applications.");
        for (Object o : views) {
            ApplicationView v = (ApplicationView) o;
            String job = v.job() == null ? v.record().getJobId() : v.job().label();
            out.println(String.format("%-40s %-13s %s%s%s", job, v.record().getStatus().name().toLowerCase(),
                    v.record().getDeadline() == null ? "no deadline" : "due " + v.record().getDeadline(),
                    v.overdue() ? "  OVERDUE" : (v.daysLeft() != null && v.daysLeft() >= 0 ? " (" + v.daysLeft() + " days left)" : ""),
                    v.record().getNotes().isBlank() ? "" : "  notes: " + v.record().getNotes()));
        }
        return 0;
    }

    // ---------- F11 ----------

    private int planPrepare(CliArgs a) {
        String jobId = require(a, "job");
        FeatureRequest req = FeatureRequest.of(Operation.PREPARE_APPLICATION).with("jobId", jobId).with("profileId", a.opt("profile"))
                .with("minutes", a.opt("minutes")).with("deadline", a.opt("deadline")).with("preferences", a.opt("preferences"));
        FeatureResult r = call(req, "Agent is preparing your application (up to 8 tool calls)");
        if (r == null) return 1;
        PreparationPreview p = r.payload(PreparationPreview.class);
        out.println("Agent steps:");
        for (AgentStep s : p.trace()) out.println("  " + s);
        out.println("\nResult: " + p.status());
        if (p.summary() != null && !p.summary().isBlank()) out.println(p.summary());
        if (p.question() != null) out.println("\nThe agent needs your input: " + p.question());
        if (p.plan() != null) {
            out.println("\nPlan:");
            for (PreparationTask t : p.plan().getTasks()) {
                out.println("  " + t.getId() + ". " + t.getDescription() + " (" + t.getMinutes() + " min" + (t.getDependsOn().isEmpty() ? "" : ", after " + t.getDependsOn()) + ")");
            }
            out.println("  Total " + p.plan().totalMinutes() + " of " + p.constraints().availableMinutes() + " minutes");
            if (p.validation() != null) for (String e : p.validation().errors()) out.println("  problem: " + e);
        }
        printWarnings(r);
        if (p.plan() != null && p.validation() != null && p.validation().feasible() && confirm("Save this plan?")) {
            FeatureResult saved = call(FeatureRequest.of(Operation.SAVE_PLAN).with("plan", p.plan()).with("minutes", p.constraints().availableMinutes())
                    .with("deadline", p.constraints().deadline() == null ? null : p.constraints().deadline().toString()), null);
            if (saved != null) out.println("Saved plan " + saved.payload(PreparationPlan.class).getId() + ".");
        }
        for (Document d : p.drafts()) {
            if (d instanceof CoverLetter l && l.unsupportedCount() > 0) { out.println("Letter draft has unsupported claims; not offered for saving."); continue; }
            if (d instanceof Resume res) res.getChanges().forEach(c -> c.setAccepted(c.isSupported()));
            if (confirm("Save the agent's " + (d instanceof Resume ? "tailored resume" : "cover letter") + " draft?")) saveDraft(d);
        }
        return 0;
    }

    // ---------- F12 ----------

    private int export(CliArgs a) {
        FeatureRequest req = FeatureRequest.of(Operation.EXPORT_PACKAGE).with("jobId", require(a, "job")).with("format", a.opt("format"))
                .with("out", require(a, "out")).with("resumeId", a.opt("resume")).with("letterId", a.opt("letter")).with("planId", a.opt("plan"))
                .with("includeResume", !a.flag("no-resume")).with("includeLetter", !a.flag("no-letter")).with("includePlan", !a.flag("no-plan"));
        FeatureResult r = call(req, null);
        if (r == null) return 1;
        ExportResult e = r.payload(ExportResult.class);
        out.println("Exported to " + e.folder() + ": " + e.files());
        printWarnings(r);
        return 0;
    }

    // ---------- helpers ----------

    private FeatureResult call(FeatureRequest req, String progress) {
        if (progress != null) out.println(progress + "...");
        FeatureResult r = submit(req);
        if (!r.isOk()) {
            out.println("Error: " + r.error());
            return null;
        }
        return r;
    }

    private boolean confirm(String question) {
        if (assumeYes) return true;
        out.print(question + " [y/N] ");
        out.flush();
        String line = readLine();
        return line != null && (line.trim().equalsIgnoreCase("y") || line.trim().equalsIgnoreCase("yes"));
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static String require(CliArgs a, String key) {
        String v = a.opt(key);
        if (v == null || v.isBlank() || v.equals("true")) throw new UsageException("--" + key + " is required");
        return v;
    }

    private void printWarnings(FeatureResult r) {
        for (String w : r.warnings()) out.println("Warning: " + w);
    }

    private void printProfile(CandidateProfile p) {
        out.println("Profile " + (p.getId() == null ? "(preview)" : p.getId() + " rev " + p.getRevision()) + ": " + p.getFullName());
        out.println("  " + String.join(" | ", nonBlank(p.getEmail(), p.getPhone(), p.getLocation())));
        if (p.getSummary() != null && !p.getSummary().isBlank()) out.println("  Summary: " + p.getSummary());
        out.println("  Skills: " + String.join(", ", p.getSkills()));
        for (ExperienceItem e : p.getExperience()) {
            out.println("  [" + e.getId() + "] " + e.describe() + " (" + e.getStart() + " to " + e.getEnd() + ")");
            e.getBullets().forEach(b -> out.println("      - " + b));
        }
        for (EducationItem d : p.getEducation()) out.println("  [" + d.getId() + "] " + d.describe() + " (" + d.getStart() + " to " + d.getEnd() + ")");
    }

    private void printJob(JobPosting j) {
        out.println((j.getId() == null ? "Job (preview)" : j.getId()) + ": " + j.getTitle() + (j.getCompany() == null || j.getCompany().isBlank() ? "" : " at " + j.getCompany())
                + ", deadline " + (j.getDeadline() == null ? "unknown" : j.getDeadline()));
        for (Requirement r : j.getRequirements()) {
            out.println("  " + r.getId() + " [" + r.getPriority() + ", " + r.getCategory() + "] " + r.getText());
            out.println("      source: \"" + r.getSourceSpan() + "\"");
        }
    }

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) if (v != null && !v.isBlank()) out.add(v);
        return out;
    }

    private void help() {
        out.println("""
                CareerPilot CLI. Usage: career <command> [options]   (no command starts the GUI)
                  Run a whole task from a file or stdin: career --input task.txt   (see: career --help)
                  resume import --file resume.pdf [--yes]                 F01 import resume and extract profile
                  profile show | profile edit [--name --email --phone --location --summary
                                 --add-skill a,b --remove-skill c --entry E1 --start 2024-05 --end Present]   F02
                  job import --file posting.txt | --text "..." [--company --url --deadline 2026-11-30] [--yes]   F03
                  job list | job show --job J1
                  job fit --job J1                                        F04 fit analysis with evidence
                  job gaps --job J1                                       F05 gaps and next steps
                  resume tailor --job J1 [--yes]                          F06 tailored resume
                  letter draft --job J1 [--tone professional|enthusiastic|concise] [--motivation "..."] [--yes]   F07
                  documents list [--job J1]
                  interview questions --job J1 [--count 8] [--focus technical|behavioural|mixed]   F08
                  interview sets [--job J1]
                  interview start --questions Q1 | interview resume --session S1   F09 mock interview
                  interview answer --session S1 --text "..." | interview retry --session S1 | interview pause --session S1
                  application update --job J1 [--status applied] [--deadline 2026-11-30] [--notes "..."] [--applied-on 2026-11-02]   F10
                  application list [--status applied] [--overdue]
                  plan prepare --job J1 [--minutes 120] [--deadline 2026-11-30] [--preferences "..."] [--yes]   F11 agent
                  export --job J1 --out ./J1-package [--format markdown|text] [--no-resume --no-letter --no-plan]   F12
                """);
    }

    private static final class UsageException extends RuntimeException {
        UsageException(String m) { super(m); }
    }
}
