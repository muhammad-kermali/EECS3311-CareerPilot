package careerpilot.domain;

/** Points at a profile fact (by path) at a specific revision, with the quoted supporting text. */
public class EvidenceRef {
    private String path;
    private int revision;
    private String quote;

    private EvidenceRef() { }

    public EvidenceRef(String path, int revision, String quote) {
        this.path = path;
        this.revision = revision;
        this.quote = quote;
    }

    public String getPath() { return path; }
    public int getRevision() { return revision; }
    public String getQuote() { return quote; }

    @Override public String toString() { return path + (quote == null || quote.isBlank() ? "" : " (\"" + quote + "\")"); }
}
