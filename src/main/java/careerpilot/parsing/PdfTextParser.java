package careerpilot.parsing;

import careerpilot.common.ValidationException;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/** Reads text-based PDFs with Apache PDFBox. Scanned (image-only) PDFs produce no text and are rejected upstream. */
public class PdfTextParser implements DocumentParser {
    @Override
    public SourceText extractText(Path path) {
        try (PDDocument doc = PDDocument.load(path.toFile())) {
            if (doc.isEncrypted()) throw new ValidationException("Encrypted PDFs are not supported. Save an unprotected copy and try again.");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return new SourceText(stripper.getText(doc), path.getFileName().toString(), doc.getNumberOfPages());
        } catch (InvalidPasswordException e) {
            throw new ValidationException("This PDF is password protected. Save an unprotected copy and try again.");
        } catch (IOException e) {
            throw new ValidationException("Could not read PDF " + path.getFileName() + ": " + e.getMessage());
        }
    }
}
