package careerpilot.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.testsupport.Fixtures;
import careerpilot.testsupport.MockLLMClient;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoggingLLMClientTest {
    @TempDir Path dir;

    @Test
    void returnsTheSameResponseAndRethrowsTheSameErrorWithoutLoggingContent() throws Exception {
        LLMResponse canned = LLMResponse.text("{\"secret\":\"Jordan Lee resume text\"}");
        MockLLMClient inner = new MockLLMClient().then(canned).thenFail("boom");
        Path log = dir.resolve("logs/llm.log");
        LoggingLLMClient client = new LoggingLLMClient(inner, log, Fixtures.clock());

        assertSame(canned, client.generate(LLMRequest.json("profile.extract", "s", "Jordan Lee resume text")));
        LLMException e = assertThrows(LLMException.class, () -> client.generate(LLMRequest.json("job.analyze", "s", "u")));
        assertTrue(e.getMessage().contains("boom"));

        String text = Files.readString(log);
        assertTrue(text.contains("profile.extract") && text.contains("OK"));
        assertTrue(text.contains("job.analyze") && text.contains("ERROR"));
        assertFalse(text.contains("Jordan"));
    }
}
