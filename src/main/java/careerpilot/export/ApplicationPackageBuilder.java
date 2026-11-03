package careerpilot.export;

import careerpilot.domain.CoverLetter;
import careerpilot.domain.JobPosting;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.Resume;

/** Builder interface: the same steps produce different representations (Markdown or plain text). */
public interface ApplicationPackageBuilder {
    void reset(JobPosting job);

    void addResume(Resume resume);

    void addLetter(CoverLetter letter);

    void addPlan(PreparationPlan plan);

    ApplicationPackage build();

    String formatName();
}
