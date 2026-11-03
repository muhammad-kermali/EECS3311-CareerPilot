package careerpilot.export;

import careerpilot.domain.CoverLetter;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import careerpilot.domain.Resume;

public class TextPackageBuilder extends AbstractPackageBuilder {
    @Override public String formatName() { return "text"; }

    @Override
    public void addResume(Resume resume) {
        files.put("resume.txt", resume.getTitle().toUpperCase() + "\n\n" + resume.render() + "\n");
        track(resume);
    }

    @Override
    public void addLetter(CoverLetter letter) {
        files.put("cover-letter.txt", letter.getText().trim() + "\n");
        track(letter);
    }

    @Override
    public void addPlan(PreparationPlan plan) {
        StringBuilder sb = new StringBuilder("PREPARATION PLAN\n\n");
        for (PreparationTask t : plan.getTasks()) {
            sb.append(t.getId()).append(". ").append(t.getDescription()).append(" (").append(t.getMinutes()).append(" min")
                    .append(t.getDependsOn().isEmpty() ? "" : ", after " + String.join(", ", t.getDependsOn())).append(")\n");
        }
        sb.append("\nTotal: ").append(plan.totalMinutes()).append(" of ").append(plan.getAvailableMinutes()).append(" minutes\n");
        files.put("plan.txt", sb.toString());
        track(plan);
    }
}
