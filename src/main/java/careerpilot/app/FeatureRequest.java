package careerpilot.app;

import careerpilot.common.ValidationException;
import java.util.HashMap;
import java.util.Map;

/** A request from either interface: an operation plus named arguments. */
public final class FeatureRequest {
    private final Operation operation;
    private final Map<String, Object> args = new HashMap<>();

    private FeatureRequest(Operation operation) { this.operation = operation; }

    public static FeatureRequest of(Operation op) { return new FeatureRequest(op); }

    public FeatureRequest with(String key, Object value) {
        if (value != null) args.put(key, value);
        return this;
    }

    public Operation operation() { return operation; }

    public String str(String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    public String required(String key) {
        String v = str(key);
        if (v == null || v.isBlank()) throw new ValidationException("Missing required value: " + key);
        return v;
    }

    public int integer(String key, int fallback) {
        Object v = args.get(key);
        if (v == null || v.toString().isBlank()) return fallback;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(key + " must be a whole number (got '" + v + "')");
        }
    }

    public Integer optionalInt(String key) {
        Object v = args.get(key);
        return v == null ? null : integer(key, 0);
    }

    public boolean flag(String key, boolean fallback) {
        Object v = args.get(key);
        return v == null ? fallback : (v instanceof Boolean b ? b : Boolean.parseBoolean(v.toString()));
    }

    public <T> T object(String key, Class<T> type) {
        Object v = args.get(key);
        if (v == null) throw new ValidationException("Missing required value: " + key);
        if (!type.isInstance(v)) throw new ValidationException(key + " has the wrong type");
        return type.cast(v);
    }
}
