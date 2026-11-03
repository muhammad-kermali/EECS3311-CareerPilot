package careerpilot.domain;

import java.util.List;

/** What already exists for one profile/job pair, and whether it is still current. Used by the agent's inspect tool. */
public record DocumentStatus(String profileId, String jobId,
                             String resumeId, String resumeState,
                             String letterId, String letterState,
                             String fitReportId, String fitState,
                             String planId, List<String> notes) {
    public static final String MISSING = "missing";
    public static final String CURRENT = "current";
    public static final String STALE = "stale";
}
