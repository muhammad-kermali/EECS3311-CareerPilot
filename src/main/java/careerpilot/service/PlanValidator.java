package careerpilot.service;

import careerpilot.common.TextUtil;
import careerpilot.domain.Constraints;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import careerpilot.domain.ValidationResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic plan checks: IDs, dependencies, cycles, durations, time budget and deadline. */
public class PlanValidator {
    private final Clock clock;

    public PlanValidator(Clock clock) { this.clock = clock; }

    public ValidationResult validate(PreparationPlan plan, Constraints constraints) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<PreparationTask> tasks = plan.getTasks();
        if (tasks.isEmpty()) errors.add("the plan has no tasks");
        Map<String, PreparationTask> byId = new HashMap<>();
        for (PreparationTask t : tasks) {
            if (TextUtil.isBlank(t.getId())) errors.add("every task needs an ID");
            else if (byId.put(t.getId(), t) != null) errors.add("duplicate task ID " + t.getId());
            if (TextUtil.isBlank(t.getDescription())) errors.add("task " + t.getId() + " has no description");
            if (t.getMinutes() <= 0) errors.add("task " + t.getId() + " must take at least 1 minute");
            if (t.getMinutes() > 480) warnings.add("task " + t.getId() + " is longer than 8 hours; consider splitting it");
        }
        for (PreparationTask t : tasks) {
            for (String d : t.getDependsOn()) {
                if (!byId.containsKey(d)) errors.add("task " + t.getId() + " depends on unknown task " + d);
                if (d.equals(t.getId())) errors.add("task " + t.getId() + " depends on itself");
            }
        }
        String cycle = findCycle(byId);
        if (cycle != null) errors.add("tasks have a circular dependency: " + cycle);

        int total = tasks.stream().mapToInt(PreparationTask::getMinutes).sum();
        boolean fitsTime = total <= constraints.availableMinutes();
        if (!fitsTime) errors.add("tasks need " + total + " minutes but only " + constraints.availableMinutes() + " are available");
        else if (total > constraints.availableMinutes() * 0.9) warnings.add("the plan uses more than 90% of the available time");
        LocalDate today = LocalDate.now(clock);
        if (constraints.deadline() != null && constraints.deadline().isBefore(today)) {
            errors.add("the deadline " + constraints.deadline() + " has already passed");
        }
        boolean wellFormed = errors.stream().noneMatch(e -> !e.startsWith("tasks need") && !e.startsWith("the deadline"));
        return new ValidationResult(wellFormed, errors.isEmpty(), total, errors, warnings);
    }

    private static String findCycle(Map<String, PreparationTask> byId) {
        Set<String> done = new HashSet<>();
        for (String start : byId.keySet()) {
            String c = visit(start, byId, new ArrayList<>(), done);
            if (c != null) return c;
        }
        return null;
    }

    private static String visit(String id, Map<String, PreparationTask> byId, List<String> path, Set<String> done) {
        if (path.contains(id)) {
            List<String> loop = new ArrayList<>(path.subList(path.indexOf(id), path.size()));
            loop.add(id);
            return String.join(" -> ", loop);
        }
        if (done.contains(id) || !byId.containsKey(id)) return null;
        path.add(id);
        for (String d : byId.get(id).getDependsOn()) {
            if (d.equals(id)) continue;
            String c = visit(d, byId, path, done);
            if (c != null) return c;
        }
        path.remove(path.size() - 1);
        done.add(id);
        return null;
    }
}
