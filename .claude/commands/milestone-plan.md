---
description: Plan the next incomplete milestone/checkpoint and stop for external plan review.
---

Enter the `PLANNING` state of `docs/ai-workflow/MILESTONE_WORKFLOW.md`.

Optional argument: `$ARGUMENTS` may name a base commit SHA to diff from. If
omitted, use the milestone's starting commit (the completion commit of the
previous milestone, from `docs/ACTIVE_MILESTONE.md`/`git log`).

0. **Dual-mode branch** (Workflow v2.1, WF1b): read
   `docs/ai-workflow/WORKFLOW_CONFIG.json` (missing/corrupt before
   activation defaults to `default_workflow_version: "1"`) and
   `docs/ai-workflow/WORKFLOW_STATE.json` (missing entirely is equivalent
   to "no work items yet"). If the milestone identified in step 1 already
   has a `work_items[id]` entry, its own `governing_workflow_version`
   (fixed at that entry's creation) governs this run, never the current
   config default. Otherwise this is a fresh milestone and the config's
   *current* `default_workflow_version` governs it.
   - **`governing_workflow_version: "1"`** (today, always, until
     `WF-Activate` runs): steps 1-7 below execute exactly as written, with
     no `WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json` reads or writes
     beyond the one just performed — this branch is v1-inert by
     construction, verified by a golden-output test (`WF8a-ii`).
   - **`governing_workflow_version: "2.1"`**: steps 1-7 below still
     execute (the plan-production/self-review/gate mechanics are
     version-independent), plus the additional sub-steps marked **[2.1]**
     interleaved below.
1. Inspect Git state (`git status --short`, `git log --oneline -10`) and read
   `docs/ACTIVE_MILESTONE.md` and `docs/ROADMAP.md` to identify the next
   incomplete milestone/checkpoint.
   - **[2.1]** Derive a `work_item_id` slug from the milestone (matching
     `^[a-z0-9][a-z0-9_-]{0,63}$`, e.g. `milestone-9`), or reuse the
     existing entry's id if resuming a milestone already present in
     `work_items`. Call `workflow_state.route_work_item(...)` (D1's
     create-or-resume routing: creates a fresh `work_items[id]` entry
     fixing `governing_workflow_version` from the config default at this
     moment, or advances `plan_revision`/`state_revision` on an existing
     non-terminal entry; refuses a terminal-phase id reuse) and persist
     the returned state to `docs/ai-workflow/WORKFLOW_STATE.json`.
2. Load only documentation relevant to that milestone: the linked execution
   guide, the reference guide only for unresolved detail, and any of
   `docs/DOMAIN_GLOSSARY.md`, `docs/UX_FLOWS.md`,
   `docs/adr/0002-offline-first-local-database-source-of-truth.md`,
   `docs/adr/0003-layered-modular-architecture.md`,
   `docs/TECHNICAL_DECISIONS.md` only as the task actually touches those
   areas. Do not read `docs/agent-context/` or completed-milestone docs
   unless a specific historical decision is unresolved.
3. Produce or update the execution/reference plan for this milestone
   (checkpoints, files touched, tests to add, migration needs if any).
   - **[2.1]** Build the checkpoint list as `{id, name, depends_on,
     complexity, session_target}` entries and the requirement map as
     `{requirement_id: {description, checkpoint_ids}}`, then call
     `workflow_state.generate_registry(...)`/`generate_mapping(...)`
     (validates D-Selection's topological-order rule and D3's
     bidirectional coverage rule at generation time, never left to be
     discovered at review time) and
     `workflow_state.write_registry_and_mapping(...)` to write
     `docs/ai-workflow/registry/<work_item_id>-registry.json` and
     `docs/ai-workflow/requirements/<work_item_id>-mapping.json` — the
     sole writer either file should ever have (D-Registry). Embed
     `workflow_state.render_registry_markdown(registry)`'s output as the
     plan document's own generated, human-readable checkpoint table —
     never hand-edited, never itself hashed.
4. Enter `SELF_REVIEWING_PLAN`: critically check the plan for missing
   requirements, migration risk, usability gaps, unnecessary complexity, and
   missing tests. Revise the plan in place — do not write a separate
   self-review journal.
5. Check the plan against every "Open decision" row in
   `docs/TECHNICAL_DECISIONS.md` it touches — flag any it would silently
   finalize instead of deciding for the user.
6. Enter `AWAITING_EXTERNAL_PLAN_REVIEW`:
   - write/refresh `.ai-review/current/PLAN.md` with the actual plan;
   - write `.ai-review/current/CONTEXT_FILES.txt` listing only the docs a
     reviewer genuinely needs beyond the plan itself;
   - write `.ai-review/current/REVIEW_REQUEST.md` per the format in
     `docs/ai-workflow/REVIEW_PROTOCOL.md` (stage: `plan`);
   - run `./scripts/prepare-ai-review.sh <base-sha> plan`.
7. Report the bundle location and **stop**. Do not implement anything. This
   is a hard gate — wait for `.ai-review/feedback/REVIEW_FEEDBACK.md`.
