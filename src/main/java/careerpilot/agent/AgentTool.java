package careerpilot.agent;

import java.util.List;
import java.util.Map;

/** Receiver role in the Command pattern: an approved capability the agent may use. */
public interface AgentTool {
    String name();

    String description();

    /** JSON Schema of the arguments, sent to the model. */
    Map<String, Object> parametersSchema();

    /** Argument names that must be present. */
    List<String> requiredArgs();

    ToolResult execute(ToolArgs args);
}
