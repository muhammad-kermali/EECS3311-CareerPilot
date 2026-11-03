package careerpilot.storage;

import careerpilot.domain.Entity;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Persistence boundary. Saves use optimistic locking on the entity's revision. */
public interface WorkspaceRepository {
    <T extends Entity> T load(Class<T> type, String id);

    <T extends Entity> Optional<T> find(Class<T> type, String id);

    /** Saves the entity if its revision still equals {@code expectedRevision}; returns it with the new revision. */
    <T extends Entity> T save(T entity, int expectedRevision);

    /** Saves several records together: either every record is written or none is. */
    List<Entity> saveBatch(List<? extends Entity> entities);

    <T extends Entity> List<T> list(Class<T> type, Predicate<T> filter);

    /** Gives a new entity an ID without saving it, so other records can reference it in the same batch. */
    void assignId(Entity entity);

    default <T extends Entity> T save(T entity) { return save(entity, entity.getRevision()); }
}
