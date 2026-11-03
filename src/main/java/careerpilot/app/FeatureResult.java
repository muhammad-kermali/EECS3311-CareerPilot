package careerpilot.app;

import java.util.List;

/** Uniform result returned to both interfaces. */
public record FeatureResult(ResultStatus status, Object payload, List<String> warnings, String error) {
    public static FeatureResult ok(Object payload, List<String> warnings) { return new FeatureResult(ResultStatus.OK, payload, List.copyOf(warnings), null); }
    public static FeatureResult error(String message) { return new FeatureResult(ResultStatus.ERROR, null, List.of(), message); }

    public boolean isOk() { return status == ResultStatus.OK; }

    public <T> T payload(Class<T> type) { return type.cast(payload); }
}
