package careerpilot.parsing;

public class TxtResumeImporter extends ResumeImporter {
    @Override
    protected DocumentParser createParser() { return new PlainTextParser(); }
}
