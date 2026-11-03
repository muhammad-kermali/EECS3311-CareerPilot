package careerpilot.agent;

import careerpilot.storage.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Outcome of one tool call. It is sent back to the model as an observation. */
public record ToolResult(String callId, String tool, boolean success, boolean rejected, Object data, String error) {
    public static ToolResult ok(String callId, String tool, Object data) { return new ToolResult(callId, tool, true, false, data, null); }
    public static ToolResult rejected(String callId, String tool, String why) { return new ToolResult(callId, tool, false, true, null, why); }
    public static ToolResult failed(String callId, String tool, String why) { return new ToolResult(callId, tool, false, false, null, why); }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", success);
        if (rejected) m.put("rejected", true);
        if (data != null) m.put("data", data);
        if (error != null) m.put("error", error);
        try {
            return JsonMapper.get().writer().without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT).writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"error\":\"result could not be encoded\"}";
        }
    }

    public String summary() { return success ? "ok" : (rejected ? "rejected: " : "failed: ") + error; }
}
