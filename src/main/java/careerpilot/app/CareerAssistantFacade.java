package careerpilot.app;

import careerpilot.common.CareerPilotException;
import careerpilot.common.DateText;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.Constraints;
import careerpilot.domain.Document;
import careerpilot.domain.HasWarnings;
import careerpilot.domain.JobPosting;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.ProfilePreview;
import careerpilot.domain.Selection;
import careerpilot.service.ApplicationChanges;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facade: the single entry point used by both the GUI and the CLI. It routes each request to one service and
 * turns exceptions into a uniform {@link FeatureResult}. Business rules stay in the services.
 */
public class CareerAssistantFacade {
    private static final Logger LOG = Logger.getLogger(CareerAssistantFacade.class.getName());
    private final ServiceSet services;
    private final String modelName;

    public CareerAssistantFacade(ServiceSet services, String modelName) {
        this.services = services;
        this.modelName = modelName;
    }

    public String modelName() { return modelName; }

    public FeatureResult execute(FeatureRequest r) {
        try {
            Object payload = dispatch(r);
            List<String> warnings = payload instanceof HasWarnings hw ? new ArrayList<>(hw.warnings()) : List.of();
            return FeatureResult.ok(payload, warnings);
        } catch (CareerPilotException e) {
            return FeatureResult.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Unexpected failure in " + r.operation(), e);
            return FeatureResult.error("Unexpected error (" + e.getClass().getSimpleName() + "). Nothing was saved.");
        }
    }

    private Object dispatch(FeatureRequest r) {
        return switch (r.operation()) {
            case IMPORT_RESUME -> services.profile().importResume(Path.of(r.required("path")));
            case CONFIRM_PROFILE -> services.profile().confirmImport(r.object("preview", ProfilePreview.class));
            case GET_PROFILE -> services.profile().getProfile(r.str("profileId"));
            case UPDATE_PROFILE -> services.profile().updateProfile(r.object("profile", CandidateProfile.class));
            case ANALYZE_JOB -> services.jobs().analyzeJob(r.str("text"), r.str("company"), r.str("url"), r.str("deadline"));
            case ANALYZE_JOB_FILE -> services.jobs().analyzeJobFile(Path.of(r.required("path")), r.str("company"), r.str("url"), r.str("deadline"));
            case SAVE_JOB -> services.jobs().saveJob(r.object("job", JobPosting.class));
            case LIST_JOBS -> services.jobs().listJobs();
            case GET_JOB -> services.jobs().getJob(r.required("jobId"));
            case ANALYZE_FIT -> services.analysis().analyzeFit(profileId(r), r.required("jobId"));
            case GET_LATEST_FIT -> services.analysis().latestFit(profileId(r), r.required("jobId")).orElse(null);
            case IDENTIFY_GAPS -> services.analysis().identifyGaps(profileId(r), r.required("jobId"));
            case TAILOR_RESUME -> services.documents().tailorResume(profileId(r), r.required("jobId"));
            case DRAFT_LETTER -> services.documents().draftLetter(profileId(r), r.required("jobId"), r.str("tone"), r.str("motivation"));
            case SAVE_DRAFT -> services.documents().saveDraft(r.object("draft", Document.class));
            case LIST_DOCUMENTS -> services.documents().listDocuments(r.str("jobId"));
            case GENERATE_QUESTIONS -> services.interviews().generateQuestions(profileId(r), r.required("jobId"), r.integer("count", 8), r.str("focus"));
            case LIST_QUESTION_SETS -> services.interviews().listQuestionSets(r.str("jobId"));
            case START_INTERVIEW -> services.interviews().startInterview(r.required("questionSetId"));
            case CURRENT_QUESTION -> services.interviews().currentQuestion(services.interviews().getSession(r.required("sessionId")));
            case SUBMIT_ANSWER -> services.interviews().submitAnswer(r.required("sessionId"), r.str("answer"));
            case RETRY_FEEDBACK -> services.interviews().retryFeedback(r.required("sessionId"));
            case PAUSE_INTERVIEW -> services.interviews().pauseInterview(r.required("sessionId"));
            case GET_SESSION -> services.interviews().getSession(r.required("sessionId"));
            case UPDATE_APPLICATION -> services.applications().updateApplication(r.required("jobId"),
                    new ApplicationChanges(r.str("status"), r.str("notes"), r.str("deadline"), r.str("appliedOn")), r.optionalInt("expectedRevision"));
            case LIST_APPLICATIONS -> services.applications().listApplications(r.str("status"), r.flag("overdueOnly", false));
            case PREPARE_APPLICATION -> services.plans().prepareApplication(profileId(r), r.required("jobId"), constraints(r));
            case SAVE_PLAN -> services.plans().savePlan(r.object("plan", PreparationPlan.class), constraints(r));
            case EXPORT_PACKAGE -> services.export().exportPackage(new Selection(r.required("jobId"), r.str("resumeId"), r.str("letterId"),
                    r.str("planId"), r.flag("includeResume", true), r.flag("includeLetter", true), r.flag("includePlan", true)),
                    r.str("format"), Path.of(r.required("out")));
        };
    }

    /** Uses the given profile ID, or the applicant's current profile when none is given. */
    private String profileId(FeatureRequest r) { return services.profile().getProfile(r.str("profileId")).getId(); }

    private static Constraints constraints(FeatureRequest r) {
        return new Constraints(r.integer("minutes", 120), DateText.parseIsoDate(r.str("deadline"), "Deadline"), r.str("preferences"));
    }
}
