package careerpilot.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.common.AiOutputException;
import careerpilot.testsupport.MockLLMClient;
import org.junit.jupiter.api.Test;

class StructuredOutputTest {
    private static final StructuredOutput.Parser<String> NAME = json -> Json.text(json, "name", true);

    @Test
    void codeFencesAreStripped() {
        MockLLMClient llm = new MockLLMClient().thenText("```json\n{\"name\":\"ok\"}\n```");
        assertEquals("ok", new StructuredOutput(llm).generate(LLMRequest.json("t", "s", "u"), NAME));
    }

    @Test
    void oneRepairAttemptIncludesTheProblem() {
        MockLLMClient llm = new MockLLMClient().thenText("{}").thenText("{\"name\":\"fixed\"}");
        assertEquals("fixed", new StructuredOutput(llm).generate(LLMRequest.json("t", "s", "u"), NAME));
        LLMRequest repair = llm.requests.get(1);
        assertEquals("t.repair", repair.purpose());
        assertTrue(repair.messages().get(repair.messages().size() - 1).content().contains("'name' is required"));
    }

    @Test
    void stillInvalidAfterRepairFailsWithoutAThirdCall() {
        MockLLMClient llm = new MockLLMClient().thenText("nope").thenText("{}");
        assertThrows(AiOutputException.class, () -> new StructuredOutput(llm).generate(LLMRequest.json("t", "s", "u"), NAME));
        assertEquals(2, llm.requests.size());
    }
}
