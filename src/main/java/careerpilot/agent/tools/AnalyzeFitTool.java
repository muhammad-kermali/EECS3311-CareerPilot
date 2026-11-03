package careerpilot.agent.tools;

import careerpilot.agent.AgentTool;
import careerpilot.agent.ToolArgs;
import careerpilot.agent.ToolResult;
import careerpilot.domain.FitReport;
import careerpilot.domain.JobPosting;
import careerpilot.domain.MatchLevel;
import careerpilot.domain.RequirementMatch;
import careerpilot.service.AnalysisService;
import careerpilot.storage.WorkspaceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reuses the current fit report, or recomputes it when missing, stale, or when refresh=true. */
public class AnalyzeFitTool implements AgentTool {
    private final AnalysisService analysis;
    private final WorkspaceRepository repo;

    public AnalyzeFitTool(AnalysisService analysis, WorkspaceRepository repo) {
        this.analysis = analysis;
        this.repo = repo;
    }

    @Override public String name() { return "analyze_fit"; }
    @Override public String description() {
        return "Compare the profile with the job requirements. Reuses a current analysis unless refresh is true; recomputes stale ones automatically.";
    }
    @Override public Map<String, Object> parametersSchema() {
        Map<String, Object> p = Schemas.ids();
        p.put("refresh", Schemas.bool("Force a new analysis even if a current one exists"));
        return Schemas.object(p, List.of("profileId", "jobId"));
    }
    @Override public List<String> requiredArgs() { return List.of("profileId", "jobId"); }

    @Override
    public ToolResult execute(ToolArgs args) {
        String profileId = args.string("profileId", null);
        String jobId = args.string("jobId", null);
        boolean reused = false;
        FitReport fit;
        if (!args.bool("refresh") && analysis.currentFit(profileId, jobId).isPresent()) {
            fit = analysis.currentFit(profileId, jobId).get();
            reused = true;
        } else {
            fit = analysis.analyzeFit(profileId, jobId);
        }
        JobPosting job = repo.load(JobPosting.class, jobId);
        List<Map<String, String>> notEvidenced = new ArrayList<>();
        for (RequirementMatch m : fit.getMatches()) {
            if (m.getLevel() != MatchLevel.SUPPORTED) {
                job.requirement(m.getRequirementId()).ifPresent(r -> notEvidenced.add(Map.of(
                        "id", r.getId(), "text", r.getText(), "priority", r.getPriority(), "level", m.getLevel().name())));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fitReportId", fit.getId());
        data.put("reusedExisting", reused);
        data.put("supported", fit.count(MatchLevel.SUPPORTED));
        data.put("partial", fit.count(MatchLevel.PARTIAL));
        data.put("notEvidenced", fit.count(MatchLevel.NOT_EVIDENCED));
        data.put("gaps", notEvidenced);
        return ToolResult.ok(null, name(), data);
    }
}
