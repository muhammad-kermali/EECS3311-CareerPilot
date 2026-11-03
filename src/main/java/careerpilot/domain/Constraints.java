package careerpilot.domain;

import java.time.LocalDate;

/** Limits the agent's plan must respect. */
public record Constraints(int availableMinutes, LocalDate deadline, String preferences) { }
