package careerpilot.common;

/** The record changed since the caller loaded it. The caller's edits are not saved and should be kept for a reload. */
public class RevisionConflictException extends CareerPilotException {
    public RevisionConflictException(String type, String id, int expected, int actual) {
        super(type + " " + id + " was changed elsewhere (you had revision " + expected
                + ", saved copy is revision " + actual + "). Reload it and apply your edits again.");
    }
}
