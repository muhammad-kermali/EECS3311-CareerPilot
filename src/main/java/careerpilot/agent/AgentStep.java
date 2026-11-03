package careerpilot.agent;

/** One entry in the agent trace, shown to the applicant so they can see how the plan was made. */
public record AgentStep(int number, String tool, String arguments, String outcome) {
    @Override public String toString() { return number + ". " + tool + " " + arguments + " -> " + outcome; }
}
