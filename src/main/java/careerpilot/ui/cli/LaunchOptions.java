package careerpilot.ui.cli;

import careerpilot.app.AppConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Options that control how CareerPilot starts, read before any CLI command:
 * which LLM API to use, where data lives, and whether to run a task file instead of an interactive command.
 * Both "--name" and "-name" spellings are accepted, e.g. "-input task.txt" or "--input task.txt".
 */
public final class LaunchOptions {
    /** Ready-made settings for the supported OpenAI-compatible providers. */
    public static final Map<String, String[]> PROVIDERS = new java.util.LinkedHashMap<>(Map.of(
            "gemini", new String[]{"https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.1-flash-lite"},
            "openai", new String[]{"https://api.openai.com/v1", "gpt-4.1-mini"},
            "ollama", new String[]{"http://localhost:11434/v1", "qwen2.5:7b"}));

    private String input;
    private String provider;
    private String model;
    private String baseUrl;
    private String keyEnv;
    private String workspace;
    private boolean json;
    private boolean gui;
    private boolean help;
    private final List<String> rest = new ArrayList<>();

    private LaunchOptions() { }

    public static LaunchOptions parse(String[] args) {
        LaunchOptions o = new LaunchOptions();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String name = a.startsWith("--") ? a.substring(2) : a.startsWith("-") && a.length() > 1 ? a.substring(1) : null;
            String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
            switch (key) {
                case "input", "i" -> o.input = value(args, ++i, a);
                case "llm", "llmapi", "llm-api" -> o.provider = value(args, ++i, a).toLowerCase(Locale.ROOT);
                case "llm-model", "model" -> o.model = value(args, ++i, a);
                case "llm-base-url", "base-url" -> o.baseUrl = value(args, ++i, a);
                case "llm-key-env", "key-env" -> o.keyEnv = value(args, ++i, a);
                case "workspace" -> o.workspace = value(args, ++i, a);
                case "json" -> o.json = true;
                case "gui" -> o.gui = true;
                case "help", "h" -> {
                    if (o.rest.isEmpty()) o.help = true; else o.rest.add(a);
                }
                default -> o.rest.add(a);
            }
        }
        if (o.provider != null && !PROVIDERS.containsKey(o.provider)) {
            throw new IllegalArgumentException("Unknown LLM API '" + o.provider + "'. Use one of gemini, openai, ollama");
        }
        return o;
    }

    private static String value(String[] args, int i, String flag) {
        if (i >= args.length || args[i].isBlank()) throw new IllegalArgumentException(flag + " needs a value");
        return args[i];
    }

    /** Applies the provider preset first, then any explicit model, base URL or key settings on top. */
    public AppConfig applyTo(AppConfig config) {
        AppConfig c = config;
        if (provider != null) {
            String[] preset = PROVIDERS.get(provider);
            c = c.with("LLM_BASE_URL", preset[0]).with("LLM_MODEL", preset[1]);
            if (provider.equals("ollama") && !c.hasApiKey()) c = c.with("LLM_API_KEY", "ollama");
        }
        c = c.with("LLM_MODEL", model).with("LLM_BASE_URL", baseUrl).with("WORKSPACE_DIR", workspace);
        if (keyEnv != null) {
            String key = System.getenv(keyEnv);
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Environment variable " + keyEnv + " is not set");
            c = c.with("LLM_API_KEY", key);
        }
        return c;
    }

    public String input() { return input; }
    public boolean json() { return json; }
    public boolean gui() { return gui; }
    public boolean help() { return help; }
    public String[] rest() { return rest.toArray(new String[0]); }

    public static String usage() {
        return """
                CareerPilot standalone CLI

                Run a task file (non-interactive, the agent runs the whole task):
                  java -jar target/careerpilot.jar [options] --input task.txt
                  java -jar target/careerpilot.jar [options] --input - < task.txt      (task read from standard input)

                Run one command (interactive confirmations unless --yes):
                  java -jar target/careerpilot.jar [options] <command> [command options]   (see: help)

                Options (can also be written with one dash, e.g. -input):
                  --input FILE | -        task file to run, or - for standard input
                  --llm gemini|openai|ollama   which LLM API to use (sets base URL and default model)
                  --llm-model NAME        model name, e.g. gemini-3.1-flash-lite
                  --llm-base-url URL      any OpenAI-compatible endpoint
                  --llm-key-env NAME      read the API key from this environment variable (default: LLM_API_KEY or .env)
                  --workspace DIR         where data is stored (default: workspace)
                  --json                  task mode: print the result as JSON (for scripts and automated tests)
                  --gui                   open the GUI (also the default with no arguments)
                  --help                  this message

                Task file format (key: value lines, # for comments):
                  task: prepare-application   (or fit, gaps, tailor-resume, cover-letter, interview-questions)
                  resume: samples/resume-jordan-lee.pdf      (optional if a profile is already saved)
                  job: samples/job-northwind-junior-dev.txt   (or job-id: J1 for a saved job)
                  minutes: 180          deadline: 2026-11-30      preferences: ...   (prepare-application)
                  tone: professional    motivation: ...                              (cover-letter)
                  count: 5              focus: mixed                                 (interview-questions)
                  save: yes|no          (default yes: saves results that pass validation)
                A task file may instead list CLI commands, one per line; they run in order with --yes.
                """;
    }
}
