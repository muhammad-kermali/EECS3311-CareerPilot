package careerpilot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.domain.CandidateProfile;
import careerpilot.domain.EvidenceRef;
import careerpilot.testsupport.Fixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvidenceValidatorTest {
    private final EvidenceValidator validator = new EvidenceValidator();
    private final CandidateProfile profile = Fixtures.profile();

    private EvidenceValidator.Checked read(String json) throws Exception {
        JsonNode node = new ObjectMapper().readTree(json);
        return validator.readEvidence(node, profile);
    }

    @Test
    void acceptsRealPathWithExactQuote() throws Exception {
        EvidenceValidator.Checked c = read("[{\"path\":\"experience:E1:bullet:0\",\"quote\":\"Spring Boot REST API\"}]");
        assertEquals(1, c.valid().size());
        assertTrue(c.rejected().isEmpty());
    }

    @Test
    void rejectsInventedPathsAndMisquotes() throws Exception {
        EvidenceValidator.Checked c = read("[{\"path\":\"experience:E7\",\"quote\":\"x\"},"
                + "{\"path\":\"skill:Java\",\"quote\":\"Kotlin\"}]");
        assertTrue(c.valid().isEmpty());
        assertEquals(2, c.rejected().size());
    }

    @Test
    void flagsNumbersThatTheEvidenceDoesNotContain() {
        List<EvidenceRef> ev = List.of(new EvidenceRef("experience:E1:bullet:1", 0, "40%"));
        assertEquals(Set.of("75", "12"), validator.unsupportedNumbers("Cut report time by 75% across 12 dashboards", ev, profile));
        assertTrue(validator.unsupportedNumbers("Cut report time by 40%", ev, profile).isEmpty());
    }

    @Test
    void commonPathVariationsAreMappedButInventedPathsStayRejected() throws Exception {
        // Real-model finding (Gemini, Nov 14): paths came back as "profile/experience/E1/bullet/0".
        EvidenceValidator.Checked c = read("[{\"path\":\"profile/experience/E1/bullet/0\",\"quote\":\"REST API\"},"
                + "{\"path\":\"[skills:Java]\",\"quote\":\"Java\"},{\"path\":\"experience.E1.bullets.1\",\"quote\":\"40%\"},"
                + "{\"path\":\"profile/experience/E9/bullet/0\",\"quote\":\"x\"}]");
        assertEquals(List.of("experience:E1:bullet:0", "skill:Java", "experience:E1:bullet:1"),
                c.valid().stream().map(EvidenceRef::getPath).toList());
        assertEquals(1, c.rejected().size());
    }
}
