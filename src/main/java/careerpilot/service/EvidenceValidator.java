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
            String path = Json.text(e, "path", false);
            String quote = Json.text(e, "quote", false);
            String fact = catalog.get(path);
            if (fact == null) {
                rejected.add("unknown evidence path '" + path + "'");
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

    /** Builds the catalog section of a prompt: one "path: text" line per fact. */
    public String catalogForPrompt(CandidateProfile profile) {
        StringBuilder sb = new StringBuilder();
        profile.evidenceCatalog().forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }
}
