package careerpilot.ai;

import java.util.List;

public record LLMResponse(ResponseKind kind, String content, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {
    public static LLMResponse text(String content) { return new LLMResponse(ResponseKind.TEXT, content, List.of(), 0, 0); }
    public static LLMResponse tools(List<ToolCall> calls) { return new LLMResponse(ResponseKind.TOOL_CALLS, null, List.copyOf(calls), 0, 0); }
}
