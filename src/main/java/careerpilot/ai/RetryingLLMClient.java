package careerpilot.ai;

import java.time.Duration;

/**
 * Decorator: retries a model call when the provider reports a temporary problem (busy, rate limited, timeout).
 * Waits 1x, 2x, 4x the base delay between attempts. Permanent errors (bad key, unknown model) are not retried.
 * Callers see either a normal response or the last error, exactly as from the wrapped client.
 */
public class RetryingLLMClient implements LLMClient {
    /** Pause between attempts; replaced in tests so they do not actually wait. */
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    private final LLMClient wrapped;
    private final int maxRetries;
    private final Duration baseDelay;
    private final Sleeper sleeper;

    public RetryingLLMClient(LLMClient wrapped, int maxRetries, Duration baseDelay) {
        this(wrapped, maxRetries, baseDelay, d -> Thread.sleep(d.toMillis()));
    }

    public RetryingLLMClient(LLMClient wrapped, int maxRetries, Duration baseDelay, Sleeper sleeper) {
        this.wrapped = wrapped;
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
        this.sleeper = sleeper;
    }

    @Override public String modelName() { return wrapped.modelName(); }

    @Override
    public LLMResponse generate(LLMRequest request) {
        LLMException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                try {
                    sleeper.sleep(baseDelay.multipliedBy(1L << (attempt - 1)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw last;
                }
            }
            try {
                return wrapped.generate(request);
            } catch (LLMException e) {
                if (!e.isRetryable()) throw e;
                last = e;
            }
        }
        throw new LLMException(last.getMessage() + " (tried " + (maxRetries + 1) + " times)", true);
    }
}
