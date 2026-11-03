package careerpilot.export;

import careerpilot.common.CareerPilotException;
import careerpilot.common.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Writes a package into a new folder. Files go to a temporary folder first, which is renamed only when complete. */
public class FileExporter {
    public Path write(ApplicationPackage pkg, Path destination) {
        Path dest = destination.toAbsolutePath().normalize();
        if (Files.exists(dest)) throw new ValidationException("Destination " + dest + " already exists; choose a new folder name");
        Path parent = dest.getParent();
        Path tmp = parent.resolve("." + dest.getFileName() + ".tmp-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            Files.createDirectories(tmp);
            for (Map.Entry<String, String> f : pkg.files().entrySet()) {
                Files.writeString(tmp.resolve(f.getKey()), f.getValue());
            }
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
            return dest;
        } catch (IOException e) {
            deleteTree(tmp);
            throw new CareerPilotException("Export failed and no files were left behind: " + e.getMessage(), e);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
