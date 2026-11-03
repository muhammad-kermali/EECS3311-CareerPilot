package careerpilot.service;

/** Fields the applicant wants to change. Null means "leave as is"; an empty deadline or "none" clears it. */
public record ApplicationChanges(String status, String notes, String deadline, String appliedOn) { }
