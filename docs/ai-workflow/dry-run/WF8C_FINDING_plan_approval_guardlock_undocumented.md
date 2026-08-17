# WF8c finding — the plan's "complete global partial order" (six lock-shaped primitives) omits a real seventh: `PLAN_APPROVAL_MUTATION.guardlock`

**Status:** BLOCKING for item 372(h) (and therefore for `WF8c`'s own
completion), discovered while designing 372(h)'s completeness-arm
conformance test — before any test code was written. No remediation
attempted: editing `docs/ai-workflow/WORKFLOW_V2_PLAN.md` is protected,
approved plan content and is out of this implementation-only bootstrap
session's charter. Per explicit user direction, this session paused rather
than inventing the missing primitive's ordering position/edges or
implementing 372(h) against the incomplete list.

## Context

`bootstrap-workflow-v2 workflow-v2-1-core` selected `WF8c` as the next
checkpoint (all 17 prior registry checkpoints already `COMPLETE`). The
ledger (`docs/ai-workflow/registry/workflow-v2-1-core-wf8c-evidence.json`)
showed exactly 4 `WF8c`-owned items still unresolved: 347, 348, 352 (the
`WFR-63` plan-approval failure-atomicity transaction, not yet wired into
the permanent `/approve-review.md`) and 372 (the mutation guard's own
failure surface). The immediately preceding commit (`decd275`) had already
closed 372(a)-(g), leaving only 372(h) — the "global lock order is a
conformance obligation" arm — as `WF8c`'s last unresolved item. While
reading that arm's spec closely enough to build its three sub-arms
(completeness, graph, direction), the completeness arm's actual mechanism
turned out to have a real, demonstrable blind spot.

## Evidence

### 1. The plan's own completeness claim

`docs/ai-workflow/WORKFLOW_V2_PLAN.md`, section "**The complete global
partial order, in one place**" (introduced revision 75, `OPUS-R92-004`;
corrected revision 76, `OPUS-R93-002`, from four primitives to six and
from a forest to a DAG, after two consecutive rounds each found the
enumeration short — revision 75 itself omitted `WORKFLOW_STATE.lock`;
revision 76 then omitted both `D-Checkpoint-Ownership` checkpoint-claims
primitives):

> "This design has **six** lock-shaped primitives:" — followed by a
> numbered list (1) `PLAN_APPROVAL_MUTATION.lease`, (2)
> `WORKFLOW_STATE.lock`, (3) `WORKTREE_IDENTITY.lock`, (4)
> `identity-gap.lock`, (5) `checkpoint-claims/<sha256(work_item_id)>.lease`,
> (6) `checkpoint-claims/<sha256(work_item_id)>.guardlock`.

Primitive (6) is described as "the guard-mutation `flock` at
`…/checkpoint-claims/<sha256(work_item_id)>.guardlock` — stable,
never-unlinked, carrying no ownership at rest, held for a handful of
syscalls so that removing (5) is atomic with respect to the comparison
that authorized the removal." The section continues for several more
paragraphs discussing per-primitive properties of (3), (4) and (6)
specifically — through what is the end of this settled block, with no
revision marker later than 76 anywhere inside it. This is not a stale
fragment awaiting a pending edit; it is finished, current text.

No seventh primitive is listed anywhere in this section.

### 2. The live code has a seventh, real lock

`scripts/workflow_state.py:2147-2148`:

```python
PLAN_APPROVAL_GUARD_PATH = Path(".ai-review/runtime/PLAN_APPROVAL_MUTATION.lease")
PLAN_APPROVAL_GUARD_LOCK_PATH = Path(".ai-review/runtime/PLAN_APPROVAL_MUTATION.guardlock")
```

`scripts/workflow_state.py:2251-2268`:

```python
@contextlib.contextmanager
def _plan_approval_guard_lock(repo_root: Path, path: Path = PLAN_APPROVAL_GUARD_LOCK_PATH):
    """The stable object every guard *mutation* (acquire's reclaim branch)
    serializes on, mirroring `D-Checkpoint-Ownership`'s
    `guard_mutation_lock` (`OPUS-R83-001`): a compare-and-remove-then-
    publish must be one indivisible step, and locking the guard file
    itself would defeat that, since the guard's whole lifecycle is
    create-and-remove and two `flock`s on two different inodes that
    briefly shared one pathname are not serialized at all. Created once
    and never unlinked; released by the kernel on process death."""
    full_path = repo_root / path
    full_path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(full_path, os.O_RDWR | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        yield
    finally:
        os.close(fd)
```

This is a genuine `fcntl.flock(LOCK_EX)` acquisition on a stable,
never-unlinked file — structurally identical in kind to primitive (6),
just guarding mutation of primitive (1)'s guard family instead of
primitive (5)'s. Its own docstring says exactly this ("mirroring
`D-Checkpoint-Ownership`'s `guard_mutation_lock`").

It is live, not dead code — held on both mutation paths of primitive (1)'s
guard:

- `acquire_plan_approval_guard` (`scripts/workflow_state.py:2307`):
  `with _plan_approval_guard_lock(repo_root):` wrapping
  `_acquire_plan_approval_guard_locked`.
- `release_plan_approval_guard` (`scripts/workflow_state.py:2398`):
  `with _plan_approval_guard_lock(repo_root):` wrapping
  `_release_plan_approval_guard_locked`.

### 3. Provenance: when this was added

```
$ git log --oneline -S "PLAN_APPROVAL_GUARD_LOCK_PATH" -- scripts/workflow_state.py
eab14ec feat(wf8c): item (g) part 2 -- WFR-63 mutation/handoff guard, owner progress record, explicit-takeover contract
```

Commit `eab14ec` (2026-08-16 21:26:49 +0100) — a prior `WF8c` session
implementing registry scope clause (g) (`WFR-63`'s plan-approval
failure-atomicity transaction) — added this deliberately: "structurally
mirroring `D-Checkpoint-Ownership`'s already-built, already-tested
guard/takeover machinery (`guard_path`/`acquire_guard`/`release_guard`/
`take_over_claim`) rather than inventing a second design." That session
built real, tested library code toward items 347-353's guard/journal
machinery, landed well after revision 76 (which last touched the
six-primitive ordering list) and never folded back into that list. The
permanent-command wiring (`.claude/commands/approve-review.md`) is still
absent, which is why the ledger still carries 347/348/352 as unresolved —
but the guard-mutation lock primitive itself is real, live, and already
exercised by `workflow_state_test.py`.

### 4. Zero occurrences of the pathname in the plan text

```
$ grep -n 'PLAN_APPROVAL_MUTATION\.guardlock\|PLAN_APPROVAL_GUARD_LOCK' docs/ai-workflow/WORKFLOW_V2_PLAN.md
(no output)

$ grep -c 'PLAN_APPROVAL_MUTATION' docs/ai-workflow/WORKFLOW_V2_PLAN.md
10
```

All 10 occurrences of `PLAN_APPROVAL_MUTATION` in the plan name only
`PLAN_APPROVAL_MUTATION.lease` (primitive 1), or refer to it generically —
e.g. "the sibling `.ai-review/runtime/PLAN_APPROVAL_MUTATION.lease` guard"
(item 349's revision-58 extension). None name `.guardlock`.

### 5. Why item 372(h)'s completeness arm, built exactly as specified, would not catch this

Item 372, part (h) (`WORKFLOW_V2_PLAN.md`), states the completeness arm
as:

> "A completeness arm: every lock-shaped object this design defines is
> enumerated **mechanically** from the plan text — every `.lease`, `.lock`
> and `.guardlock` pathname the document names — and each is asserted to
> appear in `D-Approval-Commits`' single ordering list, failing and
> **naming the omitted pathname** if any does not."

The enumeration source is explicitly **the plan text itself** — every
`.lease`/`.lock`/`.guardlock` pathname the *document names*. Since
`PLAN_APPROVAL_MUTATION.guardlock` is never named anywhere in the document
(evidence #4), it is never a candidate the completeness arm iterates over,
so it can never be flagged as an omission from the ordering list. A test
built exactly to the item's literal wording would report the six-primitive
list "complete" while a real seventh lock stays permanently invisible —
the same class of defect this exact section already caught twice
(`WORKFLOW_STATE.lock` missing at revision 75; both checkpoint-claims
primitives missing at revision 76), recurring a third time, in a form the
item's own specified mechanism structurally cannot detect, because the
mechanism's source of truth is the (currently incomplete) prose rather
than the code the prose claims to describe completely.

## Recommended eventual approach (not decided here — for a plan revision to resolve)

Once a plan revision addresses this, item 372(h)'s completeness arm should
derive its lock-shaped-primitive universe from **live code** — e.g.
mechanically discovering every module-level lock-guard pathname constant
that feeds an `fcntl.flock`/`os.link`-guard acquisition in
`scripts/workflow_state.py` — and compare that code-derived set against
the plan's declared ordering universe in both directions, rather than
trusting the hand-maintained plan-text enumeration alone as the sole
source of truth. This mirrors the item's own historical trajectory: the
graph arm was already strengthened past its original wording once
(revision 77, subset check → equality) for exactly this reason ("a
hand-maintained list is the thing that went stale twice"). A prose-sourced
completeness arm has now demonstrably let a third occurrence of the same
class of defect through undetected.

This also means item 372(h) cannot correctly be implemented, as literally
worded, without either (a) a plan revision that documents the seventh
primitive and its ordering position, or (b) a plan revision that changes
the completeness arm's own specified mechanism to a code-derived one. Both
are plan-content decisions, not implementation choices this bootstrap
session can make unilaterally.

## What this session did NOT do

- Did not edit `docs/ai-workflow/WORKFLOW_V2_PLAN.md` (protected, approved
  plan content).
- Did not implement any part of item 372(h)'s conformance test.
- Did not mark item 372, or `WF8c`, complete.
- Did not modify `docs/ai-workflow/WORKFLOW_STATE.json` — no checkpoint
  transition occurred, so no state write was due.
- Did not commit anything.

## Next step

A plan revision is needed — adding `PLAN_APPROVAL_MUTATION.guardlock` as
the design's seventh lock-shaped primitive, with its correct position and
edges in the ordering graph determined by whoever authors that revision
(deliberately not invented here) — before item 372(h)'s completeness arm
can be built as anything more than a vacuous pass. A future
`bootstrap-workflow-v2 workflow-v2-1-core` session can resume `WF8c` once
that revision lands and clears the durability guard (step 2) again.
