package careerpilot.agent;

import careerpilot.ai.ToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Invoker role. Checks the tool name, the arguments and the run's scope before binding an
 * {@link InvokeToolCommand}. The model can never reach a tool or record outside this check.
 */
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolRegistry register(AgentTool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public List<ToolDefinition> definitions() {
        List<ToolDefinition> out = new ArrayList<>();
        for (AgentTool t : tools.values()) out.add(new ToolDefinition(t.name(), t.description(), t.parametersSchema()));
        return out;
    }

    public List<String> names() { return new ArrayList<>(tools.keySet()); }

    public ToolResult executeTool(String name, String argumentsJson, String callId, RunContext context) {
        AgentTool tool = tools.get(name);
        if (tool == null) return ToolResult.rejected(callId, name, "unknown tool '" + name + "'. Available tools: " + tools.keySet());
        Map<String, Object> values;
        try {
            values = argumentsJson == null || argumentsJson.isBlank()
                    ? Map.of() : mapper.readValue(argumentsJson, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            return ToolResult.rejected(callId, name, "arguments are not a valid JSON object");
        }
        for (String required : tool.requiredArgs()) {
            if (values.get(required) == null || values.get(required).toString().isBlank()) {
                return ToolResult.rejected(callId, name, "missing required argument '" + required + "'");
            }
        }
        Object profileId = values.get("profileId");
        if (profileId != null && !profileId.toString().equals(context.profileId())) {
            return ToolResult.rejected(callId, name, "profileId '" + profileId + "' is outside this run's scope (" + context.profileId() + ")");
        }
        Object jobId = values.get("jobId");
        if (jobId != null && !jobId.toString().equals(context.jobId())) {
            return ToolResult.rejected(callId, name, "jobId '" + jobId + "' is outside this run's scope (" + context.jobId() + ")");
        }
        ToolCommand command = new InvokeToolCommand(tool, new ToolArgs(values, context), callId);
        return command.execute();
    }
}
