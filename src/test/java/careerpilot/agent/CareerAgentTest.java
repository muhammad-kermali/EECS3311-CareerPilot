package careerpilot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.ai.LLMResponse;
import careerpilot.ai.ToolCall;
import careerpilot.domain.Constraints;
import careerpilot.testsupport.Fixtures;
import careerpilot.testsupport.MockLLMClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerAgentTest {
    private final ToolRegistryTest.EchoTool echo = new ToolRegistryTest.EchoTool();
    private final ToolRegistry registry = new ToolRegistry().register(echo);
    private final RunContext ctx = new RunContext("P1", "J1", new Constraints(120, null, null));
    private static final String PLAN = "{\"status\":\"PLAN\",\"summary\":\"ok\",\"tasks\":[{\"id\":\"T1\",\"description\":\"Tailor\",\"minutes\":30}]}";

    private CareerAgent agent(MockLLMClient llm) { return new CareerAgent(llm, registry, 8, Duration.ofSeconds(60), Fixtures.clock()); }

    private static LLMResponse call(String id) { return LLMResponse.tools(List.of(new ToolCall(id, "echo", "{\"jobId\":\"J1\"}"))); }

    @Test
    void toolObservationsAreFedBackThenAPlanIsReturned() {
        MockLLMClient llm = new MockLLMClient().then(call("a")).thenText(PLAN);
        AgentResult r = agent(llm).run("goal", ctx);
        assertEquals(AgentStatus.PLAN_PROPOSED, r.status());
        assertEquals(1, r.toolCalls());
        assertNotNull(r.plan());
        assertTrue(llm.requests.get(1).messages().stream().anyMatch(m -> "tool".equals(m.role()) && "a".equals(m.toolCallId())));
    }

    @Test
    void stopsAtTheToolCallLimit() {
        MockLLMClient llm = new MockLLMClient().respondWith(req -> call("x"));
        AgentResult r = agent(llm).run("goal", ctx);
        assertEquals(AgentStatus.LIMIT_REACHED, r.status());
        assertEquals(8, r.toolCalls());
        assertEquals(8, echo.runs);
    }

    @Test
    void invalidFinalAnswerGetsExactlyOneRepair() {
        MockLLMClient ok = new MockLLMClient().thenText("not json").thenText(PLAN);
        assertEquals(AgentStatus.PLAN_PROPOSED, agent(ok).run("goal", ctx).status());
        MockLLMClient bad = new MockLLMClient().thenText("not json").thenText("{\"status\":\"MAYBE\"}");
        assertEquals(AgentStatus.FAILED, agent(bad).run("goal", ctx).status());
    }

    @Test
    void needsInputCarriesTheQuestion() {
        MockLLMClient llm = new MockLLMClient().thenText("{\"status\":\"NEEDS_INPUT\",\"question\":\"Do you have Docker experience?\",\"tasks\":[]}");
        AgentResult r = agent(llm).run("goal", ctx);
        assertEquals(AgentStatus.NEEDS_INPUT, r.status());
        assertEquals("Do you have Docker experience?", r.question());
    }

    @Test
    void providerFailureEndsTheRunCleanly() {
        AgentResult r = agent(new MockLLMClient().thenFail("AI service returned HTTP 503")).run("goal", ctx);
        assertEquals(AgentStatus.FAILED, r.status());
        assertTrue(r.summary().contains("503"));
    }
}
