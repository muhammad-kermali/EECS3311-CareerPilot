package careerpilot.ui.gui;

import careerpilot.agent.AgentStep;
import careerpilot.app.FeatureRequest;
import careerpilot.app.Operation;
import careerpilot.domain.CoverLetter;
import careerpilot.domain.Document;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import careerpilot.domain.Resume;
import careerpilot.service.PreparationPreview;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

/** F11 prepare an application with the agent. The plan and drafts are previews until accepted. */
public class PlanPanel extends JPanel implements RefreshablePanel {
    private final GuiSupport gui;
    private final JobChooser job;
    private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(120, 15, 2400, 15));
    private final JTextField deadline = new JTextField(10);
    private final JTextField preferences = new JTextField(24);
    private final JLabel status = new JLabel("Choose a job and your available time, then let the agent prepare a plan.");
    private final JTextArea summary = GuiSupport.area(4, 60, false);
    private final DefaultTableModel tasks = new DefaultTableModel(new Object[]{"Task", "Description", "Minutes", "Depends on"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultListModel<String> trace = new DefaultListModel<>();
    private final DefaultListModel<String> drafts = new DefaultListModel<>();
    private final JList<String> draftList = new JList<>(drafts);
    private PreparationPreview current;

    public PlanPanel(GuiSupport gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.job = new JobChooser(gui);
        // Two rows so the Prepare button is never pushed off the edge of a normal-size window.
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(GuiSupport.row(new JLabel("Job:"), job, new JLabel("Minutes available:"), minutes, new JLabel("Deadline (YYYY-MM-DD):"), deadline));
        top.add(GuiSupport.row(new JLabel("Preferences (optional):"), preferences, GuiSupport.button("Prepare Application", this::prepare)));
        add(top, BorderLayout.NORTH);

        JPanel result = new JPanel(new BorderLayout(4, 4));
        result.add(status, BorderLayout.NORTH);
        result.add(new JScrollPane(summary), BorderLayout.CENTER);
        JTable taskTable = new JTable(tasks);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(480);

        JPanel planBox = new JPanel(new BorderLayout());
        planBox.setBorder(BorderFactory.createTitledBorder("Proposed plan"));
        planBox.add(new JScrollPane(taskTable), BorderLayout.CENTER);
        planBox.add(GuiSupport.row(GuiSupport.button("Save Plan", this::savePlan)), BorderLayout.SOUTH);

        JPanel traceBox = new JPanel(new BorderLayout());
        traceBox.setBorder(BorderFactory.createTitledBorder("Agent steps (tool calls and observations)"));
        traceBox.add(new JScrollPane(new JList<>(trace)));

        JPanel draftBox = new JPanel(new BorderLayout());
        draftBox.setBorder(BorderFactory.createTitledBorder("Unsaved drafts created by the agent"));
        draftBox.add(new JScrollPane(draftList), BorderLayout.CENTER);
        draftBox.add(GuiSupport.row(GuiSupport.button("Review and Save Selected Draft", this::saveDraft)), BorderLayout.SOUTH);

        JPanel lower = new JPanel(new GridLayout(1, 2, 6, 6));
        lower.add(traceBox);
        lower.add(draftBox);
        JPanel center = new JPanel(new GridLayout(3, 1, 6, 6));
        center.add(result);
        center.add(planBox);
        center.add(lower);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refreshData() { job.refresh(); }

    private void prepare() {
        if (job.selectedJobId() == null) { GuiSupport.error(this, "Add a job first."); return; }
        status.setText("The agent is working (at most 8 tool calls, 60 seconds)...");
        gui.run(this, "Agent is preparing your application", FeatureRequest.of(Operation.PREPARE_APPLICATION).with("jobId", job.selectedJobId())
                .with("minutes", minutes.getValue()).with("deadline", deadline.getText()).with("preferences", preferences.getText()), r -> show(r.payload(PreparationPreview.class)));
    }

    private void show(PreparationPreview p) {
        current = p;
        status.setText("Result: " + p.status() + (p.plan() == null ? "" : "   Total " + p.plan().totalMinutes() + " of " + p.constraints().availableMinutes() + " minutes"));
        StringBuilder sb = new StringBuilder();
        if (p.summary() != null) sb.append(p.summary()).append('\n');
        if (p.question() != null) sb.append("\nThe agent needs your input: ").append(p.question()).append("\nUpdate your profile or answer in Preferences, then run it again.\n");
        if (p.validation() != null) {
            p.validation().errors().forEach(e -> sb.append("Problem: ").append(e).append('\n'));
            p.validation().warnings().forEach(e -> sb.append("Note: ").append(e).append('\n'));
        }
        summary.setText(sb.toString());
        tasks.setRowCount(0);
        if (p.plan() != null) for (PreparationTask t : p.plan().getTasks()) {
            tasks.addRow(new Object[]{t.getId(), t.getDescription(), t.getMinutes(), String.join(", ", t.getDependsOn())});
        }
        trace.clear();
        for (AgentStep s : p.trace()) trace.addElement(s.toString());
        drafts.clear();
        for (Document d : p.drafts()) drafts.addElement(d instanceof Resume ? "Tailored resume (" + ((Resume) d).getChanges().size() + " changes)" : "Cover letter");
    }

    private void savePlan() {
        if (current == null || current.plan() == null) { GuiSupport.error(this, "There is no plan to save."); return; }
        PreparationPlan plan = current.plan();
        gui.run(this, "Saving plan", FeatureRequest.of(Operation.SAVE_PLAN).with("plan", plan).with("minutes", current.constraints().availableMinutes())
                .with("deadline", current.constraints().deadline() == null ? null : current.constraints().deadline().toString()),
                r -> GuiSupport.info(this, "Saved plan " + r.payload(PreparationPlan.class).getId() + "."));
    }

    private void saveDraft() {
        int i = draftList.getSelectedIndex();
        if (current == null || i < 0) { GuiSupport.error(this, "Select a draft first."); return; }
        Document d = current.drafts().get(i);
        if (d.getId() != null) { GuiSupport.info(this, "This draft was already saved as " + d.getId() + "."); return; }
        if (d instanceof CoverLetter letter) {
            new DocumentsPanel(gui).reviewLetter(letter);
        } else if (d instanceof Resume resume) {
            StringBuilder sb = new StringBuilder("Supported changes will be kept; unsupported ones are left out.\n\n");
            resume.getChanges().forEach(c -> {
                c.setAccepted(c.isSupported());
                sb.append(c.getId()).append(c.isSupported() ? " keep: " : " LEFT OUT (" + c.getUnsupportedReason() + "): ").append(c.getAfter()).append("\n\n");
            });
            if (GuiSupport.confirm(this, GuiSupport.scroll(sb.toString(), 16, 70), "Save tailored resume", "Save")) {
                gui.run(this, "Saving resume", FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", resume),
                        r -> GuiSupport.info(this, "Saved " + r.payload(Document.class).describe() + "."));
            }
        }
    }
}
