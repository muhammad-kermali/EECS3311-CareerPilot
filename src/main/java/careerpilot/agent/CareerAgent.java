package careerpilot.agent;

import careerpilot.ai.Json;
import careerpilot.ai.LLMClient;
import careerpilot.ai.LLMException;
import careerpilot.ai.LLMRequest;
import careerpilot.ai.LLMResponse;
import careerpilot.ai.Message;
import careerpilot.ai.OutputFormatException;
import careerpilot.ai.Prompts;
import careerpilot.ai.ResponseKind;
import careerpilot.ai.StructuredOutput;
import careerpilot.ai.ToolCall;
import careerpilot.common.TextUtil;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The application-preparation agent (F11). Each turn the model either asks for a tool or gives its final answer.
 * Tool results are fed back as observations, so later decisions depend on what earlier calls found.
 * The loop stops at a final answer, after {@code maxCalls} tool calls, or after {@code timeout}.
 */
public class CareerAgent {
    private final LLMClient llm;
    private final ToolRegistry registry;
    private final int maxCalls;
    private final Duration timeout;
    private final Clock clock;
    private final StructuredOutput json;

    public CareerAgent(LLMClient llm, ToolRegistry registry, int maxCalls, Duration timeout, Clock clock) {
        this.llm = llm;
        this.registry = registry;
        this.maxCalls = maxCalls;
        this.timeout = timeout;
        this.clock = clock;
        this.json = new StructuredOutput(llm);
    }

    public AgentResult run(String goal, RunContext ctx) {
        Instant start = clock.instant();
        List<AgentStep> trace = new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(Prompts.agentSystem(maxCalls)));
        messages.add(Message.user(goal + "\n\nRun scope: profileId=" + ctx.profileId() + ", jobId=" + ctx.jobId()
                + "\nAvailable time: " + ctx.constraints().availableMinutes() + " minutes"
                + "\nDeadline: " + (ctx.constraints().deadline() == null ? "none given" : ctx.constraints().deadline())
                + "\nToday: " + LocalDate.now(clock)
                + (TextUtil.isBlank(ctx.constraints().preferences()) ? "" : "\nApplicant preferences: <answer>" + ctx.constraints().preferences() + "</answer>")));
        int calls = 0;
        boolean repairUsed = false;

        while (true) {
            if (Duration.between(start, clock.instant()).compareTo(timeout) > 0) {
                return stop(AgentStatus.TIMED_OUT, "Stopped after " + timeout.toSeconds() + " seconds without a final plan. The AI service may be slow right now; try again, or raise AGENT_TIMEOUT_SECONDS in .env.", ctx, trace, calls);
            }
            LLMResponse response;
            try {
                response = llm.generate(LLMRequest.chat("agent.step", messages).withTools(registry.definitions()));
            } catch (LLMException e) {
                return stop(AgentStatus.FAILED, e.getMessage(), ctx, trace, calls);
            }

            if (response.kind() == ResponseKind.TOOL_CALLS) {
                if (calls + response.toolCalls().size() > maxCalls) {
                    return stop(AgentStatus.LIMIT_REACHED, "Stopped: the agent wanted more than " + maxCalls
                            + " tool calls. Partial work is listed in the trace.", ctx, trace, calls);
                }
                messages.add(Message.assistantToolCalls(response.content(), response.toolCalls()));
                for (ToolCall call : response.toolCalls()) {
                    calls++;
                    ToolResult result = registry.executeTool(call.name(), call.argumentsJson(), call.id(), ctx);
                    trace.add(new AgentStep(calls, call.name(), TextUtil.truncate(call.argumentsJson(), 120), result.summary()));
                    ctx.addObservation(call.name() + ": " + result.summary());
                    messages.add(Message.toolResult(call.id(), result.toJson()));
                }
                continue;
            }

            try {
                return parseFinal(json.readJson(response.content()), ctx, trace, calls);
            } catch (OutputFormatException e) {
                if (repairUsed) return stop(AgentStatus.FAILED, "The agent's final answer was invalid twice: " + e.getMessage(), ctx, trace, calls);
                repairUsed = true;
                messages.add(Message.assistant(response.content() == null ? "" : response.content()));
                messages.add(Message.user("Your final answer could not be used: " + e.getMessage()
                        + ". Reply with ONLY the final JSON object described in the instructions."));
            }
        }
    }

    private AgentResult parseFinal(JsonNode node, RunContext ctx, List<AgentStep> trace, int calls) throws OutputFormatException {
        String statusText = Json.text(node, "status", true).toUpperCase(Locale.ROOT);
        String summary = Json.text(node, "summary", false);
        switch (statusText) {
            case "NEEDS_INPUT" -> {
                String question = Json.text(node, "question", true);
                return new AgentResult(AgentStatus.NEEDS_INPUT, null, summary, question, ctx.drafts(), trace, calls);
            }
            case "INFEASIBLE", "PLAN" -> {
                List<PreparationTask> tasks = new ArrayList<>();
                JsonNode arr = node.get("tasks");
                if (arr != null && arr.isArray()) {
                    for (JsonNode t : arr) {
                        tasks.add(new PreparationTask(Json.text(t, "id", true), Json.text(t, "description", true),
                                Json.integer(t, "minutes", 1, 1440), Json.strings(t, "dependsOn")));
                    }
                }
                if (statusText.equals("PLAN") && tasks.isEmpty()) throw new OutputFormatException("a PLAN needs at least one task");
                PreparationPlan plan = tasks.isEmpty() ? null : new PreparationPlan(ctx.profileId(), ctx.jobId(), ctx.constraints(), summary, tasks);
                AgentStatus status = statusText.equals("PLAN") ? AgentStatus.PLAN_PROPOSED : AgentStatus.INFEASIBLE;
                return new AgentResult(status, plan, summary, null, ctx.drafts(), trace, calls);
            }
            default -> throw new OutputFormatException("status must be PLAN, NEEDS_INPUT or INFEASIBLE");
        }
    }

    private static AgentResult stop(AgentStatus status, String why, RunContext ctx, List<AgentStep> trace, int calls) {
        return new AgentResult(status, null, why, null, ctx.drafts(), trace, calls);
    }
}
