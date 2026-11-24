package careerpilot.ui.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed task file. Two formats are supported:
 * key-value tasks ("task: prepare-application", "job: posting.txt", ...) and command scripts (one CLI command per line).
 */
public final class TaskFile {
    public static final List<String> TASKS = List.of(
            "prepare-application", "fit", "gaps", "tailor-resume", "cover-letter", "interview-questions");
    public static final List<String> KEYS = List.of("task", "resume", "job", "job-id", "company", "minutes", "deadline",
            "preferences", "tone", "motivation", "count", "focus", "save", "save-drafts");
    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Za-z][A-Za-z-]*)\\s*:\\s*(.*)$");

    private final Map<String, String> values;
    private final List<List<String>> commands;

    private TaskFile(Map<String, String> values, List<List<String>> commands) {
        this.values = values;
        this.commands = commands;
    }

    public static TaskFile parse(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.replace("\r\n", "\n").split("\n")) {
            String l = raw.strip();
            if (!l.isEmpty() && !l.startsWith("#")) lines.add(l);
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("The task file is empty");
        boolean keyValue = KEY_VALUE.matcher(lines.get(0)).matches();
        if (!keyValue) {
            List<List<String>> commands = new ArrayList<>();
            for (String l : lines) commands.add(tokenize(l.startsWith("career ") ? l.substring(7) : l));
            return new TaskFile(Map.of(), commands);
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (String l : lines) {
            Matcher m = KEY_VALUE.matcher(l);
            if (!m.matches()) {
                problems.add("line is not 'key: value': " + l);
                continue;
            }
            String key = m.group(1).toLowerCase(Locale.ROOT);
            if (!KEYS.contains(key)) problems.add("unknown key '" + key + "'");
            else values.put(key, unquote(m.group(2).strip()));
        }
        String task = values.getOrDefault("task", "prepare-application").toLowerCase(Locale.ROOT);
        if (!TASKS.contains(task)) problems.add("unknown task '" + task + "'; use one of " + TASKS);
        values.put("task", task);
        if (!values.containsKey("job") && !values.containsKey("job-id")) problems.add("give either 'job: <posting file>' or 'job-id: <saved job ID>'");
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Invalid task file: " + String.join("; ", problems)
                    + ". Allowed keys: " + KEYS);
        }
        return new TaskFile(values, List.of());
    }

    /** Splits a command line on spaces, keeping "quoted text" together. */
    static List<String> tokenize(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)").matcher(line);
        while (m.find()) out.add(m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3));
        return out;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    public boolean isCommandScript() { return !commands.isEmpty(); }
    public List<List<String>> commands() { return commands; }
    public String task() { return values.get("task"); }
    public String get(String key) { return values.get(key); }
    public boolean has(String key) { return values.get(key) != null && !values.get(key).isBlank(); }

    public boolean flag(String key, boolean fallback) {
        String v = values.get(key);
        if (v == null || v.isBlank()) return fallback;
        return v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("true") || v.equals("1");
    }
}
