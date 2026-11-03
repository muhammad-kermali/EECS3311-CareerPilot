package careerpilot.domain;

import java.util.List;

/** Results that carry non-fatal warnings the interfaces should show to the applicant. */
public interface HasWarnings {
    List<String> warnings();
}
