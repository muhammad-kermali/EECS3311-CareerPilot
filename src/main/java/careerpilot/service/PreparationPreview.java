package careerpilot.service;

import careerpilot.agent.AgentStatus;
import careerpilot.agent.AgentStep;
import careerpilot.domain.Constraints;
import careerpilot.domain.Document;
import careerpilot.domain.HasWarnings;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.ValidationResult;
import java.util.List;

/** What the agent proposes. Nothing here is saved until the applicant accepts it. */
public record PreparationPreview(AgentStatus status, PreparationPlan plan, ValidationResult validation, String summary,
                                 String question, List<Document> drafts, List<AgentStep> trace, Constraints constraints,
                                 List<String> warnings) implements HasWarnings { }
