package careerpilot.service;

import careerpilot.ai.Json;
import careerpilot.ai.LLMRequest;
import careerpilot.ai.OutputFormatException;
import careerpilot.ai.Prompts;
import careerpilot.ai.StructuredOutput;
import careerpilot.common.DateText;
import careerpilot.common.TextUtil;
import careerpilot.common.ValidationException;
import careerpilot.domain.JobPosting;
import careerpilot.domain.JobPreview;
import careerpilot.domain.Requirement;
import careerpilot.parsing.PlainTextParser;
import careerpilot.storage.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** F03 import and analyze a job description. */
public class JobService {
    private static final int MAX_CHARS = 30000;
    private static final Set<String> CATEGORIES = Set.of("technical", "experience", "education", "soft-skill", "other");
    private static final Pattern DEADLINE = Pattern.compile(
            "(?i)(?:deadline|apply by|closing date|applications close|closes on)[^0-9A-Za-z]{0,10}"
            + "(\\d{4}-\\d{2}-\\d{2}|[A-Za-z]{3,9}\\.? \\d{1,2},? \\d{4})");

    private final WorkspaceRepository repo;
    private final StructuredOutput ai;

    public JobService(WorkspaceRepository repo, StructuredOutput ai) {
        this.repo = repo;
        this.ai = ai;
    }

    public JobPreview analyzeJobFile(Path file, String company, String url, String deadline) {
        if (!java.nio.file.Files.isRegularFile(file)) {
            throw new careerpilot.common.ValidationException("Job posting file not found: " + file.toAbsolutePath()
                    + ". Paths are relative to the folder you run CareerPilot from, or to the task file.");
        }
        return analyzeJob(new PlainTextParser().extractText(file).text(), company, url, deadline);
    }

    /** Returns an unsaved, analyzed job for review. Requirements whose quote is not in the posting are rejected. */
    public JobPreview analyzeJob(String text, String company, String url, String deadline) {
        if (TextUtil.isBlank(text)) throw new ValidationException("Paste the job posting text first");
        List<String> warnings = new ArrayList<>();
        String posting = text.strip();
        if (posting.length() > MAX_CHARS) {
            posting = posting.substring(0, MAX_CHARS);
            warnings.add("The posting was longer than " + MAX_CHARS + " characters and was shortened");
        }
        final String source = posting;
        LocalDate due = DateText.parseIsoDate(deadline, "Deadline");
        if (due == null) due = detectDeadline(source);

        LLMRequest req = LLMRequest.json("job.analyze", Prompts.jobAnalysisSystem(), "<posting>\n" + source + "\n</posting>");
        JobPosting job = ai.generate(req,
                json -> parse(json, source, warnings, true),
                json -> parse(json, source, warnings, false));
        if (!TextUtil.isBlank(company)) job.setCompany(company.trim());
        job.setDeadline(due);
        if (due == null) warnings.add("No deadline was found; it was left blank");
        return new JobPreview(new JobPosting(job.getTitle(), job.getCompany(), TextUtil.isBlank(url) ? null : url.trim(),
                due, source, job.getRequirements()), warnings);
    }

    private JobPosting parse(JsonNode json, String posting, List<String> warnings, boolean strict) throws OutputFormatException {
        List<Requirement> reqs = new ArrayList<>();
        List<String> badQuotes = new ArrayList<>();
        int n = 1;
        for (JsonNode r : Json.array(json, "requirements")) {
            String text = Json.text(r, "text", true);
            String span = Json.text(r, "sourceSpan", true);
            String category = Json.text(r, "category", false).toLowerCase(Locale.ROOT);
            String priority = Json.text(r, "priority", false).toLowerCase(Locale.ROOT);
            if (!CATEGORIES.contains(category)) category = "other";
            if (!priority.equals("preferred")) priority = "required";
            if (!TextUtil.containsNormalized(posting, span)) {
                badQuotes.add("\"" + TextUtil.truncate(span, 60) + "\"");
                continue;
            }
            if (reqs.stream().anyMatch(x -> TextUtil.normalize(x.getText()).equals(TextUtil.normalize(text)))) continue;
            reqs.add(new Requirement("REQ" + n++, text, category, priority, span));
        }
        if (strict && !badQuotes.isEmpty()) {
            throw new OutputFormatException("these sourceSpan values are not exact quotes from the posting: " + String.join(", ", badQuotes));
        }
        if (!badQuotes.isEmpty()) warnings.add(badQuotes.size() + " requirement(s) were dropped because their quote was not found in the posting");
        if (reqs.isEmpty()) throw new OutputFormatException("no requirements with exact quotes were found");
        return new JobPosting(Json.text(json, "title", false), Json.text(json, "company", false), null, null, posting, reqs);
    }

    static LocalDate detectDeadline(String text) {
        Matcher m = DEADLINE.matcher(text);
        if (!m.find()) return null;
        String raw = m.group(1).replace(".", "").replace(",", "");
        for (String pattern : List.of("yyyy-MM-dd", "MMMM d yyyy", "MMM d yyyy")) {
            try {
                return LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    /** Saves the job after the applicant reviewed (and possibly corrected) the analysis. */
    public JobPosting saveJob(JobPosting job) {
        if (job.getRequirements().isEmpty()) throw new ValidationException("A job needs at least one requirement");
        if (TextUtil.isBlank(job.getTitle())) job.setTitle("Untitled role");
        job.setReviewed(true);
        return repo.save(job);
    }

    public JobPosting getJob(String id) { return repo.load(JobPosting.class, id); }

    public List<JobPosting> listJobs() { return repo.list(JobPosting.class, null); }
}
