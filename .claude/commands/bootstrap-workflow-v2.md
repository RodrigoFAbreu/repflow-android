---
description: One-time bootstrap driver for the workflow-v2-1-core work item. Implements exactly one checkpoint per invocation, then stops. See docs/ai-workflow/WORKFLOW_V2_PLAN.md's D-Bootstrap.
state_writer: true
review-subject: none
---

**State-writer discipline (D1, item 354):** every `docs/ai-workflow/WORKFLOW_STATE.json` write this command performs -- everywhere a step below says "persist the returned state" -- is performed by calling `workflow_state.state_transaction(repo_root, mutator)`, never by a separate read-then-write: `state_transaction` holds `.ai-review/runtime/WORKFLOW_STATE.lock` (`workflow_state.state_lock`, `fcntl.flock(LOCK_EX)`) across the complete re-read -> apply-the-named-function -> canonical-serialize -> atomic-publish sequence in one process invocation, so `mutator` is the exact transition function each step below names (e.g. `lambda state: workflow_state.<fn>(state, ...)`), applied to freshly re-read state rather than to a snapshot taken before the lock was acquired.

Bootstrap-only driver for the `workflow-v2-1-core` work item (Workflow
v2.1 core — a process/tooling milestone, not a product one). This command
is the **sole** driver for this one work item's checkpoints, from WF0
through the registry's terminal checkpoint, whatever it currently is
(`D-Bootstrap`, revised per `OPUS-R10-002`/`-003`/`OPUS-R102-009`) — never
a literal checkpoint id, since a rule that hardcodes today's terminal
checkpoint as tomorrow's is the "convention with no owner" class this
plan exists to reject. It never
hands off to `/milestone-implement`, and `/milestone-implement` is never
invoked for this work item at any point. It never reads
`docs/ACTIVE_MILESTONE.md` or `docs/ROADMAP.md`; its target work item is
hardcoded below, so it cannot accidentally resume Milestone 8 or any
other work item. It is retired — deleted — only at this work item's own
`MILESTONE_COMPLETE`.

Authoritative plan: `docs/ai-workflow/WORKFLOW_V2_PLAN.md`.
Registry: `docs/ai-workflow/registry/workflow-v2-1-core-registry.json`.
Work item: `workflow-v2-1-core` (hardcoded, never an argument).
`base_commit`: `162154d3e5e10eb65e109833acae4b4fb01fc5d6` — hardcoded here
until `docs/ai-workflow/WORKFLOW_STATE.json` exists to read it from
(after WF1a lands).

1. **State-sync** — only once `docs/ai-workflow/WORKFLOW_STATE.json`
   exists (dormant before WF1a; skip this step entirely until then): read
   `work_items["workflow-v2-1-core"]` if present, or initialize it with
   the full field set: `work_item_type: "process"`, `work_item_kind:
   "process"`, `plan_path: "docs/ai-workflow/WORKFLOW_V2_PLAN.md"`,
   `registry_path:
   "docs/ai-workflow/registry/workflow-v2-1-core-registry.json"`,
   `governing_workflow_version: "1"` (fixed, never re-derived), the
   `base_commit` above, the plan's current `plan_revision`, `phase:
   "IMPLEMENTING"`, `checkpoints` populated from every already-completed
   checkpoint's trailer (step 3), and `plan_approval: {status: "CURRENT",
   basis: "USER_OVERRIDE", reviewed_bundle_id: <WF0's approving feedback's
   "Reviewed bundle ID:" field, verbatim>, approved_review_content_id:
   <WF0's own Workflow-Plan-Approval trailer value>, review_content_manifest:
   <the plan-stage manifest at WF0's commit>, reviewed_content_commit:
   null, user_confirmation: <the user's actual go-ahead text>, recorded_at:
   <WF0's commit timestamp>}`. Set `active_work_item_id` to
   `"workflow-v2-1-core"` if not already set to some other item.
   **Self-discovered-revision obligation** (`D-Plan-Revision-Publication`,
   `WFR-65`): once the entry above is read/initialized, read the
   registry's own on-disk `plan_revision`
   (`docs/ai-workflow/registry/workflow-v2-1-core-registry.json`). If it
   is strictly greater than the just-read/initialized entry's own
   `plan_revision` mirror -- someone bumped the registry (and the plan
   document's own `Revision N` title) without going through the ordinary
   `/milestone-plan`/`/apply-plan-review` flow, since this work item is
   permanently `"1"`-governed and driven only by this command -- call
   `workflow_state.publish_plan_revision(state, "workflow-v2-1-core",
   <the registry's plan_revision>, now)` and persist the returned state,
   which mirrors the value and transitions `phase` to
   `AWAITING_EXTERNAL_PLAN_REVIEW`. **Stop immediately** after this write
   and report it -- the item now needs external plan review before any
   checkpoint work resumes; do not proceed to step 2 or step 3 in the same
   invocation. If the registry's `plan_revision` is not ahead of the
   mirror, this obligation is a no-op and step 2 proceeds normally.
2. **Durability guard**: before selecting the next checkpoint, recompute
   the plan-stage `review_content_id`
   (`python3 scripts/workflow_fingerprint.py <base_commit>`) and compare
   it against the `Workflow-Plan-Approval` trailer value discovered in
   step 3 (or, once step 1 has run, against
   `plan_approval.approved_review_content_id`). A mismatch — someone
   edited the authoritative plan documents between two invocations —
   stops immediately, naming both values. Do not proceed past this check.
3. **Select the next checkpoint**: search `git log` in
   `<base_commit>..HEAD` for commits carrying an exact
   `Workflow-Checkpoint: <id>` trailer scoped to `Workflow-Work-Item:
   workflow-v2-1-core`, requiring exactly one match per id
   (`D-Commit-Provenance`); on more than one match apply its tie-break
   (first-parent ancestor of HEAD, verified content), and on genuine
   ambiguity stop rather than pick silently. Mark every checkpoint with a
   confirmed match `COMPLETE`. Walk
   `docs/ai-workflow/registry/workflow-v2-1-core-registry.json`'s
   `checkpoints` array in dependency order and select the first entry not
   yet `COMPLETE`. Report which checkpoint this is, and which one
   completed last (if any) — every invocation states both.

   **`NO_CHECKPOINT` terminal-wrap-up branch** (`WF8c` scope clause `(p)`,
   `GPT-R108-002`): if every registry checkpoint is already `COMPLETE` —
   reachable only once `WF8c`'s own commit lands — there is no next
   checkpoint to select. Skip step 4 through step 7 and instead perform
   the same tracked self-review -> verification -> bundle-generation
   sequence `/milestone-implement`'s own steps 2-4 perform for every other
   work item, never a hand-off to that command:
   - enter `SELF_REVIEWING_IMPLEMENTATION`: review the full work item
     diff (since `base_commit`) for correctness, layer-boundary
     violations, missing tests, and maintainability; fix all blocking and
     important findings;
   - run this work item's own applicable verification suite, with
     `scripts/` as the working directory: `python3 -m unittest
     workflow_integration_test workflow_state_test
     workflow_state_completion_obligations_test workflow_fingerprint_test
     workflow_test_harness_test workflow_fingerprint_generalization_test`
     (`OPUS-R110-M01`) — never `/milestone-implement`'s Android-specific
     `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest` literal,
     which does not apply to this process/tooling work item. Report
     exactly what ran and its real result — never claim a check passed
     that did not run;
   - enter `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`: **first**, before
     writing any bundle file, call `workflow_state.record_bundle_generation(
     state, "workflow-v2-1-core", stage="implementation", head=<current
     HEAD SHA>, now=<now>)` (`WF4c`, D-Approval-Commits' sole writer of
     `reviewed_implementation_head`), persist the returned state, and
     commit it **alone** — stage exactly that one path (never a broader
     `git add`) and create one commit carrying
     `Workflow-Bundle-Generation-Record:
     workflow-v2-1-core/<implementation_revision>` +
     `Workflow-Work-Item: workflow-v2-1-core` trailers, no other trailer.
     This durability commit must land *before* generation, never after
     (`WF8B-003`): a durability commit made after generation is by
     definition one commit ahead of the value it just wrote;
   - write `<bundle_dir>/IMPLEMENTATION_SUMMARY.md` (what was built, per
     checkpoint, and why), `<bundle_dir>/TEST_RESULTS.md` (the exact
     command above and its real result, `OPUS-R109-M01`),
     `<bundle_dir>/CONTEXT_FILES.txt` (only the unchanged docs a reviewer
     needs), and `<bundle_dir>/REVIEW_REQUEST.md` per
     `docs/ai-workflow/REVIEW_PROTOCOL.md` (stage: `implementation`);
   - run `./scripts/prepare-ai-review.sh <base_commit> implementation
     workflow-v2-1-core` (`<bundle_dir>` resolves per
     `workflow_fingerprint.resolve_bundle_dir`); bundle files themselves
     are never committed (gitignored, disposable, regeneratable) — only
     the generation-record commit above is real Git history;
   - report the bundle location and **stop**. This is a hard gate — do
     not mark the work item accepted, do not start a next checkpoint
     (there is none).
4. **Implement exactly that one checkpoint**, per its registry entry and
   the plan's own decision sections, following
   `CLAUDE.md`/`AGENTS.md`/`.github/copilot-instructions.md` for layer
   boundaries, migrations, and enum persistence rules where applicable.
5. Run the checkpoint's own conformance tests at minimum; run the full
   `scripts/workflow_fingerprint_test.py` (+ `_demo_test.py` if a
   real-repository check applies) whenever the checkpoint touches the
   identity subsystem or its wiring.
6. Commit the checkpoint's changes, carrying an exact
   `Workflow-Checkpoint: <id>` + `Workflow-Work-Item: workflow-v2-1-core`
   trailer. Once `docs/ai-workflow/WORKFLOW_STATE.json` exists, also call
   `workflow_state.complete_checkpoint(state, "workflow-v2-1-core",
   checkpoint_id, registry, now, repo_root=repo_root)` and persist the
   returned state in that same commit (`WFR-69`, `OPUS-R109-004`) — the
   state file becomes the sole writable record of checkpoint status from
   that point on; the trailer remains verification evidence, never a
   second source of truth. The named transition function is the sole
   call site for this write, never a direct `checkpoints[id].status =
   COMPLETE` assignment, so `WFR-69`'s own pre-completion obligation
   pre-flight cannot be bypassed by a compliant-looking direct write.
7. **Stop immediately** — never loop, never continue to the next
   checkpoint in the same invocation, unlike `/milestone-implement`.
   Report the checkpoint just completed and its commit SHA. Continuing
   requires the user to invoke this command again in a **fresh
   session**: a session that has just edited this command file must
   never act on its own stale in-memory copy of it, and a stopped
   command naturally ends the turn, so a fresh invocation is the only
   way forward.

This command is exempt from `D-Self-Governance`'s dual-mode branching
requirement — it drives exactly one work item whose
`governing_workflow_version` is permanently `"1"` by construction, so
there is no second version to branch on. It is not exempt from
conformance coverage (`WF8a-ii` owns a dedicated test asserting it stops
after exactly one checkpoint and never reads
`docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md`).
