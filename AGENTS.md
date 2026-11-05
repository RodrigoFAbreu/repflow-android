# RepFlow Agent Instructions

RepFlow is a native, offline-first Android workout tracker.

## Always read

Before non-trivial work, read only:

- `docs/ACTIVE_MILESTONE.md`
- `docs/ROADMAP.md`
- the active milestone execution guide referenced by
  `docs/ACTIVE_MILESTONE.md`

Follow `.github/copilot-instructions.md`.

## Read conditionally

Read only the documentation relevant to the task:

- Full decision text, invariants, or test details not in the execution file:
  the reference file linked from the "Active plan" section of
  `docs/ACTIVE_MILESTONE.md`
- Domain models or business rules:
  `docs/DOMAIN_GLOSSARY.md`
- UI, navigation or user interaction:
  `docs/UX_FLOWS.md`
- Architecture, boundaries or modularization:
  `docs/adr/0003-layered-modular-architecture.md`
- Room, persistence, migrations or backup:
  `docs/adr/0002-offline-first-local-database-source-of-truth.md`
- Toolchain or dependency decisions:
  `docs/TECHNICAL_DECISIONS.md`
- Product-scope ambiguity:
  `docs/PROJECT_BRIEF.md`

Do not read every document by default.

## Workflow

For the active milestone:

1. Inspect only the files relevant to the next incomplete checkpoint.
2. Implement that checkpoint.
3. Run its narrowest relevant checks.
4. Review the resulting diff.
5. Update `docs/ACTIVE_MILESTONE.md`.
6. Continue unless there is a blocker.

Stop for:

- ambiguous product behavior;
- architecture changes;
- new dependency categories;
- schema migrations;
- destructive data operations;
- repeated verification failures;
- unrelated working-tree changes.

Never use destructive Room migrations.
Never claim a test passed unless it actually ran.

Commits are normally prohibited.

An active milestone prompt may explicitly authorize one completion commit after
all Definition of Done requirements and verification gates pass.

Never push, merge, rebase, force-push, or create a pull request.

## Commands

```bash
./gradlew build                        # full build
./gradlew spotlessCheck                # formatting check (Spotless/ktlint)
./gradlew spotlessApply                # formatting auto-fix
./gradlew detekt                       # static analysis, config/detekt/detekt.yml, maxIssues: 0
./gradlew lintDebug                    # Android Lint
./gradlew testDebugUnitTest            # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.repflow.app.application.workout.RecordWorkoutSetTest"
./gradlew connectedDebugAndroidTest     # instrumented tests, requires a device/emulator
./gradlew assembleDebugAndroidTest      # compile instrumented tests only, no device needed
./gradlew assembleDebug                 # assemble debug APK
```

CI (`.github/workflows/pipeline.yml`, the orchestrator, calling `android-checks.yml` and on `main` `release.yml`) runs, in order: `spotlessCheck`, `detekt`,
`lintDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`
(instrumented tests compile but don't run in CI — no device available). Run
the narrowest relevant check per checkpoint; run the full suite before
considering a milestone done.

Budgets: a pull request's CI stays under 20 minutes and a push to `main` under 25. A run over budget gets
a warning from `tools/ci_time_budget.sh`; find the cause, since more shards alone isn't a fix. Every `feat:`
or `fix:` merge publishes a GitHub Release with the APK, so pull request titles must be conventional commits.

## Running under the Controller

The Workflow Controller runs each milestone in `docs/backlog.yaml`. These apply to every job:

- **Plan header.** A plan carries exactly one header line of exactly this form, with nothing after the closing
  backtick: ``Pull request title: `fix: what changed` ``. Use `fix:` for a correction and `feat:` for something new.
  The title becomes the squash commit's subject and decides the release. Any other form declares no title.
- **Review severity.** Blocking or Important means wrong code or a missed requirement. Citations, file lists,
  wording of the plan and similar bookkeeping are Optional, and an Optional finding is never a reason for another
  round. Say what the user would see go wrong.
- **Simple commands.** Run `gh` and scripts as separate simple commands. A heredoc script chained with `gh` can be
  refused by the auto-mode check even though each part is allowed alone.
- **Acceptance.** Don't check `gh` first, for example with `gh auth status`, which the auto-mode check refuses: `workflow_state.satisfy_acceptance_gate` runs the Workflow's own GitHub query, and calling it is the intended path, not a way round anything.
- **Optional findings are this lane's to triage, not the owner's.** Check each against the code: `fixed`, `dropped` with a reason, or `follow-up` with a link to an issue or `docs/items/` page. Before acceptance, give each one a disposition in
  `docs/ai-workflow/registry/<id>-dispositions.json`: `python3 scripts/workflow_state.py --finding-dispositions <id>`
  lists them and prints a skeleton. A follow-up issue is enough; acceptance never waits for a fix.
- **Devices.** The orchestrator keeps adb and the emulator running outside the jobs. Don't start or kill them, since a
  process a job starts can hold that job open. Instrumented tests run only on `emulator-5554`, because
  `connectedDebugAndroidTest` uninstalls the app afterwards and would wipe a phone's data.

