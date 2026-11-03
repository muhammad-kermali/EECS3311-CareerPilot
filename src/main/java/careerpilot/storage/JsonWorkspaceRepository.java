package careerpilot.storage;

import careerpilot.common.CareerPilotException;
import careerpilot.common.NotFoundException;
import careerpilot.common.RevisionConflictException;
import careerpilot.domain.Entity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Stores each record as one JSON file: workspace/TypeName/ID.json.
 * Writes go to a temporary file first and are then moved into place, so a crash never leaves half a record.
 */
public class JsonWorkspaceRepository implements WorkspaceRepository {
    private final Path root;
    private final Clock clock;
    private final ObjectMapper mapper = JsonMapper.get();

    public JsonWorkspaceRepository(Path root, Clock clock) {
        this.root = root;
        this.clock = clock;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new CareerPilotException("Cannot create workspace folder " + root + ": " + e.getMessage(), e);
        }
    }

    public Path root() { return root; }

    @Override
    public synchronized <T extends Entity> T load(Class<T> type, String id) {
        return find(type, id).orElseThrow(() -> new NotFoundException(type.getSimpleName() + " '" + id + "' was not found"));
    }

    @Override
    public synchronized <T extends Entity> Optional<T> find(Class<T> type, String id) {
        if (id == null || id.isBlank() || !id.matches("[A-Za-z0-9_-]+")) return Optional.empty();
        Path file = fileFor(type, id);
        try {
            if (!Files.exists(file) || Files.size(file) == 0) return Optional.empty();
            return Optional.of(mapper.readValue(file.toFile(), type));
        } catch (IOException e) {
            throw new CareerPilotException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized <T extends Entity> T save(T entity, int expectedRevision) {
        int newRevision = checkRevision(entity, expectedRevision);
        if (entity.getId() == null) assignId(entity);
        Path tmp = writeTemp(entity, newRevision);
        commit(tmp, fileFor(entity.getClass(), entity.getId()));
        entity.markSaved(newRevision, clock.instant());
        return entity;
    }

    @Override
    public synchronized List<Entity> saveBatch(List<? extends Entity> entities) {
        List<Integer> revisions = new ArrayList<>();
        for (Entity e : entities) revisions.add(checkRevision(e, e.getRevision()));
        for (Entity e : entities) if (e.getId() == null) assignId(e);
        List<Path> temps = new ArrayList<>();
        try {
            for (int i = 0; i < entities.size(); i++) temps.add(writeTemp(entities.get(i), revisions.get(i)));
        } catch (RuntimeException ex) {
            temps.forEach(JsonWorkspaceRepository::deleteQuietly);
            throw ex;
        }
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            commit(temps.get(i), fileFor(e.getClass(), e.getId()));
            e.markSaved(revisions.get(i), clock.instant());
        }
        return new ArrayList<>(entities);
    }

    @Override
    public synchronized <T extends Entity> List<T> list(Class<T> type, Predicate<T> filter) {
        Path dir = root.resolve(type.getSimpleName());
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        List<T> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                if (Files.size(f) == 0) continue; // ID reserved by a preview that was never saved
                T item = mapper.readValue(f.toFile(), type);
                if (filter == null || filter.test(item)) out.add(item);
            }
        } catch (IOException e) {
            throw new CareerPilotException("Could not list " + type.getSimpleName() + " records: " + e.getMessage(), e);
        }
        out.sort(Comparator.comparingInt(JsonWorkspaceRepository::numberOf).thenComparing(Entity::getId));
        return out;
    }

    @Override
    public synchronized void assignId(Entity entity) {
        if (entity.getId() != null) return;
        Path dir = root.resolve(entity.getClass().getSimpleName());
        int max = 0;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : files.toList()) {
                    String name = f.getFileName().toString();
                    if (name.startsWith(entity.idPrefix()) && name.endsWith(".json")) {
                        String digits = name.substring(entity.idPrefix().length(), name.length() - 5);
                        if (digits.matches("\\d+")) max = Math.max(max, Integer.parseInt(digits));
                    }
                }
            } catch (IOException e) {
                throw new CareerPilotException("Could not allocate an ID: " + e.getMessage(), e);
            }
        }
        // Reserve the ID with an empty marker so two quick saves cannot pick the same number.
        String id = entity.idPrefix() + (max + 1);
        try {
            Files.createDirectories(dir);
            Path marker = dir.resolve(id + ".json");
            if (!Files.exists(marker)) Files.writeString(marker, "");
        } catch (IOException e) {
            throw new CareerPilotException("Could not allocate an ID: " + e.getMessage(), e);
        }
        entity.assignId(id);
    }

    private int checkRevision(Entity entity, int expectedRevision) {
        if (entity.getId() == null) return 1;
        Path file = fileFor(entity.getClass(), entity.getId());
        int actual = 0;
        try {
            if (Files.exists(file) && Files.size(file) > 0) actual = mapper.readTree(file.toFile()).path("revision").asInt(0);
        } catch (IOException e) {
            throw new CareerPilotException("Could not read " + file + ": " + e.getMessage(), e);
        }
        if (actual != expectedRevision) {
            throw new RevisionConflictException(entity.getClass().getSimpleName(), entity.getId(), expectedRevision, actual);
        }
        return actual + 1;
    }

    private Path writeTemp(Entity entity, int newRevision) {
        int oldRevision = entity.getRevision();
        try {
            Path dir = root.resolve(entity.getClass().getSimpleName());
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, entity.getId() + "-", ".tmp");
            entity.markSaved(newRevision, clock.instant());
            mapper.writeValue(tmp.toFile(), entity);
            return tmp;
        } catch (IOException e) {
            throw new CareerPilotException("Could not save " + entity.sourceKey() + ": " + e.getMessage(), e);
        } finally {
            // The in-memory revision only changes for real after commit.
            if (entity.getRevision() != oldRevision) entity.markSaved(oldRevision, clock.instant());
        }
    }

    private static void commit(Path tmp, Path target) {
        try {
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new CareerPilotException("Could not save " + target.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private Path fileFor(Class<?> type, String id) { return root.resolve(type.getSimpleName()).resolve(id + ".json"); }

    private static int numberOf(Entity e) {
        String digits = e.getId() == null ? "" : e.getId().replaceAll("\\D", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    private static void deleteQuietly(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
    }
}
