package careerpilot;

import careerpilot.app.AppConfig;
import careerpilot.app.AppFactory;
import careerpilot.app.CareerAssistantFacade;
import careerpilot.ui.cli.CareerCLI;
import careerpilot.ui.cli.LaunchOptions;
import careerpilot.ui.cli.TaskRunner;
import careerpilot.ui.gui.CareerGUI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point. No arguments (or --gui) opens the Swing GUI. --input runs a task file (or standard input) without
 * questions. Anything else is an interactive CLI command. Launch options such as --llm gemini come first.
 */
public final class Main {
    /**
     * Held in a static field on purpose: java.util.logging only keeps weak references to loggers, so a level set on
     * a logger nobody references can be lost after garbage collection and PDFBox's font-cache messages come back.
     */
    private static final java.util.logging.Logger PDFBOX_LOG = java.util.logging.Logger.getLogger("org.apache.pdfbox");

    private Main() { }

    public static void main(String[] args) {
        PDFBOX_LOG.setLevel(java.util.logging.Level.SEVERE);
        int code = run(args);
        if (code >= 0) System.exit(code); // -1 means the GUI is open and keeps the program running
    }

    /** Runs the program and returns the exit code (the GUI returns immediately and keeps running on its own thread). */
    static int run(String[] args) {
        LaunchOptions options;
        AppConfig config;
        try {
            options = LaunchOptions.parse(args);
            config = options.applyTo(AppConfig.load(Path.of(".env")));
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("Run with --help to see the options.");
            return 2;
        }
        if (options.help()) {
            System.out.println(LaunchOptions.usage());
            return 0;
        }
        CareerAssistantFacade facade = AppFactory.create(config);
        if (options.input() != null) {
            String text;
            Path base;
            try {
                if (options.input().equals("-")) {
                    text = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                    base = Path.of(".");
                } else {
                    Path file = Path.of(options.input());
                    text = Files.readString(file);
                    base = file.toAbsolutePath().getParent();
                }
            } catch (IOException e) {
                System.out.println("Error: could not read task input " + options.input() + " (" + e.getMessage() + ")");
                return 2;
            }
            return new TaskRunner(facade, System.out, base).quietFor(options.json()).run(text, options.json());
        }
        if (options.rest().length == 0 || options.gui()) {
            CareerGUI.launch(facade, config.hasApiKey());
            return -1;
        }
        return new CareerCLI(facade, System.in, System.out).run(options.rest());
    }
}
