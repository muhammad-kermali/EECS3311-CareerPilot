# CareerPilot (EECS3311 course project)

Muhammad Abbas Kermali

CareerPilot is an AI job application assistant written in Java, with a Swing GUI and a command-line interface. It imports a resume into a structured profile, analyzes job postings, compares the profile with each job using cited evidence, tailors resumes and cover letters without inventing facts, runs mock interviews, tracks applications and deadlines, and uses a tool-using agent to plan the work for an application.

Both interfaces call the same `CareerAssistantFacade`, so every feature behaves the same in the GUI and the CLI.

## Project stages

- **Stage 1 (design):** [CareerPilot-Stage1-Report.pdf](CareerPilot-Stage1-Report.pdf) and [CareerPilot-Stage1.zip](CareerPilot-Stage1.zip) (PlantUML sources and traceability CSV)
- **Stage 2 (implementation):** this source code, `stage2-report` (PDF), and the demo videos linked from that report
- **Stage 3 (testing):** to come

## Prerequisites

- Java 17 or newer (`java -version`)
- Apache Maven 3.9 or newer (`mvn -version`)
- A free Google Gemini API key from https://aistudio.google.com (no payment needed). Any other OpenAI-compatible provider also works, for example OpenAI's `gpt-4.1-mini`. Deterministic features (profile editing, application tracking, export) work without a key.

## Set up your API key (local only)

```bash
cp .env.example .env        # Windows: copy .env.example .env
```

Open `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your key. `.env` is listed in `.gitignore` and must never be committed. The example is set up for Gemini (`gemini-3.1-flash-lite`); to use another OpenAI-compatible provider, change `LLM_BASE_URL` and `LLM_MODEL` (the file shows the OpenAI values). If a model returns 404 or "no longer available to new users", pick a current model name from your provider. Busy (503) and rate-limit (429) errors are retried automatically up to 3 times.

## Build and run

```bash
mvn package                 # compiles, runs the unit tests, builds target/careerpilot.jar
./career                    # opens the GUI      (Windows: career.bat)
./career help               # lists CLI commands
```

You can also run `java -jar target/careerpilot.jar` directly. With no arguments it opens the GUI; with arguments it runs a CLI command. Data is stored as JSON files in `workspace/` (git-ignored). Model calls are logged to `workspace/logs/llm-calls.log` with timing and token counts only, never prompt text.

## Standalone CLI (run a task from a file or standard input)

CareerPilot can run a whole task from a file without asking anything, which is how it is meant to be run by scripts and in Stage 3 testing. The agent and every validation step run exactly as in the GUI, because both go through the same `CareerAssistantFacade`.

```bash
java -jar target/careerpilot.jar --llm gemini --input samples/task-prepare-application.txt
```

That task file (in `samples/`) looks like this:

```text
task: prepare-application
resume: samples/resume-jordan-lee.pdf
job: samples/job-northwind-junior-dev.txt
minutes: 180
deadline: 2026-11-30
preferences: focus on backend Java work
```

Expected output: the resume is imported as a profile, the job is analyzed, the agent's tool calls are listed (for example `inspect_documents`, `analyze_fit`, `validate_plan`), then its plan, and `Saved plan PL1`. The exit code is 0.

**Other ways to give the task**

```bash
# from standard input
cat samples/task-prepare-application.txt | java -jar target/careerpilot.jar --llm gemini --input -
# one-dash spelling also works
java -jar target/careerpilot.jar -LLMAPI gemini -input samples/task-fit.txt
# JSON output for scripts and automated tests (stdout holds only the JSON document)
java -jar target/careerpilot.jar --llm gemini --json --input samples/task-fit.txt
# a command script: one CLI command per line, run in order without questions
java -jar target/careerpilot.jar --input samples/task-commands.txt
```

**Options** (put them before the command; `--name` and `-name` are the same)

| Option | Meaning |
|---|---|
| `--input FILE` or `--input -` | Run the task in FILE, or read it from standard input |
| `--llm gemini\|openai\|ollama` (alias `-LLMAPI`) | Which LLM API to use; sets the base URL and a default model |
| `--llm-model NAME` | Model name, for example `gemini-3.1-flash-lite` |
| `--llm-base-url URL` | Any OpenAI-compatible endpoint |
| `--llm-key-env NAME` | Read the API key from this environment variable (default: `LLM_API_KEY` or `.env`) |
| `--workspace DIR` | Where data is stored (default `workspace`) |
| `--json` | Task mode: print the result as one JSON document |
| `--gui` | Open the GUI (also the default with no arguments) |
| `--help` | Show all options |

**Task file keys** (`key: value`, one per line, `#` starts a comment)

| Key | Used by | Meaning |
|---|---|---|
| `task` | all | `prepare-application` (default), `fit`, `gaps`, `tailor-resume`, `cover-letter`, `interview-questions` |
| `resume` | all | Resume file to import first (optional if a profile is already saved) |
| `job` or `job-id` | all | Job posting file to analyze, or the ID of a saved job |
| `company` | all | Company name, if the posting does not say |
| `minutes`, `deadline`, `preferences` | prepare-application | Time available, deadline (YYYY-MM-DD), anything the agent should know |
| `tone`, `motivation` | cover-letter | `professional`, `enthusiastic` or `concise`; your own reason for applying |
| `count`, `focus` | interview-questions | 1 to 20 questions; `technical`, `behavioural` or `mixed` |
| `save` | all | `yes` (default) saves results that pass validation; `no` only prints them |
| `save-drafts` | prepare-application | `yes` also saves drafts the agent created (default `no`) |

Relative paths are tried from the folder you run in (the project folder in these examples), then from the task file's folder. The agent has 8 tool calls and, by default, 180 seconds per run (set `AGENT_TIMEOUT_SECONDS` in `.env` to change it). Exit codes: `0` the task finished (including when the agent asks a question or says the time is not enough), `1` a step failed (for example the AI service was unavailable, or the agent ran out of time or tool calls), `2` the task file or options are invalid.

## Try it with the sample data

The `samples/` folder has a fictional resume and job posting.

```bash
./career resume import --file samples/resume-jordan-lee.pdf      # F01, review then save
./career profile edit --add-skill Docker                          # F02
./career job import --file samples/job-northwind-junior-dev.txt   # F03, saved as J1
./career job fit --job J1                                         # F04
./career job gaps --job J1                                        # F05
./career resume tailor --job J1                                   # F06
./career letter draft --job J1 --tone professional                # F07
./career interview questions --job J1 --count 5                   # F08, saved as Q1
./career interview start --questions Q1                           # F09 (type /pause to stop)
./career application update --job J1 --status applied --notes "Applied online"   # F10
./career application list --overdue
./career plan prepare --job J1 --minutes 120                      # F11 agent
./career export --job J1 --out ./exports/J1 --format markdown     # F12
```

Add `--yes` to accept the confirmation prompts automatically.

## Features

| ID | Feature | Type | GUI | CLI |
|---|---|---|---|---|
| F01 | Import resume and extract profile | Hybrid | Profile > Import Resume | `resume import` |
| F02 | Review and correct profile | Deterministic | Profile > Save Changes | `profile show`, `profile edit` |
| F03 | Import and analyze a job | Hybrid | Jobs & Fit > Add Job | `job import`, `job list`, `job show` |
| F04 | Fit analysis with evidence | Hybrid | Jobs & Fit > Analyze Fit | `job fit` |
| F05 | Gaps and next steps | Hybrid | Jobs & Fit > Skill Gaps | `job gaps` |
| F06 | Tailored resume version | Hybrid | Documents > Tailor Resume | `resume tailor` |
| F07 | Cover letter draft | Hybrid | Documents > Draft Cover Letter | `letter draft` |
| F08 | Interview questions | Hybrid | Interview > Generate Questions | `interview questions` |
| F09 | Mock interview with feedback | Hybrid | Interview > Start Practice | `interview start/resume/answer/retry/pause` |
| F10 | Application tracking | Deterministic | Applications | `application update`, `application list` |
| F11 | Application preparation agent | Hybrid (agent) | Prepare (Agent) | `plan prepare` |
| F12 | Export application package | Deterministic | Export | `export` |

## Design patterns in the code

| Pattern | Main classes |
|---|---|
| Facade | `app.CareerAssistantFacade` |
| Adapter | `ai.OpenAILLMAdapter` (adapts `ai.ProviderClient` to `ai.LLMClient`) |
| Decorator | `ai.LoggingLLMClient`, `ai.RetryingLLMClient` |
| Prototype | `domain.ResumePrototype`, `domain.Resume.deepCopy()` |
| Builder | `export.ApplicationPackageDirector`, `export.ApplicationPackageBuilder`, `MarkdownPackageBuilder`, `TextPackageBuilder` |
| Command | `agent.ToolCommand`, `agent.InvokeToolCommand`, `agent.ToolRegistry` (invoker), `agent.AgentTool` (receiver) |
| Factory Method | `parsing.ResumeImporter.createParser()`, `PdfResumeImporter`, `TxtResumeImporter` |

## Project layout

```
src/main/java/careerpilot/
  app/        facade, requests/results, configuration, composition root
  ui/gui/     Swing interface          ui/cli/  command-line interface
  service/    feature services and deterministic validators
  agent/      CareerAgent, ToolRegistry, commands, tools/
  ai/         LLM client interface, OpenAI adapter, logging decorator, prompts
  domain/     records such as CandidateProfile, Resume, JobPosting, FitReport
  parsing/    resume importers and text parsers
  export/     package builders and file writer
  storage/    JSON repository with revision checks
src/test/java/  unit and integration tests (scripted model, no network)
samples/        fictional sample resume and job posting
docs/           Stage 2 report draft and updated UML
```

## Safety notes

- Generated claims about the candidate must cite profile evidence. Unsupported resume changes cannot be saved, and unsupported letter paragraphs must be edited first.
- The agent's tools are read-only or create unsaved drafts. Only the applicant saves documents and plans.
- Job postings and resumes are treated as data; instructions inside them are ignored.
- Nothing is ever sent to an employer.

## Stage 2 demo videos

- Part 1 (F01 to F06): https://youtu.be/Uz7fT3sWZ2U
- Part 2 (F07 to F12): https://youtu.be/4y0vOQqgCr8
- Part 3 (one task in the GUI, then the standalone CLI): https://youtu.be/Ty_fys1pfW8

Report: [stage2-report.pdf](stage2-report.pdf). AI collaboration log: [docs/ai-collaboration-log.md](docs/ai-collaboration-log.md)
