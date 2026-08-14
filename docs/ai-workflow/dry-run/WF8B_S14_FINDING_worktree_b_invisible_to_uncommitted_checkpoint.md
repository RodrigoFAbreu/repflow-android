# WF8b finding — worktree B cannot observe worktree A's uncommitted `IN_PROGRESS` checkpoint, so S14 as literally specified cannot reach `verify_dirty_resume_safety` without either violating its own "no tracked-file edits" rule or an unauthorized extra file copy

**Status:** BLOCKING for `S14`, and transitively for `S15` (`WF8B_SCENARIOS.md`'s
execution-ordering note requires `S14` to complete, unchanged, before `S15`
reuses the same checkpoint 3). Discovered before any mutation, while setting
up `S14`'s temporary worktree — no `/milestone-implement v2-1-dry-run`
invocation was attempted from worktree B.

**Scenario:** `S14` — Dirty `IN_PROGRESS` resume attempted from a mismatched
worktree, refused (`WF8B_SCENARIOS.md`).

## Context

The "S14/S15 setup step" (`WF8B_SCENARIOS.md`) left `v2-1-dry-run`'s
checkpoint 3 (`S-CP3`) `IN_PROGRESS` with **no completion commit** — the
`WORKFLOW_STATE.json` write that set `current_checkpoint_id: "S-CP3"` and
`checkpoints["S-CP3"].status: "IN_PROGRESS"` is deliberately uncommitted,
sitting only in worktree A's working tree (confirmed: `git diff
docs/ai-workflow/WORKFLOW_STATE.json` against `HEAD` shows exactly this
change; `git show HEAD:docs/ai-workflow/WORKFLOW_STATE.json` has no `S-CP3`
key at all).

`S14`'s own text says to create a second real Git worktree ("worktree B")
and invoke `/milestone-implement v2-1-dry-run` from it, expecting
`verify_dirty_resume_safety` to raise `WorktreeIdentityMissingError`
(`S14a`) and then, after copying worktree A's
`.ai-review/runtime/WORKTREE_IDENTITY.json` into B, `WorktreeIdentityMismatchError`
(`S14b`).

## Root cause

`git worktree add` gives worktree B its own separate working directory that
reflects only **committed** history at the checkout ref — it cannot see
worktree A's uncommitted changes at all; this is ordinary Git worktree
semantics, not a repository-specific quirk. Verified directly: worktree B
was created at `HEAD` (`8375b64f9ad9ad44afe7574841a62457f5d83cea`), and
`git show 8375b64:docs/ai-workflow/WORKFLOW_STATE.json`'s `v2-1-dry-run`
entry has `current_checkpoint_id: null`, `checkpoints` containing only
`S-CP1`/`S-CP2` (both `COMPLETE`) — no `S-CP3` entry exists in committed
history yet.

Tracing `/milestone-implement`'s own `[2.1 step 1]` procedure against this:

- **1a** `implementing_entry_reachable` — unaffected, would return `True`
  from either worktree (checks the plan-approval commit, not checkpoint
  state).
- **1b** `select_next_checkpoint(work_item, registry)` — `work_item` is
  loaded from whatever `WORKFLOW_STATE.json` the invoking worktree's own
  `repo_root` resolves to (`scripts/workflow_state.py`'s `_git_identity`:
  `repo_root == worktree_root`, always the invoking worktree's own
  toplevel). From worktree B's own checked-out copy, `current_checkpoint_id`
  is `null` and `S-CP3` is absent from `checkpoints`, so
  `select_next_checkpoint` resolves `S-CP3` as a **fresh start** — not a
  resume of an already-`IN_PROGRESS` checkpoint. The command's own step 1b
  text is explicit that resume-vs-fresh-start is decided by exactly this:
  "If it equals `current_checkpoint_id` with status `IN_PROGRESS`, this is
  a resume; otherwise it is a fresh start."
- **1c** `verify_dirty_resume_safety` — is documented as **resume only**.
  A fresh-start classification at 1b never reaches it. `S14`'s named
  subject function is therefore never invoked by a literal, unmodified
  command run from worktree B in this repository's actual state.
- Continuing the (wrongly) fresh-start path would instead execute **1d**,
  which calls `transition_checkpoint_in_progress` and
  `write_worktree_identity` — real, durable writes to worktree B's own
  `WORKFLOW_STATE.json` and to a newly created
  `.ai-review/runtime/WORKTREE_IDENTITY.json` under worktree B. That
  directly contradicts `S14`'s own locked-in authorization (`WF8B_SCENARIOS.md`,
  "Decisions locked in for execution", item 2, and `S14`'s own point 2:
  "No mutation from that worktree ... No commits, no tracked-file edits, no
  writes of any kind outside worktree B" — writes *inside* worktree B's own
  tracked `WORKFLOW_STATE.json` are still tracked-file edits, and the
  broader intent throughout this document is plainly that worktree B never
  performs a real, durable state transition of any kind against this work
  item).

So the scenario as literally specified is self-contradictory against real
Git semantics: running the real command from worktree B either (a) never
reaches `verify_dirty_resume_safety` at all and instead performs a real,
forbidden fresh-start mutation, or (b) requires some additional step —
not named in `S14`'s own authorization text — to make worktree B's view of
`WORKFLOW_STATE.json` agree with worktree A's uncommitted `S-CP3
IN_PROGRESS` state before invoking the command, so that selection resolves
to a resume rather than a fresh start.

`S14`'s authorization text names copying exactly one file into worktree B
(`.ai-review/runtime/WORKTREE_IDENTITY.json`, for `S14b` only) and states
"No mutation from that worktree" as an absolute; it never names copying
`WORKFLOW_STATE.json` (needed for *both* `S14a` and `S14b` to reach a
resume classification at all, not just `S14b`), and copying it would itself
be an action beyond what's currently authorized.

## Why it matters

- `S14`'s stated purpose is "prove `verify_dirty_resume_safety`'s failure
  path stops cleanly rather than silently resuming or silently
  discarding." As specified, the scenario cannot exercise that function at
  all — it would either silently take a *different* code path (fresh
  start) or require an unauthorized extra step.
- `S15` depends on `S14` leaving checkpoint 3 `IN_PROGRESS`, byte-identical
  to the setup step's recorded before-state, "because `S14` is required to
  mutate nothing." If `S14` were run as literally written and happened to
  hit the fresh-start branch instead of the intended refusal, it would
  **not** leave checkpoint 3 untouched — it would silently transition it
  again (fresh-start bookkeeping is not idempotent-safe against an
  already-dirty checkpoint id) inside worktree B's own state, corrupting
  the premise `S15` is built on.
- This is exactly the risk `v2-1-dry-run-plan.md`'s own "Areas the reviewer
  should challenge" section flagged without resolving: "Whether checkpoint
  3 serving both S14 and S15 is genuinely safe. It rests entirely on S14
  making zero mutation" — this finding shows that property does not hold
  for a literal, unmodified command invocation from worktree B.

## Confirmation: repository and workflow state unchanged

- Worktree B was created (`git worktree add
  /tmp/.../wf8b-s14-worktree -b wf8b-s14-scratch HEAD`, at
  `8375b64f9ad9ad44afe7574841a62457f5d83cea`) and, once this gap was
  identified, removed again (`git worktree remove`, `git branch -d
  wf8b-s14-scratch`) before any command logic was run against it — only
  read-only inspection (`ls`, `git rev-parse --show-toplevel
  --git-common-dir HEAD`) was ever performed from worktree B.
- `git worktree list` after cleanup shows only the two real worktrees
  (main repo checkout, this worktree) — no leftover `wf8b-s14-scratch`.
- `git status --short` in worktree A: unchanged throughout this
  investigation except for this new finding file — still exactly
  `M docs/ai-workflow/WORKFLOW_STATE.json`, `?? docs/ai-workflow/dry-run/scratch/c.txt`,
  `?? docs/ai-workflow/dry-run/verify_review_content_id.py` (the latter two
  pre-existing and unrelated to this investigation, per `S1`'s own outcome
  note).
- `docs/ai-workflow/WORKFLOW_STATE.json`'s working-tree bytes: unchanged —
  `checkpoints["S-CP3"]` still `{"status": "IN_PROGRESS", "start_commit":
  "8375b64f9ad9ad44afe7574841a62457f5d83cea"}`, `current_checkpoint_id`
  still `"S-CP3"`, `state_revision` still `18`, matching the "S14/S15 setup
  step"'s own recorded before-state exactly.
- `.ai-review/runtime/WORKTREE_IDENTITY.json` in worktree A: unchanged
  (sha256 `8482417e1dcfa8ca85f00b64224fb9a79c4805bdd05727b4ac1897efd4b4515e`,
  recorded before worktree B was created and re-verified identical after
  its removal).

## Proposed resolution options (not decided here)

1. **Scope `S14` to `verify_dirty_resume_safety` directly.** Call
   `workflow_state.verify_dirty_resume_safety(repo_root=<worktree B>,
   work_item_id="v2-1-dry-run")` directly from worktree B — the exact
   function `S14` names as its subject — without replaying steps 1a/1b,
   which cannot correctly reach a resume classification from worktree B's
   own on-disk state. Requires no file copy beyond what `S14b` already
   authorizes (`WORKTREE_IDENTITY.json`); `S14a` needs no copy at all.
   Narrows "Command invoked" from "the full command" to "the resume-safety
   check the command would perform once it already knows this is a resume
   attempt" — an honest scoping, not a silent one.
2. **Mirror `WORKFLOW_STATE.json` into worktree B.** Copy worktree A's
   real, uncommitted `WORKFLOW_STATE.json` bytes into worktree B's working
   copy (never committed, never pushed back, removed with B at `S17`) so
   the full 1a–1c sequence naturally reaches the resume branch there too,
   then genuinely runs the complete step-1a/1b/1c sequence as one
   invocation. Requires widening `S14`'s locked-in authorization text
   (currently silent on this file).
3. **Redesign the setup step to commit the `IN_PROGRESS` transition.**
   Change "S14/S15 setup step" so checkpoint 3's `IN_PROGRESS` transition
   is committed (not left as pure working-tree state) before `S14` runs,
   making it visible to any worktree checked out at that commit. This
   changes what `S15` tests, too — "interrupted checkpoint recovery" would
   then be recovering from a *committed* `IN_PROGRESS` marker rather than
   `D3`'s general "uncommitted-and-interrupted" case, which is a materially
   different (and currently untested) shape.

No option has been chosen or implemented. `checkpoint S-CP3` remains
`IN_PROGRESS`, untouched, in worktree A, exactly as the setup step left it.

## Next step

Route this design question through the normal review channel per this
document's own established discipline (`WF8B_S1_FINDING...`,
`WF8B_S5_FINDING...`) — do not silently pick one of the three options above
and proceed. Once a direction is chosen and, if it changes `WF8B_SCENARIOS.md`'s
or `v2-1-dry-run-plan.md`'s own normative text, reviewed, `S14` can be
re-attempted from a fresh temporary worktree (the one used for this
investigation was already removed) and `S15` — which this finding also
blocks — can follow it.

---

# Follow-up analysis (second session, 2026-08-12) — classification, root cause, and proposed repair

Everything above is preserved as originally recorded. This section answers the
questions the first session deliberately left open, adds the mechanical
verification it did not perform, and proposes one repair with executable
evidence. **No option above was silently adopted; the repair proposed below is
a fourth option and is not applied — it requires the plan-revision route named
at the end of this section.**

## 1. Classification: not only a scenario-specification defect

`S14` is unexecutable as written — that much the first session established.
But the reason it is unexecutable is that **the guarantee `S14` was written to
test does not exist in the approved architecture**. Both statements are true,
and the second is the load-bearing one:

- **Scenario-specification defect (real, secondary).** `S14`'s procedure
  assumes a fresh worktree B will reach `verify_dirty_resume_safety`. It
  cannot, for ordinary Git reasons.
- **Workflow architecture defect (real, primary).** `D3`'s dirty-resume rule
  ("an `IN_PROGRESS` checkpoint's uncommitted work is worktree-local — resume
  requires matching local `WORKTREE_IDENTITY.json`, else stop and report",
  `WORKFLOW_V2_PLAN.md` revision 62) is conditioned entirely on the *invoking
  worktree's own view* of `WORKFLOW_STATE.json`. It therefore protects only
  the worktree that already knows it is resuming. Against the case it reads as
  protecting against — another worktree acting on the same interrupted
  checkpoint — it is inert, because that worktree never classifies the
  checkpoint as a resume in the first place.

The defect is not that `verify_dirty_resume_safety` is wrong. That function is
correct and its two error classes are both genuinely reachable. The defect is
that **nothing routes a foreign worktree into it.**

## 2. Verified: what `/milestone-implement v2-1-dry-run` would do in worktree B today

Established by calling the real `scripts/workflow_state.py` functions against
both views, read-only, with no repository mutation:

| view | `current_checkpoint_id` | `select_next_checkpoint` | step 1b classification | reaches 1c? | 1d executes? |
| --- | --- | --- | --- | --- | --- |
| worktree B (committed `HEAD`) | `null` | `S-CP3` | **FRESH START** | no — 1c is resume-only | **yes** |
| worktree A (working tree) | `S-CP3` | `S-CP3` | RESUME | yes | no |

So the answer to "what would the command do in B today" is concrete: **it would
select `S-CP3` and start it as a fresh checkpoint.** Step 1d would write
`checkpoints["S-CP3"] = {"status": "IN_PROGRESS", start_commit: <B's HEAD>}`
into worktree B's own tracked `WORKFLOW_STATE.json` and create
`.ai-review/runtime/WORKTREE_IDENTITY.json` under B; step 1e would implement
the checkpoint; step 1f would commit it with `Workflow-Checkpoint: S-CP3` +
`Workflow-Work-Item: v2-1-dry-run` trailers and flip the same checkpoint to
`COMPLETE` — while worktree A still holds it `IN_PROGRESS` with uncommitted
work. Two worktrees, one checkpoint, both believing they own it, no refusal at
any point. Reproduced end to end in `wf8b-s14-repro/pass1.py`, section A.

## 3. Exact root cause: the ownership fact is never durable

`/milestone-implement`'s `[2.1 step 1]` writes the `IN_PROGRESS` transition at
**1d** and does not commit it. The next commit that touches the checkpoint is
**1f**, which flips the same checkpoint to `COMPLETE` in the same write. The
`IN_PROGRESS` state therefore exists *only* as working-tree bytes in the
worktree that produced it, for the whole interval it is meant to protect —
which is exactly the interval in which a second worktree can be created.

Verified against this repository's own history: **no commit reachable from
`HEAD` has ever carried a `v2-1-dry-run` checkpoint in `IN_PROGRESS`.** The one
`IN_PROGRESS` marker that *is* durable — `workflow-v2-1-core:WF8b` — became so
only incidentally, first appearing in commit `535d4fb`, an unrelated
`GPT-R67-001` fix commit that happened to include the whole
`WORKFLOW_STATE.json` file. Durability of the ownership fact is an accident of
what other commands commit, never a contract.

`WORKTREE_IDENTITY.json` cannot close this: it is gitignored and worktree-local
by design (`.gitignore:1`), so a fresh worktree has no copy of it and, more to
the point, is never asked for one — the identity check is downstream of a
classification that already went the wrong way.

## 4. Yes — a real wrong-worktree guarantee requires durable/shared authority

The guarantee "worktree B cannot treat a checkpoint interrupted in worktree A
as a safe fresh start" is only decidable if B can *discover* A's claim. Three
candidate authorities:

1. **Worktree-local uncommitted `WORKFLOW_STATE.json`** — today's source. Not
   shared; provably insufficient (section 2).
2. **Committed history** — durable and shared, but requires committing every
   `IN_PROGRESS` transition. That is a large change: an extra commit per
   checkpoint start, new interactions with `D-Approval-Commits`' commit-shape
   rules, the "no protected path is dirty" gate, and bundle/round identity. It
   also still fails when the second worktree branched before the transition
   commit existed.
3. **The shared Git common directory** — `git rev-parse --git-common-dir`
   resolves to the *same* absolute path from every linked worktree of a
   repository (verified for this repository: both real worktrees resolve
   `/home/rodrigo/Workspace/repflow-android/.git`), while being invisible to
   `git status`, to path classification, to `review_content_id`/`bundle_id`,
   and to every bundle. It is the smallest authority that is genuinely shared
   and genuinely non-committed — matching what the fact actually is: local,
   repository-scoped, and never part of the reviewed content.

Option 3 is the one proposed below. Its honest limit, measured rather than
assumed (`pass2.py` E8): a **separate clone** has a different common directory
and sees no claim — but it equally cannot see the uncommitted work, so nothing
is regressed; the multi-worktree case is the one `S14` is about and the one
this closes.

## 5. Proposed minimal generic fix (Option 4 — supersedes options 1–3 above)

**Record the claim, not the work, in the shared common directory; keep every
existing refusal exactly where it is.**

- **New shared record**: one file per work item at
  `$(git rev-parse --git-common-dir)/ai-workflow/checkpoint-claims/<sha256(work_item_id)>.json`,
  carrying `{schema_version, work_item_id, checkpoint_id, repo_root,
  git_common_dir, worktree_root, claimed_at}`. Keyed per work item, so
  `OPUS-R10-008`'s interleaving property is preserved unchanged. Named by
  digest so no `work_item_id` value can escape the directory (the same
  "token, not a path" discipline `D-Bundle-Manifest`'s `bundles/<token>`
  already uses).
- **Published atomically** with `O_CREAT | O_EXCL`, so the filesystem decides
  the winner and a stale read cannot produce a lost update.
- **Step 1b changes, and only step 1b**: the invocation is resume-gated if the
  local state says `IN_PROGRESS` **or** a shared claim exists for this work
  item. `select_next_checkpoint` itself is untouched — it stays pure and
  deterministic (missing-test item 28).
- **Step 1c is unchanged and is still what refuses.** A foreign worktree now
  reaches it and raises exactly the two existing, distinguishable errors:
  `WorktreeIdentityMissingError` (`S14a` — no local identity file) and
  `WorktreeIdentityMismatchError` (`S14b` — A's identity file copied in). No
  new refusal class is needed on the S14 path.
- **Step 1d claims before it writes state.** The ordering is load-bearing, not
  stylistic: `pass4.py` O1 runs both orderings and shows the state-first
  variant reopens the hole exactly.
- **Step 1f releases the claim** alongside `complete_checkpoint`; the explicit
  discard branch of interrupted-checkpoint recovery (`S15`) releases it too.
- **Three new error classes**, for cases that are genuinely new rather than
  routed into existing ones: `CheckpointOwnedByOtherWorktreeError` (claiming
  or releasing another worktree's claim), `CheckpointOwnershipStateMismatchError`
  (this worktree's own claim cannot be reconciled with its own state file —
  reported, never guessed), `CheckpointOwnershipUnavailableError` (the record
  is unreadable or unpublishable — fails closed rather than proceeding
  unprotected).
- **Exactly one self-healing case**, deliberately narrow: a self-owned claim
  whose checkpoint is durably `COMPLETE` (a crash between the checkpoint commit
  and the release) is released automatically. Every other disagreement refuses
  and reports.

What this does **not** do: it does not change `select_next_checkpoint`, does
not commit anything new, does not touch `WORKTREE_IDENTITY.json`'s schema or
either of its error classes, does not change any protected path's content
hash, and adds no new phase or gate.

## 6. Stress evidence

Five bounded adversarial passes against real Git worktrees, using this
repository's own `scripts/workflow_state.py` functions unmodified. Runnable:
`wf8b-s14-repro/pass{1..5}.py` (stdlib only; each creates and destroys its own
throwaway repository under `/tmp`).

| pass | scope | result |
| --- | --- | --- |
| 1 | defect reproduction + the seven required properties | 16/16 green |
| 2 | crash windows, corruption, unwritable record, two-item interleaving, clone, relocation | 20/20 green — **found 1 Blocking defect in the draft design**, fixed |
| 3 | concurrency, `git gc`/`worktree prune`, older-commit worktree, third worktree, non-regression | 12/12 green — **found 1 Important defect (lost update), fixed** |
| 4 | 1d write ordering (both arms), nested worktree, symlinked path, discard/restart, repeated refusal | 15/15 green — no new Blocking/Important finding |
| 5 | orphaned claim, `git worktree move`, exhausted registry, real-repository read-only checks | 14/14 green — no new Blocking/Important finding |

**77 checks green.** The two defects found were in the draft design and were
fixed and re-verified with passes 1–3 re-run as regressions:

1. **Blocking (pass 2, E6)** — the stale-claim auto-release branch triggered
   whenever the claimed checkpoint was `COMPLETE`, *including* when a different
   checkpoint was locally `IN_PROGRESS`. It would have silently released a live
   claim and returned "fresh start". Fixed: the auto-release is now reachable
   only when nothing at all is locally `IN_PROGRESS`; every other disagreement
   raises `CheckpointOwnershipStateMismatchError`.
2. **Important (pass 3, C1)** — a single shared JSON document read-modify-
   written was last-writer-wins, so a stale pre-read could overwrite the
   winner's claim. Fixed: per-work-item files published with `O_CREAT | O_EXCL`.

The seven properties this finding was required to test all hold (`pass1.py`,
section B): A owns a dirty interrupted checkpoint; B has only committed state;
B cannot fresh-start it; `S14a`/`S14b` remain distinguishable by error class;
both refusals mutate nothing (byte-compared over A's state file, A's identity
file, A's scratch file, both HEADs, both `git status` outputs, B's state file
and the claim record); A subsequently performs `S15` recovery and completes the
checkpoint; and **no copy of `WORKFLOW_STATE.json` into B is required at any
point** — B's copy stays byte-identical to its own committed bytes throughout.

## 7. Where the repair belongs, and the review path it requires

**Both, in this order.**

1. **`workflow-v2-1-core` plan revision (63) — required first.** The fix
   introduces a design contract that Revision 62 does not contain: `D3` and
   `D-Selection` rule 1 currently condition everything on the invoking
   worktree's own state file and say nothing about cross-worktree ownership.
   This is therefore *new design*, not the generalization-of-approved-design
   case that authorized landing `GPT-R67-001` directly as WF8b implementation
   work in commit `535d4fb` (whose own message states the distinction: "was
   already fully specified and approved through revision 58; only its
   generalization ... was missing"). `docs/ai-workflow/WORKFLOW_V2_PLAN.md` is
   a plan-stage protected path under `workflow-v2-1-core`'s **CURRENT**
   plan approval, so editing it stales that approval and must go through the
   normal route: revise the plan → `AWAITING_EXTERNAL_PLAN_REVIEW` → external
   plan review → `/apply-plan-review` → `AWAITING_PLAN_APPROVAL` →
   `/approve-review plan workflow-v2-1-core`. This is the same
   self-discovered-continued-`WF8b`-scope route revisions 16, 21, 22 and 27
   already took; `workflow-v2-1-core` is `governing_workflow_version: "1"`, so
   the two-stage local/manual-external protocol does not apply to it.
2. **`workflow-v2-1-core` `WF8b` implementation — after that approval.**
   `scripts/` and `.claude/commands/` are implementation-stage protected
   *prefixes* (`workflow-v2-1-core-artifacts.json`), the same surface
   `535d4fb` edited as ordinary WF8b checkpoint work. The concrete edits are
   the ones listed in section 5, plus hermetic tests mirroring the five passes.
3. **`v2-1-dry-run` — scenario text only, no plan revision.**
   `WF8B_SCENARIOS.md` is not a protected path of any current approval; its
   `S14` section needs its mechanism sentence corrected (B reaches the refusal
   via the shared claim, not via a state-file copy). **`v2-1-dry-run-plan.md`
   — Revision 5, plan approval `CURRENT` — does not need to change.** Its only
   normative statements about this scenario are that checkpoint 3 is started in
   worktree A and left `IN_PROGRESS`; that it is the subject of `S14`'s
   missing-identity and mismatched-identity sub-cases and then, unchanged, of
   `S15`; and that the same checkpoint legitimately serves both because `S14`
   makes zero mutation. Every one of those remains true, verbatim, under the
   proposed fix — checked line by line against lines 41–58 and 499–536. **No
   `v2-1-dry-run` plan revision, and no re-approval of Revision 5, is
   required.**

## 8. Effect on the current `S-CP3` state: none

Preserved exactly, and re-verified at the end of this session
(`pass5.py` R4c–R4f): `checkpoints["S-CP3"] == {"status": "IN_PROGRESS",
"start_commit": "8375b64f9ad9ad44afe7574841a62457f5d83cea"}`,
`current_checkpoint_id == "S-CP3"`, `state_revision == 18`, still uncommitted;
`docs/ai-workflow/dry-run/scratch/c.txt` untouched; the only tracked files
modified in worktree A remain `WORKFLOW_STATE.json` and `WF8B_SCENARIOS.md`,
and every untracked addition is WF8b dry-run evidence under
`docs/ai-workflow/dry-run/`. No claim record was written anywhere in the real
repository — the proposed mechanism exists only in the throwaway fixtures the
passes create and destroy. `S14` and `S15` both remain unexecuted and blocked
on step 1 above.

**This verdict no longer holds — superseded, third session (2026-08-14).**
`workflow-v2-1-core`'s own Revision 80 plan-approval commit
(`8f8d878c0985da96d9b462703b6c88ec5b3ab07b`, `docs(workflow-v2): record
Revision 80 plan approval`) stages the *whole* working tree, exactly as
`535d4fbce423523eed09fbf0a3b7d75ca81e8b66` did for `workflow-v2-1-core:WF8b`
itself (`D-Checkpoint-Ownership`'s own "checkout can supply `IN_PROGRESS`"
observation) — and `v2-1-dry-run:S-CP3`'s dirty `IN_PROGRESS` bytes rode
along with it. Verified directly against real Git objects, not inferred:

```
$ git show 8375b64:docs/ai-workflow/WORKFLOW_STATE.json | jq '.work_items["v2-1-dry-run"] | {current_checkpoint_id, has_S_CP3: (.checkpoints | has("S-CP3"))}'
{"current_checkpoint_id": null, "has_S_CP3": false}

$ git show 8f8d878:docs/ai-workflow/WORKFLOW_STATE.json | jq '.work_items["v2-1-dry-run"] | {current_checkpoint_id, "S-CP3": .checkpoints["S-CP3"]}'
{"current_checkpoint_id": "S-CP3", "S-CP3": {"status": "IN_PROGRESS", "start_commit": "8375b64f9ad9ad44afe7574841a62457f5d83cea"}}

$ git diff HEAD -- docs/ai-workflow/WORKFLOW_STATE.json | wc -l
0
```

`8f8d878` is `8375b64`'s direct child (`git log --oneline` confirms no
intervening commit touches `WORKFLOW_STATE.json`), and the working tree is
now byte-identical to `HEAD` for this file — the file this section's own
"Confirmation" block above recorded as `M` (modified, uncommitted) is no
longer modified at all. This was an ordinary side effect of the Bootstrap
plan-approval procedure (`D-Approval-Commits`), which stages the resolved
plan-stage commit member set for `workflow-v2-1-core` and, per the
"checkout can supply `IN_PROGRESS`" mechanism this very finding's design
proposal already names, necessarily swept `v2-1-dry-run`'s own in-flight
`WORKFLOW_STATE.json` delta along with it — no `v2-1-dry-run` command was
ever invoked to produce this.

**Consequence for the setup step's own premise.** The "S14/S15 setup step"
in `WF8B_SCENARIOS.md` frames checkpoint 3's `IN_PROGRESS` transition as
"deliberately uncommitted... a working-tree-only write, never committed by
design at this step" — that framing is now false for the live repository.
Section 5's proposed `D-Checkpoint-Ownership` mechanism (approved as plan
revision 80, `docs/ai-workflow/WORKFLOW_V2_PLAN.md`) states the consequence
exactly, in its own "reduction over multiple observations" rule: a
checkpoint observed `IN_PROGRESS` at **any** commit in the origination
reference (`git rev-list --all --full-history -- <state_rel_path>`) refuses
automatic adoption **permanently**, regardless of what any later commit
records. `v2-1-dry-run:S-CP3` is now such a commit. Once
`D-Checkpoint-Ownership` is implemented for real, the setup step's "adopt
the claim, in worktree A" action will hit `CheckpointOriginationUnprovableError`
rather than adopting automatically, and will require the same explicit,
user-authorized takeover the plan already documents as the general
escape — exactly the cost the plan's own text says "accumulates" as
whole-tree checkpoint commits sweep other work items' in-flight
transitions, now demonstrated against a second, independent
`(work_item_id, checkpoint_id)` pair in this same repository (the first
being `workflow-v2-1-core`/`WF8b` itself).

**Nothing is broken and nothing needs to be undone.** `S-CP3` is still
`IN_PROGRESS`, still un-*completed*, and worktree A still holds the only
copy of its uncommitted deliverable (`scratch/c.txt`) — S15's premise
("checkpoint 3 interrupted, recoverable") is intact. Only the *mechanism*
by which S14/S15's setup will re-establish protection for it changes: an
explicit takeover in place of automatic adoption, once
`D-Checkpoint-Ownership` is implemented. This is recorded here, before more
commits accumulate and further obscure which commit first introduced it,
so the implementer does not have to re-derive it under time pressure.
