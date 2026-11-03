package careerpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter: translates our {@link LLMRequest}/{@link LLMResponse} contract to and from the OpenAI
 * Chat Completions format (also used by many OpenAI-compatible providers).
 */
public class OpenAILLMAdapter implements LLMClient {
    private final ProviderClient provider;
    private final String modelId;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAILLMAdapter(ProviderClient provider, String modelId) {
        this.provider = provider;
        this.modelId = modelId;
    }

    @Override public String modelName() { return modelId; }

    @Override
    public LLMResponse generate(LLMRequest request) {
        String body = toProviderJson(request);
        String raw = provider.post("/chat/completions", body);
        return fromProviderJson(raw);
    }

    String toProviderJson(LLMRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", modelId);
        root.put("temperature", request.temperature());
        ArrayNode messages = root.putArray("messages");
        for (Message m : request.messages()) {
            ObjectNode node = messages.addObject();
            node.put("role", m.role());
            if (m.content() == null) node.putNull("content"); else node.put("content", m.content());
            if ("tool".equals(m.role())) node.put("tool_call_id", m.toolCallId());
            if (!m.toolCalls().isEmpty()) {
                ArrayNode calls = node.putArray("tool_calls");
                for (ToolCall c : m.toolCalls()) {
                    ObjectNode call = calls.addObject();
                    call.put("id", c.id());
                    call.put("type", "function");
                    ObjectNode fn = call.putObject("function");
                    fn.put("name", c.name());
                    fn.put("arguments", c.argumentsJson() == null ? "{}" : c.argumentsJson());
                }
            }
        }
        if (!request.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition t : request.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode fn = tool.putObject("function");
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.set("parameters", mapper.valueToTree(t.parametersSchema()));
            }
            root.put("tool_choice", "auto");
        } else if (request.jsonOutput()) {
            root.putObject("response_format").put("type", "json_object");
        }
        return root.toString();
    }

    LLMResponse fromProviderJson(String raw) {
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (IOException e) {
            throw new LLMException("The AI service returned a response that is not JSON", true);
        }
        JsonNode message = root.path("choices").path(0).path("message");
        if (message.isMissingNode()) throw new LLMException("The AI service returned no answer", true);
        int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
        JsonNode calls = message.path("tool_calls");
        if (calls.isArray() && calls.size() > 0) {
            List<ToolCall> out = new ArrayList<>();
            for (JsonNode c : calls) {
                out.add(new ToolCall(c.path("id").asText(), c.path("function").path("name").asText(),
                        c.path("function").path("arguments").asText("{}")));
            }
            return new LLMResponse(ResponseKind.TOOL_CALLS, message.path("content").asText(null), out, promptTokens, completionTokens);
        }
        return new LLMResponse(ResponseKind.TEXT, message.path("content").asText(""), List.of(), promptTokens, completionTokens);
    }
}
