package careerpilot.ai;

/** Used when no API key is set: AI features fail with a clear message, deterministic features keep working. */
public class UnconfiguredLLMClient implements LLMClient {
    @Override
    public LLMResponse generate(LLMRequest request) {
        throw new LLMException("No AI key is configured. Copy .env.example to .env and set LLM_API_KEY, then restart CareerPilot.", false);
    }

    @Override public String modelName() { return "not-configured"; }
}
