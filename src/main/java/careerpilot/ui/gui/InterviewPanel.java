package careerpilot.ui.gui;

import careerpilot.app.FeatureRequest;
import careerpilot.app.FeatureResult;
import careerpilot.app.Operation;
import careerpilot.domain.AnswerRecord;
import careerpilot.domain.Feedback;
import careerpilot.domain.InterviewQuestion;
import careerpilot.domain.InterviewSession;
import careerpilot.domain.QuestionSet;
import careerpilot.service.AnswerOutcome;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** F08 interview questions and F09 mock interview with feedback. */
public class InterviewPanel extends JPanel implements RefreshablePanel {
    private final GuiSupport gui;
    private final JobChooser job;
    private final JSpinner count = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
    private final JComboBox<String> focus = new JComboBox<>(new String[]{"mixed", "technical", "behavioural"});
    private final JComboBox<String> sets = new JComboBox<>();
    private final JTextArea questions = GuiSupport.area(10, 50, false);
    private final JLabel progress = new JLabel("No active session.");
    private final JTextArea currentQuestion = GuiSupport.area(3, 50, false);
    private final JTextArea answer = GuiSupport.area(6, 50, true);
    private final JTextArea feedback = GuiSupport.area(8, 50, false);
    private final JTextField resumeId = new JTextField(6);
    private String sessionId;

    public InterviewPanel(GuiSupport gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.job = new JobChooser(gui);
        job.addActionListener(e -> loadSets());
        sets.addActionListener(e -> showSet());
        JPanel top = new JPanel(new BorderLayout());
        top.add(GuiSupport.row(new JLabel("Job:"), job, new JLabel("Questions:"), count, new JLabel("Focus:"), focus,
                GuiSupport.button("Generate Questions", this::generate)), BorderLayout.NORTH);
        top.add(GuiSupport.row(new JLabel("Question set:"), sets, GuiSupport.button("Start Practice", this::start),
                new JLabel("   or resume session ID:"), resumeId, GuiSupport.button("Resume", this::resume)), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(BorderFactory.createTitledBorder("Questions in the selected set"));
        left.add(new JScrollPane(questions));

        JPanel practice = new JPanel(new BorderLayout(4, 4));
        practice.setBorder(BorderFactory.createTitledBorder("Mock interview"));
        JPanel qa = new JPanel(new GridLayout(3, 1, 4, 4));
        qa.add(labelled("Question", currentQuestion));
        qa.add(labelled("Your answer", answer));
        qa.add(labelled("Feedback (coaching, not an employer decision)", feedback));
        practice.add(progress, BorderLayout.NORTH);
        practice.add(qa, BorderLayout.CENTER);
        practice.add(GuiSupport.row(GuiSupport.button("Submit Answer", this::submit), GuiSupport.button("Pause", this::pause),
                GuiSupport.button("Retry Feedback", this::retry)), BorderLayout.SOUTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 6, 6));
        center.add(left);
        center.add(practice);
        add(center, BorderLayout.CENTER);
        loadSets();
    }

    private static JPanel labelled(String label, JTextArea area) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(label), BorderLayout.NORTH);
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        return p;
    }

    @Override
    public void refreshData() {
        job.refresh();
        loadSets();
    }

    private void loadSets() {
        sets.removeAllItems();
        if (job.selectedJobId() == null) return;
        FeatureResult r = gui.now(FeatureRequest.of(Operation.LIST_QUESTION_SETS).with("jobId", job.selectedJobId()));
        if (!r.isOk()) return;
        List<?> list = (List<?>) r.payload();
        for (Object o : list) sets.addItem(((QuestionSet) o).getId());
        if (!list.isEmpty()) sets.setSelectedIndex(list.size() - 1);
    }

    private void showSet() {
        Object id = sets.getSelectedItem();
        if (id == null) { questions.setText(""); return; }
        FeatureResult r = gui.now(FeatureRequest.of(Operation.LIST_QUESTION_SETS).with("jobId", job.selectedJobId()));
        if (!r.isOk()) return;
        for (Object o : (List<?>) r.payload()) {
            QuestionSet s = (QuestionSet) o;
            if (s.getId().equals(id)) {
                StringBuilder sb = new StringBuilder(s.getFocus() + " questions:\n\n");
                for (InterviewQuestion q : s.getQuestions()) {
                    sb.append(q.getId()).append(". ").append(q.getPrompt()).append("\n   linked to ").append(q.getRequirementIds())
                            .append("\n   a strong answer shows: ").append(String.join("; ", q.getCriteria())).append("\n\n");
                }
                questions.setText(sb.toString());
                questions.setCaretPosition(0);
            }
        }
    }

    private void generate() {
        if (job.selectedJobId() == null) { GuiSupport.error(this, "Add a job first."); return; }
        gui.run(this, "Generating questions", FeatureRequest.of(Operation.GENERATE_QUESTIONS).with("jobId", job.selectedJobId())
                .with("count", count.getValue()).with("focus", focus.getSelectedItem()), r -> {
            loadSets();
            sets.setSelectedItem(r.payload(QuestionSet.class).getId());
        });
    }

    private void start() {
        Object id = sets.getSelectedItem();
        if (id == null) { GuiSupport.error(this, "Generate or choose a question set first."); return; }
        gui.run(this, "Starting session", FeatureRequest.of(Operation.START_INTERVIEW).with("questionSetId", id), r -> {
            InterviewSession s = r.payload(InterviewSession.class);
            sessionId = s.getId();
            feedback.setText("");
            showProgress(s);
        });
    }

    private void resume() {
        String id = resumeId.getText().trim();
        if (id.isEmpty()) return;
        gui.run(this, "Loading session", FeatureRequest.of(Operation.GET_SESSION).with("sessionId", id), r -> {
            sessionId = id;
            showProgress(r.payload(InterviewSession.class));
        });
    }

    private void submit() {
        if (sessionId == null) { GuiSupport.error(this, "Start or resume a session first."); return; }
        gui.run(this, "Getting feedback", FeatureRequest.of(Operation.SUBMIT_ANSWER).with("sessionId", sessionId).with("answer", answer.getText()), r -> {
            AnswerOutcome o = r.payload(AnswerOutcome.class);
            showFeedback(o.answer());
            answer.setText("");
            showProgress(o.session());
        });
    }

    private void retry() {
        if (sessionId == null) return;
        gui.run(this, "Retrying feedback", FeatureRequest.of(Operation.RETRY_FEEDBACK).with("sessionId", sessionId), r -> {
            AnswerOutcome o = r.payload(AnswerOutcome.class);
            showFeedback(o.answer());
            showProgress(o.session());
        });
    }

    private void pause() {
        if (sessionId == null) return;
        gui.run(this, "Pausing", FeatureRequest.of(Operation.PAUSE_INTERVIEW).with("sessionId", sessionId), r -> {
            progress.setText("Session " + sessionId + " paused. Resume it later with its ID.");
            currentQuestion.setText("");
        });
    }

    private void showFeedback(AnswerRecord a) {
        Feedback f = a.getFeedback();
        if (f == null) { feedback.setText("Feedback is pending. Your answer is saved; click Retry Feedback."); return; }
        StringBuilder sb = new StringBuilder("Rating ").append(f.getRating()).append("/5. ").append(f.getSummary()).append('\n');
        f.getStrengths().forEach(s -> sb.append("+ ").append(s).append('\n'));
        f.getImprovements().forEach(s -> sb.append("- ").append(s).append('\n'));
        feedback.setText(sb.toString());
        feedback.setCaretPosition(0);
    }

    private void showProgress(InterviewSession s) {
        progress.setText("Session " + s.getId() + ": question " + Math.min(s.getCurrentIndex() + 1, s.getQuestionCount()) + " of " + s.getQuestionCount()
                + "  (" + s.getStatus() + ")");
        FeatureResult q = gui.now(FeatureRequest.of(Operation.CURRENT_QUESTION).with("sessionId", s.getId()));
        currentQuestion.setText(q.isOk() && q.payload() != null ? ((InterviewQuestion) q.payload()).getPrompt() : "Session complete. Well done!");
    }
}
