package careerpilot.domain;

import java.util.List;

/** Unsaved result of a resume import. The applicant reviews it before anything is stored. */
public record ProfilePreview(CandidateProfile profile, Resume resume, String sourceName, List<String> warnings) implements HasWarnings { }
