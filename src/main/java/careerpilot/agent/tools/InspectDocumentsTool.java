package careerpilot.agent.tools;

import careerpilot.agent.AgentTool;
import careerpilot.agent.ToolArgs;
import careerpilot.agent.ToolResult;
import careerpilot.domain.DocumentStatus;
import careerpilot.service.DocumentService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InspectDocumentsTool implements AgentTool {
    private final DocumentService documents;

    public InspectDocumentsTool(DocumentService documents) { this.documents = documents; }

    @Override public String name() { return "inspect_documents"; }
    @Override public String description() {
        return "Check which documents already exist for this job (tailored resume, cover letter, fit analysis, plan) and whether each is current, stale or missing.";
    }
    @Override public Map<String, Object> parametersSchema() { return Schemas.object(Schemas.ids(), List.of("profileId", "jobId")); }
    @Override public List<String> requiredArgs() { return List.of("profileId", "jobId"); }

    @Override
    public ToolResult execute(ToolArgs args) {
        DocumentStatus s = documents.inspectDocuments(args.string("profileId", null), args.string("jobId", null));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tailoredResume", s.resumeState() + (s.resumeId() == null ? "" : " (" + s.resumeId() + ")"));
        data.put("coverLetter", s.letterState() + (s.letterId() == null ? "" : " (" + s.letterId() + ")"));
        data.put("fitAnalysis", s.fitState() + (s.fitReportId() == null ? "" : " (" + s.fitReportId() + ")"));
        data.put("savedPlan", s.planId() == null ? "missing" : s.planId());
        data.put("notes", s.notes());
        return ToolResult.ok(null, name(), data);
    }
}
