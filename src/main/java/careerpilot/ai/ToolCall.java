package careerpilot.ai;

/**
 * A tool the model asked us to run. argumentsJson is untrusted until the ToolRegistry validates it.
 * providerData holds opaque provider fields (for example Gemini's thought signature) that must be sent back
 * unchanged when the conversation continues; it may be null.
 */
public record ToolCall(String id, String name, String argumentsJson, String providerData) {
    public ToolCall(String id, String name, String argumentsJson) { this(id, name, argumentsJson, null); }
}
