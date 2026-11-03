package careerpilot.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral model request. {@code purpose} is a short label for logs (e.g. "job.analyze");
 * it never contains personal text.
 */
public final class LLMRequest {
    private final String purpose;
    private final List<Message> messages;
    private final boolean jsonOutput;
    private final List<ToolDefinition> tools;
    private final double temperature;

    private LLMRequest(String purpose, List<Message> messages, boolean jsonOutput, List<ToolDefinition> tools, double temperature) {
        this.purpose = purpose;
        this.messages = List.copyOf(messages);
        this.jsonOutput = jsonOutput;
        this.tools = List.copyOf(tools);
        this.temperature = temperature;
    }

    /** A request whose answer must be a single JSON object. */
    public static LLMRequest json(String purpose, String system, String user) {
        return new LLMRequest(purpose, List.of(Message.system(system), Message.user(user)), true, List.of(), 0.2);
    }

    public static LLMRequest chat(String purpose, List<Message> messages) {
        return new LLMRequest(purpose, messages, false, List.of(), 0.2);
    }

    public LLMRequest withTools(List<ToolDefinition> defs) { return new LLMRequest(purpose, messages, jsonOutput, defs, temperature); }

    public LLMRequest withTemperature(double t) { return new LLMRequest(purpose, messages, jsonOutput, tools, t); }

    /** Same conversation plus the bad answer and what was wrong with it. Used for the single repair attempt. */
    public LLMRequest withRepair(String badOutput, String problem) {
        List<Message> m = new ArrayList<>(messages);
        m.add(Message.assistant(badOutput == null ? "" : badOutput));
        m.add(Message.user("Your previous answer could not be used: " + problem
                + "\nReply again with ONLY the corrected JSON object, following the same schema and rules."));
        return new LLMRequest(purpose + ".repair", m, jsonOutput, tools, temperature);
    }

    public String purpose() { return purpose; }
    public List<Message> messages() { return messages; }
    public boolean jsonOutput() { return jsonOutput; }
    public List<ToolDefinition> tools() { return tools; }
    public double temperature() { return temperature; }
}
