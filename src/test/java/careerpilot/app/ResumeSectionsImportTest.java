package careerpilot.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.domain.ProfilePreview;
import careerpilot.domain.ResumeSection;
import careerpilot.storage.JsonWorkspaceRepository;
import careerpilot.testsupport.Fixtures;
import careerpilot.testsupport.MockLLMClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real-model finding (Gemini, Nov 14): the extraction returned only a PROJECTS section, losing Experience and Education. */
class ResumeSectionsImportTest {
    @TempDir Path dir;
    private MockLLMClient llm;
    private CareerAssistantFacade facade;
    private Path resume;

    private static final String PROFILE_PART = "\"fullName\":\"Jordan Lee\",\"location\":\"Toronto\",\"summary\":\"CS student\","
            + "\"skills\":[\"Java\",\"SQL\"],"
            + "\"education\":[{\"school\":\"York University\",\"credential\":\"BSc\",\"start\":\"2022-09\",\"end\":\"Present\"}],"
            + "\"experience\":[{\"title\":\"Intern\",\"organization\":\"Maple Leaf Logistics\",\"start\":\"2025-05\",\"end\":\"2025-08\","
            + "\"bullets\":[\"Built a Java REST API used by 3 teams\"]}]";
    private static final String ONLY_PROJECTS = "{" + PROFILE_PART
            + ",\"sections\":[{\"heading\":\"PROJECTS\",\"entries\":[\"StudyBuddy: a React app\"]}]}";
    private static final String FULL = "{" + PROFILE_PART + ",\"sections\":["
            + "{\"heading\":\"Experience\",\"entries\":[\"Intern, Maple Leaf Logistics\",\"Built a Java REST API used by 3 teams\"]},"
            + "{\"heading\":\"Projects\",\"entries\":[\"StudyBuddy: a React app\"]}]}";

    @BeforeEach
    void setUp() throws Exception {
        llm = new MockLLMClient();
        facade = AppFactory.create(new JsonWorkspaceRepository(dir.resolve("ws"), Fixtures.clock()), llm, Fixtures.clock());
        resume = dir.resolve("resume.txt");
        Files.writeString(resume, "Jordan Lee\nBuilt a Java REST API used by 3 teams\nStudyBuddy: a React app\nSkills: Java, SQL");
    }

    private ProfilePreview importIt() {
        FeatureResult r = facade.execute(FeatureRequest.of(Operation.IMPORT_RESUME).with("path", resume.toString()));
        assertTrue(r.isOk(), () -> r.error());
        return r.payload(ProfilePreview.class);
    }

    @Test
    void missingSectionsTriggerOneRepairAndTheRepairedAnswerIsUsed() {
        llm.thenText(ONLY_PROJECTS).thenText(FULL);
        ProfilePreview p = importIt();
        assertEquals(2, llm.requests.size());
        assertTrue(llm.requests.get(1).messages().get(llm.requests.get(1).messages().size() - 1).content().contains("bullet(s) are missing"));
        assertEquals(List.of("Experience", "Projects"), p.resume().getSections().stream().map(ResumeSection::getHeading).toList());
        assertFalse(p.warnings().stream().anyMatch(w -> w.contains("rebuilt")));
    }

    @Test
    void stillMissingAfterRepairRebuildsSectionsFromTheProfileAndKeepsProjects() {
        llm.thenText(ONLY_PROJECTS).thenText(ONLY_PROJECTS);
        ProfilePreview p = importIt();
        List<String> headings = p.resume().getSections().stream().map(ResumeSection::getHeading).toList();
        assertEquals(List.of("Summary", "Education", "Experience", "PROJECTS", "Skills"), headings);
        assertTrue(p.resume().getSections().get(2).getEntries().contains("Built a Java REST API used by 3 teams"));
        assertTrue(p.warnings().stream().anyMatch(w -> w.contains("rebuilt")));
    }
}
