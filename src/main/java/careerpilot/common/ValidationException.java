package careerpilot.common;

import java.util.List;

/** Input or state failed a deterministic rule. Nothing was saved. */
public class ValidationException extends CareerPilotException {
    private final List<String> problems;

    public ValidationException(String message) { this(message, List.of()); }

    public ValidationException(String message, List<String> problems) {
        super(problems.isEmpty() ? message : message + ": " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() { return problems; }
}
