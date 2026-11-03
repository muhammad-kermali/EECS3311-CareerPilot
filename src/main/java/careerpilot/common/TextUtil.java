package careerpilot.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small deterministic text helpers used by the validators. */
public final class TextUtil {
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?%?");

    private TextUtil() { }

    /** Lower case, unify quotes and dashes, collapse whitespace. Used for quote matching. */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC)
                .replace('\u2019', '\'').replace('\u2018', '\'')
                .replace('\u201c', '"').replace('\u201d', '"')
                .replace('\u2013', '-').replace('\u2014', '-')
                .replace('\u2022', ' ');
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** True when {@code needle} appears in {@code haystack} after normalization. Blank needles never match. */
    public static boolean containsNormalized(String haystack, String needle) {
        String n = normalize(needle);
        return !n.isEmpty() && normalize(haystack).contains(n);
    }

    /** Numbers (including percentages) that appear in the text, e.g. "30%", "2", "1.5". */
    public static Set<String> numbersIn(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group().replace(",", "").replace("%", ""));
        return out;
    }

    public static boolean isBlank(String s) { return s == null || s.isBlank(); }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
    }

    public static List<String> nonBlank(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) for (String s : in) if (!isBlank(s)) out.add(s.trim());
        return out;
    }
}
