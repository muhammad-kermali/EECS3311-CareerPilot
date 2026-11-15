package careerpilot.service;

import careerpilot.ai.Json;
import careerpilot.ai.LLMRequest;
import careerpilot.ai.OutputFormatException;
import careerpilot.ai.Prompts;
import careerpilot.ai.StructuredOutput;
import careerpilot.common.TextUtil;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.FitReport;
import careerpilot.domain.GapAction;
import careerpilot.domain.GapReport;
import careerpilot.domain.JobPosting;
import careerpilot.domain.MatchLevel;
import careerpilot.domain.Requirement;
import careerpilot.domain.RequirementMatch;
import careerpilot.storage.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** F04 fit analysis with evidence, F05 gaps and next steps. */
public class AnalysisService {
    private final WorkspaceRepository repo;
    private final StructuredOutput ai;
    private final EvidenceValidator evidence;

    public AnalysisService(WorkspaceRepository repo, StructuredOutput ai, EvidenceValidator evidence) {
        this.repo = repo;
        this.ai = ai;
        this.evidence = evidence;
    }

    public FitReport analyzeFit(String profileId, String jobId) {
        CandidateProfile profile = repo.load(CandidateProfile.class, profileId);
        JobPosting job = repo.load(JobPosting.class, jobId);
        StringBuilder user = new StringBuilder("Evidence catalog (each line is [path] text):\n<profile>\n")
                .append(evidence.catalogForPrompt(profile)).append("</profile>\n\nRequirements:\n");
        for (Requirement r : job.getRequirements()) {
            user.append(r.getId()).append(" [").append(r.getPriority()).append(", ").append(r.getCategory()).append("]: ")
                    .append(r.getText()).append('\n');
        }
        List<String> warnings = new ArrayList<>();
        LLMRequest req = LLMRequest.json("analysis.fit", Prompts.fitSystem(), user.toString());
        FitReport report = ai.generate(req,
                json -> parseFit(json, profile, job, warnings, true),
                json -> parseFit(json, profile, job, warnings, false));
        return repo.save(report);
    }

    private FitReport parseFit(JsonNode json, CandidateProfile profile, JobPosting job, List<String> warnings, boolean strict)
            throws OutputFormatException {
        List<RequirementMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> evidenceProblems = new ArrayList<>();
        for (JsonNode m : Json.array(json, "matches")) {
            String reqId = Json.text(m, "requirementId", true);
            if (job.requirement(reqId).isEmpty()) throw new OutputFormatException("unknown requirementId '" + reqId + "'");
            if (!seen.add(reqId)) throw new OutputFormatException("requirementId '" + reqId + "' appears more than once");
            MatchLevel level;
            try {
                level = MatchLevel.valueOf(Json.text(m, "level", true).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new OutputFormatException("level must be SUPPORTED, PARTIAL or NOT_EVIDENCED");
            }
            EvidenceValidator.Checked checked = evidence.readEvidence(m.get("evidence"), profile);
            for (String r : checked.rejected()) evidenceProblems.add(reqId + ": " + r);
            if (level != MatchLevel.NOT_EVIDENCED && checked.valid().isEmpty()) {
                evidenceProblems.add(reqId + ": " + level + " needs at least one valid evidence item");
                if (!strict) {
                    warnings.add(reqId + " was downgraded to NOT_EVIDENCED because no valid evidence was given");
                    level = MatchLevel.NOT_EVIDENCED;
                }
            }
            matches.add(new RequirementMatch(reqId, level, checked.valid(), Json.text(m, "explanation", false)));
        }
        for (Requirement r : job.getRequirements()) {
            if (!seen.contains(r.getId())) throw new OutputFormatException("requirement " + r.getId() + " is missing from matches");
        }
        if (!evidenceProblems.isEmpty()) {
            if (strict) throw new OutputFormatException("invalid evidence: " + String.join("; ", evidenceProblems));
            warnings.add(evidenceProblems.size() + " invalid evidence item(s) were removed");
        }
        return new FitReport(profile, job, matches, Json.text(json, "summary", false), warnings);
    }

    /** The newest fit report for this pair that is still current, if any. */
    public Optional<FitReport> currentFit(String profileId, String jobId) {
        CandidateProfile profile = repo.load(CandidateProfile.class, profileId);
        JobPosting job = repo.load(JobPosting.class, jobId);
        return latestFit(profileId, jobId).filter(f -> !f.isStaleFor(profile, job));
    }

    public Optional<FitReport> latestFit(String profileId, String jobId) {
        return repo.list(FitReport.class, f -> f.getProfileId().equals(profileId) && f.getJobId().equals(jobId)).stream()
                .max(Comparator.comparing(FitReport::getCreatedAt));
    }

    /** Uses the current fit report, or recomputes it when missing or stale, then asks for next steps for each gap. */
    public GapReport identifyGaps(String profileId, String jobId) {
        FitReport fit = currentFit(profileId, jobId).orElseGet(() -> analyzeFit(profileId, jobId));
        JobPosting job = repo.load(JobPosting.class, jobId);
        List<RequirementMatch> gaps = fit.getMatches().stream().filter(m -> m.getLevel() != MatchLevel.SUPPORTED).toList();
        if (gaps.isEmpty()) {
            return repo.save(new GapReport(fit, List.of(), "No gaps: every requirement has supporting evidence in your profile."));
        }
        StringBuilder user = new StringBuilder("Gap requirements:\n");
        Set<String> gapIds = new HashSet<>();
        for (RequirementMatch m : gaps) {
            Requirement r = job.requirement(m.getRequirementId()).orElseThrow();
            gapIds.add(r.getId());
            user.append(r.getId()).append(" [").append(m.getLevel()).append(", ").append(r.getPriority()).append("]: ")
                    .append(r.getText()).append(TextUtil.isBlank(m.getExplanation()) ? "" : " (analysis: " + m.getExplanation() + ")").append('\n');
        }
        LLMRequest req = LLMRequest.json("analysis.gaps", Prompts.gapSystem(), user.toString());
        GapReport report = ai.generate(req, json -> parseGaps(json, fit, gapIds));
        return repo.save(report);
    }

    private GapReport parseGaps(JsonNode json, FitReport fit, Set<String> gapIds) throws OutputFormatException {
        List<GapAction> actions = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (JsonNode a : Json.array(json, "actions")) {
            String reqId = Json.text(a, "requirementId", true);
            if (!gapIds.contains(reqId)) throw new OutputFormatException("requirementId '" + reqId + "' is not one of the listed gaps");
            if (!covered.add(reqId)) throw new OutputFormatException("requirementId '" + reqId + "' has more than one action");
            GapAction.GapType type;
            try {
                type = GapAction.GapType.valueOf(Json.text(a, "gapType", true).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new OutputFormatException("gapType must be MISSING_EVIDENCE or LEARNING_NEED");
            }
            actions.add(new GapAction(reqId, type, Json.text(a, "action", true), Json.integer(a, "estimatedHours", 1, 200)));
        }
        Set<String> missing = new HashSet<>(gapIds);
        missing.removeAll(covered);
        if (!missing.isEmpty()) throw new OutputFormatException("no action was given for " + missing);
        return new GapReport(fit, actions, Json.text(json, "note", false));
    }
}
