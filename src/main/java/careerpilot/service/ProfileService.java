package careerpilot.service;

import careerpilot.ai.Json;
import careerpilot.ai.LLMRequest;
import careerpilot.ai.OutputFormatException;
import careerpilot.ai.Prompts;
import careerpilot.ai.StructuredOutput;
import careerpilot.common.DateText;
import careerpilot.common.NotFoundException;
import careerpilot.common.TextUtil;
import careerpilot.common.ValidationException;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.EducationItem;
import careerpilot.domain.ExperienceItem;
import careerpilot.domain.ProfilePreview;
import careerpilot.domain.Resume;
import careerpilot.domain.ResumeSection;
import careerpilot.parsing.ResumeImporter;
import careerpilot.parsing.SourceText;
import careerpilot.storage.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** F01 import resume and F02 review/correct profile. */
public class ProfileService {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d ()\\-.]{8,}\\d)");

    private final WorkspaceRepository repo;
    private final StructuredOutput ai;
    private final Map<String, ResumeImporter> importers;

    public ProfileService(WorkspaceRepository repo, StructuredOutput ai, Map<String, ResumeImporter> importers) {
        this.repo = repo;
        this.ai = ai;
        this.importers = importers;
    }

    /** Extracts an unsaved profile preview from a resume file. Nothing is stored until {@link #updateProfile}. */
    public ProfilePreview importResume(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        ResumeImporter importer = importers.get(ext);
        if (importer == null) throw new ValidationException("Unsupported file type '" + ext + "'. Supported: " + importers.keySet());
        SourceText source = importer.importText(path);
        String text = TextUtil.truncate(source.text(), 20000);

        List<String> warnings = new ArrayList<>();
        LLMRequest req = LLMRequest.json("profile.extract", Prompts.resumeExtractionSystem(), "<resume>\n" + text + "\n</resume>");
        CandidateProfile profile = new CandidateProfile();
        Resume resume = ai.generate(req, json -> parseExtraction(json, profile, warnings));

        Matcher email = EMAIL.matcher(text);
        if (email.find()) profile.setEmail(email.group());
        Matcher phone = PHONE.matcher(text);
        if (phone.find()) profile.setPhone(phone.group().trim());

        for (String skill : profile.getSkills()) {
            if (!TextUtil.containsNormalized(text, skill)) warnings.add("Skill '" + skill + "' was not found word-for-word in the resume; check it");
        }
        for (ExperienceItem e : profile.getExperience()) {
            for (String b : e.getBullets()) {
                if (!TextUtil.containsNormalized(text, b)) warnings.add("Bullet under '" + e.describe() + "' does not match the resume text exactly: " + TextUtil.truncate(b, 70));
            }
        }
        resume.setSourceName(source.sourceName());
        resume.setTitle("Base resume (" + source.sourceName() + ")");
        return new ProfilePreview(profile, resume, source.sourceName(), warnings);
    }

    private Resume parseExtraction(JsonNode json, CandidateProfile profile, List<String> warnings) throws OutputFormatException {
        profile.setFullName(Json.text(json, "fullName", false));
        profile.setLocation(Json.text(json, "location", false));
        profile.setSummary(Json.text(json, "summary", false));
        profile.setSkills(dedupe(Json.strings(json, "skills")));

        List<EducationItem> education = new ArrayList<>();
        JsonNode edu = json.get("education");
        if (edu != null && edu.isArray()) {
            int i = 1;
            for (JsonNode e : edu) {
                education.add(new EducationItem("D" + i++, Json.text(e, "school", false), Json.text(e, "credential", false),
                        normalizeDate(Json.text(e, "start", false), warnings), normalizeDate(Json.text(e, "end", false), warnings)));
            }
        }
        List<ExperienceItem> experience = new ArrayList<>();
        JsonNode exp = json.get("experience");
        if (exp != null && exp.isArray()) {
            int i = 1;
            for (JsonNode e : exp) {
                experience.add(new ExperienceItem("E" + i++, Json.text(e, "title", false), Json.text(e, "organization", false),
                        normalizeDate(Json.text(e, "start", false), warnings), normalizeDate(Json.text(e, "end", false), warnings),
                        Json.strings(e, "bullets")));
            }
        }
        profile.setEducation(education);
        profile.setExperience(experience);

        List<ResumeSection> sections = new ArrayList<>();
        for (JsonNode s : Json.array(json, "sections")) {
            List<String> entries = Json.strings(s, "entries");
            if (!entries.isEmpty()) sections.add(new ResumeSection(Json.text(s, "heading", true), entries));
        }
        if (sections.isEmpty()) throw new OutputFormatException("sections must contain the resume's sections with their entries");
        if (profile.getSkills().isEmpty() && experience.isEmpty() && education.isEmpty()) {
            throw new OutputFormatException("no skills, experience or education were extracted");
        }
        return new Resume(null, "Base resume", sections);
    }

    private static String normalizeDate(String raw, List<String> warnings) {
        String n = DateText.normalize(raw);
        if (n == null) {
            warnings.add("Date '" + raw + "' was not recognised and was left blank; please enter it as YYYY-MM");
            return "";
        }
        return n;
    }

    private static List<String> dedupe(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (out.stream().noneMatch(o -> o.equalsIgnoreCase(s))) out.add(s);
        return out;
    }

    /** Saves an imported preview: profile and base resume are written together (saveBatch). */
    public CandidateProfile confirmImport(ProfilePreview preview) {
        CandidateProfile profile = preview.profile();
        List<String> problems = validate(profile);
        if (!problems.isEmpty()) throw new ValidationException("The profile cannot be saved yet", problems);
        Resume resume = preview.resume();
        repo.assignId(profile);
        repo.assignId(resume);
        resume.setProfileId(profile.getId());
        resume.setReviewed(true);
        profile.setBaseResumeId(resume.getId());
        repo.saveBatch(List.of(resume, profile));
        return profile;
    }

    public CandidateProfile getProfile(String id) {
        if (id != null && !id.isBlank()) return repo.load(CandidateProfile.class, id);
        return repo.list(CandidateProfile.class, null).stream()
                .max(Comparator.comparing(CandidateProfile::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow(() -> new NotFoundException("No profile yet. Import a resume first (Profile > Import Resume, or: career resume import --file resume.pdf)."));
    }

    /** F02: validates edits and saves them if nobody else changed the profile meanwhile. */
    public CandidateProfile updateProfile(CandidateProfile edited) {
        if (edited.getId() == null) throw new ValidationException("Only an existing profile can be edited; import a resume first");
        List<String> problems = validate(edited);
        if (!problems.isEmpty()) throw new ValidationException("The profile was not saved", problems);
        return repo.save(edited);
    }

    public List<String> validate(CandidateProfile p) {
        List<String> problems = new ArrayList<>();
        if (TextUtil.isBlank(p.getFullName())) problems.add("full name is required");
        if (!TextUtil.isBlank(p.getEmail()) && !EMAIL.matcher(p.getEmail().trim()).matches()) problems.add("email '" + p.getEmail() + "' is not valid");
        for (ExperienceItem e : p.getExperience()) {
            if (TextUtil.isBlank(e.getTitle()) || TextUtil.isBlank(e.getOrganization())) problems.add("every experience entry needs a title and an organization");
            checkDates(e.describe(), e.getStart(), e.getEnd(), problems);
        }
        for (EducationItem d : p.getEducation()) {
            if (TextUtil.isBlank(d.getSchool())) problems.add("every education entry needs a school");
            checkDates(d.describe(), d.getStart(), d.getEnd(), problems);
        }
        for (String s : p.getSkills()) if (TextUtil.isBlank(s)) problems.add("skills cannot be blank");
        return problems;
    }

    private static void checkDates(String label, String start, String end, List<String> problems) {
        if (!DateText.isValid(start)) problems.add(label + ": start date '" + start + "' must be YYYY-MM or YYYY");
        if (!DateText.isValid(end)) problems.add(label + ": end date '" + end + "' must be YYYY-MM, YYYY or Present");
        if (DateText.isValid(start) && DateText.isValid(end) && !TextUtil.isBlank(start) && !TextUtil.isBlank(end)
                && DateText.sortKey(end).compareTo(DateText.sortKey(start)) < 0) {
            problems.add(label + ": end date is before start date");
        }
    }
}
