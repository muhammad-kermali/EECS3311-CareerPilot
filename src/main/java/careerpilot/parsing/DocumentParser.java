package careerpilot.parsing;

import java.nio.file.Path;

/** Product role in the Factory Method: turns one file format into plain text. */
public interface DocumentParser {
    SourceText extractText(Path path);
}
