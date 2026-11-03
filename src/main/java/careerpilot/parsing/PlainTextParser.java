package careerpilot.parsing;

import careerpilot.common.ValidationException;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlainTextParser implements DocumentParser {
    @Override
    public SourceText extractText(Path path) {
        try {
            String text;
            try {
                text = Files.readString(path, StandardCharsets.UTF_8);
            } catch (CharacterCodingException e) {
                text = Files.readString(path, StandardCharsets.ISO_8859_1);
            }
            return new SourceText(text.replace("\r\n", "\n"), path.getFileName().toString(), 1);
        } catch (IOException e) {
            throw new ValidationException("Could not read " + path.getFileName() + ": " + e.getMessage());
        }
    }
}
