package careerpilot.common;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Profile dates are stored as "YYYY-MM", "YYYY", "Present" or blank. */
public final class DateText {
    private static final Pattern YYYY_MM = Pattern.compile("^(\\d{4})-(0[1-9]|1[0-2])$");
    private static final Pattern YYYY = Pattern.compile("^(\\d{4})$");
    private static final Pattern MONTH_YEAR = Pattern.compile("^([A-Za-z]{3,9})\\.?\\s+(\\d{4})$");
    private static final Pattern MM_YYYY = Pattern.compile("^(0?[1-9]|1[0-2])/(\\d{4})$");
    private static final Map<String, String> MONTHS = Map.ofEntries(
            Map.entry("jan", "01"), Map.entry("feb", "02"), Map.entry("mar", "03"), Map.entry("apr", "04"),
            Map.entry("may", "05"), Map.entry("jun", "06"), Map.entry("jul", "07"), Map.entry("aug", "08"),
            Map.entry("sep", "09"), Map.entry("oct", "10"), Map.entry("nov", "11"), Map.entry("dec", "12"));

    private DateText() { }

    public static boolean isValid(String value) {
        if (TextUtil.isBlank(value)) return true;
        String v = value.trim();
        return v.equalsIgnoreCase("present") || YYYY_MM.matcher(v).matches() || YYYY.matcher(v).matches();
    }

    /** Converts common resume formats ("May 2024", "05/2024") to the stored form; returns null if unrecognised. */
    public static String normalize(String value) {
        if (TextUtil.isBlank(value)) return "";
        String v = value.trim();
        if (isValid(v)) return v.equalsIgnoreCase("present") ? "Present" : v;
        if (v.equalsIgnoreCase("current") || v.equalsIgnoreCase("now")) return "Present";
        Matcher m = MONTH_YEAR.matcher(v);
        if (m.matches()) {
            String key = m.group(1).toLowerCase(Locale.ROOT);
            String month = MONTHS.get(key.length() >= 3 ? key.substring(0, 3) : key);
            if (month != null) return m.group(2) + "-" + month;
        }
        m = MM_YYYY.matcher(v);
        if (m.matches()) return m.group(2) + "-" + String.format("%02d", Integer.parseInt(m.group(1)));
        return null;
    }

    /** Comparable key: "Present" sorts last, blank sorts first. */
    public static String sortKey(String value) {
        if (TextUtil.isBlank(value)) return "";
        if (value.equalsIgnoreCase("present")) return "9999-99";
        return value.length() == 4 ? value + "-00" : value;
    }

    /** Parses an ISO date (YYYY-MM-DD). Blank returns null. */
    public static LocalDate parseIsoDate(String value, String fieldName) {
        if (TextUtil.isBlank(value) || value.equalsIgnoreCase("none")) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException(fieldName + " must be a date like 2026-11-20 (got '" + value + "')");
        }
    }
}
