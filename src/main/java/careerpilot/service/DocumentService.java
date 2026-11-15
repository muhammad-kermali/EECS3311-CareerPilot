package careerpilot.service;

import careerpilot.ai.Json;
import careerpilot.ai.LLMRequest;
import careerpilot.ai.OutputFormatException;
import careerpilot.ai.Prompts;
import careerpilot.ai.StructuredOutput;
import careerpilot.common.TextUtil;
import careerpilot.common.ValidationException;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.CoverLetter;
import careerpilot.domain.Document;
import careerpilot.domain.DocumentStatus;
import careerpilot.domain.FitReport;
import careerpilot.domain.JobPosting;
import careerpilot.domain.LetterParagraph;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.Requirement;
import careerpilot.domain.Resume;
import careerpilot.domain.ResumeChange;
import careerpilot.storage.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** F06 tailored resume, F07 cover letter, plus document status for the agent. */
public class DocumentService {
    public static final Set<String> TONES = Set.of("professional", "enthusiastic", "concise");

    private final WorkspaceRepository repo;
    private final StructuredOutput ai;
    private final EvidenceValidator evidence;
    private final AnalysisService analysis;

    public DocumentService(WorkspaceRepository repo, StructuredOutput ai, EvidenceValidator evidence, AnalysisService analysis) {
        this.repo = repo;
        this.ai = ai;
        this.evidence = evidence;
        this.analysis = analysis;
    }

    /** Returns an unsaved tailored copy of the base resume with proposed, grounding-checked changes applied. */
    public Resume tailorResume(String profileId, String jobId) {
        CandidateProfile profile = repo.load(CandidateProfile.class, profileId);
        JobPosting job = repo.load(JobPosting.class, jobId);
        if (profile.getBaseResumeId() == null) throw new ValidationException("The profile has no base resume; import a resume first");
        Resume base = repo.load(Resume.class, profile.getBaseResumeId());

        Resume copy = base.deepCopy();
        copy.setJobId(jobId);
        copy.setTitle("Resume for " + job.label());
        copy.recordSource(profile);
        copy.recordSource(job);
        copy.recordSource(base);

        StringBuilder user = new StringBuilder("Resume entries (sectionIndex.entryIndex):\n<resume>\n");
        for (int s = 0; s < copy.getSections().size(); s++) {
            user.append("[").append(s).append("] ").append(copy.getSections().get(s).getHeading()).append('\n');
            List<String> entries = copy.getSections().get(s).getEntries();
            for (int e = 0; e < entries.size(); e++) user.append("  ").append(s).append('.').append(e).append(": ").append(entries.get(e)).append('\n');
        }
        user.append("</resume>\n\nJob requirements:\n").append(requirementsText(job))
                .append("\nEvidence catalog (each line is [path] text):\n<profile>\n").append(evidence.catalogForPrompt(profile)).append("</profile>");

        LLMRequest req = LLMRequest.json("document.tailor", Prompts.tailorSystem(), user.toString());
        List<ResumeChange> changes = ai.generate(req, json -> parseChanges(json, copy, job, profile));
        for (ResumeChange c : changes) {
            copy.setEntry(c.getSectionIndex(), c.getEntryIndex(), c.getAfter());
            copy.getChanges().add(c);
        }
        return copy;
    }

    private List<ResumeChange> parseChanges(JsonNode json, Resume copy, JobPosting job, CandidateProfile profile) throws OutputFormatException {
        List<ResumeChange> out = new ArrayList<>();
        int n = 1;
        for (JsonNode c : Json.array(json, "changes")) {
            int s = Json.integer(c, "sectionIndex", 0, copy.getSections().size() - 1);
            int e = Json.integer(c, "entryIndex", 0, 500);
            String before = copy.entry(s, e);
            if (before == null) throw new OutputFormatException("entry " + s + "." + e + " does not exist");
            if (out.stream().anyMatch(x -> x.getSectionIndex() == s && x.getEntryIndex() == e)) {
                throw new OutputFormatException("entry " + s + "." + e + " is changed more than once");
            }
            String after = Json.text(c, "newText", true);
            List<String> reqIds = new ArrayList<>();
            for (String id : Json.strings(c, "requirementIds")) if (job.requirement(id).isPresent()) reqIds.add(id);
            EvidenceValidator.Checked checked = evidence.readEvidence(c.get("evidence"), profile);
            ResumeChange change = new ResumeChange("C" + n++, s, e, before, after, Json.text(c, "reason", false), reqIds, checked.valid());
            List<String> problems = new ArrayList<>(checked.rejected());
            if (checked.valid().isEmpty()) problems.add("no valid evidence was cited");
            Set<String> newNumbers = evidence.unsupportedNumbers(after, checked.valid(), profile, before);
            if (!newNumbers.isEmpty()) problems.add("numbers not in the evidence: " + newNumbers);
            change.markGrounding(problems.isEmpty(), problems.isEmpty() ? null : String.join("; ", problems));
            out.add(change);
        }
        if (out.size() > 6) throw new OutputFormatException("propose at most 6 changes");
        return out;
    }

    /** Returns an unsaved cover letter preview. Paragraphs with unsupported claims are flagged for correction. */
    public CoverLetter draftLetter(String profileId, String jobId, String tone, String motivation) {
        String t = tone == null || tone.isBlank() ? "professional" : tone.trim().toLowerCase(Locale.ROOT);
        if (!TONES.contains(t)) throw new ValidationException("Tone must be one of " + TONES);
        CandidateProfile profile = repo.load(CandidateProfile.class, profileId);
        JobPosting job = repo.load(JobPosting.class, jobId);
        String user = "Tone: " + t + "\nCandidate name: " + profile.getFullName()
                + "\nRole: " + job.getTitle() + "\nCompany: " + (TextUtil.isBlank(job.getCompany()) ? "(not stated; do not name it)" : job.getCompany())
                + (TextUtil.isBlank(motivation) ? "" : "\nApplicant's own motivation (may be used as stated): <answer>" + motivation.trim() + "</answer>")
                + "\n\nJob requirements:\n" + requirementsText(job)
                + "\nEvidence catalog (each line is [path] text):\n<profile>\n" + evidence.catalogForPrompt(profile) + "</profile>";
        LLMRequest req = LLMRequest.json("document.letter", Prompts.letterSystem(), user).withTemperature(0.5);
        List<LetterParagraph> paragraphs = ai.generate(req,
                json -> parseParagraphs(json, profile, job, motivation, true),
                json -> parseParagraphs(json, profile, job, motivation, false));
        CoverLetter letter = new CoverLetter(profileId, jobId, t, paragraphs);
        letter.recordSource(profile);
        letter.recordSource(job);
        for (int i = 0; i < paragraphs.size(); i++) {
            if (!paragraphs.get(i).isSupported()) {
                letter.getWarnings().add("Paragraph " + (i + 1) + " needs checking: " + paragraphs.get(i).getUnsupportedReason());
            }
        }
        int words = letter.getText().split("\\s+").length;
        if (words < 150 || words > 500) letter.getWarnings().add("The letter has " + words + " words; 250 to 400 is typical");
        return letter;
    }

    private List<LetterParagraph> parseParagraphs(JsonNode json, CandidateProfile profile, JobPosting job, String motivation, boolean strict)
            throws OutputFormatException {
        List<LetterParagraph> out = new ArrayList<>();
        for (JsonNode p : Json.array(json, "paragraphs")) {
            String text = Json.text(p, "text", true);
            EvidenceValidator.Checked checked = evidence.readEvidence(p.get("evidence"), profile);
            LetterParagraph para = new LetterParagraph(text, checked.valid());
            List<String> problems = new ArrayList<>(checked.rejected());
            // A letter paragraph may combine several facts, so a number counts as supported if it appears anywhere in
            // the applicant's confirmed profile (or the posting / their own motivation), not only in the cited items.
            String allFacts = String.join(" ", profile.evidenceCatalog().values());
            Set<String> numbers = evidence.unsupportedNumbers(text, checked.valid(), profile, allFacts, job.getSourceText(), motivation);
            if (!numbers.isEmpty()) problems.add("numbers not in the evidence: " + numbers);
            para.markGrounding(problems.isEmpty(), problems.isEmpty() ? null : String.join("; ", problems));
            out.add(para);
        }
        if (out.size() < 2 || out.size() > 6) throw new OutputFormatException("write 3 to 5 paragraphs");
        if (strict) {
            List<String> bad = new ArrayList<>();
            for (int i = 0; i < out.size(); i++) {
                if (!out.get(i).isSupported()) bad.add("paragraph " + (i + 1) + ": " + out.get(i).getUnsupportedReason());
            }
            if (!bad.isEmpty()) throw new OutputFormatException("fix the evidence: " + String.join("; ", bad));
        }
        return out;
    }

    /**
     * Saves a reviewed draft. For resumes, rejected changes are reverted first and accepted unsupported changes are
     * refused. For letters, unedited text with unsupported paragraphs is refused.
     */
    public Document saveDraft(Document draft) {
        if (draft.getId() != null) throw new ValidationException("This draft was already saved as " + draft.getId());
        if (draft instanceof Resume resume) {
            if (resume.isBase()) throw new ValidationException("Only tailored resumes are saved as drafts");
            for (ResumeChange c : resume.getChanges()) {
                if (c.isAccepted() && !c.isSupported()) {
                    throw new ValidationException("Change " + c.getId() + " is not supported by your profile (" + c.getUnsupportedReason()
                            + "). Reject it or correct your profile first");
                }
            }
            for (ResumeChange c : resume.getChanges()) if (!c.isAccepted()) resume.setEntry(c.getSectionIndex(), c.getEntryIndex(), c.getBefore());
        } else if (draft instanceof CoverLetter letter) {
            if (TextUtil.isBlank(letter.getText())) throw new ValidationException("The letter is empty");
            if (letter.unsupportedCount() > 0 && !letter.isEditedByUser()) {
                throw new ValidationException(letter.unsupportedCount() + " paragraph(s) contain claims that are not supported by your profile. Edit them before saving");
            }
        }
        draft.setReviewed(true);
        return repo.save(draft);
    }

    public List<Document> listDocuments(String jobId) {
        List<Document> out = new ArrayList<>();
        out.addAll(repo.list(Resume.class, r -> jobId == null ? !r.isBase() : jobId.equals(r.getJobId())));
        out.addAll(repo.list(CoverLetter.class, l -> jobId == null || jobId.equals(l.getJobId())));
        out.sort(Comparator.comparing(Document::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    public Optional<Resume> latestResume(String jobId) {
        return repo.list(Resume.class, r -> jobId.equals(r.getJobId()) && r.isReviewed()).stream().max(Comparator.comparing(Resume::getCreatedAt));
    }

    public Optional<CoverLetter> latestLetter(String jobId) {
        return repo.list(CoverLetter.class, l -> jobId.equals(l.getJobId()) && l.isReviewed()).stream().max(Comparator.comparing(CoverLetter::getCreatedAt));
    }

    /** What exists for this pair, and whether it is still based on the current profile and job revisions. */
    public DocumentStatus inspectDocuments(String profileId, String jobId) {
        CandidateProfile profile = repo.load(CandidateProfile.class, profileId);
        JobPosting job = repo.load(JobPosting.class, jobId);
        List<String> notes = new ArrayList<>();
        Optional<Resume> resume = latestResume(jobId);
        Optional<CoverLetter> letter = latestLetter(jobId);
        Optional<FitReport> fit = analysis.latestFit(profileId, jobId);
        Optional<PreparationPlan> plan = repo.list(PreparationPlan.class, p -> jobId.equals(p.getJobId()) && p.isReviewed()).stream()
                .max(Comparator.comparing(PreparationPlan::getCreatedAt));
        String resumeState = resume.map(r -> stateOf(r, profile, job)).orElse(DocumentStatus.MISSING);
        String letterState = letter.map(l -> stateOf(l, profile, job)).orElse(DocumentStatus.MISSING);
        String fitState = fit.map(f -> f.isStaleFor(profile, job) ? DocumentStatus.STALE : DocumentStatus.CURRENT).orElse(DocumentStatus.MISSING);
        if (DocumentStatus.STALE.equals(resumeState)) notes.add("The saved resume was made before the profile or job changed");
        if (DocumentStatus.STALE.equals(fitState)) notes.add("The fit analysis is out of date");
        return new DocumentStatus(profileId, jobId, resume.map(Resume::getId).orElse(null), resumeState,
                letter.map(CoverLetter::getId).orElse(null), letterState, fit.map(FitReport::getId).orElse(null), fitState,
                plan.map(PreparationPlan::getId).orElse(null), notes);
    }

    private static String stateOf(Document d, CandidateProfile profile, JobPosting job) {
        Integer p = d.getSourceRevisions().get(profile.sourceKey());
        Integer j = d.getSourceRevisions().get(job.sourceKey());
        boolean current = p != null && j != null && p == profile.getRevision() && j == job.getRevision();
        return current ? DocumentStatus.CURRENT : DocumentStatus.STALE;
    }

    static String requirementsText(JobPosting job) {
        StringBuilder sb = new StringBuilder();
        for (Requirement r : job.getRequirements()) sb.append(r.getId()).append(" [").append(r.getPriority()).append("]: ").append(r.getText()).append('\n');
        return sb.toString();
    }
}
