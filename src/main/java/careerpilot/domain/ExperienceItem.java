package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class ExperienceItem {
    private String id;
    private String title;
    private String organization;
    private String start;
    private String end;
    private List<String> bullets = new ArrayList<>();

    private ExperienceItem() { }

    public ExperienceItem(String id, String title, String organization, String start, String end, List<String> bullets) {
        this.id = id; this.title = title; this.organization = organization; this.start = start; this.end = end;
        this.bullets = new ArrayList<>(bullets == null ? List.of() : bullets);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getOrganization() { return organization; }
    public String getStart() { return start; }
    public String getEnd() { return end; }
    public List<String> getBullets() { return bullets; }
    public void setId(String id) { this.id = id; }
    public void setStart(String start) { this.start = start; }
    public void setEnd(String end) { this.end = end; }

    public String describe() { return title + " at " + organization; }
}
