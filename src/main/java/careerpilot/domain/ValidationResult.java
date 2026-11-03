package careerpilot.domain;

import java.util.List;

/** valid: the plan is well formed. feasible: it also fits the available time. */
public record ValidationResult(boolean valid, boolean feasible, int totalMinutes, List<String> errors, List<String> warnings) { }
