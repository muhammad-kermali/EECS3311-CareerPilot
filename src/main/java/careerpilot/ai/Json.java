package careerpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Small helpers for reading model JSON with clear error messages. */
public final class Json {
    private Json() { }

    public static String text(JsonNode node, String field, boolean required) throws OutputFormatException {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || (v.isTextual() && v.asText().isBlank())) {
            if (required) throw new OutputFormatException("field '" + field + "' is required");
            return "";
        }
        if (!v.isValueNode()) throw new OutputFormatException("field '" + field + "' must be text");
        return v.asText().trim();
    }

    public static int integer(JsonNode node, String field, int min, int max) throws OutputFormatException {
        JsonNode v = node.get(field);
        if (v == null || !v.canConvertToInt()) throw new OutputFormatException("field '" + field + "' must be a whole number");
        int i = v.asInt();
        if (i < min || i > max) throw new OutputFormatException("field '" + field + "' must be between " + min + " and " + max);
        return i;
    }

    public static JsonNode array(JsonNode node, String field) throws OutputFormatException {
        JsonNode v = node.get(field);
        if (v == null || !v.isArray()) throw new OutputFormatException("field '" + field + "' must be an array");
        return v;
    }

    public static List<String> strings(JsonNode node, String field) throws OutputFormatException {
        List<String> out = new ArrayList<>();
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return out;
        if (!v.isArray()) throw new OutputFormatException("field '" + field + "' must be an array of strings");
        for (JsonNode s : v) if (s.isValueNode() && !s.asText().isBlank()) out.add(s.asText().trim());
        return out;
    }
}
