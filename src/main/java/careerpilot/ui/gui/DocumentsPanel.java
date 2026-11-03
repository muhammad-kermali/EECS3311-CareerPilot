package careerpilot.ui.gui;

import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import careerpilot.app.Operation;
import careerpilot.domain.CoverLetter;
import careerpilot.domain.Document;
import careerpilot.domain.Resume;
import careerpilot.domain.ResumeChange;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;

/** F06 tailored resume and F07 cover letter. Drafts are previews until the applicant saves them. */
public class DocumentsPanel extends JPanel implements RefreshablePanel {
    private final GuiSupport gui;
    private final JobChooser job;
    private final JComboBox<String> tone = new JComboBox<>(new String[]{"professional", "enthusiastic", "concise"});
    private final JTextField motivation = new JTextField(30);
    private final DefaultListModel<Document> saved = new DefaultListModel<>();
    private final JList<Document> savedList = new JList<>(saved);
    private final JTextArea preview = GuiSupport.area(20, 60, false);

    public DocumentsPanel(GuiSupport gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.job = new JobChooser(gui);
        job.addActionListener(e -> loadSaved());
        JPanel top = new JPanel(new BorderLayout());
        top.add(GuiSupport.row(new JLabel("Job:"), job, GuiSupport.button("Tailor Resume", this::tailor)), BorderLayout.NORTH);
        top.add(GuiSupport.row(new JLabel("Tone:"), tone, new JLabel("Why this role (optional, your words):"), motivation,
                GuiSupport.button("Draft Cover Letter", this::letter)), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        savedList.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel l = new JLabel(value.describe());
            l.setOpaque(true);
            l.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            return l;
        });
        savedList.addListSelectionListener(e -> {
            Document d = savedList.getSelectedValue();
            if (d instanceof Resume r) preview.setText(r.render());
            else if (d instanceof CoverLetter l) preview.setText(l.getText());
            preview.setCaretPosition(0);
        });
        JScrollPane left = new JScrollPane(savedList);
        left.setPreferredSize(new Dimension(330, 300));
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(GuiSupport.heading("Saved documents for this job"), BorderLayout.NORTH);
        leftPanel.add(left, BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);
        add(new JScrollPane(preview), BorderLayout.CENTER);
        loadSaved();
    }

    @Override
    public void refreshData() {
        job.refresh();
        loadSaved();
    }

    private void loadSaved() {
        saved.clear();
        if (job.selectedJobId() == null) return;
        FeatureResult r = gui.now(FeatureRequest.of(Operation.LIST_DOCUMENTS).with("jobId", job.selectedJobId()));
        if (r.isOk()) for (Object o : (List<?>) r.payload()) saved.addElement((Document) o);
    }

    private void tailor() {
        String jobId = job.selectedJobId();
        if (jobId == null) { GuiSupport.error(this, "Add a job first."); return; }
        gui.run(this, "Tailoring your resume", FeatureRequest.of(Operation.TAILOR_RESUME).with("jobId", jobId), r -> reviewResume(r.payload(Resume.class)));
    }

    private void reviewResume(Resume draft) {
        if (draft.getChanges().isEmpty()) { GuiSupport.info(this, "No truthful changes were suggested for this job."); return; }
        ChangeTableModel model = new ChangeTableModel(draft.getChanges());
        JTable table = new JTable(model);
        table.setRowHeight(48);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(40);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setCellRenderer(new WrapRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new WrapRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new WrapRenderer());
        JPanel content = new JPanel(new BorderLayout());
        content.add(new JLabel("Tick the changes to keep. Changes marked NOT SUPPORTED cite no valid evidence and cannot be kept."), BorderLayout.NORTH);
        content.add(GuiSupport.sized(table, 950, 380), BorderLayout.CENTER);
        if (!GuiSupport.confirm(this, content, "Review tailored resume", "Save resume")) return;
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        gui.run(this, "Saving resume", FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", draft), r -> {
            loadSaved();
            GuiSupport.info(this, "Saved " + r.payload(Document.class).describe() + ". Your base resume is unchanged.");
        });
    }

    private void letter() {
        String jobId = job.selectedJobId();
        if (jobId == null) { GuiSupport.error(this, "Add a job first."); return; }
        gui.run(this, "Drafting cover letter", FeatureRequest.of(Operation.DRAFT_LETTER).with("jobId", jobId)
                .with("tone", tone.getSelectedItem()).with("motivation", motivation.getText()), r -> reviewLetter(r.payload(CoverLetter.class)));
    }

    void reviewLetter(CoverLetter letter) {
        JTextArea text = GuiSupport.area(22, 70, true);
        text.setText(letter.getText());
        JPanel content = new JPanel(new BorderLayout(4, 4));
        String notes = letter.getWarnings().isEmpty() ? "All paragraphs cite valid profile evidence. Check the facts, edit freely, then save."
                : "Please fix before saving:\n- " + String.join("\n- ", letter.getWarnings());
        content.add(GuiSupport.scroll(notes, 4, 70), BorderLayout.NORTH);
        content.add(new JScrollPane(text), BorderLayout.CENTER);
        if (!GuiSupport.confirm(this, content, "Review cover letter", "Save letter")) return;
        letter.setText(text.getText());
        gui.run(this, "Saving letter", FeatureRequest.of(Operation.SAVE_DRAFT).with("draft", letter), r -> {
            loadSaved();
            GuiSupport.info(this, "Saved " + r.payload(Document.class).describe() + ".");
        });
    }

    /** Table of proposed changes; only supported changes can be ticked. */
    static final class ChangeTableModel extends AbstractTableModel {
        private final List<ResumeChange> changes;
        private final String[] cols = {"Keep", "ID", "Before", "After", "Why / grounding"};

        ChangeTableModel(List<ResumeChange> changes) { this.changes = changes; }

        @Override public int getRowCount() { return changes.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int r, int c) { return c == 0 && changes.get(r).isSupported(); }

        @Override
        public Object getValueAt(int r, int c) {
            ResumeChange ch = changes.get(r);
            return switch (c) {
                case 0 -> ch.isAccepted();
                case 1 -> ch.getId();
                case 2 -> ch.getBefore();
                case 3 -> ch.getAfter();
                default -> (ch.isSupported() ? "Supported. " : "NOT SUPPORTED: " + ch.getUnsupportedReason() + ". ") + ch.getReason()
                        + (ch.getEvidence().isEmpty() ? "" : " Evidence: " + ch.getEvidence());
            };
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 0) changes.get(r).setAccepted(Boolean.TRUE.equals(v));
        }
    }

    /** Renders long text on several lines inside a table cell. */
    static final class WrapRenderer extends JTextArea implements javax.swing.table.TableCellRenderer {
        WrapRenderer() { setLineWrap(true); setWrapStyleWord(true); }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            setText(v == null ? "" : v.toString());
            setBackground(sel ? t.getSelectionBackground() : t.getBackground());
            return this;
        }
    }
}
