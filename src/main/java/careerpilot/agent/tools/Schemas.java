package careerpilot.agent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds small JSON Schemas for tool arguments. */
final class Schemas {
    private Schemas() { }

    static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        m.put("required", required);
        return m;
    }

    static Map<String, Object> string(String description) { return Map.of("type", "string", "description", description); }

    static Map<String, Object> bool(String description) { return Map.of("type", "boolean", "description", description); }

    static Map<String, Object> ids() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("profileId", string("Profile ID from the run scope"));
        p.put("jobId", string("Job ID from the run scope"));
        return p;
    }
}
