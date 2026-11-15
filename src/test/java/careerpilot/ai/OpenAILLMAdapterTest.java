package careerpilot.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAILLMAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** Starts a local HTTP server that returns {@code body} with {@code status} and captures the request. */
    private HttpServer server(int status, String body, AtomicReference<String> captured, AtomicReference<String> auth) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/v1/chat/completions", ex -> {
            captured.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        s.start();
        return s;
    }

    private OpenAILLMAdapter adapter(HttpServer s) {
        return new OpenAILLMAdapter(new ProviderClient("http://127.0.0.1:" + s.getAddress().getPort() + "/v1", "sk-test", Duration.ofSeconds(5)), "gpt-4.1-mini");
    }

    @Test
    void translatesToolsAndToolMessagesBothWays() throws Exception {
        AtomicReference<String> req = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        String reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"get_job\",\"arguments\":\"{\\\"jobId\\\":\\\"J1\\\"}\"}}]}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3}}";
        HttpServer s = server(200, reply, req, auth);
        try {
            LLMRequest request = LLMRequest.chat("agent.step", List.of(Message.system("sys"), Message.user("go"),
                            Message.assistantToolCalls(null, List.of(new ToolCall("call_0", "get_profile", "{}"))),
                            Message.toolResult("call_0", "{\"success\":true}")))
                    .withTools(List.of(new ToolDefinition("get_job", "Get job", Map.of("type", "object"))));
            LLMResponse response = adapter(s).generate(request);

            assertEquals(ResponseKind.TOOL_CALLS, response.kind());
            assertEquals("get_job", response.toolCalls().get(0).name());
            assertEquals(12, response.promptTokens());
            assertEquals("Bearer sk-test", auth.get());
            JsonNode sent = mapper.readTree(req.get());
            assertEquals("gpt-4.1-mini", sent.path("model").asText());
            assertEquals("get_job", sent.path("tools").get(0).path("function").path("name").asText());
            assertEquals("call_0", sent.path("messages").get(2).path("tool_calls").get(0).path("id").asText());
            assertEquals("call_0", sent.path("messages").get(3).path("tool_call_id").asText());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void jsonRequestsAskForJsonObjects() throws Exception {
        AtomicReference<String> req = new AtomicReference<>();
        HttpServer s = server(200, "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}", req, new AtomicReference<>());
        try {
            LLMResponse r = adapter(s).generate(LLMRequest.json("t", "Return JSON", "u"));
            assertEquals(ResponseKind.TEXT, r.kind());
            assertEquals("json_object", mapper.readTree(req.get()).path("response_format").path("type").asText());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void httpErrorsBecomeLLMExceptionsWithAHint() throws Exception {
        HttpServer s = server(401, "{\"error\":{\"message\":\"Incorrect API key\"}}", new AtomicReference<>(), new AtomicReference<>());
        try {
            LLMException e = assertThrows(LLMException.class, () -> adapter(s).generate(LLMRequest.json("t", "s", "u")));
            assertTrue(e.getMessage().contains("401"));
            assertTrue(e.getMessage().contains("LLM_API_KEY"));
            assertTrue(!e.isRetryable());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void geminiThoughtSignaturesAreKeptAndSentBack() throws Exception {
        AtomicReference<String> req = new AtomicReference<>();
        String reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"get_job\",\"arguments\":\"{}\"},\"extra_content\":{\"google\":{\"thought_signature\":\"SIG123\"}}}]}}]}";
        HttpServer s = server(200, reply, req, new AtomicReference<>());
        try {
            LLMResponse first = adapter(s).generate(LLMRequest.chat("agent.step", List.of(Message.user("go")))
                    .withTools(List.of(new ToolDefinition("get_job", "Get job", Map.of("type", "object")))));
            ToolCall call = first.toolCalls().get(0);
            assertTrue(call.providerData().contains("SIG123"));

            adapter(s).generate(LLMRequest.chat("agent.step", List.of(Message.user("go"),
                    Message.assistantToolCalls(null, List.of(call)), Message.toolResult("c1", "{}"))));
            JsonNode sent = mapper.readTree(req.get()).path("messages").get(1).path("tool_calls").get(0);
            assertEquals("SIG123", sent.path("extra_content").path("google").path("thought_signature").asText());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void geminiStyleErrorListsAreReadForTheMessage() throws Exception {
        String body = "[{\"error\":{\"code\":404,\"message\":\"This model is no longer available to new users.\"}}]";
        HttpServer s = server(404, body, new AtomicReference<>(), new AtomicReference<>());
        try {
            LLMException e = assertThrows(LLMException.class, () -> adapter(s).generate(LLMRequest.json("t", "s", "u")));
            assertTrue(e.getMessage().contains("no longer available to new users"));
        } finally {
            s.stop(0);
        }
    }

    @Test
    void toolOnlyAssistantTurnsOmitContentAndMissingIdsAreFilledIn() throws Exception {
        AtomicReference<String> req = new AtomicReference<>();
        String reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"type\":\"function\","
                + "\"function\":{\"name\":\"get_job\",\"arguments\":\"{}\"}}]}}]}";
        HttpServer s = server(200, reply, req, new AtomicReference<>());
        try {
            ToolCall call = adapter(s).generate(LLMRequest.chat("agent.step", List.of(Message.user("go")))
                    .withTools(List.of(new ToolDefinition("get_job", "Get job", Map.of("type", "object"))))).toolCalls().get(0);
            assertTrue(!call.id().isBlank());
            adapter(s).generate(LLMRequest.chat("agent.step", List.of(Message.user("go"),
                    Message.assistantToolCalls(null, List.of(call)), Message.toolResult(call.id(), "{}"))));
            JsonNode assistant = mapper.readTree(req.get()).path("messages").get(1);
            assertTrue(!assistant.has("content"));
            assertEquals(call.id(), mapper.readTree(req.get()).path("messages").get(2).path("tool_call_id").asText());
        } finally {
            s.stop(0);
        }
    }
}
