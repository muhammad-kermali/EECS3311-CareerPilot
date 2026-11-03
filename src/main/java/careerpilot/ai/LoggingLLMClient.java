package careerpilot.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decorator: wraps any {@link LLMClient}, records timing, outcome and token counts, then returns exactly what the
 * wrapped client returned (or rethrows exactly what it threw). Prompts and answers are never written to the log.
 */
public class LoggingLLMClient implements LLMClient {
    private final LLMClient wrapped;
    private final Path logFile;
    private final Clock clock;
    private final AtomicLong totalPromptTokens = new AtomicLong();
    private final AtomicLong totalCompletionTokens = new AtomicLong();

    public LoggingLLMClient(LLMClient wrapped, Path logFile, Clock clock) {
        this.wrapped = wrapped;
        this.logFile = logFile;
        this.clock = clock;
    }

    @Override public String modelName() { return wrapped.modelName(); }

    @Override
    public LLMResponse generate(LLMRequest request) {
        long start = System.nanoTime();
        try {
            LLMResponse response = wrapped.generate(request);
            totalPromptTokens.addAndGet(response.promptTokens());
            totalCompletionTokens.addAndGet(response.completionTokens());
            write(request, start, "OK " + response.kind(), response.promptTokens(), response.completionTokens());
            return response;
        } catch (RuntimeException e) {
            write(request, start, "ERROR " + e.getClass().getSimpleName(), 0, 0);
            throw e;
        }
    }

    public long totalPromptTokens() { return totalPromptTokens.get(); }
    public long totalCompletionTokens() { return totalCompletionTokens.get(); }

    private void write(LLMRequest request, long startNanos, String outcome, int in, int out) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        String line = String.format("%s\t%s\t%s\t%dms\t%s\tin=%d\tout=%d%n",
                clock.instant(), wrapped.modelName(), request.purpose(), ms, outcome, in, out);
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must never break the feature that is being logged.
        }
    }
}
