package careerpilot.domain;

public class EducationItem {
    private String id;
    private String school;
    private String credential;
    private String start;
    private String end;

    private EducationItem() { }

    public EducationItem(String id, String school, String credential, String start, String end) {
        this.id = id; this.school = school; this.credential = credential; this.start = start; this.end = end;
    }

    public String getId() { return id; }
    public String getSchool() { return school; }
    public String getCredential() { return credential; }
    public String getStart() { return start; }
    public String getEnd() { return end; }
    public void setId(String id) { this.id = id; }
    public void setStart(String start) { this.start = start; }
    public void setEnd(String end) { this.end = end; }

    public String describe() { return credential + ", " + school; }
}
