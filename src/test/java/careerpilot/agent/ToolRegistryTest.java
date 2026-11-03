package careerpilot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.domain.Constraints;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
    /** Records whether it ran, so tests can prove rejected calls never reach the receiver. */
    static class EchoTool implements AgentTool {
        int runs;
        @Override public String name() { return "echo"; }
        @Override public String description() { return "test"; }
        @Override public Map<String, Object> parametersSchema() { return Map.of("type", "object"); }
        @Override public List<String> requiredArgs() { return List.of("jobId"); }
        @Override public ToolResult execute(ToolArgs args) { runs++; return ToolResult.ok(null, name(), args.asMap()); }
    }

    private final EchoTool echo = new EchoTool();
    private final ToolRegistry registry = new ToolRegistry().register(echo);
    private final RunContext ctx = new RunContext("P1", "J1", new Constraints(60, null, null));

    @Test
    void validCallIsBoundToACommandAndExecuted() {
        ToolResult r = registry.executeTool("echo", "{\"jobId\":\"J1\"}", "c1", ctx);
        assertTrue(r.success());
        assertEquals("c1", r.callId());
        assertEquals(1, echo.runs);
    }

    @Test
    void unknownToolMissingArgsBadJsonAndOutOfScopeAreRejectedWithoutRunning() {
        assertTrue(registry.executeTool("delete_everything", "{}", "c1", ctx).rejected());
        assertTrue(registry.executeTool("echo", "{}", "c2", ctx).rejected());
        assertTrue(registry.executeTool("echo", "not json", "c3", ctx).rejected());
        ToolResult scope = registry.executeTool("echo", "{\"jobId\":\"J2\"}", "c4", ctx);
        assertTrue(scope.rejected());
        assertTrue(scope.error().contains("outside this run's scope"));
        assertEquals(0, echo.runs);
    }

    @Test
    void receiverExceptionsBecomeFailedResults() {
        ToolRegistry r = new ToolRegistry().register(new EchoTool() {
            @Override public ToolResult execute(ToolArgs args) { throw new careerpilot.common.NotFoundException("Job J1 was not found"); }
        });
        ToolResult result = r.executeTool("echo", "{\"jobId\":\"J1\"}", "c1", ctx);
        assertFalse(result.success());
        assertFalse(result.rejected());
        assertEquals("Job J1 was not found", result.error());
    }
}
