package careerpilot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import careerpilot.testsupport.Fixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ResumePrototypeTest {
    @Test
    void deepCopyDoesNotShareMutableLists() {
        Resume base = Fixtures.baseResume();
        base.assignId("R1");
        base.markSaved(3, Instant.now());
        base.setReviewed(true);

        Resume copy = base.deepCopy();
        copy.setEntry(0, 0, "Changed in the copy");
        copy.getSections().get(1).getEntries().add("New skill line");

        assertEquals("Built a Java Spring Boot REST API used by 3 internal teams", base.entry(0, 0));
        assertEquals(1, base.getSections().get(1).getEntries().size());
        assertNotSame(base.getSections().get(0), copy.getSections().get(0));
    }

    @Test
    void copyIsANewUnreviewedRecordThatRemembersItsSource() {
        Resume base = Fixtures.baseResume();
        base.assignId("R1");
        base.markSaved(2, Instant.now());
        base.setReviewed(true);
        Resume copy = base.deepCopy();
        assertNull(copy.getId());
        assertEquals(0, copy.getRevision());
        assertFalse(copy.isReviewed());
        assertEquals("R1", copy.getSourceResumeId());
    }
}
