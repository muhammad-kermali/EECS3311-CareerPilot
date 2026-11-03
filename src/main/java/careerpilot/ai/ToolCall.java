package careerpilot.ai;

/** A tool the model asked us to run. argumentsJson is untrusted until the ToolRegistry validates it. */
public record ToolCall(String id, String name, String argumentsJson) { }
