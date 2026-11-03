package careerpilot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class JobServiceDeadlineTest {
    @Test
    void detectsCommonDeadlinePhrases() {
        assertEquals(LocalDate.of(2026, 11, 30), JobService.detectDeadline("Apply by November 30, 2026."));
        assertEquals(LocalDate.of(2026, 12, 5), JobService.detectDeadline("Closing date: 2026-12-05"));
        assertEquals(LocalDate.of(2026, 12, 1), JobService.detectDeadline("Applications close Dec. 1, 2026"));
    }

    @Test
    void unknownDeadlineStaysBlank() {
        assertNull(JobService.detectDeadline("We review applications on a rolling basis."));
    }
}
