package careerpilot.common;

/** Base type for expected, user-facing failures. The message is safe to show to the user. */
public class CareerPilotException extends RuntimeException {
    public CareerPilotException(String message) { super(message); }
    public CareerPilotException(String message, Throwable cause) { super(message, cause); }
}
