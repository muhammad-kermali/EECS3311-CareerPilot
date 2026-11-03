package careerpilot.domain;

/** Prototype role: produces an independent copy that can be changed without touching the original. */
public interface ResumePrototype {
    Resume deepCopy();
}
