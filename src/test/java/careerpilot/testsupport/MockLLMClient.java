package careerpilot.testsupport;

import careerpilot.ai.LLMClient;
import careerpilot.ai.LLMException;
import careerpilot.ai.LLMRequest;
import careerpilot.ai.LLMResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/** Test double: returns scripted responses in order, or answers with a function. Records every request. */
public class MockLLMClient implements LLMClient {
    private final Deque<Object> script = new ArrayDeque<>();
    private Function<LLMRequest, LLMResponse> responder;
    public final List<LLMRequest> requests = new ArrayList<>();

    public MockLLMClient thenText(String json) { script.add(LLMResponse.text(json)); return this; }
    public MockLLMClient then(LLMResponse r) { script.add(r); return this; }
    public MockLLMClient thenFail(String message) { script.add(new LLMException(message, true)); return this; }
    public MockLLMClient respondWith(Function<LLMRequest, LLMResponse> f) { responder = f; return this; }

    @Override
    public LLMResponse generate(LLMRequest request) {
        requests.add(request);
        if (!script.isEmpty()) {
            Object next = script.poll();
            if (next instanceof LLMException e) throw e;
            return (LLMResponse) next;
        }
        if (responder != null) return responder.apply(request);
        throw new AssertionError("MockLLMClient ran out of scripted responses (request: " + request.purpose() + ")");
    }

    @Override public String modelName() { return "mock"; }
}
