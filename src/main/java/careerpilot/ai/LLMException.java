package careerpilot.ai;

import careerpilot.common.CareerPilotException;

/** The model provider could not be reached or returned an error. */
public class LLMException extends CareerPilotException {
    private final boolean retryable;

    public LLMException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }
}
