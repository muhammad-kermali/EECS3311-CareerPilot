package careerpilot.app;

import careerpilot.service.AnalysisService;
import careerpilot.service.ApplicationService;
import careerpilot.service.DocumentService;
import careerpilot.service.ExportService;
import careerpilot.service.InterviewService;
import careerpilot.service.JobService;
import careerpilot.service.PlanService;
import careerpilot.service.ProfileService;

/** The services the facade routes to. */
public record ServiceSet(ProfileService profile, JobService jobs, AnalysisService analysis, DocumentService documents,
                         InterviewService interviews, ApplicationService applications, PlanService plans, ExportService export) { }
