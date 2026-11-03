package careerpilot.agent;

import java.util.List;
import java.util.Map;

/** Arguments after JSON parsing and validation, plus the run context they apply to. */
public class ToolArgs {
    private final Map<String, Object> values;
    private final RunContext context;

    public ToolArgs(Map<String, Object> values, RunContext context) {
        this.values = Map.copyOf(values);
        this.context = context;
    }

    public RunContext context() { return context; }
    public boolean has(String key) { return values.get(key) != null; }
    public Object raw(String key) { return values.get(key); }

    public String string(String key, String fallback) {
        Object v = values.get(key);
        return v == null ? fallback : v.toString();
    }

    public boolean bool(String key) {
        Object v = values.get(key);
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    public List<Object> list(String key) {
        Object v = values.get(key);
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    public Map<String, Object> asMap() { return values; }
}
