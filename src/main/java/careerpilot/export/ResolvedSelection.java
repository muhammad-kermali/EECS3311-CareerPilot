package careerpilot.export;

import careerpilot.domain.CoverLetter;
import careerpilot.domain.JobPosting;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.Resume;

/** The loaded, reviewed documents to package. Any of resume, letter or plan may be null. */
public record ResolvedSelection(JobPosting job, Resume resume, CoverLetter letter, PreparationPlan plan) { }
