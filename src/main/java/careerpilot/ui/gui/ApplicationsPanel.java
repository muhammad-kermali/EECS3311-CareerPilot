package careerpilot.ui.gui;

import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import careerpilot.app.Operation;
import careerpilot.domain.ApplicationRecord;
import careerpilot.domain.ApplicationStatus;
import careerpilot.service.ApplicationView;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** F10 track applications, notes and deadlines. */
public class ApplicationsPanel extends JPanel implements RefreshablePanel {
    private final GuiSupport gui;
    private final JComboBox<String> filter = new JComboBox<>();
    private final JCheckBox overdueOnly = new JCheckBox("Overdue only");
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Job", "Status", "Deadline", "Days left", "Overdue", "Applied on", "Notes"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JobChooser job;
    private final JComboBox<String> status = new JComboBox<>();
    private final JTextField deadline = new JTextField(10);
    private final JTextField appliedOn = new JTextField(10);
    private final JTextField notes = new JTextField(40);
    private final List<ApplicationView> rows = new ArrayList<>();
    private Integer editingRevision;

    public ApplicationsPanel(GuiSupport gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.job = new JobChooser(gui);
        filter.addItem("all");
        for (ApplicationStatus s : ApplicationStatus.values()) {
            filter.addItem(s.name().toLowerCase());
            status.addItem(s.name().toLowerCase());
        }
        filter.addActionListener(e -> refreshData());
        overdueOnly.addActionListener(e -> refreshData());
        add(GuiSupport.row(new JLabel("Show:"), filter, overdueOnly, GuiSupport.button("Refresh", this::refreshData)), BorderLayout.NORTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) loadSelected(); });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel form = new JPanel(new BorderLayout());
        form.setBorder(BorderFactory.createTitledBorder("Add or update an application (status is always set by you)"));
        form.add(GuiSupport.row(new JLabel("Job:"), job, new JLabel("Status:"), status, new JLabel("Deadline (YYYY-MM-DD, blank = none):"), deadline), BorderLayout.NORTH);
        form.add(GuiSupport.row(new JLabel("Applied on:"), appliedOn, new JLabel("Notes:"), notes,
                GuiSupport.button("Save", this::save), GuiSupport.button("Clear form", this::clearForm)), BorderLayout.SOUTH);
        add(form, BorderLayout.SOUTH);
        refreshData();
    }

    @Override
    public void refreshData() {
        job.refresh();
        rows.clear();
        model.setRowCount(0);
        FeatureResult r = gui.now(FeatureRequest.of(Operation.LIST_APPLICATIONS).with("status", filter.getSelectedItem()).with("overdueOnly", overdueOnly.isSelected()));
        if (!r.isOk()) return;
        for (Object o : (List<?>) r.payload()) {
            ApplicationView v = (ApplicationView) o;
            rows.add(v);
            ApplicationRecord rec = v.record();
            model.addRow(new Object[]{v.job() == null ? rec.getJobId() : v.job().label(), rec.getStatus().name().toLowerCase(),
                    rec.getDeadline() == null ? "" : rec.getDeadline().toString(), v.daysLeft() == null ? "" : v.daysLeft(),
                    v.overdue() ? "OVERDUE" : "", rec.getAppliedOn() == null ? "" : rec.getAppliedOn().toString(), rec.getNotes()});
        }
    }

    private void loadSelected() {
        int i = table.getSelectedRow();
        if (i < 0 || i >= rows.size()) return;
        ApplicationRecord rec = rows.get(i).record();
        for (int k = 0; k < job.getItemCount(); k++) if (job.getItemAt(k).getId().equals(rec.getJobId())) job.setSelectedIndex(k);
        status.setSelectedItem(rec.getStatus().name().toLowerCase());
        deadline.setText(rec.getDeadline() == null ? "" : rec.getDeadline().toString());
        appliedOn.setText(rec.getAppliedOn() == null ? "" : rec.getAppliedOn().toString());
        notes.setText(rec.getNotes());
        editingRevision = rec.getRevision();
    }

    private void clearForm() {
        table.clearSelection();
        deadline.setText("");
        appliedOn.setText("");
        notes.setText("");
        editingRevision = null;
    }

    private void save() {
        if (job.selectedJobId() == null) { GuiSupport.error(this, "Add a job first."); return; }
        FeatureRequest req = FeatureRequest.of(Operation.UPDATE_APPLICATION).with("jobId", job.selectedJobId())
                .with("status", status.getSelectedItem()).with("notes", notes.getText()).with("deadline", deadline.getText())
                .with("appliedOn", appliedOn.getText()).with("expectedRevision", editingRevision);
        gui.run(this, "Saving application", req, r -> {
            clearForm();
            refreshData();
        });
    }
}
