package careerpilot.ai;

/** Target interface for every model call. Services and the agent depend only on this. */
public interface LLMClient {
    LLMResponse generate(LLMRequest request);

    String modelName();
}
