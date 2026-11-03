package careerpilot.domain;

import careerpilot.common.ValidationException;
import java.util.Arrays;
import java.util.Locale;

public enum ApplicationStatus {
    SAVED, APPLIED, INTERVIEWING, OFFER, REJECTED, WITHDRAWN;

    public boolean isOpen() { return this == SAVED || this == APPLIED || this == INTERVIEWING || this == OFFER; }

    public static ApplicationStatus parse(String value) {
        if (value == null || value.isBlank()) throw new ValidationException("Status is required");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown status '" + value + "'. Use one of " + Arrays.toString(values()).toLowerCase(Locale.ROOT));
        }
    }
}
