package careerpilot.ai;

import java.util.List;

/** One chat message in our provider-neutral format. */
public record Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
    public static Message system(String text) { return new Message("system", text, List.of(), null); }
    public static Message user(String text) { return new Message("user", text, List.of(), null); }
    public static Message assistant(String text) { return new Message("assistant", text, List.of(), null); }
    public static Message assistantToolCalls(String text, List<ToolCall> calls) { return new Message("assistant", text, List.copyOf(calls), null); }
    public static Message toolResult(String callId, String json) { return new Message("tool", json, List.of(), callId); }
}
