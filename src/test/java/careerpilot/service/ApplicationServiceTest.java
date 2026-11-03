package careerpilot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.common.ValidationException;
import careerpilot.domain.ApplicationRecord;
import careerpilot.domain.ApplicationStatus;
import careerpilot.storage.JsonWorkspaceRepository;
import careerpilot.testsupport.Fixtures;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationServiceTest {
    @TempDir Path dir;
    private ApplicationService service;
    private String jobId;

    @BeforeEach
    void setUp() {
        JsonWorkspaceRepository repo = new JsonWorkspaceRepository(dir, Fixtures.clock());
        jobId = Fixtures.seed(repo)[1];
        service = new ApplicationService(repo, Fixtures.clock());
    }

    @Test
    void newRecordTakesTheJobDeadlineAndOverdueIsComputedFromToday() {
        ApplicationRecord r = service.updateApplication(jobId, new ApplicationChanges(null, "note", null, null), null);
        assertEquals(LocalDate.of(2026, 11, 30), r.getDeadline());
        service.updateApplication(jobId, new ApplicationChanges(null, null, "2026-10-15", null), null);
        assertTrue(service.listApplications(null, true).get(0).overdue());
    }

    @Test
    void appliedStatusFillsAppliedDateAndClearsOverdue() {
        service.updateApplication(jobId, new ApplicationChanges(null, null, "2026-10-15", null), null);
        ApplicationRecord r = service.updateApplication(jobId, new ApplicationChanges("applied", null, null, null), null);
        assertEquals(ApplicationStatus.APPLIED, r.getStatus());
        assertEquals(LocalDate.of(2026, 11, 1), r.getAppliedOn());
        assertTrue(service.listApplications(null, true).isEmpty());
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(ValidationException.class, () -> service.updateApplication(jobId, new ApplicationChanges("ghosted", null, null, null), null));
        assertThrows(ValidationException.class, () -> service.updateApplication(jobId, new ApplicationChanges(null, null, "30/11/2026", null), null));
        assertThrows(ValidationException.class, () -> service.updateApplication(jobId, new ApplicationChanges(null, null, null, "2027-01-01"), null));
    }
}
