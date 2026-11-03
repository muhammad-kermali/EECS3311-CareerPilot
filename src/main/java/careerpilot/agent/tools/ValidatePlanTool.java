package careerpilot.agent.tools;

import careerpilot.agent.AgentTool;
import careerpilot.agent.ToolArgs;
import careerpilot.agent.ToolResult;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import careerpilot.domain.ValidationResult;
import careerpilot.service.PlanValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lets the agent check a draft plan against the deterministic rules before it proposes it. */
public class ValidatePlanTool implements AgentTool {
    private final PlanValidator validator;

    public ValidatePlanTool(PlanValidator validator) { this.validator = validator; }

    @Override public String name() { return "validate_plan"; }
    @Override public String description() {
        return "Check draft tasks: unique IDs, valid dependencies, no cycles, positive minutes and total time within the available minutes.";
    }
    @Override public Map<String, Object> parametersSchema() {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("type", "object");
        task.put("properties", Map.of(
                "id", Schemas.string("Task ID such as T1"),
                "description", Schemas.string("What to do"),
                "minutes", Map.of("type", "integer"),
                "dependsOn", Map.of("type", "array", "items", Map.of("type", "string"))));
        task.put("required", List.of("id", "description", "minutes"));
        return Schemas.object(Map.of("tasks", Map.of("type", "array", "items", task)), List.of("tasks"));
    }
    @Override public List<String> requiredArgs() { return List.of("tasks"); }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolArgs args) {
        List<PreparationTask> tasks = new ArrayList<>();
        for (Object o : args.list("tasks")) {
            if (!(o instanceof Map<?, ?> m)) return ToolResult.rejected(null, name(), "each task must be an object");
            Object minutes = m.get("minutes");
            int mins;
            try {
                mins = minutes instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(minutes));
            } catch (NumberFormatException e) {
                return ToolResult.rejected(null, name(), "minutes must be a whole number");
            }
            List<String> deps = new ArrayList<>();
            if (m.get("dependsOn") instanceof List<?> l) for (Object d : l) deps.add(String.valueOf(d));
            tasks.add(new PreparationTask(String.valueOf(m.get("id")), String.valueOf(m.get("description")), mins, deps));
        }
        var ctx = args.context();
        ValidationResult r = validator.validate(new PreparationPlan(ctx.profileId(), ctx.jobId(), ctx.constraints(), "", tasks), ctx.constraints());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", r.valid());
        data.put("feasible", r.feasible());
        data.put("totalMinutes", r.totalMinutes());
        data.put("availableMinutes", ctx.constraints().availableMinutes());
        data.put("errors", r.errors());
        data.put("warnings", r.warnings());
        return ToolResult.ok(null, name(), data);
    }
}
