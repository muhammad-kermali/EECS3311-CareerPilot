package careerpilot.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads settings from environment variables, falling back to a local .env file (which is git-ignored).
 * The API key is never printed or logged.
 */
public final class AppConfig {
    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) { this.values = values; }

    public static AppConfig load(Path envFile) {
        Map<String, String> v = new HashMap<>();
        if (envFile != null && Files.isRegularFile(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    String l = line.trim();
                    if (l.isEmpty() || l.startsWith("#") || !l.contains("=")) continue;
                    String key = l.substring(0, l.indexOf('=')).trim();
                    String value = l.substring(l.indexOf('=') + 1).trim();
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    v.put(key, value);
                }
            } catch (IOException ignored) {
                // Missing or unreadable .env just means defaults are used.
            }
        }
        for (String key : new String[]{"LLM_API_KEY", "LLM_MODEL", "LLM_BASE_URL", "LLM_TIMEOUT_SECONDS", "AGENT_TIMEOUT_SECONDS", "WORKSPACE_DIR"}) {
            String env = System.getenv(key);
            if (env != null && !env.isBlank()) v.put(key, env);
        }
        return new AppConfig(v);
    }

    public static AppConfig of(Map<String, String> values) { return new AppConfig(new HashMap<>(values)); }

    /** A copy with one setting replaced; used for command-line overrides such as --llm-model. */
    public AppConfig with(String key, String value) {
        Map<String, String> copy = new HashMap<>(values);
        if (value != null && !value.isBlank()) copy.put(key, value);
        return new AppConfig(copy);
    }

    public String apiKey() { return values.getOrDefault("LLM_API_KEY", ""); }
    public boolean hasApiKey() {
        String k = apiKey();
        return !k.isBlank() && !k.equals("YOUR_LOCAL_API_KEY_HERE");
    }
    public String model() { return values.getOrDefault("LLM_MODEL", "gpt-4.1-mini"); }
    public String baseUrl() { return values.getOrDefault("LLM_BASE_URL", "https://api.openai.com/v1"); }
    public int timeoutSeconds() {
        try {
            return Integer.parseInt(values.getOrDefault("LLM_TIMEOUT_SECONDS", "60"));
        } catch (NumberFormatException e) {
            return 60;
        }
    }
    /** Total time the agent may spend on one run, including AI calls made inside its tools. Default 180 seconds. */
    public int agentTimeoutSeconds() {
        try {
            int s = Integer.parseInt(values.getOrDefault("AGENT_TIMEOUT_SECONDS", "180"));
            return Math.max(30, Math.min(s, 600));
        } catch (NumberFormatException e) {
            return 180;
        }
    }
    public Path workspace() { return Path.of(values.getOrDefault("WORKSPACE_DIR", "workspace")); }
}
