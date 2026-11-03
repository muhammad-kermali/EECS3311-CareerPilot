package careerpilot.ui.gui;

import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import careerpilot.app.Operation;
import careerpilot.domain.JobPosting;
import java.awt.Component;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

/** A combo box of saved jobs, refreshed whenever its tab is shown. */
public class JobChooser extends JComboBox<JobPosting> {
    private final GuiSupport gui;

    public JobChooser(GuiSupport gui) {
        this.gui = gui;
        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                String text = value instanceof JobPosting j ? j.label() : "(no jobs yet: add one in the Jobs tab)";
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        refresh();
    }

    public void refresh() {
        String selectedId = selectedJobId();
        removeAllItems();
        FeatureResult r = gui.now(FeatureRequest.of(Operation.LIST_JOBS));
        if (!r.isOk()) return;
        for (Object o : (List<?>) r.payload()) {
            JobPosting j = (JobPosting) o;
            addItem(j);
            if (j.getId().equals(selectedId)) setSelectedItem(j);
        }
    }

    public String selectedJobId() {
        Object o = getSelectedItem();
        return o instanceof JobPosting j ? j.getId() : null;
    }
}
