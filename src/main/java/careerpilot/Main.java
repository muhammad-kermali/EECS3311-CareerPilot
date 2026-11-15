package careerpilot;

import careerpilot.app.AppConfig;
import careerpilot.app.AppFactory;
import careerpilot.app.CareerAssistantFacade;
import careerpilot.ui.cli.CareerCLI;
import careerpilot.ui.gui.CareerGUI;
import java.nio.file.Path;

/** No arguments (or --gui) starts the Swing GUI; anything else is a CLI command. */
public final class Main {
    /**
     * Held in a static field on purpose: java.util.logging only keeps weak references to loggers, so a level set on
     * a logger nobody references can be lost after garbage collection and PDFBox's font-cache messages come back.
     */
    private static final java.util.logging.Logger PDFBOX_LOG = java.util.logging.Logger.getLogger("org.apache.pdfbox");

    private Main() { }

    public static void main(String[] args) {
        PDFBOX_LOG.setLevel(java.util.logging.Level.SEVERE);
        AppConfig config = AppConfig.load(Path.of(".env"));
        CareerAssistantFacade facade = AppFactory.create(config);
        if (args.length == 0 || (args.length == 1 && args[0].equals("--gui"))) {
            CareerGUI.launch(facade, config.hasApiKey());
        } else {
            int code = new CareerCLI(facade, System.in, System.out).run(args);
            System.exit(code);
        }
    }
}
