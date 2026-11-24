package careerpilot.ui.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.app.AppConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LaunchOptionsTest {
    @Test
    void singleDashFormFromTheAssignmentExampleWorks() {
        LaunchOptions o = LaunchOptions.parse(new String[]{"-LLMAPI", "gemini", "-input", "task.txt"});
        assertEquals("task.txt", o.input());
        AppConfig c = o.applyTo(AppConfig.of(Map.of()));
        assertEquals("gemini-3.1-flash-lite", c.model());
        assertTrue(c.baseUrl().contains("generativelanguage.googleapis.com"));
    }

    @Test
    void explicitModelOverridesThePresetAndCommandArgumentsPassThrough() {
        LaunchOptions o = LaunchOptions.parse(new String[]{"--llm", "openai", "--llm-model", "gpt-4.1-nano", "job", "fit", "--job", "J1", "--yes"});
        assertEquals("gpt-4.1-nano", o.applyTo(AppConfig.of(Map.of())).model());
        assertArrayEquals(new String[]{"job", "fit", "--job", "J1", "--yes"}, o.rest());
    }

    @Test
    void badOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--llm", "claude"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--input"}));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[]{"--llm-key-env", "SURELY_NOT_SET_12345"}).applyTo(AppConfig.of(Map.of())));
    }
}
