package careerpilot.ui.gui;

import careerpilot.app.CareerAssistantFacade;
import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/** Swing boundary. Every tab sends FeatureRequests to the same facade the CLI uses. */
public class CareerGUI extends JFrame {
    private final CareerAssistantFacade facade;
    private final JLabel statusBar = new JLabel(" ");

    public CareerGUI(CareerAssistantFacade facade, boolean aiConfigured) {
        super("CareerPilot - AI job application assistant");
        this.facade = facade;
        GuiSupport gui = new GuiSupport(facade, s -> statusBar.setText(" " + s));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Profile", new ProfilePanel(gui));
        tabs.addTab("Jobs & Fit", new JobsPanel(gui));
        tabs.addTab("Documents", new DocumentsPanel(gui));
        tabs.addTab("Interview", new InterviewPanel(gui));
        tabs.addTab("Applications", new ApplicationsPanel(gui));
        tabs.addTab("Prepare (Agent)", new PlanPanel(gui));
        tabs.addTab("Export", new ExportPanel(gui));
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() instanceof RefreshablePanel p) p.refreshData();
        });
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.setText(aiConfigured ? " Ready. AI model: " + facade.modelName()
                : " AI is not configured: copy .env.example to .env and set LLM_API_KEY. Deterministic features still work.");
        add(tabs, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1120, 760));
        pack();
        setLocationRelativeTo(null);
    }

    /** Sends a request through the facade (same path as the CLI). */
    public FeatureResult submit(FeatureRequest request) { return facade.execute(request); }

    public static void launch(CareerAssistantFacade facade, boolean aiConfigured) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
            new CareerGUI(facade, aiConfigured).setVisible(true);
        });
    }
}
