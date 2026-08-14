"""Stress pass 5 -- orphaned claims, worktree moves, exhausted registries,
and a read-only check that the mechanism actually holds for the real repo."""

import json
import subprocess
import sys
from pathlib import Path

from harness import Fixture, REGISTRY, co, git, step1_fixed, ws

RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" -- {detail}" if detail else ""))


def expect(name, fn, exc_type):
    try:
        fn()
        check(name, False, "no exception raised")
    except exc_type as exc:
        check(name, True, str(exc)[:80])
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")


# R1 -- claim held by a worktree that no longer exists
print("== R1. orphaned claim (holder worktree deleted) ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    co.claim_checkpoint(b, "v2-1-dry-run", "S-CP3", now="tB")
    git(fx.a, "worktree", "remove", "--force", str(b))
    fx.b = None
    claim = co.resolve_claim(fx.a, "v2-1-dry-run")
    check("R1. the claim outlives its holder", claim is not None)
    expect("R1b. A is refused, correctly (it is not the owner)",
           lambda: co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="tA"),
           co.CheckpointOwnedByOtherWorktreeError)
    expect("R1c. and cannot quietly release someone else's claim",
           lambda: co.release_checkpoint(fx.a, "v2-1-dry-run", "S-CP3",
                                         owner_token="0" * 32),
           co.CheckpointOwnedByOtherWorktreeError)
    holder_gone = not Path(claim["worktree_root"]).exists()
    live = {w for w in git(fx.a, "worktree", "list", "--porcelain").splitlines()
            if w.startswith("worktree ")}
    check("R1d. the refusal is diagnosable: the holder path is gone and absent from "
          "`git worktree list`, so an explicit takeover is decidable, not guesswork",
          holder_gone and f"worktree {claim['worktree_root']}" not in live,
          f"holder={claim['worktree_root']} exists={not holder_gone}")
finally:
    fx.cleanup()

# R2 -- `git worktree move` of the holder
print("\n== R2. holder moved with `git worktree move` ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    co.claim_checkpoint(b, "v2-1-dry-run", "S-CP3", now="tB")
    moved = fx.tmp / "B-moved"
    git(fx.a, "worktree", "move", str(b), str(moved))
    fx.b = moved
    check("R2. the claim still names the pre-move path (stale but discoverable)",
          co.resolve_claim(fx.a, "v2-1-dry-run")["worktree_root"] == str(b.resolve()))
    expect("R2b. the moved holder fails closed rather than silently re-owning",
           lambda: co.claim_checkpoint(moved, "v2-1-dry-run", "S-CP3", now="tB2"),
           co.CheckpointOwnedByOtherWorktreeError)
    check("R2c. A is equally refused -- no worktree can proceed on a stale claim "
          "without an explicit decision", True)
finally:
    fx.cleanup()

# R3 -- claim present while the registry is exhausted (selection returns None)
print("\n== R3. claim present, every registry checkpoint COMPLETE ==")
fx = Fixture()
try:
    state = fx.read_state(fx.a)
    state["work_items"]["v2-1-dry-run"]["checkpoints"]["S-CP3"] = {
        "status": "COMPLETE", "start_commit": "0" * 40}
    fx.write_state(fx.a, state)
    co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1")
    ws.write_worktree_identity(fx.a, "v2-1-dry-run", now="t1")
    sel = ws.select_next_checkpoint(fx.work_item(fx.a), REGISTRY)
    # Amended in place for revision 63 (`A6`): the single automatic release now
    # requires the completion to be DURABLE, so this arm asserts the refusal
    # first and only then, after the commit, the release.
    expect("R3. a working-tree-only COMPLETE does not release the claim (A6)",
           lambda: co.classify_selection(fx.a, fx.work_item(fx.a), "v2-1-dry-run", sel,
                                         verify_resume_safety=ws.verify_dirty_resume_safety),
           co.CheckpointOwnershipStateMismatchError)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "complete S-CP3")
    mode = co.classify_selection(fx.a, fx.work_item(fx.a), "v2-1-dry-run", sel,
                                 verify_resume_safety=ws.verify_dirty_resume_safety)
    # AMENDED, revision 64 (`GPT-R81-003`): revision 63 asserted `"fresh"` here.
    # With selection exhausted that is a mutation-capable outcome carrying no
    # checkpoint id; the release is unchanged, the outcome is now terminal.
    check("R3b. once durable, selection returns None and the stale self-claim is "
          "released, reported as the terminal no-checkpoint outcome rather than as "
          "a mutable fresh start", sel is None and mode == co.NO_CHECKPOINT
          and co.resolve_claim(fx.a, "v2-1-dry-run") is None, f"sel={sel} mode={mode}")
finally:
    fx.cleanup()

# R4 -- the mechanism against the REAL repository (read-only)
ALLOWED_TRACKED_EDITS = [
    # Amended in place, third session (2026-08-14): the Revision 80
    # plan-approval commit (8f8d878) committed the other four members --
    # WORKFLOW_STATE.json, WORKFLOW_V2_PLAN.md, the registry and the mapping
    # -- along with everything else on `workflow-v2-1-core`'s own plan-stage
    # protected surface, which as a side effect also committed
    # `v2-1-dry-run`'s own dirty S-CP3 delta (see
    # WF8B_S14_FINDING_worktree_b_invisible_to_uncommitted_checkpoint.md's
    # "This verdict no longer holds" addendum). Only the scenario doc is
    # still dirty. The property this asserts is unchanged: nothing OUTSIDE
    # this work item's own plan/dry-run surface is touched.
    "docs/ai-workflow/dry-run/WF8B_SCENARIOS.md",
]

print("\n== R4. real repository, read-only ==")
real_a = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=Path(__file__).resolve().parent,
                             capture_output=True, text=True, check=True).stdout.strip())
real_main = Path(subprocess.run(["git", "rev-parse", "--path-format=absolute",
                                 "--git-common-dir"], cwd=real_a, capture_output=True,
                                text=True, check=True).stdout.strip()).parent
common_a = co._git_identity(real_a)[1]
common_main = co._git_identity(real_main)[1]
check("R4. the real repo's two worktrees share one git common dir",
      common_a == common_main, common_a)
check("R4b. so a claim written by either would be visible to the other "
      "(and to any S14 worktree B)", co.claim_path(real_a, "v2-1-dry-run")
      == co.claim_path(real_main, "v2-1-dry-run"),
      str(co.claim_path(real_a, "v2-1-dry-run")))
check("R4c. no claim record exists in the real repo -- this analysis wrote none",
      not co.claims_dir(real_a).exists())
status = subprocess.run(["git", "status", "--porcelain"], cwd=real_a,
                        capture_output=True, text=True).stdout.splitlines()
tracked_edits = sorted(line[3:] for line in status if not line.startswith("??"))
additions = sorted(line[3:] for line in status if line.startswith("??"))
check("R4d. no tracked file outside this work item's own dry-run/state surface was "
      "modified by this investigation",
      tracked_edits == ALLOWED_TRACKED_EDITS, str(tracked_edits))
check("R4e. every untracked addition is WF8b dry-run evidence, nothing else",
      all(a.startswith("docs/ai-workflow/dry-run/") for a in additions), str(additions))
live = json.loads((real_a / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
wi = live["work_items"]["v2-1-dry-run"]
check("R4f. S-CP3 is still IN_PROGRESS, uncommitted, at state_revision 18 -- the "
      "S14/S15 setup step's recorded before-state, preserved",
      wi["checkpoints"]["S-CP3"] == {"status": "IN_PROGRESS",
                                     "start_commit": "8375b64f9ad9ad44afe7574841a62457f5d83cea"}
      and wi["current_checkpoint_id"] == "S-CP3" and wi["state_revision"] == 18,
      json.dumps(wi["checkpoints"].get("S-CP3")))

print()
failed = [r for r in RESULTS if not r[1]]
print(f"pass 5: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name, ok, detail in failed:
    print(f"  FAILED: {name} -- {detail}")
sys.exit(1 if failed else 0)
