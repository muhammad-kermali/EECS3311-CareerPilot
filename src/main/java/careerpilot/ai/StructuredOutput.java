package careerpilot.ai;

import careerpilot.common.AiOutputException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Runs a JSON-producing model call, validates the answer with a caller-supplied parser, and allows exactly one
 * repair attempt when validation fails (SD08). Schema and evidence rules live in the caller's parser.
 */
public class StructuredOutput {
    @FunctionalInterface
    public interface Parser<T> {
        T parse(JsonNode json) throws OutputFormatException;
    }

    private final LLMClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    public StructuredOutput(LLMClient llm) { this.llm = llm; }

    public LLMClient client() { return llm; }

    public <T> T generate(LLMRequest request, Parser<T> parser) {
        return generate(request, parser, parser);
    }

    /**
     * @param strict  parser for the first answer; any rule violation triggers the repair attempt
     * @param repairParser parser for the repaired answer; may be more lenient (for example, drop bad evidence with a warning)
     */
    public <T> T generate(LLMRequest request, Parser<T> strict, Parser<T> repairParser) {
        LLMResponse first = llm.generate(request);
        String problem;
        try {
            return strict.parse(readJson(first.content()));
        } catch (OutputFormatException e) {
            problem = e.getMessage();
        }
        LLMResponse second = llm.generate(request.withRepair(first.content(), problem));
        try {
            return repairParser.parse(readJson(second.content()));
        } catch (OutputFormatException e) {
            throw new AiOutputException("The AI answer was still invalid after one repair attempt (" + e.getMessage()
                    + "). Nothing was saved; please try again.");
        }
    }

    public JsonNode readJson(String content) throws OutputFormatException {
        if (content == null || content.isBlank()) throw new OutputFormatException("the answer was empty");
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) s = s.substring(firstNewline + 1, lastFence).trim();
        }
        try {
            JsonNode node = mapper.readTree(s);
            if (node == null || !node.isObject()) throw new OutputFormatException("the answer must be one JSON object");
            return node;
        } catch (IOException e) {
            throw new OutputFormatException("the answer was not valid JSON");
        }
    }
}
