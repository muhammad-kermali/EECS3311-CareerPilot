package careerpilot.app;

/** Every operation the GUI and CLI can request. Feature IDs are noted for traceability. */
public enum Operation {
    IMPORT_RESUME,        // F01 preview
    CONFIRM_PROFILE,      // F01 save
    GET_PROFILE,          // F02
    UPDATE_PROFILE,       // F02
    ANALYZE_JOB,          // F03 preview (text)
    ANALYZE_JOB_FILE,     // F03 preview (file)
    SAVE_JOB,             // F03 save
    LIST_JOBS,
    GET_JOB,
    ANALYZE_FIT,          // F04
    GET_LATEST_FIT,       // F04 view
    IDENTIFY_GAPS,        // F05
    TAILOR_RESUME,        // F06 preview
    DRAFT_LETTER,         // F07 preview
    SAVE_DRAFT,           // F06/F07 save
    LIST_DOCUMENTS,
    GENERATE_QUESTIONS,   // F08
    LIST_QUESTION_SETS,
    START_INTERVIEW,      // F09
    CURRENT_QUESTION,     // F09
    SUBMIT_ANSWER,        // F09
    RETRY_FEEDBACK,       // F09
    PAUSE_INTERVIEW,      // F09
    GET_SESSION,          // F09
    UPDATE_APPLICATION,   // F10
    LIST_APPLICATIONS,    // F10
    PREPARE_APPLICATION,  // F11 agent preview
    SAVE_PLAN,            // F11 save
    EXPORT_PACKAGE        // F12
}
