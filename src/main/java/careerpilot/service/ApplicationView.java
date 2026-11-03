package careerpilot.service;

import careerpilot.domain.ApplicationRecord;
import careerpilot.domain.JobPosting;

/** A tracked application with values computed from today's date. */
public record ApplicationView(ApplicationRecord record, JobPosting job, boolean overdue, Long daysLeft) { }
