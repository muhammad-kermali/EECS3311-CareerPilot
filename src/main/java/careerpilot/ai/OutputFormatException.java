package careerpilot.ai;

/** Thrown by a parser when model output breaks the expected schema or a grounding rule. Triggers the repair attempt. */
public class OutputFormatException extends Exception {
    public OutputFormatException(String message) { super(message); }
}
