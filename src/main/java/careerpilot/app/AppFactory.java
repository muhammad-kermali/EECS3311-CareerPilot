package careerpilot.app;

import careerpilot.agent.CareerAgent;
import careerpilot.agent.ToolRegistry;
import careerpilot.agent.tools.AnalyzeFitTool;
import careerpilot.agent.tools.DraftApplicationTool;
import careerpilot.agent.tools.GetJobTool;
import careerpilot.agent.tools.GetProfileTool;
import careerpilot.agent.tools.InspectDocumentsTool;
import careerpilot.agent.tools.ValidatePlanTool;
import careerpilot.ai.LLMClient;
import careerpilot.ai.LoggingLLMClient;
import careerpilot.ai.OpenAILLMAdapter;
import careerpilot.ai.ProviderClient;
import careerpilot.ai.RetryingLLMClient;
import careerpilot.ai.StructuredOutput;
import careerpilot.ai.UnconfiguredLLMClient;
import careerpilot.export.ApplicationPackageBuilder;
import careerpilot.export.ApplicationPackageDirector;
import careerpilot.export.FileExporter;
import careerpilot.export.MarkdownPackageBuilder;
import careerpilot.export.TextPackageBuilder;
import careerpilot.parsing.PdfResumeImporter;
import careerpilot.parsing.ResumeImporter;
import careerpilot.parsing.TxtResumeImporter;
import careerpilot.service.AnalysisService;
import careerpilot.service.ApplicationService;
import careerpilot.service.DocumentService;
import careerpilot.service.EvidenceValidator;
import careerpilot.service.ExportService;
import careerpilot.service.InterviewService;
import careerpilot.service.JobService;
import careerpilot.service.PlanService;
import careerpilot.service.PlanValidator;
import careerpilot.service.ProfileService;
import careerpilot.storage.JsonWorkspaceRepository;
import careerpilot.storage.WorkspaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Composition root: the only place that knows the concrete classes. */
public final class AppFactory {
    public static final int MAX_AGENT_CALLS = 8;
    /**
     * Default time limit for one agent run. Stage 1 planned 60 seconds; with a free-tier model the AI calls made
     * inside tools (for example a full fit analysis) and the occasional slow reply that has to be retried made
     * 60 seconds too short, so the default is 180 and it can be changed with AGENT_TIMEOUT_SECONDS.
     */
    public static final Duration AGENT_TIMEOUT = Duration.ofSeconds(180);

    private AppFactory() { }

    public static CareerAssistantFacade create(AppConfig config) {
        Clock clock = Clock.systemDefaultZone();
        LLMClient base = config.hasApiKey()
                ? new OpenAILLMAdapter(new ProviderClient(config.baseUrl(), config.apiKey(), Duration.ofSeconds(config.timeoutSeconds())), config.model())
                : new UnconfiguredLLMClient();
        // Decorators stack: each attempt is logged, and temporary provider errors are retried up to 3 times.
        LLMClient logged = new LoggingLLMClient(base, config.workspace().resolve("logs").resolve("llm-calls.log"), clock);
        LLMClient llm = new RetryingLLMClient(logged, 3, Duration.ofSeconds(2));
        return create(new JsonWorkspaceRepository(config.workspace(), clock), llm, clock, Duration.ofSeconds(config.agentTimeoutSeconds()));
    }

    /** Wires every service around the given repository and model client. Tests pass a fake client here. */
    public static CareerAssistantFacade create(WorkspaceRepository repo, LLMClient llm, Clock clock) {
        return create(repo, llm, clock, AGENT_TIMEOUT);
    }

    /** Same as above, with an explicit time limit for the agent. */
    public static CareerAssistantFacade create(WorkspaceRepository repo, LLMClient llm, Clock clock, Duration agentTimeout) {
        StructuredOutput ai = new StructuredOutput(llm);
        EvidenceValidator evidence = new EvidenceValidator();
        Map<String, ResumeImporter> importers = new LinkedHashMap<>();
        importers.put(".pdf", new PdfResumeImporter());
        importers.put(".txt", new TxtResumeImporter());
        importers.put(".md", new TxtResumeImporter());

        ProfileService profile = new ProfileService(repo, ai, importers);
        JobService jobs = new JobService(repo, ai);
        AnalysisService analysis = new AnalysisService(repo, ai, evidence);
        DocumentService documents = new DocumentService(repo, ai, evidence, analysis);
        InterviewService interviews = new InterviewService(repo, ai, clock);
        ApplicationService applications = new ApplicationService(repo, clock);
        PlanValidator planValidator = new PlanValidator(clock);

        ToolRegistry registry = new ToolRegistry()
                .register(new GetProfileTool(repo))
                .register(new GetJobTool(repo))
                .register(new AnalyzeFitTool(analysis, repo))
                .register(new InspectDocumentsTool(documents))
                .register(new DraftApplicationTool(documents))
                .register(new ValidatePlanTool(planValidator));
        CareerAgent agent = new CareerAgent(llm, registry, MAX_AGENT_CALLS, agentTimeout, clock);
        PlanService plans = new PlanService(repo, agent, planValidator);

        Map<String, ApplicationPackageBuilder> builders = new LinkedHashMap<>();
        builders.put("markdown", new MarkdownPackageBuilder());
        builders.put("text", new TextPackageBuilder());
        ExportService export = new ExportService(repo, documents, new ApplicationPackageDirector(), builders, new FileExporter());

        return new CareerAssistantFacade(new ServiceSet(profile, jobs, analysis, documents, interviews, applications, plans, export), llm.modelName());
    }
}
