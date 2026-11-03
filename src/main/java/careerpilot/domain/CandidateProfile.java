package careerpilot.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The applicant's confirmed facts. Every AI-written claim must point back to one of these facts. */
public class CandidateProfile extends Entity {
    private String fullName;
    private String email;
    private String phone;
    private String location;
    private String summary;
    private List<String> skills = new ArrayList<>();
    private List<EducationItem> education = new ArrayList<>();
    private List<ExperienceItem> experience = new ArrayList<>();
    private String baseResumeId;

    @Override public String idPrefix() { return "P"; }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getLocation() { return location; }
    public String getSummary() { return summary; }
    public List<String> getSkills() { return skills; }
    public List<EducationItem> getEducation() { return education; }
    public List<ExperienceItem> getExperience() { return experience; }
    public String getBaseResumeId() { return baseResumeId; }

    public void setFullName(String v) { fullName = v; }
    public void setEmail(String v) { email = v; }
    public void setPhone(String v) { phone = v; }
    public void setLocation(String v) { location = v; }
    public void setSummary(String v) { summary = v; }
    public void setSkills(List<String> v) { skills = new ArrayList<>(v); }
    public void setEducation(List<EducationItem> v) { education = new ArrayList<>(v); }
    public void setExperience(List<ExperienceItem> v) { experience = new ArrayList<>(v); }
    public void setBaseResumeId(String v) { baseResumeId = v; }

    /**
     * Every fact the model may cite, keyed by a stable path:
     * summary, skill:NAME, experience:ID, experience:ID:bullet:N, education:ID.
     */
    public Map<String, String> evidenceCatalog() {
        Map<String, String> out = new LinkedHashMap<>();
        if (summary != null && !summary.isBlank()) out.put("summary", summary);
        for (String s : skills) out.put("skill:" + s, s);
        for (ExperienceItem e : experience) {
            out.put("experience:" + e.getId(), e.describe() + " (" + e.getStart() + " to " + e.getEnd() + ")");
            for (int i = 0; i < e.getBullets().size(); i++) {
                out.put("experience:" + e.getId() + ":bullet:" + i, e.getBullets().get(i));
            }
        }
        for (EducationItem d : education) out.put("education:" + d.getId(), d.describe());
        return out;
    }

    public Optional<String> resolveEvidence(String path) {
        return Optional.ofNullable(path == null ? null : evidenceCatalog().get(path));
    }
}
