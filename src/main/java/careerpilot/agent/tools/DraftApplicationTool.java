package careerpilot.agent.tools;

import careerpilot.agent.AgentTool;
import careerpilot.agent.ToolArgs;
import careerpilot.agent.ToolResult;
import careerpilot.domain.CoverLetter;
import careerpilot.domain.Document;
import careerpilot.domain.Resume;
import careerpilot.domain.ResumeChange;
import careerpilot.service.DocumentService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates an UNSAVED resume or letter preview for the applicant to review later. The agent cannot save documents;
 * only the applicant can, through a separate save request.
 */
public class DraftApplicationTool implements AgentTool {
    private final DocumentService documents;

    public DraftApplicationTool(DocumentService documents) { this.documents = documents; }

    @Override public String name() { return "draft_application"; }
    @Override public String description() {
        return "Create an unsaved draft for the applicant to review: documentType 'resume' (tailored resume) or 'letter' (cover letter). "
                + "Only draft a document that is missing or stale. Each type can be drafted once per run.";
    }
    @Override public Map<String, Object> parametersSchema() {
        Map<String, Object> p = Schemas.ids();
        p.put("documentType", Map.of("type", "string", "enum", List.of("resume", "letter")));
        p.put("tone", Map.of("type", "string", "enum", List.of("professional", "enthusiastic", "concise")));
        return Schemas.object(p, List.of("profileId", "jobId", "documentType"));
    }
    @Override public List<String> requiredArgs() { return List.of("profileId", "jobId", "documentType"); }

    @Override
    public ToolResult execute(ToolArgs args) {
        String type = args.string("documentType", "");
        Class<? extends Document> kind = switch (type) {
            case "resume" -> Resume.class;
            case "letter" -> CoverLetter.class;
            default -> null;
        };
        if (kind == null) return ToolResult.rejected(null, name(), "documentType must be 'resume' or 'letter'");
        if (args.context().drafts().stream().anyMatch(kind::isInstance)) {
            return ToolResult.rejected(null, name(), "a " + type + " draft was already created in this run");
        }
        String profileId = args.string("profileId", null);
        String jobId = args.string("jobId", null);
        Map<String, Object> data = new LinkedHashMap<>();
        if (kind == Resume.class) {
            Resume r = documents.tailorResume(profileId, jobId);
            args.context().addDraft(r);
            data.put("draft", "resume preview #" + args.context().drafts().size());
            data.put("changes", r.getChanges().size());
            data.put("unsupportedChanges", r.getChanges().stream().filter(c -> !c.isSupported()).map(ResumeChange::getId).toList());
        } else {
            CoverLetter l = documents.draftLetter(profileId, jobId, args.string("tone", "professional"), null);
            args.context().addDraft(l);
            data.put("draft", "letter preview #" + args.context().drafts().size());
            data.put("words", l.getText().split("\\s+").length);
            data.put("warnings", l.getWarnings());
        }
        data.put("saved", false);
        return ToolResult.ok(null, name(), data);
    }
}
