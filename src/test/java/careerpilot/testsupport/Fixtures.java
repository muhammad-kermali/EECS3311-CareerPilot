package careerpilot.testsupport;

import careerpilot.domain.CandidateProfile;
import careerpilot.domain.EducationItem;
import careerpilot.domain.ExperienceItem;
import careerpilot.domain.JobPosting;
import careerpilot.domain.Requirement;
import careerpilot.domain.Resume;
import careerpilot.domain.ResumeSection;
import careerpilot.storage.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Small, fictional records shared by several tests. */
public final class Fixtures {
    private Fixtures() { }

    /** 2026-11-01 12:00 Toronto time. */
    public static Clock clock() { return Clock.fixed(Instant.parse("2026-11-01T17:00:00Z"), ZoneId.of("America/Toronto")); }

    public static CandidateProfile profile() {
        CandidateProfile p = new CandidateProfile();
        p.setFullName("Jordan Lee");
        p.setEmail("jordan.lee@example.com");
        p.setSkills(List.of("Java", "SQL", "JUnit"));
        p.setExperience(List.of(new ExperienceItem("E1", "Software Developer Intern", "Maple Leaf Logistics", "2025-05", "2025-08",
                List.of("Built a Java Spring Boot REST API used by 3 internal teams", "Reduced report time by 40% with faster SQL"))));
        p.setEducation(List.of(new EducationItem("D1", "York University", "BSc Computer Science", "2022-09", "Present")));
        return p;
    }

    public static Resume baseResume() {
        return new Resume(null, "Base resume", List.of(
                new ResumeSection("Experience", List.of("Built a Java Spring Boot REST API used by 3 internal teams", "Reduced report time by 40% with faster SQL")),
                new ResumeSection("Skills", List.of("Java, SQL, JUnit"))));
    }

    public static JobPosting job(LocalDate deadline) {
        return new JobPosting("Junior Developer", "Northwind", null, deadline, "We need REST APIs in Java and SQL skills.",
                List.of(new Requirement("REQ1", "Build REST APIs with Java", "technical", "required", "REST APIs in Java"),
                        new Requirement("REQ2", "SQL", "technical", "required", "SQL skills")));
    }

    /** Saves a profile with its base resume and a job; returns {profileId, jobId}. */
    public static String[] seed(WorkspaceRepository repo) {
        CandidateProfile p = profile();
        Resume r = baseResume();
        repo.assignId(p);
        repo.assignId(r);
        r.setProfileId(p.getId());
        r.setReviewed(true);
        p.setBaseResumeId(r.getId());
        repo.saveBatch(List.of(r, p));
        JobPosting j = job(LocalDate.of(2026, 11, 30));
        j.setReviewed(true);
        repo.save(j);
        return new String[]{p.getId(), j.getId()};
    }
}
