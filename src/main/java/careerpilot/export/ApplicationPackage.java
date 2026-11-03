package careerpilot.export;

import java.util.LinkedHashMap;
import java.util.Map;

/** Product of the builders: file name to content, plus the revision of every source record used. */
public record ApplicationPackage(Map<String, String> files, Map<String, Integer> sourceRevisions) {
    public ApplicationPackage {
        files = new LinkedHashMap<>(files);
        sourceRevisions = new LinkedHashMap<>(sourceRevisions);
    }
}
