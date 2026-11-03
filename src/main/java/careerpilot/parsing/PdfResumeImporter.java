package careerpilot.parsing;

public class PdfResumeImporter extends ResumeImporter {
    @Override
    protected DocumentParser createParser() { return new PdfTextParser(); }
}
