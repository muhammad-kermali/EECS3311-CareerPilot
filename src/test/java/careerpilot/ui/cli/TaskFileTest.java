package careerpilot.ui.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskFileTest {
    @Test
    void keyValueTaskIsParsedWithCommentsAndQuotes() {
        TaskFile t = TaskFile.parse("# comment\ntask: cover-letter\njob: posting.txt\nmotivation: \"I like tools\"\nsave: no\n");
        assertFalse(t.isCommandScript());
        assertEquals("cover-letter", t.task());
        assertEquals("I like tools", t.get("motivation"));
        assertFalse(t.flag("save", true));
    }

    @Test
    void taskDefaultsToTheAgent() {
        assertEquals("prepare-application", TaskFile.parse("job-id: J1").task());
    }

    @Test
    void invalidTaskFilesListEveryProblem() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TaskFile.parse("task: dance\njobb: x"));
        assertTrue(e.getMessage().contains("unknown key 'jobb'"));
        assertTrue(e.getMessage().contains("unknown task 'dance'"));
        assertTrue(e.getMessage().contains("job-id"));
        assertThrows(IllegalArgumentException.class, () -> TaskFile.parse("# only a comment\n"));
    }

    @Test
    void commandScriptsKeepQuotedArguments() {
        TaskFile t = TaskFile.parse("job list\ncareer application update --job J1 --notes \"two words\"");
        assertTrue(t.isCommandScript());
        assertEquals(List.of("application", "update", "--job", "J1", "--notes", "two words"), t.commands().get(1));
    }
}
