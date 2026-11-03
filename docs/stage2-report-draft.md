# CareerPilot: Stage 2 Implementation Report (DRAFT)

EECS3311 Software Design | Muhammad Abbas Kermali | Repository: https://github.com/muhammad-kermali/EECS3311-CareerPilot

> **Draft notes (delete before submitting).** Items in [brackets] must be filled in by you: video links, commit links, your review notes, and AI token/cost totals. Read every section against the code and change anything that does not match what you did. Export the final version as `stage2-report.pdf` in the repo root.

## 1. Project overview and implementation status

CareerPilot is a Java 17 desktop application that helps students and early-career applicants prepare job applications. A Swing GUI and a command-line interface both call one `CareerAssistantFacade`, which routes each request to a feature service. Records are stored locally as JSON files with IDs and revision numbers.

**AI integration.** The application calls an OpenAI-compatible chat model (default `gpt-4.1-mini`) through `OpenAILLMAdapter`. The model extracts profile data, analyzes postings, compares evidence, drafts documents, writes interview questions and feedback, and drives the preparation agent. Java code handles validation, state, dates, storage and export.

**Agent behaviour.** In F11, `CareerAgent` runs a tool-use loop with six tools (`get_profile`, `get_job`, `analyze_fit`, `inspect_documents`, `draft_application`, `validate_plan`). It chooses tools based on earlier results: it reuses current fit reports, drafts only missing documents, checks its plan with `validate_plan` before answering, and stops to ask a question when required evidence is missing. Runs stop after 8 tool calls or 60 seconds. Tools cannot save anything; the applicant saves plans and drafts separately.

**Grounding.** Every AI claim about the candidate must cite profile evidence (`experience:E1:bullet:0` style paths with an exact quote). `EvidenceValidator` checks that each path exists and the quote appears in that fact, and flags any number in a claim that the evidence does not contain. Unsupported resume changes cannot be saved, and unsupported letter paragraphs must be edited before saving. Job requirements must quote the posting exactly.

**Status.** All 12 Stage 1 features are implemented and work in both interfaces. [Update after testing with your real API key.]

**Limitations.**
- Only text-based PDF, TXT and MD resumes are supported (no OCR, no DOCX).
- Tailoring rewrites existing resume lines; it does not reorder sections.
- Grounding checks catch invented paths, misquotes and new numbers, but they cannot prove that reworded text is fully faithful. The applicant still reviews every draft.
- One applicant profile per workspace.
- [Add anything you find while testing with the real model.]

## 2. Feature to implementation mapping

Every feature enters through `CareerGUI.submit()` or `CareerCLI.submit()`, then `CareerAssistantFacade.execute()`, which dispatches on `Operation` and returns a `FeatureResult`. Paths below are under `src/main/java/careerpilot/`.

| ID | Feature | Stage 1 | Status | Main classes / files | Key methods | Patterns | Video |
|---|---|---|---|---|---|---|---|
| F01 | Import resume and extract profile | F01 | Implemented (modified, see 4) | `service/ProfileService`, `parsing/ResumeImporter`, `PdfResumeImporter`, `TxtResumeImporter`, `PdfTextParser`, `PlainTextParser`, `ai/StructuredOutput` | `importResume()`, `importText()`, `createParser()`, `extractText()`, `confirmImport()` | Facade, Factory Method, Adapter, Decorator | [link + time] |
| F02 | Review and correct profile | F02 | Implemented | `service/ProfileService`, `domain/CandidateProfile`, `storage/JsonWorkspaceRepository`, `ui/gui/ProfilePanel` | `getProfile()`, `updateProfile()`, `validate()`, `save()` | Facade | [link] |
| F03 | Import and analyze a job | F03 | Implemented | `service/JobService`, `domain/JobPosting`, `Requirement`, `JobPreview` | `analyzeJob()`, `detectDeadline()`, `saveJob()` | Facade, Adapter, Decorator | [link] |
| F04 | Fit analysis with evidence | F04 | Implemented | `service/AnalysisService`, `EvidenceValidator`, `domain/FitReport` | `analyzeFit()`, `readEvidence()`, `isStaleFor()` | Facade, Adapter, Decorator | [link] |
| F05 | Gaps and next steps | F05 | Implemented | `service/AnalysisService`, `domain/GapReport`, `GapAction` | `identifyGaps()`, `currentFit()` | Facade, Adapter, Decorator | [link] |
| F06 | Tailored resume version | F06 | Implemented | `service/DocumentService`, `domain/Resume`, `ResumePrototype`, `ResumeChange` | `tailorResume()`, `deepCopy()`, `saveDraft()` | Facade, Prototype, Adapter, Decorator | [link] |
| F07 | Cover letter draft | F07 | Implemented | `service/DocumentService`, `domain/CoverLetter`, `LetterParagraph` | `draftLetter()`, `saveDraft()` | Facade, Adapter, Decorator | [link] |
| F08 | Interview questions | F08 | Implemented | `service/InterviewService`, `domain/QuestionSet`, `InterviewQuestion` | `generateQuestions()` | Facade, Adapter, Decorator | [link] |
| F09 | Mock interview with feedback | F09 | Implemented (modified, see 4) | `service/InterviewService`, `AnswerOutcome`, `domain/InterviewSession`, `AnswerRecord`, `Feedback` | `startInterview()`, `submitAnswer()`, `retryFeedback()`, `pauseInterview()` | Facade, Adapter, Decorator | [link] |
| F10 | Application tracking | F10 | Implemented | `service/ApplicationService`, `ApplicationView`, `domain/ApplicationRecord`, `ApplicationStatus` | `updateApplication()`, `listApplications()`, `isOverdue()` | Facade | [link] |
| F11 | Application preparation agent | F11 | Implemented | `service/PlanService`, `agent/CareerAgent`, `ToolRegistry`, `ToolCommand`, `InvokeToolCommand`, `AgentTool`, `agent/tools/*`, `service/PlanValidator` | `prepareApplication()`, `run()`, `executeTool()`, `execute()`, `validate()`, `savePlan()` | Facade, Command, Adapter, Decorator | [link] |
| F12 | Export application package | F12 | Implemented | `service/ExportService`, `export/ApplicationPackageDirector`, `ApplicationPackageBuilder`, `MarkdownPackageBuilder`, `TextPackageBuilder`, `FileExporter` | `exportPackage()`, `construct()`, `reset()`, `addResume()`, `build()`, `write()` | Facade, Builder | [link] |

### How each feature works

**F01 (hybrid).** `ProfileService.importResume()` picks a `ResumeImporter` by file extension. `importText()` checks size and emptiness, calls the subclass's `createParser()`, and extracts text with PDFBox or plain reading. `StructuredOutput.generate()` sends the text to the model with a JSON schema and validates the answer (one repair attempt). Email and phone are found with regular expressions, dates are normalized to YYYY-MM, and any skill or bullet not found word for word in the resume becomes a warning. The result is an unsaved `ProfilePreview`; `confirmImport()` saves the profile and base resume together with `saveBatch()`.

**F02 (deterministic).** `getProfile()` loads the current profile; the GUI form or `profile edit` flags change it. `updateProfile()` validates required fields, email format and date order, then `JsonWorkspaceRepository.save()` rejects the write if the revision changed since loading. A new revision makes older fit reports and documents stale.

**F03 (hybrid).** `JobService.analyzeJob()` limits input length, detects a deadline with `detectDeadline()`, and asks the model for requirements. Each requirement's `sourceSpan` must be an exact quote from the posting; any bad quote triggers one repair, and quotes that are still wrong are dropped with a warning. The applicant reviews the `JobPreview`; `saveJob()` stores it.

**F04 (hybrid).** `AnalysisService.analyzeFit()` sends the profile's evidence catalog and the requirements. The parser requires every requirement exactly once and checks each evidence item with `EvidenceValidator.readEvidence()`. Invalid evidence triggers one repair; after that, matches without valid evidence are downgraded to NOT_EVIDENCED with a warning. The `FitReport` records the profile and job revisions it used.

**F05 (hybrid).** `identifyGaps()` reuses the current fit report, or recomputes it if it is missing or stale (`isStaleFor()`). It asks for one action per non-supported requirement and checks that every gap gets exactly one action with a valid type and hour estimate.

**F06 (hybrid).** `DocumentService.tailorResume()` loads the base resume and calls `deepCopy()` (Prototype), so edits never touch the original. The model proposes line rewrites with evidence. Each change is marked supported only if it cites valid evidence and adds no new numbers. The applicant ticks changes to keep; `saveDraft()` reverts unticked changes and refuses unsupported ones.

**F07 (hybrid).** `draftLetter()` validates the tone, sends evidence, requirements and the applicant's own motivation text, and checks each paragraph's evidence and numbers. Problems become warnings. `saveDraft()` refuses an unedited letter that still contains unsupported paragraphs.

**F08 (hybrid).** `InterviewService.generateQuestions()` checks the count (1 to 20) and focus, then requires exactly that many distinct questions, each linked to real requirement IDs.

**F09 (hybrid).** `startInterview()` creates a session. `submitAnswer()` saves the answer first, then requests feedback. If the model fails, the answer stays saved with feedback pending and the session still advances; `retryFeedback()` fills it in later. Sessions can be paused and resumed.

**F10 (deterministic).** `ApplicationService.updateApplication()` creates or updates the one record per job, parses status and dates, rejects future applied dates, and checks the revision. `listApplications()` filters by status and computes overdue flags and days left from the clock.

**F11 (agent).** `PlanService.prepareApplication()` validates the time budget and starts `CareerAgent.run()`. Each turn the model either requests tools or gives a final JSON answer. `ToolRegistry.executeTool()` rejects unknown tools, bad JSON, missing arguments and IDs outside the run's scope, then binds a valid request to an `InvokeToolCommand` and executes it. Results are fed back as tool messages. `PlanValidator` checks the final plan (IDs, dependencies, cycles, minutes, deadline). The applicant saves the plan with `savePlan()` and any drafts separately with `saveDraft()`.

**F12 (deterministic).** `ExportService.exportPackage()` loads the latest reviewed documents for the job, refuses unreviewed ones, and warns about outdated or missing ones. `ApplicationPackageDirector.construct()` calls `reset()`, the add methods and `build()` on the chosen builder. `FileExporter.write()` writes into a temporary folder and renames it only when complete, and refuses existing destinations.

## 3. Implemented design patterns

All seven Stage 1 patterns are implemented as designed. Updated class diagrams: `docs/uml/A-application.png`, `B-ai-agent.png`, `C-domain-export-parsing.png` (Mermaid sources beside them). The Stage 1 use-case and sequence diagrams still describe the implemented flows; small differences are listed in section 4.

| Pattern | Problem | Participants (role) | Files and methods | Matches Stage 1? |
|---|---|---|---|---|
| Facade | GUI and CLI need the same operations without duplicating coordination and error handling | `CareerAssistantFacade` (facade); services (subsystem); `CareerGUI`, `CareerCLI` (clients) | `app/CareerAssistantFacade.execute()`, `dispatch()` | Yes |
| Adapter | The provider's request/response format must not spread through the services | `LLMClient` (target), `OpenAILLMAdapter` (adapter), `ProviderClient` (adaptee) | `ai/OpenAILLMAdapter.generate()`, `toProviderJson()`, `fromProviderJson()` | Yes |
| Decorator | Model calls need timing, outcome and token logging without changing provider code | `LLMClient` (component), `OpenAILLMAdapter` (concrete component), `LoggingLLMClient` (decorator) | `ai/LoggingLLMClient.generate()` returns or rethrows exactly what the wrapped client did and never logs text | Yes |
| Prototype | Tailoring must not change the base resume through shared lists | `ResumePrototype` (prototype interface), `Resume` (concrete prototype), `DocumentService` (client) | `domain/Resume.deepCopy()`, `ResumeSection.copy()` | Yes |
| Builder | The same selected documents must be assembled into Markdown or text without format conditions in the service | `ApplicationPackageDirector` (director), `ApplicationPackageBuilder` (builder), `MarkdownPackageBuilder`, `TextPackageBuilder` (concrete builders), `ApplicationPackage` (product) | `export/ApplicationPackageDirector.construct()` | Yes; `reset()` now receives the job (see 4) |
| Command | Each validated tool request should be a self-contained object, separate from the agent loop | `ToolRegistry` (invoker), `ToolCommand` (command), `InvokeToolCommand` (concrete command), `AgentTool` implementations (receivers), `CareerAgent` (client) | `agent/ToolRegistry.executeTool()`, `InvokeToolCommand.execute()` | Yes |
| Factory Method | The import workflow should not change when a file format is added | `ResumeImporter` (creator), `PdfResumeImporter`, `TxtResumeImporter` (concrete creators), `DocumentParser` (product), `PdfTextParser`, `PlainTextParser` (concrete products) | `parsing/ResumeImporter.importText()` calls `createParser()` | Yes |

## 4. Changes and rationale

| Original design | Implemented change | Reason | Impact |
|---|---|---|---|
| `updateProfile()` saved both imports and edits | Imports are saved by `ProfileService.confirmImport()`; edits by `updateProfile()` | Import needs `saveBatch()` of two new records; edits need a revision check on one existing record | F01/F02, SD01; facade operations `CONFIRM_PROFILE` and `UPDATE_PROFILE` |
| `JobService.analyzeJob()` returned `JobPosting` | Returns `JobPreview` (job plus warnings) | Dropped requirements and missing deadlines must be shown to the user | F03, SD02 |
| Invalid fit evidence: one repair, then error | One repair, then invalid evidence is removed and the match is downgraded to NOT_EVIDENCED with a warning | A single bad citation should not discard a whole useful report; the downgrade keeps it honest | F04 |
| `InterviewSession.answers: List<String>` | `List<AnswerRecord>` (answer, feedback, pending flag, time) | Pending feedback and retries need per-answer state | F09, class diagram E |
| `ApplicationPackageBuilder.reset()` | `reset(JobPosting job)` and a shared `AbstractPackageBuilder` | Headers and the manifest need the job; the abstract class removes duplicated state | F12, Builder participants unchanged |
| Agent tool names `getProfile`, `getJob`, ... | `get_profile`, `get_job`, ... | Snake case is the common convention for model function names | F11 |
| Not in Stage 1 | `StructuredOutput` helper | One place implements the "at most one repair" rule from SD08 for every service | All hybrid features |
| Not in Stage 1 | `EvidenceValidator` | Shared grounding checks for F04, F06, F07 | F04, F06, F07 |
| Not in Stage 1 | `AppFactory` (composition root), `AppConfig` (.env loading), `UnconfiguredLLMClient` | Wire concrete classes in one place; keep keys out of code; give a clear message when no key is set | Startup only |
| Not in Stage 1 | Export warns when a document was made from an older profile or job | Stale documents should not be exported silently | F12 |
| PDF/TXT resumes | PDF/TXT/MD resumes | Markdown is plain text and needs no new parser class | F01 |
| GPT-4.1 mini only | Any OpenAI-compatible endpoint via `LLM_BASE_URL` and `LLM_MODEL` | Allows cheaper or free models | Configuration only |

[Add any changes you make during the rest of Stage 2.]

## 5. AI tools and collaboration log

| Tool / platform | Model | Used for | Tokens and cost |
|---|---|---|---|
| Claude (claude.ai app, agentic coding session) | Configured as `claude-opus-5-5` [confirm in the app's model picker] | Generated the initial implementation of all packages, the tests, sample data, README and this draft; compiled and ran the code against a local stand-in for the OpenAI API | [fill in from your plan or usage page] |
| [ChatGPT or others, if used] | [model] | [tasks] | [tokens, $] |
| Application runtime model | `gpt-4.1-mini` via OpenAI API | Used by CareerPilot itself, not a development tool | [from your OpenAI usage page] |

**Representative interactions** (keep the log in `docs/ai-collaboration-log.md` up to date and summarize it here):

| Date | Tool | Prompt / task | AI output | Human contribution (reviewed, corrected, rejected, redesigned) |
|---|---|---|---|---|
| 2026-11-01 | Claude | "Implement the Stage 1 design in Java using the exact class and method names" | Full first implementation, 43 tests, sample data | [what you reviewed and changed] |
| [date] | | | | |

## 6. Development and verification summary

**Weekly commit summary**

| Period | Dates | Commits (links) |
|---|---|---|
| 1 | Oct 20-26 | [none, or links] |
| 2 | Oct 27-Nov 2 | [links] |
| 3 | Nov 3-9 | [links] |

**Checks performed**
- 43 automated tests (`mvn test`): repository revision conflicts, Prototype independence, plan validation (cycles, unknown dependencies, time, deadline), evidence grounding, tool registry rejections, agent loop (tool feedback, 8-call limit, single repair, NEEDS_INPUT, provider failure), adapter request/response translation against a local HTTP server, logging decorator, export builders and atomic writing, and end-to-end flows through the facade with a scripted model.
- Every CLI command was run end to end against a local OpenAI-compatible test server, including error paths: invalid dates, unsupported file types, AI service down, invalid quotes, invalid evidence, missing API key, stale revisions, existing export folders and the agent's scope, limit and infeasible cases.
- The GUI was launched and every tab was exercised, including fit analysis and the agent running on a background thread.
- [Your own checks with the real model.]

**Defects found and corrected**
- PDFBox printed font-cache messages on every import; its logger is now limited to severe messages.
- The interview's pending-feedback message ran two sentences together; reworded.
- Export could silently package a resume made from an older profile; it now warns.
- [Add defects you find and fix.]

**Remaining limitations:** see section 1.

## 7. Demonstration index

| Feature | GUI segment | CLI segment | Error/alternative shown |
|---|---|---|---|
| F01 Import resume | [link?t=] | [link?t=] | Unsupported file type |
| F02 Edit profile | | | Invalid date rejected |
| F03 Analyze job | | | Empty posting |
| F04 Fit analysis | | | Stale report after profile edit |
| F05 Gaps | | | |
| F06 Tailor resume | | | Unsupported change blocked |
| F07 Cover letter | | | Unsupported paragraph must be edited |
| F08 Questions | | | Count over 20 |
| F09 Mock interview | | | Pending feedback and retry |
| F10 Tracking | | | Overdue filter, invalid status |
| F11 Agent | | | Infeasible time budget |
| F12 Export | | | Existing folder refused |
