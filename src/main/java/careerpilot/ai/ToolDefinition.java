package careerpilot.ai;

import java.util.Map;

/** A tool offered to the model: name, purpose and JSON Schema for its arguments. */
public record ToolDefinition(String name, String description, Map<String, Object> parametersSchema) { }
