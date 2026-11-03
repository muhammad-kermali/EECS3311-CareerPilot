package careerpilot.service;

import careerpilot.domain.HasWarnings;
import java.nio.file.Path;
import java.util.List;

public record ExportResult(Path folder, List<String> files, List<String> warnings) implements HasWarnings { }
