package careerpilot.agent.tools;

import careerpilot.agent.AgentTool;
import careerpilot.agent.ToolArgs;
import careerpilot.agent.ToolResult;
import careerpilot.domain.JobPosting;
import careerpilot.domain.Requirement;
import careerpilot.storage.WorkspaceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GetJobTool implements AgentTool {
    private final WorkspaceRepository repo;

    public GetJobTool(WorkspaceRepository repo) { this.repo = repo; }

    @Override public String name() { return "get_job"; }
    @Override public String description() { return "Get the reviewed job posting: title, company, deadline and requirements with priority."; }
    @Override public Map<String, Object> parametersSchema() {
        return Schemas.object(Map.of("jobId", Schemas.string("Job ID from the run scope")), List.of("jobId"));
    }
    @Override public List<String> requiredArgs() { return List.of("jobId"); }

    @Override
    public ToolResult execute(ToolArgs args) {
        JobPosting j = repo.load(JobPosting.class, args.string("jobId", null));
        List<Map<String, String>> reqs = new ArrayList<>();
        for (Requirement r : j.getRequirements()) reqs.add(Map.of("id", r.getId(), "text", r.getText(), "priority", r.getPriority()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", j.getId());
        data.put("title", j.getTitle());
        data.put("company", j.getCompany() == null ? "" : j.getCompany());
        data.put("deadline", j.getDeadline() == null ? "unknown" : j.getDeadline().toString());
        data.put("requirements", reqs);
        return ToolResult.ok(null, name(), data);
    }
}
