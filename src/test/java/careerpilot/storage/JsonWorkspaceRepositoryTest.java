package careerpilot.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.common.NotFoundException;
import careerpilot.common.RevisionConflictException;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.JobPosting;
import careerpilot.testsupport.Fixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonWorkspaceRepositoryTest {
    @TempDir Path dir;
    private JsonWorkspaceRepository repo;

    @BeforeEach
    void setUp() { repo = new JsonWorkspaceRepository(dir, Fixtures.clock()); }

    @Test
    void saveAssignsReadableIdAndRevision() {
        JobPosting a = repo.save(Fixtures.job(null));
        JobPosting b = repo.save(Fixtures.job(null));
        assertEquals("J1", a.getId());
        assertEquals("J2", b.getId());
        assertEquals(1, a.getRevision());
        assertEquals("Junior Developer", repo.load(JobPosting.class, "J1").getTitle());
    }

    @Test
    void staleRevisionIsRejectedAndNothingIsOverwritten() {
        CandidateProfile saved = repo.save(Fixtures.profile());
        CandidateProfile copyA = repo.load(CandidateProfile.class, saved.getId());
        CandidateProfile copyB = repo.load(CandidateProfile.class, saved.getId());
        copyA.setFullName("First edit");
        repo.save(copyA);
        copyB.setFullName("Second edit");
        assertThrows(RevisionConflictException.class, () -> repo.save(copyB));
        assertEquals("First edit", repo.load(CandidateProfile.class, saved.getId()).getFullName());
        assertEquals(2, repo.load(CandidateProfile.class, saved.getId()).getRevision());
    }

    @Test
    void reservedIdsAreSkippedByListAndFind() {
        CandidateProfile unsaved = Fixtures.profile();
        repo.assignId(unsaved);
        assertTrue(repo.list(CandidateProfile.class, null).isEmpty());
        assertTrue(repo.find(CandidateProfile.class, unsaved.getId()).isEmpty());
        assertThrows(NotFoundException.class, () -> repo.load(CandidateProfile.class, unsaved.getId()));
    }

    @Test
    void saveBatchWritesAllRecords() {
        String[] ids = Fixtures.seed(repo);
        CandidateProfile p = repo.load(CandidateProfile.class, ids[0]);
        assertEquals("R1", p.getBaseResumeId());
        assertEquals(1, repo.list(careerpilot.domain.Resume.class, null).size());
        assertEquals(List.of("J1"), repo.list(JobPosting.class, null).stream().map(JobPosting::getId).toList());
    }

    @Test
    void invalidIdsNeverTouchTheFileSystem() {
        assertTrue(repo.find(JobPosting.class, "../../etc/passwd").isEmpty());
    }
}
