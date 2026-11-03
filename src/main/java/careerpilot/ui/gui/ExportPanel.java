package careerpilot.ui.gui;

import careerpilot.app.FeatureRequest;
import careerpilot.app.Operation;
import careerpilot.service.ExportResult;
import java.awt.BorderLayout;
import java.io.File;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** F12 export a reviewed application package. */
public class ExportPanel extends JPanel implements RefreshablePanel {
    private final GuiSupport gui;
    private final JobChooser job;
    private final JComboBox<String> format = new JComboBox<>(new String[]{"markdown", "text"});
    private final JCheckBox resume = new JCheckBox("Tailored resume", true);
    private final JCheckBox letter = new JCheckBox("Cover letter", true);
    private final JCheckBox plan = new JCheckBox("Preparation plan", true);
    private final JTextField out = new JTextField(new File("exports").getAbsolutePath() + File.separator + "package", 40);
    private final JTextArea result = GuiSupport.area(12, 60, false);

    public ExportPanel(GuiSupport gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.job = new JobChooser(gui);
        JPanel top = new JPanel(new BorderLayout());
        top.add(GuiSupport.row(new JLabel("Job:"), job, new JLabel("Format:"), format), BorderLayout.NORTH);
        top.add(GuiSupport.row(new JLabel("Include (latest reviewed versions):"), resume, letter, plan), BorderLayout.CENTER);
        top.add(GuiSupport.row(new JLabel("New output folder:"), out, GuiSupport.button("Browse...", this::browse),
                GuiSupport.button("Export", this::export)), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        result.setText("Only reviewed documents are exported. The folder must not exist yet. Nothing is sent to any employer.");
        add(new JScrollPane(result), BorderLayout.CENTER);
    }

    @Override
    public void refreshData() { job.refresh(); }

    private void browse() {
        JFileChooser ch = new JFileChooser(new File("."));
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) out.setText(ch.getSelectedFile().getAbsolutePath() + File.separator + "package");
    }

    private void export() {
        if (job.selectedJobId() == null) { GuiSupport.error(this, "Add a job first."); return; }
        gui.run(this, "Exporting", FeatureRequest.of(Operation.EXPORT_PACKAGE).with("jobId", job.selectedJobId()).with("format", format.getSelectedItem())
                .with("out", out.getText()).with("includeResume", resume.isSelected()).with("includeLetter", letter.isSelected())
                .with("includePlan", plan.isSelected()), r -> {
            ExportResult e = r.payload(ExportResult.class);
            result.setText("Exported to " + e.folder() + "\nFiles: " + String.join(", ", e.files())
                    + (e.warnings().isEmpty() ? "" : "\n\nNotes:\n- " + String.join("\n- ", e.warnings())));
        });
    }
}
