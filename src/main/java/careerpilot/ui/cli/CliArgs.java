package careerpilot.ui.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parses "group action --key value --flag" style arguments. */
public final class CliArgs {
    private final List<String> positional = new ArrayList<>();
    private final Map<String, String> options = new HashMap<>();

    public CliArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                String value = "true";
                if (key.contains("=")) {
                    value = key.substring(key.indexOf('=') + 1);
                    key = key.substring(0, key.indexOf('='));
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                }
                options.put(key, value);
            } else {
                positional.add(a);
            }
        }
    }

    public String positional(int i) { return i < positional.size() ? positional.get(i) : ""; }
    public String opt(String key) { return options.get(key); }
    public boolean has(String key) { return options.containsKey(key); }
    public boolean flag(String key) { return "true".equalsIgnoreCase(options.get(key)); }
}
