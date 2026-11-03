package careerpilot.parsing;

/** Text extracted from an input file, with where it came from. */
public record SourceText(String text, String sourceName, int pageCount) { }
