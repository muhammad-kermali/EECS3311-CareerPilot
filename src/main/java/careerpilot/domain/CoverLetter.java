package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class CoverLetter extends Document implements HasWarnings {
    private String profileId;
    private String jobId;
    private String tone;
    private String text;
    private String generatedText;
    private List<LetterParagraph> paragraphs = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Override public String idPrefix() { return "L"; }

    private CoverLetter() { }

    public CoverLetter(String profileId, String jobId, String tone, List<LetterParagraph> paragraphs) {
        this.profileId = profileId;
        this.jobId = jobId;
        this.tone = tone;
        this.paragraphs = new ArrayList<>(paragraphs);
        StringBuilder sb = new StringBuilder();
        for (LetterParagraph p : paragraphs) sb.append(p.getText().trim()).append("\n\n");
        this.text = sb.toString().trim();
        this.generatedText = this.text;
    }

    public String getProfileId() { return profileId; }
    @Override public String getJobId() { return jobId; }
    public String getTone() { return tone; }
    public String getText() { return text; }
    public List<LetterParagraph> getParagraphs() { return paragraphs; }
    public List<String> getWarnings() { return warnings; }
    @Override public List<String> warnings() { return warnings; }
    public void setText(String text) { this.text = text; }

    /** True if the applicant changed the generated text before saving. */
    public boolean isEditedByUser() { return generatedText != null && !generatedText.equals(text); }

    public long unsupportedCount() { return paragraphs.stream().filter(p -> !p.isSupported()).count(); }

    @Override public String describe() { return "Cover letter " + getId() + " for " + jobId + " (" + tone + ")"; }
}
