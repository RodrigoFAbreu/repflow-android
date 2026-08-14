"""Stress pass 2 -- adversarial edges against the proposed fix itself."""

import json
import os
import shutil
import stat
import subprocess
import sys

from harness import Fixture, co, git, step1_fixed, ws

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


# E1 -- crash between claim and the state write: claim exists, no local entry
print("== E1. crash between claim and state write (claim, no local checkpoint entry) ==")
fx = Fixture()
try:
    co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1")
    ws.write_worktree_identity(fx.a, "v2-1-dry-run", now="t1")  # A's own identity exists
    # Amended in place for revision 63 (`A3`): refusing here permanently locked
    # the LEGITIMATE OWNER out of its own claim. The owner now continues its own
    # interrupted acquisition; every other worktree is still refused (E1b).
    check("E1. the owner continues its own interrupted acquisition, never guesses",
          step1_fixed(fx, fx.a)["mode"] == "continue_claim")
    b = fx.add_worktree_b()
    expect("E1b. and B still cannot fresh-start it either",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

# E2 -- crash between the checkpoint commit and the release
print("\n== E2. crash between checkpoint commit and claim release ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    state = ws.complete_checkpoint(fx.read_state(fx.a), "v2-1-dry-run", "S-CP3",
                                   {"checkpoints": [{"id": "S-CP1"}, {"id": "S-CP2"}, {"id": "S-CP3"}]},
                                   now="t9")
    fx.write_state(fx.a, state)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "complete S-CP3")   # committed, but claim NOT released
    check("E2. stale self-claim survives the crash", co.resolve_claim(fx.a, "v2-1-dry-run") is not None)
    wi = fx.work_item(fx.a)
    mode = co.classify_selection(fx.a, wi, "v2-1-dry-run", None,
                                 verify_resume_safety=ws.verify_dirty_resume_safety)
    # AMENDED, revision 64 (`GPT-R81-003`): the release is unchanged; the outcome
    # reported alongside it is the terminal one, because selection is exhausted
    # here and a `FRESH` carrying no checkpoint id must never reach step 1d.
    check("E2b. a durably COMPLETE checkpoint auto-releases its own stale claim",
          mode == co.NO_CHECKPOINT and co.resolve_claim(fx.a, "v2-1-dry-run") is None, mode)
    b = fx.add_worktree_b()
    check("E2c. B is then free again (no permanent false lock)",
          co.resolve_claim(b, "v2-1-dry-run") is None)
finally:
    fx.cleanup()

# E3 -- corrupt ownership record must fail closed everywhere
print("\n== E3. corrupt / unsupported ownership record ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    b = fx.add_worktree_b()
    co.claim_path(fx.a, "v2-1-dry-run").write_text("{not json")
    expect("E3. corrupt record fails closed in the owning worktree",
           lambda: step1_fixed(fx, fx.a), co.CheckpointOwnershipUnavailableError)
    expect("E3b. corrupt record fails closed in the foreign worktree too (no fresh start)",
           lambda: step1_fixed(fx, b), co.CheckpointOwnershipUnavailableError)
    co.claim_path(fx.a, "v2-1-dry-run").write_text(json.dumps({"schema_version": 99}))
    expect("E3c. unsupported schema_version fails closed",
           lambda: step1_fixed(fx, b), co.CheckpointOwnershipUnavailableError)
finally:
    fx.cleanup()

# E4 -- unwritable common dir: a claim that cannot be published must not start work
print("\n== E4. shared claim cannot be published ==")
fx = Fixture()
try:
    cdir = co.claims_dir(fx.a)
    cdir.mkdir(parents=True, exist_ok=True)
    mode0 = cdir.stat().st_mode
    os.chmod(cdir, stat.S_IRUSR | stat.S_IXUSR)
    expect("E4. an unpublishable claim refuses to start the checkpoint",
           lambda: co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1"),
           co.CheckpointOwnershipUnavailableError)
    os.chmod(cdir, mode0)
    check("E4b. nothing was written to A's WORKFLOW_STATE.json by the failed claim",
          "S-CP3" not in (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
finally:
    fx.cleanup()

# E5 -- two work items interleaved (OPUS-R10-008's property must survive)
print("\n== E5. two work items with interleaved IN_PROGRESS work ==")
fx = Fixture()
try:
    co.claim_checkpoint(fx.a, "item-one", "X1", now="t1")
    b = fx.add_worktree_b()
    co.claim_checkpoint(b, "item-two", "Y1", now="t2")
    check("E5. per-work-item claims do not disturb each other",
          co.resolve_claim(fx.a, "item-one")["worktree_root"] == str(fx.a.resolve())
          and co.resolve_claim(fx.a, "item-two")["worktree_root"] == str(b.resolve()))
    expect("E5b. but B still cannot claim item-one",
           lambda: co.claim_checkpoint(b, "item-one", "X1", now="t3"),
           co.CheckpointOwnedByOtherWorktreeError)
    expect("E5c. and B cannot release item-one either",
           lambda: co.release_checkpoint(b, "item-one", "X1", owner_token="0" * 32),
           co.CheckpointOwnedByOtherWorktreeError)
    co.release_checkpoint(b, "item-two", "Y1",
                          owner_token=co.resolve_claim(b, "item-two")["owner_token"])
    check("E5d. releasing item-two leaves item-one's claim intact",
          co.resolve_claim(fx.a, "item-one") is not None
          and co.resolve_claim(fx.a, "item-two") is None)
finally:
    fx.cleanup()

# E6 -- claim/selection disagreement inside the owning worktree
print("\n== E6. claim names a different checkpoint than selection resolves ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    claim = co.resolve_claim(fx.a, "v2-1-dry-run")
    claim["checkpoint_id"] = "S-CP2"
    co.claim_path(fx.a, "v2-1-dry-run").write_text(json.dumps(claim))
    expect("E6. claim/selection disagreement refuses rather than picking one",
           lambda: step1_fixed(fx, fx.a), co.CheckpointOwnershipStateMismatchError)
finally:
    fx.cleanup()

# E7 -- the shared record must be invisible to Git and to path classification
print("\n== E7. invisibility to Git / bundle identity ==")
fx = Fixture()
try:
    before_status = git(fx.a, "status", "--porcelain")
    before_dirty = ws._dirty_paths(fx.a)
    fx.start_cp3_in_a(claim=True)
    own = co.claim_path(fx.a, "v2-1-dry-run")
    b = fx.add_worktree_b()
    check("E7. every linked worktree resolves the identical shared record path",
          own.exists() and co.claim_path(b, "v2-1-dry-run") == own, str(own))
    co.claim_checkpoint(fx.a, "probe", "P", now="t2")
    after_dirty = ws._dirty_paths(fx.a)
    check("E7b. it appears in no worktree's git status / dirty-path set",
          not any("checkpoint-claims" in p for p in after_dirty)
          and "checkpoint-claims" not in git(fx.a, "status", "--porcelain"),
          str(sorted(after_dirty)))
finally:
    fx.cleanup()

# E8 -- separate clone (different git common dir): documented residual limit
print("\n== E8. separate clone rather than a linked worktree ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    clone = fx.tmp / "CLONE"
    subprocess.run(["git", "clone", "-q", str(fx.a), str(clone)], check=True,
                   capture_output=True)
    seen = co.resolve_claim(clone, "v2-1-dry-run")
    check("E8. a separate clone shares no common dir, so it sees no claim "
          "(known residual limit, must be stated, not silently assumed away)",
          seen is None, "clone sees claim" if seen else "clone sees nothing")
    check("E8b. the clone equally cannot see the uncommitted IN_PROGRESS state",
          "S-CP3" not in (clone / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
finally:
    fx.cleanup()

# E9 -- worktree A relocated after claiming
print("\n== E9. owning worktree relocated on disk ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    moved = fx.tmp / "A-moved"
    shutil.move(str(fx.a), str(moved))
    subprocess.run(["git", "worktree", "repair"], cwd=str(moved), capture_output=True)
    fx.a = moved
    expect("E9. a relocated owner fails closed rather than silently resuming",
           lambda: step1_fixed(fx, moved), ws.WorktreeIdentityMismatchError)
finally:
    fx.cleanup()

print()
failed = [r for r in RESULTS if not r[1]]
print(f"pass 2: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name, ok, detail in failed:
    print(f"  FAILED: {name} -- {detail}")
sys.exit(1 if failed else 0)
