package careerpilot.agent.tools;

import careerpilot.agent.AgentTool;
import careerpilot.agent.ToolArgs;
import careerpilot.agent.ToolResult;
import careerpilot.domain.CandidateProfile;
import careerpilot.storage.WorkspaceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Retrieves the applicant's reviewed facts (evidence catalog), never contact details. */
public class GetProfileTool implements AgentTool {
    private final WorkspaceRepository repo;

    public GetProfileTool(WorkspaceRepository repo) { this.repo = repo; }

    @Override public String name() { return "get_profile"; }
    @Override public String description() { return "Get the applicant's confirmed profile facts (skills, experience, education) as evidence paths."; }
    @Override public Map<String, Object> parametersSchema() {
        return Schemas.object(Map.of("profileId", Schemas.string("Profile ID from the run scope")), List.of("profileId"));
    }
    @Override public List<String> requiredArgs() { return List.of("profileId"); }

    @Override
    public ToolResult execute(ToolArgs args) {
        CandidateProfile p = repo.load(CandidateProfile.class, args.string("profileId", null));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profileId", p.getId());
        data.put("revision", p.getRevision());
        data.put("evidence", p.evidenceCatalog());
        return ToolResult.ok(null, name(), data);
    }
}
