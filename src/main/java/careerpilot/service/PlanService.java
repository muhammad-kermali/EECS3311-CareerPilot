package careerpilot.service;

import careerpilot.agent.AgentResult;
import careerpilot.agent.AgentStatus;
import careerpilot.agent.CareerAgent;
import careerpilot.agent.RunContext;
import careerpilot.common.ValidationException;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.Constraints;
import careerpilot.domain.JobPosting;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.ValidationResult;
import careerpilot.storage.WorkspaceRepository;
import java.util.ArrayList;
import java.util.List;

/** F11 prepare an application action plan with the agent; the applicant accepts it separately. */
public class PlanService {
    private final WorkspaceRepository repo;
    private final CareerAgent agent;
    private final PlanValidator validator;

    public PlanService(WorkspaceRepository repo, CareerAgent agent, PlanValidator validator) {
        this.repo = repo;
        this.agent = agent;
        this.validator = validator;
    }

    public PreparationPreview prepareApplication(String profileId, String jobId, Constraints constraints) {
        if (constraints.availableMinutes() < 15 || constraints.availableMinutes() > 2400) {
            throw new ValidationException("Available time must be between 15 and 2400 minutes");
        }
        CandidateProfile profile = repo.load(CandidateProfile.class, profileId);
        JobPosting job = repo.load(JobPosting.class, jobId);
        String goal = "Prepare my application for " + job.label() + ". Make a plan that fits my available time and reuses current saved work.";
        AgentResult result = agent.run(goal, new RunContext(profile.getId(), job.getId(), constraints));

        List<String> warnings = new ArrayList<>();
        ValidationResult validation = null;
        AgentStatus status = result.status();
        if (result.plan() != null) {
            validation = validator.validate(result.plan(), constraints);
            if (status == AgentStatus.PLAN_PROPOSED && !validation.feasible()) {
                warnings.add("The proposed plan does not pass validation: " + String.join("; ", validation.errors()));
            }
        }
        if (!result.drafts().isEmpty()) warnings.add(result.drafts().size() + " draft(s) were created but not saved. Review and save them separately.");
        return new PreparationPreview(status, result.plan(), validation, result.summary(), result.question(),
                result.drafts(), result.trace(), constraints, warnings);
    }

    /** Saves the plan only if it is valid and fits the time again at the moment of saving. */
    public PreparationPlan savePlan(PreparationPlan plan, Constraints constraints) {
        if (plan == null) throw new ValidationException("There is no plan to save");
        ValidationResult v = validator.validate(plan, constraints);
        if (!v.feasible()) throw new ValidationException("The plan cannot be saved", v.errors());
        plan.setReviewed(true);
        return repo.save(plan);
    }
}
