# EECS3311 Course Project: Stage 2 Implementation Report

CareerPilot, an AI job application assistant

Muhammad Abbas Kermali  
Fall 2026  
Repository: https://github.com/muhammad-kermali/EECS3311-CareerPilot

Demo videos: [Part 1 (F01 to F06)](https://youtu.be/Uz7fT3sWZ2U) and [Part 2 (F07 to F12)](https://youtu.be/4y0vOQqgCr8)

## 1. Project overview and implementation status

CareerPilot is a Java 17 desktop application that helps students and early-career applicants prepare job applications. A Swing GUI and a command-line interface both call one CareerAssistantFacade, which routes each request to a feature service. Records are stored locally as JSON files with IDs and revision numbers.

AI integration: The application calls an OpenAI-compatible chat model through OpenAILLMAdapter. The submitted configuration uses Google's free Gemini model gemini-3.1-flash-lite through Gemini's OpenAI-compatible endpoint; OpenAI's gpt-4.1-mini (the Stage 1 choice) works by changing two settings. The model extracts profile data, analyzes postings, compares evidence, drafts documents, writes interview questions and feedback, and drives the preparation agent. Java code handles validation, state, dates, storage and export.

Agent behaviour: In F11, CareerAgent runs a tool-use loop with six tools (get_profile, get_job, analyze_fit, inspect_documents, draft_application, validate_plan). It chooses tools based on earlier results: it reuses current fit reports, drafts only missing documents, checks its plan with validate_plan before answering, and stops to ask a question when required evidence is missing. Runs stop after 8 tool calls or 60 seconds. Tools cannot save anything; the applicant saves plans and drafts separately.

Grounding: Every AI claim about the candidate must cite profile evidence (experience:E1:bullet:0 style paths with an exact quote). EvidenceValidator checks that each path exists and the quote appears in that fact, and flags any number in a claim that the evidence does not contain. Unsupported resume changes cannot be saved, and unsupported letter paragraphs must be edited before saving. Job requirements must quote the posting exactly.

Status: All 12 Stage 1 features are implemented, work in both interfaces, and were tested end to end with the real model (gemini-3.1-flash-lite). Testing with the real model found five defects that a scripted test model had not shown; all five were fixed and retested (section 6). The project has 54 automated tests, all passing.

Limitations:

- Only text-based PDF, TXT and MD resumes are supported (no OCR, no DOCX).
- Tailoring rewrites existing resume lines; it does not reorder sections.
- Grounding checks catch invented paths, misquotes and new numbers, but they cannot prove that reworded text is fully faithful. The applicant still reviews every draft.
- One applicant profile per workspace.
- The free Gemini tier is rate limited. Temporary errors are retried automatically, but a very busy period can still make an AI feature fail; trying again a minute later works.
- Output quality depends on the model. A small free model sometimes formats answers differently from the instructions (for example evidence paths); the validators catch this and ask for one repair, and anything still invalid is flagged or dropped rather than trusted.

## 2. Feature to implementation mapping

Every feature enters through CareerGUI.submit() or CareerCLI.submit(), then CareerAssistantFacade.execute(), which dispatches on Operation and returns a FeatureResult. Paths below are under src/main/java/careerpilot/.

| ID | Feature | Stage 1 | Status | Main classes / files | Key methods | Patterns | Video |
|---|---|---|---|---|---|---|---|
| F01 | Import resume and extract profile | F01 | Implemented (modified, see 4) | service/ProfileService, parsing/ResumeImporter, PdfResumeImporter, TxtResumeImporter, PdfTextParser, PlainTextParser, ai/StructuredOutput | importResume(), importText(), createParser(), extractText(), confirmImport() | Facade, Factory Method, Adapter, Decorator | [Part 1, F01 segment](https://youtu.be/Uz7fT3sWZ2U) |
| F02 | Review and correct profile | F02 | Implemented | service/ProfileService, domain/CandidateProfile, storage/JsonWorkspaceRepository, ui/gui/ProfilePanel | getProfile(), updateProfile(), validate(), save() | Facade | [Part 1, F02 segment](https://youtu.be/Uz7fT3sWZ2U) |
| F03 | Import and analyze a job | F03 | Implemented | service/JobService, domain/JobPosting, Requirement, JobPreview | analyzeJob(), detectDeadline(), saveJob() | Facade, Adapter, Decorator | [Part 1, F03 segment](https://youtu.be/Uz7fT3sWZ2U) |
| F04 | Fit analysis with evidence | F04 | Implemented | service/AnalysisService, EvidenceValidator, domain/FitReport | analyzeFit(), readEvidence(), isStaleFor() | Facade, Adapter, Decorator | [Part 1, F04 segment](https://youtu.be/Uz7fT3sWZ2U) |
| F05 | Gaps and next steps | F05 | Implemented | service/AnalysisService, domain/GapReport, GapAction | identifyGaps(), currentFit() | Facade, Adapter, Decorator | [Part 1, F05 segment](https://youtu.be/Uz7fT3sWZ2U) |
| F06 | Tailored resume version | F06 | Implemented | service/DocumentService, domain/Resume, ResumePrototype, ResumeChange | tailorResume(), deepCopy(), saveDraft() | Facade, Prototype, Adapter, Decorator | [Part 1, F06 segment](https://youtu.be/Uz7fT3sWZ2U) |
| F07 | Cover letter draft | F07 | Implemented | service/DocumentService, domain/CoverLetter, LetterParagraph | draftLetter(), saveDraft() | Facade, Adapter, Decorator | [Part 2, F07 segment](https://youtu.be/4y0vOQqgCr8) |
| F08 | Interview questions | F08 | Implemented | service/InterviewService, domain/QuestionSet, InterviewQuestion | generateQuestions() | Facade, Adapter, Decorator | [Part 2, F08 segment](https://youtu.be/4y0vOQqgCr8) |
| F09 | Mock interview with feedback | F09 | Implemented (modified, see 4) | service/InterviewService, AnswerOutcome, domain/InterviewSession, AnswerRecord, Feedback | startInterview(), submitAnswer(), retryFeedback(), pauseInterview() | Facade, Adapter, Decorator | [Part 2, F09 segment](https://youtu.be/4y0vOQqgCr8) |
| F10 | Application tracking | F10 | Implemented | service/ApplicationService, ApplicationView, domain/ApplicationRecord, ApplicationStatus | updateApplication(), listApplications(), isOverdue() | Facade | [Part 2, F10 segment](https://youtu.be/4y0vOQqgCr8) |
| F11 | Application preparation agent | F11 | Implemented | service/PlanService, agent/CareerAgent, ToolRegistry, ToolCommand, InvokeToolCommand, AgentTool, agent/tools/*, service/PlanValidator | prepareApplication(), run(), executeTool(), execute(), validate(), savePlan() | Facade, Command, Adapter, Decorator | [Part 2, F11 segment](https://youtu.be/4y0vOQqgCr8) |
| F12 | Export application package | F12 | Implemented | service/ExportService, export/ApplicationPackageDirector, ApplicationPackageBuilder, MarkdownPackageBuilder, TextPackageBuilder, FileExporter | exportPackage(), construct(), reset(), addResume(), build(), write() | Facade, Builder | [Part 2, F12 segment](https://youtu.be/4y0vOQqgCr8) |

### How each feature works

F01 (hybrid). ProfileService.importResume() picks a ResumeImporter by file extension. importText() checks size and emptiness, calls the subclass's createParser(), and extracts text with PDFBox or plain reading. StructuredOutput.generate() sends the text to the model with a JSON schema and validates the answer (one repair attempt). Email and phone are found with regular expressions, dates are normalized to YYYY-MM, and any skill or bullet not found word for word in the resume becomes a warning. The result is an unsaved ProfilePreview; confirmImport() saves the profile and base resume together with saveBatch().

F02 (deterministic). getProfile() loads the current profile; the GUI form or profile edit flags change it. updateProfile() validates required fields, email format and date order, then JsonWorkspaceRepository.save() rejects the write if the revision changed since loading. A new revision makes older fit reports and documents stale.

F03 (hybrid). JobService.analyzeJob() limits input length, detects a deadline with detectDeadline(), and asks the model for requirements. Each requirement's sourceSpan must be an exact quote from the posting; any bad quote triggers one repair, and quotes that are still wrong are dropped with a warning. The applicant reviews the JobPreview; saveJob() stores it.

F04 (hybrid). AnalysisService.analyzeFit() sends the profile's evidence catalog and the requirements. The parser requires every requirement exactly once and checks each evidence item with EvidenceValidator.readEvidence(). Invalid evidence triggers one repair; after that, matches without valid evidence are downgraded to NOT_EVIDENCED with a warning. The FitReport records the profile and job revisions it used.

F05 (hybrid). identifyGaps() reuses the current fit report, or recomputes it if it is missing or stale (isStaleFor()). It asks for one action per non-supported requirement and checks that every gap gets exactly one action with a valid type and hour estimate.

F06 (hybrid). DocumentService.tailorResume() loads the base resume and calls deepCopy() (Prototype), so edits never touch the original. The model proposes line rewrites with evidence. Each change is marked supported only if it cites valid evidence and adds no new numbers. The applicant ticks changes to keep; saveDraft() reverts unticked changes and refuses unsupported ones.

F07 (hybrid). draftLetter() validates the tone, sends evidence, requirements and the applicant's own motivation text, and checks each paragraph's evidence and numbers. Problems become warnings. saveDraft() refuses an unedited letter that still contains unsupported paragraphs.

F08 (hybrid). InterviewService.generateQuestions() checks the count (1 to 20) and focus, then requires exactly that many distinct questions, each linked to real requirement IDs.

F09 (hybrid). startInterview() creates a session. submitAnswer() saves the answer first, then requests feedback. If the model fails, the answer stays saved with feedback pending and the session still advances; retryFeedback() fills it in later. Sessions can be paused and resumed.

F10 (deterministic). ApplicationService.updateApplication() creates or updates the one record per job, parses status and dates, rejects future applied dates, and checks the revision. listApplications() filters by status and computes overdue flags and days left from the clock.

F11 (agent). PlanService.prepareApplication() validates the time budget and starts CareerAgent.run(). Each turn the model either requests tools or gives a final JSON answer. ToolRegistry.executeTool() rejects unknown tools, bad JSON, missing arguments and IDs outside the run's scope, then binds a valid request to an InvokeToolCommand and executes it. Results are fed back as tool messages. PlanValidator checks the final plan (IDs, dependencies, cycles, minutes, deadline). The applicant saves the plan with savePlan() and any drafts separately with saveDraft().

F12 (deterministic). ExportService.exportPackage() loads the latest reviewed documents for the job, refuses unreviewed ones, and warns about outdated or missing ones. ApplicationPackageDirector.construct() calls reset(), the add methods and build() on the chosen builder. FileExporter.write() writes into a temporary folder and renames it only when complete, and refuses existing destinations.

## 3. Implemented design patterns

All seven Stage 1 patterns are implemented as designed. Updated class diagrams: docs/uml/A-application.png, B-ai-agent.png, C-domain-export-parsing.png (Mermaid sources beside them). The Stage 1 use-case and sequence diagrams still describe the implemented flows; small differences are listed in section 4.

| Pattern | Problem | Participants (role) | Files and methods | Matches Stage 1? |
|---|---|---|---|---|
| Facade | GUI and CLI need the same operations without duplicating coordination and error handling | CareerAssistantFacade (facade); services (subsystem); CareerGUI, CareerCLI (clients) | app/CareerAssistantFacade.execute(), dispatch() | Yes |
| Adapter | The provider's request/response format must not spread through the services | LLMClient (target), OpenAILLMAdapter (adapter), ProviderClient (adaptee) | ai/OpenAILLMAdapter.generate(), toProviderJson(), fromProviderJson() | Yes |
| Decorator | Model calls need logging and automatic retry of temporary errors without changing provider code | LLMClient (component), OpenAILLMAdapter (concrete component), LoggingLLMClient, RetryingLLMClient (decorators, stacked in AppFactory) | ai/LoggingLLMClient.generate() logs timing and tokens but never text; ai/RetryingLLMClient.generate() retries 429/5xx/timeouts with 2, 4, 8 second waits | Extended: second decorator added |
| Prototype | Tailoring must not change the base resume through shared lists | ResumePrototype (prototype interface), Resume (concrete prototype), DocumentService (client) | domain/Resume.deepCopy(), ResumeSection.copy() | Yes |
| Builder | The same selected documents must be assembled into Markdown or text without format conditions in the service | ApplicationPackageDirector (director), ApplicationPackageBuilder (builder), MarkdownPackageBuilder, TextPackageBuilder (concrete builders), ApplicationPackage (product) | export/ApplicationPackageDirector.construct() | Yes; reset() now receives the job (see 4) |
| Command | Each validated tool request should be a self-contained object, separate from the agent loop | ToolRegistry (invoker), ToolCommand (command), InvokeToolCommand (concrete command), AgentTool implementations (receivers), CareerAgent (client) | agent/ToolRegistry.executeTool(), InvokeToolCommand.execute() | Yes |
| Factory Method | The import workflow should not change when a file format is added | ResumeImporter (creator), PdfResumeImporter, TxtResumeImporter (concrete creators), DocumentParser (product), PdfTextParser, PlainTextParser (concrete products) | parsing/ResumeImporter.importText() calls createParser() | Yes |

## 4. Changes and rationale

| Original design | Implemented change | Reason | Impact |
|---|---|---|---|
| updateProfile() saved both imports and edits | Imports are saved by ProfileService.confirmImport(); edits by updateProfile() | Import needs saveBatch() of two new records; edits need a revision check on one existing record | F01/F02, SD01; facade operations CONFIRM_PROFILE and UPDATE_PROFILE |
| JobService.analyzeJob() returned JobPosting | Returns JobPreview (job plus warnings) | Dropped requirements and missing deadlines must be shown to the user | F03, SD02 |
| Invalid fit evidence: one repair, then error | One repair, then invalid evidence is removed and the match is downgraded to NOT_EVIDENCED with a warning | A single bad citation should not discard a whole useful report; the downgrade keeps it honest | F04 |
| InterviewSession.answers: List<String> | List<AnswerRecord> (answer, feedback, pending flag, time) | Pending feedback and retries need per-answer state | F09, class diagram E |
| ApplicationPackageBuilder.reset() | reset(JobPosting job) and a shared AbstractPackageBuilder | Headers and the manifest need the job; the abstract class removes duplicated state | F12, Builder participants unchanged |
| Agent tool names getProfile, getJob, ... | get_profile, get_job, ... | Snake case is the common convention for model function names | F11 |
| Not in Stage 1 | StructuredOutput helper | One place implements the "at most one repair" rule from SD08 for every service | All hybrid features |
| Not in Stage 1 | EvidenceValidator | Shared grounding checks for F04, F06, F07 | F04, F06, F07 |
| Not in Stage 1 | AppFactory (composition root), AppConfig (.env loading), UnconfiguredLLMClient | Wire concrete classes in one place; keep keys out of code; give a clear message when no key is set | Startup only |
| Not in Stage 1 | Export warns when a document was made from an older profile or job | Stale documents should not be exported silently | F12 |
| PDF/TXT resumes | PDF/TXT/MD resumes | Markdown is plain text and needs no new parser class | F01 |
| GPT-4.1 mini via OpenAI | Gemini gemini-3.1-flash-lite (free tier) through Gemini's OpenAI-compatible endpoint; any OpenAI-compatible provider via LLM_BASE_URL and LLM_MODEL | Avoids API cost; the assignment allows free models; no code depended on the provider thanks to the Adapter | Configuration, README, .env.example |
| Tool calls carried only ID, name and arguments | ToolCall also carries opaque provider data, which the adapter sends back unchanged | Gemini returns a thought_signature with tool calls and expects it back when the conversation continues; dropping it would break the agent | F11, ai/ToolCall, OpenAILLMAdapter |
| Only LoggingLLMClient decorated the model client | Added RetryingLLMClient, stacked around the logging decorator | The free tier can answer with temporary 429/503 errors; retrying with growing waits makes features more reliable | All AI features |
| Resume sections came only from the model | ProfileService checks that every experience bullet appears in the returned sections; if not, it asks for one repair, then rebuilds Summary, Education, Experience and Skills from the extracted profile | The real model once returned only a Projects section, which left tailoring almost nothing to work with | F01, F06 |
| Evidence paths had to match exactly | EvidenceValidator.canonicalPath() maps common variations (for example profile/experience/E1/bullet/0) to the catalog form; prompts show paths as [path] text; bad letter evidence gets one repair before anything is flagged | The real model wrote paths in its own format, which blocked a truthful cover letter; invented paths are still rejected | F04, F06, F07 |
| Letter numbers had to appear in the cited items | A number in a letter paragraph is supported if it appears anywhere in the confirmed profile, the posting, or the applicant's own motivation | A paragraph can combine several facts; requiring the exact cited item rejected true statements | F07 |
| Agent tab controls on one row | Controls split into two rows | The Prepare Application button was cut off at normal window sizes (found while recording) | F11 GUI |

## 5. AI tools and collaboration log

| Tool / platform | Model | Used for | Tokens and cost |
|---|---|---|---|
| Claude (claude.ai app, agentic coding session) | Configured as claude-opus-5-5 (the app notes the model serving a turn can differ) | Generated the initial implementation of all packages, the tests, sample data, README and report drafts; diagnosed and fixed the defects I found when testing with the real model; wrote the demo video script | Tokens: not shown per conversation on the Claude Pro plan. Cost: Claude Pro subscription, $35/month as charged, no per-use charge |
| Application runtime model | gemini-3.1-flash-lite via Gemini API free tier | Used by CareerPilot itself, not a development tool | Free tier ($0); per-call token counts are logged in workspace/logs/llm-calls.log |

I did not record using any other AI development tool for Stage 2.

Representative interactions (keep the log in docs/ai-collaboration-log.md up to date and summarize it here):

| Date | Task | Input / Instruction | AI Contribution | Human Contribution | Outcome |
|---|---|---|---|---|---|
| 2026-11-01 | Implement the Stage 1 design | "Implement the Stage 1 design" with the exact Stage 1 names | Full first implementation (132 classes), 43 tests, GUI, CLI, sample data, docs | Built it locally, set up the git-ignored .env | Committed in 13 commits by area |
| 2026-11-02 | Avoid API cost | Asked for a free way to run the LLM | Gemini free tier or local Ollama | Chose the Gemini free tier and configured it | Runs on gemini-3.1-flash-lite with no code change (Adapter) |
| 2026-11-14 | First real-model test | Pasted the output of the sample flow | Fixes for the agent's HTTP 400 (thought signatures, null content, error details, retries) and missing resume sections (check, repair, rebuild) | Found both bugs by running the app with Gemini; applied and retested | 51 tests; 5 resume sections; agent PLAN_PROPOSED after 5 tool calls |
| 2026-11-14 | Fix the blocked cover letter | Reported a truthful letter blocked with no reason, then pasted the warnings | Visible warnings, evidence-path normalization, one repair attempt | Noticed the problem, reported it, retested until the letter saved | 54 tests; all 12 features work with the real model |
| 2026-11-15 | Record the demo videos | Asked for a video script, then reported the cut-off agent button | Two-part script; layout fix for the agent tab | Recorded both videos, noticed the hidden button, applied the fix and re-recorded part 2 | Two demo videos; fix committed |

Full log: docs/ai-collaboration-log.md.

## 6. Development and verification summary

Weekly commit summary

| Period | Dates | Commits (links) |
|---|---|---|
| 1 | Oct 20-26 | None. No code was committed in this period. |
| 2 | Oct 27-Nov 2 | 7 code commits: [e752680](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/e752680), [cb9a018](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/cb9a018), [f948515](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/f948515), [9a167ce](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/9a167ce), [ccf487a](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/ccf487a), [f0b42c6](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/f0b42c6), [41c0c30](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/41c0c30) |
| 3 | Nov 3-9 | 5 code commits: [de92520](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/de92520), [dd767b5](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/dd767b5), [894a9ed](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/894a9ed), [5af2304](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/5af2304), [0a706d0](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/0a706d0) (pushed Nov 2; counted toward period 3 with the instructor's approval). Docs commit [d512006](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/d512006) |
| After period 3 | Nov 14-15 | 7 code commits fixing defects found with the real model: [0912874](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/0912874), [7308be7](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/7308be7), [1613327](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/1613327), [a366268](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/a366268), [46f1178](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/46f1178), [e418dad](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/e418dad), [c5f5eed](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/c5f5eed) (plus docs commit [02056b8](https://github.com/muhammad-kermali/EECS3311-CareerPilot/commit/02056b8)) |

Checks performed

- 54 automated tests (mvn test, all passing): repository revision conflicts, Prototype independence, plan validation (cycles, unknown dependencies, time, deadline), evidence grounding, tool registry rejections, agent loop (tool feedback, 8-call limit, single repair, NEEDS_INPUT, provider failure), adapter request/response translation against a local HTTP server (including Gemini thought signatures and Gemini error lists), logging and retrying decorators, export builders and atomic writing, and end-to-end flows through the facade with a scripted model.
- Every CLI command was run end to end against a local OpenAI-compatible test server, including error paths: invalid dates, unsupported file types, AI service down, invalid quotes, invalid evidence, missing API key, stale revisions, existing export folders and the agent's scope, limit and infeasible cases.
- The GUI was launched and every tab was exercised, including fit analysis and the agent running on a background thread.
- I ran every feature with the real model (gemini-3.1-flash-lite) through the CLI and the GUI using the sample data, fixed the defects listed below, and retested each fix. Both demo videos were recorded on the final version.

Defects found and corrected
Found by testing with the real model:

- Agent failed with "HTTP 400" after its first tool call. The adapter dropped Gemini's thought_signature on tool calls and sent content: null on tool-only turns. Both are fixed, and ProviderClient now reads Gemini's error format so the provider's reason is shown instead of only the status code. Retested: the agent finished with PLAN_PROPOSED after 5 tool calls.
- Resume import kept only a Projects section. Added the completeness check, one repair, and the rebuild fallback. Retested: all five sections imported.
- Cover letter warnings were never shown, so a blocked letter gave no reason. CoverLetter now reports its warnings through the facade.
- Truthful cover letter blocked because the model wrote evidence paths as profile/experience/E1/bullet/0. Added path normalization, clearer path formatting in prompts, and one repair attempt. Retested: the letter passed every check and saved.
- Prepare Application button cut off in the agent tab at normal window sizes (found while recording). Controls now use two rows.

Found earlier:

- Added RetryingLLMClient for temporary 429/503 errors from the free tier.
- PDFBox font-cache warnings reappeared because the logger level was set on a weakly referenced logger; Main now keeps a static reference.
- PDFBox printed font-cache messages on every import; its logger is now limited to severe messages.
- The interview's pending-feedback message ran two sentences together; reworded.
- Export could silently package a resume made from an older profile; it now warns.

Remaining limitations: see section 1.

## 7. Demonstration index

The demo is two videos, both public by link (YouTube, Unlisted): [Part 1](https://youtu.be/Uz7fT3sWZ2U) covers F01 to F06 and [Part 2](https://youtu.be/4y0vOQqgCr8) covers F07 to F12. Each feature is a separate segment, introduced by saying its ID and name, in the order below. Both the GUI and the CLI are shown.

| Feature | Video segment | Interface | Error or alternative shown |
|---|---|---|---|
| F01 Import resume | [Part 1, F01](https://youtu.be/Uz7fT3sWZ2U) | GUI (error in CLI) | Unsupported file type rejected |
| F02 Review and correct profile | [Part 1, F02](https://youtu.be/Uz7fT3sWZ2U) | GUI | Invalid date rejected |
| F03 Import and analyze a job | [Part 1, F03](https://youtu.be/Uz7fT3sWZ2U) | GUI | Empty posting rejected |
| F04 Fit analysis | [Part 1, F04](https://youtu.be/Uz7fT3sWZ2U) | CLI | Unknown job ID |
| F05 Gaps and next steps | [Part 1, F05](https://youtu.be/Uz7fT3sWZ2U) | GUI | Stale fit recomputed first (explained) |
| F06 Tailored resume | [Part 1, F06](https://youtu.be/Uz7fT3sWZ2U) | GUI | A change unticked; base resume unchanged |
| F07 Cover letter | [Part 2, F07](https://youtu.be/4y0vOQqgCr8) | CLI | Invalid tone rejected |
| F08 Interview questions | [Part 2, F08](https://youtu.be/4y0vOQqgCr8) | GUI (error in CLI) | Count over 20 rejected |
| F09 Mock interview | [Part 2, F09](https://youtu.be/4y0vOQqgCr8) | CLI | Blank answer rejected; pause and resume |
| F10 Application tracking | [Part 2, F10](https://youtu.be/4y0vOQqgCr8) | GUI | Overdue filter; invalid date rejected |
| F11 Preparation agent | [Part 2, F11](https://youtu.be/4y0vOQqgCr8) | GUI | Too little time (30 minutes) |
| F12 Export | [Part 2, F12](https://youtu.be/4y0vOQqgCr8) | CLI | Existing folder refused |
