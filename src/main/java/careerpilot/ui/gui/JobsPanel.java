package careerpilot.ui.gui;

import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import careerpilot.app.Operation;
import careerpilot.domain.FitReport;
import careerpilot.domain.GapAction;
import careerpilot.domain.GapReport;
import careerpilot.domain.JobPosting;
import careerpilot.domain.JobPreview;
import careerpilot.domain.MatchLevel;
import careerpilot.domain.Requirement;
import careerpilot.domain.RequirementMatch;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/** F03 import/analyze a job, F04 fit analysis, F05 gaps. */
public class JobsPanel extends JPanel implements RefreshablePanel {
    private final GuiSupport gui;
    private final DefaultListModel<JobPosting> jobs = new DefaultListModel<>();
    private final JList<JobPosting> jobList = new JList<>(jobs);
    private final JTextArea details = GuiSupport.area(20, 60, false);
    private final JTextArea fit = GuiSupport.area(20, 60, false);
    private final JTextArea gaps = GuiSupport.area(20, 60, false);
    private final JTabbedPane views = new JTabbedPane();

    public JobsPanel(GuiSupport gui) {
        super(new BorderLayout());
        this.gui = gui;
        jobList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jobList.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel l = new JLabel(value.label());
            l.setOpaque(true);
            l.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            l.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return l;
        });
        jobList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        add(GuiSupport.row(
                GuiSupport.button("Add Job...", this::addJob),
                GuiSupport.button("Analyze Fit", this::analyzeFit),
                GuiSupport.button("Skill Gaps", this::gaps),
                GuiSupport.button("Refresh", this::refreshData)), BorderLayout.NORTH);
        views.addTab("Requirements", new JScrollPane(details));
        views.addTab("Fit analysis", new JScrollPane(fit));
        views.addTab("Gaps and next steps", new JScrollPane(gaps));
        JScrollPane left = new JScrollPane(jobList);
        left.setPreferredSize(new Dimension(300, 400));
        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, views), BorderLayout.CENTER);
        refreshData();
    }

    @Override
    public void refreshData() {
        String selected = jobList.getSelectedValue() == null ? null : jobList.getSelectedValue().getId();
        jobs.clear();
        FeatureResult r = gui.now(FeatureRequest.of(Operation.LIST_JOBS));
        if (!r.isOk()) return;
        for (Object o : (List<?>) r.payload()) jobs.addElement((JobPosting) o);
        for (int i = 0; i < jobs.size(); i++) if (jobs.get(i).getId().equals(selected)) jobList.setSelectedIndex(i);
        if (jobList.getSelectedIndex() < 0 && !jobs.isEmpty()) jobList.setSelectedIndex(0);
    }

    private JobPosting selected() {
        JobPosting j = jobList.getSelectedValue();
        if (j == null) GuiSupport.error(this, "Select a job first (or add one).");
        return j;
    }

    private void showSelected() {
        JobPosting j = jobList.getSelectedValue();
        if (j == null) return;
        details.setText(describe(j));
        details.setCaretPosition(0);
        FeatureResult r = gui.now(FeatureRequest.of(Operation.GET_LATEST_FIT).with("jobId", j.getId()));
        fit.setText(r.isOk() && r.payload() != null ? describeFit(r.payload(FitReport.class), j) : "No fit analysis yet. Click Analyze Fit.");
        gaps.setText("Click Skill Gaps to see next steps.");
    }

    private void addJob() {
        JTextArea text = GuiSupport.area(14, 60, true);
        JTextField company = new JTextField(20);
        JTextField url = new JTextField(20);
        JTextField deadline = new JTextField(10);
        JPanel fields = new JPanel(new GridLayout(3, 2, 4, 4));
        fields.add(new JLabel("Company (optional)")); fields.add(company);
        fields.add(new JLabel("URL (optional)")); fields.add(url);
        fields.add(new JLabel("Deadline YYYY-MM-DD (optional)")); fields.add(deadline);
        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.add(new JLabel("Paste the job posting text, or load it from a .txt file:"), BorderLayout.NORTH);
        content.add(new JScrollPane(text), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(GuiSupport.row(GuiSupport.button("Load from file...", () -> {
            JFileChooser ch = new JFileChooser(new File("."));
            if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    text.setText(Files.readString(ch.getSelectedFile().toPath()));
                } catch (Exception ex) {
                    GuiSupport.error(this, "Could not read file: " + ex.getMessage());
                }
            }
        })), BorderLayout.NORTH);
        south.add(fields, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        if (!GuiSupport.confirm(this, content, "Add job posting", "Analyze")) return;
        gui.run(this, "Analyzing job posting", FeatureRequest.of(Operation.ANALYZE_JOB).with("text", text.getText())
                .with("company", company.getText()).with("url", url.getText()).with("deadline", deadline.getText()), r -> {
            JobPreview preview = r.payload(JobPreview.class);
            if (GuiSupport.confirm(this, GuiSupport.scroll(describe(preview.job()), 20, 70), "Review requirements", "Save job")) {
                gui.run(this, "Saving job", FeatureRequest.of(Operation.SAVE_JOB).with("job", preview.job()), saved -> {
                    refreshData();
                    JobPosting j = saved.payload(JobPosting.class);
                    for (int i = 0; i < jobs.size(); i++) if (jobs.get(i).getId().equals(j.getId())) jobList.setSelectedIndex(i);
                });
            }
        });
    }

    private void analyzeFit() {
        JobPosting j = selected();
        if (j == null) return;
        gui.run(this, "Comparing your profile with " + j.getId(), FeatureRequest.of(Operation.ANALYZE_FIT).with("jobId", j.getId()), r -> {
            fit.setText(describeFit(r.payload(FitReport.class), j));
            fit.setCaretPosition(0);
            views.setSelectedIndex(1);
        });
    }

    private void gaps() {
        JobPosting j = selected();
        if (j == null) return;
        gui.run(this, "Finding gaps and next steps", FeatureRequest.of(Operation.IDENTIFY_GAPS).with("jobId", j.getId()), r -> {
            GapReport g = r.payload(GapReport.class);
            StringBuilder sb = new StringBuilder("Gap report ").append(g.getId()).append(" (from fit report ").append(g.getFitReportId()).append(")\n\n");
            if (g.getNote() != null && !g.getNote().isBlank()) sb.append(g.getNote()).append("\n\n");
            for (GapAction a : g.getActions()) {
                String req = j.requirement(a.getRequirementId()).map(Requirement::getText).orElse("");
                sb.append(a.getRequirementId()).append(": ").append(req).append("\n  ")
                        .append(a.getGapType() == GapAction.GapType.MISSING_EVIDENCE ? "Missing evidence (you may already have this): " : "Learning need: ")
                        .append(a.getAction()).append("  (~").append(a.getEstimatedHours()).append(" h)\n\n");
            }
            gaps.setText(sb.toString());
            gaps.setCaretPosition(0);
            views.setSelectedIndex(2);
        });
    }

    static String describe(JobPosting j) {
        StringBuilder sb = new StringBuilder();
        sb.append(j.getTitle()).append(j.getCompany() == null || j.getCompany().isBlank() ? "" : " at " + j.getCompany())
                .append("\nDeadline: ").append(j.getDeadline() == null ? "unknown" : j.getDeadline()).append("\n\nRequirements:\n");
        for (Requirement r : j.getRequirements()) {
            sb.append(r.getId()).append(" [").append(r.getPriority()).append(", ").append(r.getCategory()).append("] ").append(r.getText())
                    .append("\n    source: \"").append(r.getSourceSpan()).append("\"\n");
        }
        return sb.toString();
    }

    static String describeFit(FitReport f, JobPosting j) {
        StringBuilder sb = new StringBuilder("Fit report ").append(f.getId()).append(" (profile rev ").append(f.getProfileRevision())
                .append(", job rev ").append(f.getJobRevision()).append(")\n").append(f.getSummary()).append("\n\n");
        sb.append("Supported: ").append(f.count(MatchLevel.SUPPORTED)).append("   Partial: ").append(f.count(MatchLevel.PARTIAL))
                .append("   Not evidenced: ").append(f.count(MatchLevel.NOT_EVIDENCED))
                .append("\n(Not evidenced means your profile does not show it. It is not a hiring prediction.)\n\n");
        for (RequirementMatch m : f.getMatches()) {
            sb.append("[").append(m.getLevel()).append("] ").append(m.getRequirementId()).append(": ")
                    .append(j.requirement(m.getRequirementId()).map(Requirement::getText).orElse("")).append('\n');
            if (m.getExplanation() != null && !m.getExplanation().isBlank()) sb.append("    ").append(m.getExplanation()).append('\n');
            m.getEvidence().forEach(e -> sb.append("    evidence: ").append(e).append('\n'));
        }
        for (String w : f.warnings()) sb.append("\nNote: ").append(w);
        return sb.toString();
    }
}
