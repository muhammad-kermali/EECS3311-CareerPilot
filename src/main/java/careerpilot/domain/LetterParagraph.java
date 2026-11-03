package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class LetterParagraph {
    private String text;
    private List<EvidenceRef> evidence = new ArrayList<>();
    private boolean supported;
    private String unsupportedReason;

    private LetterParagraph() { }

    public LetterParagraph(String text, List<EvidenceRef> evidence) {
        this.text = text;
        this.evidence = new ArrayList<>(evidence);
    }

    public String getText() { return text; }
    public List<EvidenceRef> getEvidence() { return evidence; }
    public boolean isSupported() { return supported; }
    public String getUnsupportedReason() { return unsupportedReason; }

    public void markGrounding(boolean supported, String reason) {
        this.supported = supported;
        this.unsupportedReason = reason;
    }
}
