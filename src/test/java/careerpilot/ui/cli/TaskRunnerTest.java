package careerpilot.ui.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.ai.LLMResponse;
import careerpilot.ai.ToolCall;
import careerpilot.app.AppFactory;
import careerpilot.app.CareerAssistantFacade;
import careerpilot.domain.PreparationPlan;
import careerpilot.storage.JsonMapper;
import careerpilot.storage.JsonWorkspaceRepository;
import careerpilot.testsupport.Fixtures;
import careerpilot.testsupport.MockLLMClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The standalone CLI runs a whole task from a file without asking anything (needed for Stage 3 testing). */
class TaskRunnerTest {
    @TempDir Path dir;
    private MockLLMClient llm;
    private JsonWorkspaceRepository repo;
    private CareerAssistantFacade facade;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        llm = new MockLLMClient();
        repo = new JsonWorkspaceRepository(dir.resolve("ws"), Fixtures.clock());
        facade = AppFactory.create(repo, llm, Fixtures.clock());
        Fixtures.seed(repo);
        Files.writeString(dir.resolve("task.txt"), "task: prepare-application\njob-id: J1\nminutes: 120\n");
    }

    private int run(String text, boolean json) {
        return new TaskRunner(facade, new PrintStream(buffer, true, StandardCharsets.UTF_8), dir).quietFor(json).run(text, json);
    }

    private String output() { return buffer.toString(StandardCharsets.UTF_8); }

    @Test
    void agentTaskRunsToCompletionAndSavesAValidPlan() throws Exception {
        llm.then(LLMResponse.tools(List.of(new ToolCall("c1", "inspect_documents", "{\"profileId\":\"P1\",\"jobId\":\"J1\"}"))))
                .thenText("{\"status\":\"PLAN\",\"summary\":\"ok\",\"tasks\":[{\"id\":\"T1\",\"description\":\"Tailor resume\",\"minutes\":30,\"dependsOn\":[]}]}");
        int code = run(Files.readString(dir.resolve("task.txt")), false);
        assertEquals(0, code, output());
        assertTrue(output().contains("1. inspect_documents"));
        assertTrue(output().contains("Saved plan PL1"));
        assertEquals(1, repo.list(PreparationPlan.class, null).size());
    }

    @Test
    void jsonModePrintsOnlyOneJsonDocument() throws Exception {
        llm.thenText("{\"status\":\"NEEDS_INPUT\",\"summary\":\"\",\"question\":\"Do you have Docker experience?\",\"tasks\":[]}");
        int code = run("task: prepare-application\njob-id: J1", true);
        assertEquals(0, code);
        JsonNode doc = JsonMapper.get().readTree(output());
        assertEquals("COMPLETED", doc.path("status").asText());
        assertEquals("NEEDS_INPUT", doc.path("result").path("agentStatus").asText());
        assertEquals("Do you have Docker experience?", doc.path("result").path("question").asText());
    }

    @Test
    void invalidTaskAndFailedStepsGiveDistinctExitCodes() throws Exception {
        assertEquals(2, run("task: dance\njob-id: J1", true));
        assertEquals("INVALID_TASK", JsonMapper.get().readTree(output()).path("status").asText());
        buffer.reset();
        llm.thenFail("AI service returned HTTP 503").thenFail("AI service returned HTTP 503");
        assertEquals(1, run("task: fit\njob-id: J1", false));
        assertTrue(output().contains("fit analysis failed"));
        buffer.reset();
        assertEquals(1, run("task: fit\njob-id: J99", false));
    }

    @Test
    void commandScriptsRunEachLineWithoutQuestions() {
        int code = run("application update --job J1 --status applied --applied-on 2026-11-01\napplication list", false);
        assertEquals(0, code, output());
        assertTrue(output().contains("$ career application list"));
        assertTrue(output().contains("applied"));
    }
}
