package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class ResumeSection {
    private String heading;
    private List<String> entries = new ArrayList<>();

    private ResumeSection() { }

    public ResumeSection(String heading, List<String> entries) {
        this.heading = heading;
        this.entries = new ArrayList<>(entries == null ? List.of() : entries);
    }

    public String getHeading() { return heading; }
    public List<String> getEntries() { return entries; }

    /** Deep copy: the entry list is not shared with the original. */
    public ResumeSection copy() { return new ResumeSection(heading, new ArrayList<>(entries)); }
}
