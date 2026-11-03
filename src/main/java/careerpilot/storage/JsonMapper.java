package careerpilot.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** One shared Jackson configuration: private fields are stored, getters are ignored. */
public final class JsonMapper {
    private static final ObjectMapper MAPPER = create();

    private JsonMapper() { }

    public static ObjectMapper get() { return MAPPER; }

    private static ObjectMapper create() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        m.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        m.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        m.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
        m.enable(SerializationFeature.INDENT_OUTPUT);
        return m;
    }
}
