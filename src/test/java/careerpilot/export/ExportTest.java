package careerpilot.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.common.ValidationException;
import careerpilot.domain.JobPosting;
import careerpilot.domain.Resume;
import careerpilot.testsupport.Fixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportTest {
    @TempDir Path dir;

    private ResolvedSelection selection() {
        JobPosting job = Fixtures.job(null);
        job.assignId("J1");
        Resume r = Fixtures.baseResume().deepCopy();
        r.assignId("R2");
        r.setTitle("Resume for J1");
        return new ResolvedSelection(job, r, null, null);
    }

    @Test
    void sameDirectorStepsProduceDifferentFormats() {
        ApplicationPackageDirector director = new ApplicationPackageDirector();
        ApplicationPackage md = director.construct(new MarkdownPackageBuilder(), selection());
        ApplicationPackage txt = director.construct(new TextPackageBuilder(), selection());
        assertTrue(md.files().containsKey("resume.md"));
        assertTrue(md.files().get("resume.md").startsWith("# Resume for J1"));
        assertTrue(txt.files().containsKey("resume.txt"));
        assertTrue(md.files().get("MANIFEST.txt").contains("Resume:R2"));
        assertEquals(md.sourceRevisions().keySet(), txt.sourceRevisions().keySet());
    }

    @Test
    void writerCreatesTheFolderAndRefusesToOverwrite() throws Exception {
        ApplicationPackage pkg = new ApplicationPackageDirector().construct(new MarkdownPackageBuilder(), selection());
        Path out = new FileExporter().write(pkg, dir.resolve("pkg"));
        assertTrue(Files.exists(out.resolve("resume.md")));
        assertThrows(ValidationException.class, () -> new FileExporter().write(pkg, dir.resolve("pkg")));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }
}
