package careerpilot.export;

import careerpilot.domain.CoverLetter;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import careerpilot.domain.Resume;
import careerpilot.domain.ResumeSection;

public class MarkdownPackageBuilder extends AbstractPackageBuilder {
    @Override public String formatName() { return "markdown"; }

    @Override
    public void addResume(Resume resume) {
        StringBuilder sb = new StringBuilder("# ").append(resume.getTitle()).append("\n\n");
        for (ResumeSection s : resume.getSections()) {
            sb.append("## ").append(s.getHeading()).append("\n\n");
            for (String e : s.getEntries()) sb.append("- ").append(e).append('\n');
            sb.append('\n');
        }
        files.put("resume.md", sb.toString().trim() + "\n");
        track(resume);
    }

    @Override
    public void addLetter(CoverLetter letter) {
        String heading = "# Cover letter" + (job == null ? "" : " - " + job.getTitle()) + "\n\n";
        files.put("cover-letter.md", heading + letter.getText().trim() + "\n");
        track(letter);
    }

    @Override
    public void addPlan(PreparationPlan plan) {
        StringBuilder sb = new StringBuilder("# Preparation plan\n\n");
        if (plan.getSummary() != null && !plan.getSummary().isBlank()) sb.append(plan.getSummary()).append("\n\n");
        sb.append("| Task | Description | Minutes | Depends on |\n|---|---|---|---|\n");
        for (PreparationTask t : plan.getTasks()) {
            sb.append("| ").append(t.getId()).append(" | ").append(t.getDescription().replace("|", "/")).append(" | ")
                    .append(t.getMinutes()).append(" | ").append(String.join(", ", t.getDependsOn())).append(" |\n");
        }
        sb.append("\nTotal: ").append(plan.totalMinutes()).append(" of ").append(plan.getAvailableMinutes()).append(" minutes\n");
        files.put("plan.md", sb.toString());
        track(plan);
    }
}
