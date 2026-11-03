package careerpilot.agent;

import careerpilot.domain.Document;
import careerpilot.domain.PreparationPlan;
import java.util.List;

public record AgentResult(AgentStatus status, PreparationPlan plan, String summary, String question,
                          List<Document> drafts, List<AgentStep> trace, int toolCalls) { }
