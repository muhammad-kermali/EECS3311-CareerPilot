package careerpilot.ui.gui;

import careerpilot.app.CareerAssistantFacade;
import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Runs facade calls off the Swing event thread and shows results, warnings and errors consistently. */
public final class GuiSupport {
    private final CareerAssistantFacade facade;
    private final Consumer<String> status;

    public GuiSupport(CareerAssistantFacade facade, Consumer<String> status) {
        this.facade = facade;
        this.status = status;
    }

    /** Sends the request on a background thread; calls onOk on the event thread if it succeeded. */
    public void run(Component parent, String busyText, FeatureRequest request, Consumer<FeatureResult> onOk) {
        Component root = parent == null ? null : SwingUtilities.getRoot(parent);
        if (root != null) root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        status.accept(busyText == null ? "Working..." : busyText + "...");
        new SwingWorker<FeatureResult, Void>() {
            @Override protected FeatureResult doInBackground() { return facade.execute(request); }

            @Override protected void done() {
                if (root != null) root.setCursor(Cursor.getDefaultCursor());
                FeatureResult r;
                try {
                    r = get();
                } catch (Exception e) {
                    r = FeatureResult.error("Unexpected error: " + e.getMessage());
                }
                if (!r.isOk()) {
                    status.accept("Error");
                    error(parent, r.error());
                    return;
                }
                status.accept("Done");
                onOk.accept(r);
                if (!r.warnings().isEmpty()) warnings(parent, r.warnings());
            }
        }.execute();
    }

    /** Synchronous call for quick deterministic reads (lists), used to fill combo boxes. */
    public FeatureResult now(FeatureRequest request) { return facade.execute(request); }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, wrap(message), "CareerPilot", JOptionPane.ERROR_MESSAGE);
    }

    public static void warnings(Component parent, List<String> warnings) {
        JOptionPane.showMessageDialog(parent, scroll(String.join("\n", warnings), 6, 60), "Please check", JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, wrap(message), "CareerPilot", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, JComponent content, String title, String okText) {
        Object[] options = {okText, "Cancel"};
        return JOptionPane.showOptionDialog(parent, content, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]) == 0;
    }

    public static JTextArea area(int rows, int cols, boolean editable) {
        JTextArea a = new JTextArea(rows, cols);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(editable);
        a.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        return a;
    }

    public static JScrollPane scroll(String text, int rows, int cols) {
        JTextArea a = area(rows, cols, false);
        a.setText(text);
        a.setCaretPosition(0);
        return new JScrollPane(a);
    }

    public static JPanel row(Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (Component c : parts) p.add(c);
        return p;
    }

    public static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 14f));
        l.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 6));
        return l;
    }

    public static JScrollPane sized(JComponent c, int w, int h) {
        JScrollPane s = new JScrollPane(c);
        s.setPreferredSize(new Dimension(w, h));
        return s;
    }

    private static Object wrap(String message) {
        return message != null && message.length() > 120 ? scroll(message, 5, 60) : message;
    }
}
