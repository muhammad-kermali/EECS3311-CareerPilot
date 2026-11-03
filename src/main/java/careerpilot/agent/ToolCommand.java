package careerpilot.agent;

/** Command role: one validated tool request, bound to its receiver and arguments, ready to execute. */
public interface ToolCommand {
    ToolResult execute();

    String describe();
}
