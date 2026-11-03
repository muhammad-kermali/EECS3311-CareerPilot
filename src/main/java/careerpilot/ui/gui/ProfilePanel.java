package careerpilot.ui.gui;

import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import careerpilot.app.Operation;
import careerpilot.domain.CandidateProfile;
import careerpilot.domain.EducationItem;
import careerpilot.domain.ExperienceItem;
import careerpilot.domain.ProfilePreview;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/** F01 import resume and F02 review/correct the profile. */
public class ProfilePanel extends JPanel implements RefreshablePanel {
    private final GuiSupport gui;
    private final JTextField name = new JTextField(28);
    private final JTextField email = new JTextField(28);
    private final JTextField phone = new JTextField(16);
    private final JTextField location = new JTextField(16);
    private final JTextArea summary = GuiSupport.area(3, 50, true);
    private final JTextField skills = new JTextField(60);
    private final DefaultTableModel experience = new DefaultTableModel(new Object[]{"ID", "Title", "Organization", "Start", "End", "Bullets (separate with |)"}, 0);
    private final DefaultTableModel education = new DefaultTableModel(new Object[]{"ID", "School", "Credential", "Start", "End"}, 0);
    private final JLabel info = new JLabel("No profile loaded.");
    private CandidateProfile loaded;

    public ProfilePanel(GuiSupport gui) {
        super(new BorderLayout());
        this.gui = gui;
        add(GuiSupport.row(
                GuiSupport.button("Import Resume...", this::importResume),
                GuiSupport.button("Reload", this::refreshData),
                GuiSupport.button("Save Changes", this::save),
                info), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        addField(form, c, 0, "Full name", name);
        addField(form, c, 1, "Email", email);
        addField(form, c, 2, "Phone", phone);
        addField(form, c, 3, "Location", location);
        addField(form, c, 4, "Summary", new JScrollPane(summary));
        addField(form, c, 5, "Skills (comma separated)", skills);

        JTable expTable = new JTable(experience);
        expTable.getColumnModel().getColumn(5).setPreferredWidth(420);
        JTable eduTable = new JTable(education);
        JPanel tables = new JPanel(new GridLayout(2, 1, 4, 4));
        tables.add(titled("Experience (dates as YYYY-MM or Present)", expTable,
                GuiSupport.button("Add experience", () -> experience.addRow(new Object[]{"E" + (experience.getRowCount() + 1), "", "", "", "", ""})),
                GuiSupport.button("Remove selected", () -> removeSelected(expTable, experience))));
        tables.add(titled("Education", eduTable,
                GuiSupport.button("Add education", () -> education.addRow(new Object[]{"D" + (education.getRowCount() + 1), "", "", "", ""})),
                GuiSupport.button("Remove selected", () -> removeSelected(eduTable, education))));

        JPanel center = new JPanel(new BorderLayout());
        center.add(form, BorderLayout.NORTH);
        center.add(tables, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        refreshData();
    }

    private static void addField(JPanel form, GridBagConstraints c, int row, String label, java.awt.Component field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    private static JPanel titled(String title, JTable table, java.awt.Component... buttons) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(GuiSupport.row(buttons), BorderLayout.SOUTH);
        return p;
    }

    private static void removeSelected(JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        if (row >= 0) model.removeRow(table.convertRowIndexToModel(row));
    }

    @Override
    public void refreshData() {
        FeatureResult r = gui.now(FeatureRequest.of(Operation.GET_PROFILE));
        if (r.isOk()) show(r.payload(CandidateProfile.class));
        else info.setText("No profile yet. Click Import Resume to start.");
    }

    private void importResume() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("Resume (PDF, TXT, MD)", "pdf", "txt", "md"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        gui.run(this, "Reading resume and extracting profile",
                FeatureRequest.of(Operation.IMPORT_RESUME).with("path", chooser.getSelectedFile().getAbsolutePath()), r -> {
                    ProfilePreview p = r.payload(ProfilePreview.class);
                    StringBuilder sb = new StringBuilder();
                    CandidateProfile pr = p.profile();
                    sb.append("Name: ").append(pr.getFullName()).append("\nEmail: ").append(pr.getEmail()).append("\nPhone: ").append(pr.getPhone())
                            .append("\nLocation: ").append(pr.getLocation()).append("\n\nSkills: ").append(String.join(", ", pr.getSkills())).append("\n\nExperience:\n");
                    for (ExperienceItem e : pr.getExperience()) {
                        sb.append("  ").append(e.describe()).append(" (").append(e.getStart()).append(" to ").append(e.getEnd()).append(")\n");
                        e.getBullets().forEach(b -> sb.append("    - ").append(b).append('\n'));
                    }
                    sb.append("\nEducation:\n");
                    for (EducationItem d : pr.getEducation()) sb.append("  ").append(d.describe()).append('\n');
                    if (!p.warnings().isEmpty()) sb.append("\nPlease check:\n  ").append(String.join("\n  ", p.warnings()));
                    sb.append("\n\nYou can correct anything after saving, in this tab.");
                    if (GuiSupport.confirm(this, GuiSupport.scroll(sb.toString(), 22, 70), "Review extracted profile from " + p.sourceName(), "Save profile")) {
                        gui.run(this, "Saving profile", FeatureRequest.of(Operation.CONFIRM_PROFILE).with("preview", p), saved -> {
                            show(saved.payload(CandidateProfile.class));
                            GuiSupport.info(this, "Profile saved.");
                        });
                    }
                });
    }

    private void save() {
        if (loaded == null) { GuiSupport.error(this, "Import a resume first."); return; }
        stopEditing(this);
        loaded.setFullName(name.getText().trim());
        loaded.setEmail(email.getText().trim());
        loaded.setPhone(phone.getText().trim());
        loaded.setLocation(location.getText().trim());
        loaded.setSummary(summary.getText().trim());
        List<String> list = new ArrayList<>();
        for (String s : skills.getText().split(",")) if (!s.isBlank()) list.add(s.trim());
        loaded.setSkills(list);
        List<ExperienceItem> exp = new ArrayList<>();
        for (int i = 0; i < experience.getRowCount(); i++) {
            List<String> bullets = new ArrayList<>();
            for (String b : cell(experience, i, 5).split("\\|")) if (!b.isBlank()) bullets.add(b.trim());
            exp.add(new ExperienceItem(cell(experience, i, 0), cell(experience, i, 1), cell(experience, i, 2), cell(experience, i, 3), cell(experience, i, 4), bullets));
        }
        loaded.setExperience(exp);
        List<EducationItem> edu = new ArrayList<>();
        for (int i = 0; i < education.getRowCount(); i++) {
            edu.add(new EducationItem(cell(education, i, 0), cell(education, i, 1), cell(education, i, 2), cell(education, i, 3), cell(education, i, 4)));
        }
        loaded.setEducation(edu);
        gui.run(this, "Saving profile", FeatureRequest.of(Operation.UPDATE_PROFILE).with("profile", loaded), r -> {
            show(r.payload(CandidateProfile.class));
            GuiSupport.info(this, "Profile saved. Earlier fit reports and documents are now marked stale.");
        });
    }

    /** Tables keep the cell being edited until focus leaves; commit it before saving. */
    private static void stopEditing(java.awt.Container c) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof JTable t && t.isEditing()) t.getCellEditor().stopCellEditing();
            if (child instanceof java.awt.Container cc) stopEditing(cc);
        }
    }

    private static String cell(DefaultTableModel m, int row, int col) {
        Object v = m.getValueAt(row, col);
        return v == null ? "" : v.toString().trim();
    }

    private void show(CandidateProfile p) {
        loaded = p;
        info.setText("Profile " + p.getId() + ", revision " + p.getRevision());
        name.setText(p.getFullName());
        email.setText(p.getEmail());
        phone.setText(p.getPhone());
        location.setText(p.getLocation());
        summary.setText(p.getSummary());
        skills.setText(String.join(", ", p.getSkills()));
        experience.setRowCount(0);
        for (ExperienceItem e : p.getExperience()) {
            experience.addRow(new Object[]{e.getId(), e.getTitle(), e.getOrganization(), e.getStart(), e.getEnd(), String.join(" | ", e.getBullets())});
        }
        education.setRowCount(0);
        for (EducationItem d : p.getEducation()) education.addRow(new Object[]{d.getId(), d.getSchool(), d.getCredential(), d.getStart(), d.getEnd()});
    }
}
