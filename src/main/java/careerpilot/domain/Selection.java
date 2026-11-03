package careerpilot.domain;

/** Documents chosen for export. Null IDs mean "use the latest reviewed one for this job, if any". */
public record Selection(String jobId, String resumeId, String letterId, String planId,
                        boolean includeResume, boolean includeLetter, boolean includePlan) { }
