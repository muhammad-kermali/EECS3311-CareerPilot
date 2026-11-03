package careerpilot.service;

import careerpilot.common.ValidationException;
import careerpilot.domain.CoverLetter;
import careerpilot.domain.JobPosting;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.Resume;
import careerpilot.domain.Selection;
import careerpilot.export.ApplicationPackage;
import careerpilot.export.ApplicationPackageBuilder;
import careerpilot.export.ApplicationPackageDirector;
import careerpilot.export.FileExporter;
import careerpilot.export.ResolvedSelection;
import careerpilot.storage.WorkspaceRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** F12 export a reviewed application package. Deterministic; nothing is sent anywhere. */
public class ExportService {
    private final WorkspaceRepository repo;
    private final DocumentService documents;
    private final ApplicationPackageDirector director;
    private final Map<String, ApplicationPackageBuilder> builders;
    private final FileExporter exporter;

    public ExportService(WorkspaceRepository repo, DocumentService documents, ApplicationPackageDirector director,
                         Map<String, ApplicationPackageBuilder> builders, FileExporter exporter) {
        this.repo = repo;
        this.documents = documents;
        this.director = director;
        this.builders = builders;
        this.exporter = exporter;
    }

    public ExportResult exportPackage(Selection selection, String format, Path out) {
        ApplicationPackageBuilder builder = builders.get(format == null ? "markdown" : format.toLowerCase(Locale.ROOT));
        if (builder == null) throw new ValidationException("Format must be one of " + builders.keySet());
        if (out == null) throw new ValidationException("Choose an output folder");
        JobPosting job = repo.load(JobPosting.class, selection.jobId());
        List<String> warnings = new ArrayList<>();

        Resume resume = null;
        if (selection.includeResume()) {
            resume = selection.resumeId() != null ? repo.load(Resume.class, selection.resumeId()) : documents.latestResume(job.getId()).orElse(null);
            if (resume == null) warnings.add("No reviewed tailored resume exists for this job; it was omitted");
        }
        CoverLetter letter = null;
        if (selection.includeLetter()) {
            letter = selection.letterId() != null ? repo.load(CoverLetter.class, selection.letterId()) : documents.latestLetter(job.getId()).orElse(null);
            if (letter == null) warnings.add("No reviewed cover letter exists for this job; it was omitted");
        }
        PreparationPlan plan = null;
        if (selection.includePlan()) {
            plan = selection.planId() != null ? repo.load(PreparationPlan.class, selection.planId())
                    : repo.list(PreparationPlan.class, p -> job.getId().equals(p.getJobId()) && p.isReviewed()).stream()
                    .max(Comparator.comparing(PreparationPlan::getCreatedAt)).orElse(null);
            if (plan == null) warnings.add("No saved plan exists for this job; it was omitted");
        }
        if (resume == null && letter == null && plan == null) throw new ValidationException("There is nothing reviewed to export for " + job.label());
        if (resume != null && (!resume.isReviewed() || !job.getId().equals(resume.getJobId()))) throw new ValidationException("Resume " + resume.getId() + " is not a reviewed version for this job");
        if (letter != null && (!letter.isReviewed() || !job.getId().equals(letter.getJobId()))) throw new ValidationException("Letter " + letter.getId() + " is not a reviewed letter for this job");
        if (plan != null && !plan.isReviewed()) throw new ValidationException("Plan " + plan.getId() + " has not been accepted");

        for (careerpilot.domain.Document d : java.util.Arrays.asList(resume, letter)) {
            if (d != null && isOutdated(d)) warnings.add(d.describe() + " was made from an older version of your profile or this job; consider regenerating it");
        }
        ApplicationPackage pkg = director.construct(builder, new ResolvedSelection(job, resume, letter, plan));
        Path folder = exporter.write(pkg, out);
        return new ExportResult(folder, new ArrayList<>(pkg.files().keySet()), warnings);
    }

    /** True if any profile or job the document was built from has changed since. */
    private boolean isOutdated(careerpilot.domain.Document d) {
        for (Map.Entry<String, Integer> src : d.getSourceRevisions().entrySet()) {
            String[] parts = src.getKey().split(":", 2);
            if (parts.length != 2) continue;
            Class<? extends careerpilot.domain.Entity> type = switch (parts[0]) {
                case "CandidateProfile" -> careerpilot.domain.CandidateProfile.class;
                case "JobPosting" -> JobPosting.class;
                default -> null;
            };
            if (type == null) continue;
            var current = repo.find(type, parts[1]);
            if (current.isPresent() && current.get().getRevision() != src.getValue()) return true;
        }
        return false;
    }
}
