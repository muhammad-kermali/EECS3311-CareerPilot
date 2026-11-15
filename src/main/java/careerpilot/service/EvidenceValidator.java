package careerpilot.service;

import careerpilot.ai.Json;
import careerpilot.ai.OutputFormatException;
import careerpilot.common.TextUtil;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.EvidenceRef;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic grounding checks. An evidence item is valid only if its path exists in the profile's evidence
 * catalog and its quote really appears in that fact's text. A claim is unsupported if it contains numbers that
 * none of its evidence (or the original text it replaces) contains.
 */
public class EvidenceValidator {

    /** Result of reading an evidence array: the valid items plus a description of each rejected one. */
    public record Checked(List<EvidenceRef> valid, List<String> rejected) { }

    public Checked readEvidence(JsonNode array, CandidateProfile profile) throws OutputFormatException {
        List<EvidenceRef> valid = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        if (array == null || array.isNull()) return new Checked(valid, rejected);
        if (!array.isArray()) throw new OutputFormatException("evidence must be an array");
        Map<String, String> catalog = profile.evidenceCatalog();
        for (JsonNode e : array) {
            String path = canonicalPath(Json.text(e, "path", false), catalog);
            String quote = Json.text(e, "quote", false);
            String fact = catalog.get(path);
            if (fact == null) {
                rejected.add("unknown evidence path '" + path + "' (use a path exactly as shown in square brackets, e.g. experience:E1:bullet:0)");
            } else if (!quote.isBlank() && !TextUtil.containsNormalized(fact, quote)) {
                rejected.add("quote \"" + TextUtil.truncate(quote, 60) + "\" is not in " + path);
            } else {
                valid.add(new EvidenceRef(path, profile.getRevision(), quote.isBlank() ? TextUtil.truncate(fact, 80) : quote));
            }
        }
        return new Checked(valid, rejected);
    }

    public boolean isValid(EvidenceRef ref, CandidateProfile profile) {
        Optional<String> fact = profile.resolveEvidence(ref.getPath());
        return fact.isPresent() && (TextUtil.isBlank(ref.getQuote()) || TextUtil.containsNormalized(fact.get(), ref.getQuote()));
    }

    /** Numbers in the claim that do not appear in the cited evidence or in the allowed extra text. */
    public Set<String> unsupportedNumbers(String claim, List<EvidenceRef> evidence, CandidateProfile profile, String... allowedText) {
        Set<String> numbers = TextUtil.numbersIn(claim);
        StringBuilder support = new StringBuilder();
        for (EvidenceRef e : evidence) support.append(profile.resolveEvidence(e.getPath()).orElse("")).append(' ');
        for (String a : allowedText) support.append(a == null ? "" : a).append(' ');
        Set<String> available = TextUtil.numbersIn(support.toString());
        numbers.removeAll(available);
        return numbers;
    }

    /**
     * Maps common variations of a path to the catalog's form, e.g. "profile/experience/E1/bullet/0" or
     * "experience.E1.bullets.0" become "experience:E1:bullet:0". Real-model finding: Gemini sometimes rewrites paths.
     * Returns the input unchanged when no catalog entry matches, so invented paths are still rejected.
     */
    static String canonicalPath(String raw, Map<String, String> catalog) {
        if (raw == null) return "";
        String p = raw.trim().replaceAll("^[\\[\"'`]+|[\\]\"'`]+$", "");
        if (catalog.containsKey(p)) return p;
        p = p.replaceFirst("(?i)^profile[/:.]", "");
        // Dots are separators only outside skill paths, because skill names such as Node.js contain dots.
        if (!p.toLowerCase(java.util.Locale.ROOT).startsWith("skill")) p = p.replace('.', ':');
        String[] parts = p.split("[/:]", -1);
        String head = parts[0].toLowerCase(java.util.Locale.ROOT);
        parts[0] = head.equals("skills") ? "skill" : head.equals("experiences") ? "experience" : head;
        if (parts[0].equals("skill") && parts.length > 2) {
            // skill:Node.js written as skill/Node/js is not recoverable; keep the rest joined as one name
            parts = new String[]{"skill", String.join(":", java.util.Arrays.copyOfRange(parts, 1, parts.length))};
        }
        for (int i = 1; i < parts.length; i++) if (parts[i].equalsIgnoreCase("bullets")) parts[i] = "bullet";
        String candidate = String.join(":", parts);
        if (catalog.containsKey(candidate)) return candidate;
        for (String key : catalog.keySet()) if (key.equalsIgnoreCase(candidate)) return key;
        return raw.trim();
    }

    /** Builds the catalog section of a prompt: one "[path] text" line per fact. */
    public String catalogForPrompt(CandidateProfile profile) {
        StringBuilder sb = new StringBuilder();
        profile.evidenceCatalog().forEach((k, v) -> sb.append('[').append(k).append("] ").append(v).append('\n'));
        return sb.toString();
    }
}
