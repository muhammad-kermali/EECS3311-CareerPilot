package careerpilot.common;

/** The model answered, but its output stayed invalid after the single allowed repair attempt. */
public class AiOutputException extends CareerPilotException {
    public AiOutputException(String message) { super(message); }
}
