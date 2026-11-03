package careerpilot.agent;

import careerpilot.common.CareerPilotException;

/** Concrete command: invokes one tool with already-validated arguments and turns failures into a ToolResult. */
public class InvokeToolCommand implements ToolCommand {
    private final AgentTool receiver;
    private final ToolArgs args;
    private final String callId;

    public InvokeToolCommand(AgentTool receiver, ToolArgs args, String callId) {
        this.receiver = receiver;
        this.args = args;
        this.callId = callId;
    }

    @Override
    public ToolResult execute() {
        try {
            ToolResult r = receiver.execute(args);
            return new ToolResult(callId, receiver.name(), r.success(), r.rejected(), r.data(), r.error());
        } catch (CareerPilotException e) {
            return ToolResult.failed(callId, receiver.name(), e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.failed(callId, receiver.name(), "tool error: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public String describe() { return receiver.name() + args.asMap().keySet(); }

    public String callId() { return callId; }
}
