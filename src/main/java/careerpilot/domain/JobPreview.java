package careerpilot.domain;

import java.util.List;

/** Unsaved job analysis for the applicant to review. */
public record JobPreview(JobPosting job, List<String> warnings) implements HasWarnings { }
