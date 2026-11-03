package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class GapReport extends Entity {
    private String fitReportId;
    private String profileId;
    private String jobId;
    private List<GapAction> actions = new ArrayList<>();
    private String note;

    @Override public String idPrefix() { return "G"; }

    private GapReport() { }

    public GapReport(FitReport fit, List<GapAction> actions, String note) {
        this.fitReportId = fit.getId(); this.profileId = fit.getProfileId(); this.jobId = fit.getJobId();
        this.actions = new ArrayList<>(actions); this.note = note;
    }

    public String getFitReportId() { return fitReportId; }
    public String getProfileId() { return profileId; }
    public String getJobId() { return jobId; }
    public List<GapAction> getActions() { return actions; }
    public String getNote() { return note; }
}
