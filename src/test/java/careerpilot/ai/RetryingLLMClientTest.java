package careerpilot.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.testsupport.MockLLMClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryingLLMClientTest {
    private final List<Duration> waits = new ArrayList<>();

    private RetryingLLMClient retrying(MockLLMClient inner) {
        return new RetryingLLMClient(inner, 3, Duration.ofSeconds(2), waits::add);
    }

    @Test
    void temporaryErrorsAreRetriedWithGrowingWaits() {
        LLMResponse ok = LLMResponse.text("{}");
        MockLLMClient inner = new MockLLMClient().thenFail("HTTP 503 busy").thenFail("HTTP 429").then(ok);
        assertSame(ok, retrying(inner).generate(LLMRequest.json("t", "s", "u")));
        assertEquals(3, inner.requests.size());
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)), waits);
    }

    @Test
    void permanentErrorsAreNotRetried() {
        MockLLMClient inner = new MockLLMClient().then(LLMResponse.text("unused"));
        RetryingLLMClient client = new RetryingLLMClient(new LLMClient() {
            @Override public LLMResponse generate(LLMRequest r) { inner.requests.add(r); throw new LLMException("HTTP 401 bad key", false); }
            @Override public String modelName() { return "x"; }
        }, 3, Duration.ofSeconds(2), waits::add);
        assertThrows(LLMException.class, () -> client.generate(LLMRequest.json("t", "s", "u")));
        assertEquals(1, inner.requests.size());
        assertTrue(waits.isEmpty());
    }

    @Test
    void givesUpAfterTheLastRetry() {
        MockLLMClient inner = new MockLLMClient().thenFail("busy").thenFail("busy").thenFail("busy").thenFail("busy");
        LLMException e = assertThrows(LLMException.class, () -> retrying(inner).generate(LLMRequest.json("t", "s", "u")));
        assertEquals(4, inner.requests.size());
        assertTrue(e.getMessage().contains("tried 4 times"));
    }
}
