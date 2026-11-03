package careerpilot.parsing;

import careerpilot.common.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Factory Method creator. {@link #importText(Path)} is the shared import workflow; subclasses decide which
 * {@link DocumentParser} to create by overriding {@link #createParser()}.
 */
public abstract class ResumeImporter {
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public final SourceText importText(Path path) {
        if (path == null || !Files.isRegularFile(path)) throw new ValidationException("File not found: " + path);
        try {
            if (Files.size(path) > MAX_BYTES) throw new ValidationException("Resume files must be 5 MB or smaller");
            if (Files.size(path) == 0) throw new ValidationException("The file is empty");
        } catch (IOException e) {
            throw new ValidationException("Could not read " + path.getFileName() + ": " + e.getMessage());
        }
        DocumentParser parser = createParser();
        SourceText text = parser.extractText(path);
        if (text.text() == null || text.text().trim().length() < 30) {
            throw new ValidationException("No readable text was found in " + path.getFileName()
                    + ". Scanned or image-only resumes are not supported; use a text-based PDF or TXT file.");
        }
        return text;
    }

    /** The factory method. */
    protected abstract DocumentParser createParser();
}
