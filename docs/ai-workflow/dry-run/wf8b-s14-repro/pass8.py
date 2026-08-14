"""Stress pass 8 (revision-63 authoring) -- the repaired S14/S15 end to end, and
path manipulation around the claim location.

S1 replays the corrected `WF8B_SCENARIOS.md` sequence exactly as it will be
written: A adopts its already-interrupted checkpoint (setup, no implementation),
B runs the real step-1 procedure twice and is refused twice with the two
distinguishable classes, then A recovers and completes the same checkpoint. Every
authoritative byte A owns is compared before and after.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
from pathlib import Path

from harness import REGISTRY, Fixture, git, step1_fixed, ws
import checkpoint_ownership as co

RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f"  -- {detail}" if detail else ""))


def expect(name, fn, exc_type):
    try:
        fn()
    except exc_type as exc:
        check(name, True, str(exc)[:90])
        return exc
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")
    else:
        check(name, False, "no exception raised")
    return None


# ---------------------------------------------------------------------------
# S1 -- the repaired S14/S15, end to end
# ---------------------------------------------------------------------------
print("== S1. repaired S14 -> S15, end to end ==")
fx = Fixture()
try:
    # Setup step, as it stands in the real repository TODAY: S-CP3 interrupted
    # in A, uncommitted, with no claim (the mechanism did not exist yet).
    fx.start_cp3_in_a(claim=False)
    setup = fx.snapshot()

    # Corrected setup addition: A adopts its own interrupted checkpoint. This is
    # the ONLY new setup action, it runs in A, and it implements nothing.
    co.adopt_claim(fx.a, "v2-1-dry-run", fx.work_item(fx.a), now="t2",
                   verify_resume_safety=ws.verify_dirty_resume_safety)
    after_adopt = fx.snapshot()
    check("S1. adoption changed NO authoritative byte in A (state, identity, "
          "scratch, HEAD, git status)",
          all(setup[k] == after_adopt[k] for k in
              ("A_state_bytes", "A_identity_bytes", "A_status", "A_head", "A_scratch")))
    check("S1b. the only thing that changed is the shared claim record",
          setup["ownership_bytes"] is None and after_adopt["ownership_bytes"] is not None)

    b = fx.add_worktree_b()
    check("S1c. B has only committed state -- no S-CP3 entry, no identity file",
          "S-CP3" not in fx.work_item(b)["checkpoints"]
          and not (b / ".ai-review/runtime/WORKTREE_IDENTITY.json").exists())

    # S14a -- missing identity
    before_a = fx.snapshot()
    exc = expect("S1d. S14a: B refuses with WorktreeIdentityMissingError, not a fresh start",
                 lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
    check("S1e. and the refusal names the holder and the explicit escape",
          getattr(exc, "ownership_claim", {}).get("worktree_root") == str(fx.a.resolve()))
    check("S1f. S14a mutated nothing anywhere", before_a == fx.snapshot())

    # S14b -- mismatched identity (the one authorized gitignored copy)
    (b / ".ai-review/runtime").mkdir(parents=True, exist_ok=True)
    shutil.copy(fx.a / ".ai-review/runtime/WORKTREE_IDENTITY.json",
                b / ".ai-review/runtime/WORKTREE_IDENTITY.json")
    before_b = fx.snapshot()
    expect("S1g. S14b: B refuses with WorktreeIdentityMismatchError (distinct class)",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMismatchError)
    check("S1h. S14b mutated nothing authoritative", before_b == fx.snapshot())
    check("S1i. and B's WORKFLOW_STATE.json was never copied or edited -- still its "
          "own committed bytes",
          (b / "docs/ai-workflow/WORKFLOW_STATE.json").read_text()
          == git(b, "show", "HEAD:docs/ai-workflow/WORKFLOW_STATE.json") + "\n")
    check("S1j. checkpoint 3 is still IN_PROGRESS in A, byte-identical to setup",
          fx.snapshot()["A_state_bytes"] == setup["A_state_bytes"])

    # S15 -- the originating worktree recovers and finishes the same checkpoint
    out = step1_fixed(fx, fx.a)
    check("S1k. S15: A classifies it a RESUME and mutates nothing to do so",
          out == {"selected": "S-CP3", "mode": "resume", "mutated": False}, str(out))
    state = ws.complete_checkpoint(fx.read_state(fx.a), "v2-1-dry-run", "S-CP3",
                                   {"checkpoints": [{"id": "S-CP1"}, {"id": "S-CP2"},
                                                    {"id": "S-CP3"}]}, now="t9")
    fx.write_state(fx.a, state)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "complete S-CP3")          # 1f: commit first
    co.release_checkpoint(fx.a, "v2-1-dry-run", "S-CP3",  # 1f: release after
                          owner_token=co.resolve_claim(fx.a, "v2-1-dry-run")["owner_token"])
    check("S1l. S15 completes S-CP3, releases the claim, and reaches the wrap-up phase",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "COMPLETE"
          and co.resolve_claim(fx.a, "v2-1-dry-run") is None
          and fx.work_item(fx.a)["phase"] == "SELF_REVIEWING_IMPLEMENTATION")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# S2 -- the claims DIRECTORY is a symlink
# ---------------------------------------------------------------------------
print("\n== S2. claims directory replaced by a symlink ==")
fx = Fixture()
try:
    claims = co.claims_dir(fx.a)
    claims.parent.mkdir(parents=True, exist_ok=True)
    outside = fx.tmp / "elsewhere"
    outside.mkdir()
    claims.symlink_to(outside, target_is_directory=True)
    expect("S2. reading through a symlinked claims directory fails closed",
           lambda: co.resolve_claim(fx.a, "v2-1-dry-run"),
           co.CheckpointOwnershipUnavailableError)
    expect("S2b. and publishing through it is refused too",
           lambda: co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1"),
           co.CheckpointOwnershipUnavailableError)
    check("S2c. nothing was written into the off-tree directory",
          list(outside.iterdir()) == [])
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# S3 -- work_item_id values that would escape a path-based naming scheme
# ---------------------------------------------------------------------------
print("\n== S3. hostile work_item_id values ==")
fx = Fixture()
try:
    base = co.claims_dir(fx.a).resolve()
    hostile = ["../../escape", "a/b/c", "..", "/etc/passwd", "x" * 300, "with space"]
    contained = all(
        co.claim_path(fx.a, wid).parent.resolve() == base
        and co.claim_path(fx.a, wid).name.endswith(".json")
        and len(co.claim_path(fx.a, wid).name) == 64 + len(".json")
        for wid in hostile)
    check("S3. every id, hostile or not, maps to a fixed-length digest inside the "
          "claims directory -- no value can address anything outside it", contained)
    a1 = co.claim_path(fx.a, "item-one")
    a2 = co.claim_path(fx.a, "item-two")
    check("S3b. distinct ids map to distinct records", a1 != a2)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# S4 -- a repository whose .git is itself a symlink
# ---------------------------------------------------------------------------
print("\n== S4. repository whose .git is a symlink ==")
fx = Fixture()
try:
    real_git = fx.tmp / "real-git-dir"
    shutil.move(str(fx.a / ".git"), str(real_git))
    (fx.a / ".git").symlink_to(real_git, target_is_directory=True)
    b = fx.add_worktree_b()
    pa = co.claim_path(fx.a, "v2-1-dry-run").resolve()
    pb = co.claim_path(b, "v2-1-dry-run").resolve()
    check("S4. both worktrees still resolve the identical claim record through the "
          "symlinked git dir", pa == pb, f"{pa} vs {pb}")
    fx.start_cp3_in_a(claim=False)
    co.adopt_claim(fx.a, "v2-1-dry-run", fx.work_item(fx.a), now="t2",
                   verify_resume_safety=ws.verify_dirty_resume_safety)
    expect("S4b. and B is refused, not fresh-started",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# S5 -- a refusal creates nothing at all under the common dir
# ---------------------------------------------------------------------------
print("\n== S5. refusals leave the shared location untouched ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    common = Path(co._git_identity(fx.a)[1])
    before = sorted(p.relative_to(common).as_posix()
                    for p in common.rglob("*") if "ai-workflow" in p.as_posix())
    check("S5. no claims directory exists before anything runs", before == [])
    # A foreign worktree resolving an absent claim must not create the directory.
    check("S5b. resolving an absent claim returns None and creates nothing",
          co.resolve_claim(b, "v2-1-dry-run") is None
          and not co.claims_dir(b).exists())
    fx.start_cp3_in_a(claim=False)
    co.adopt_claim(fx.a, "v2-1-dry-run", fx.work_item(fx.a), now="t2",
                   verify_resume_safety=ws.verify_dirty_resume_safety)
    listing = sorted(p.name for p in co.claims_dir(fx.a).iterdir())
    for _ in range(3):
        try:
            step1_fixed(fx, b)
        except Exception:  # noqa: BLE001
            pass
    check("S5c. three refusals from B add no file, no temp file, no directory",
          sorted(p.name for p in co.claims_dir(fx.a).iterdir()) == listing, str(listing))
    check("S5d. and the shared record stays invisible to every worktree's git status",
          git(fx.a, "status", "--porcelain").find("checkpoint-claims") == -1
          and git(b, "status", "--porcelain") == "")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# S6 -- discard/restart after adoption (required case 11)
# ---------------------------------------------------------------------------
print("\n== S6. explicit discard after adoption ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=False)
    co.adopt_claim(fx.a, "v2-1-dry-run", fx.work_item(fx.a), now="t2",
                   verify_resume_safety=ws.verify_dirty_resume_safety)
    b = fx.add_worktree_b()
    expect("S6. B refused while A holds the adopted claim",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
    # A discards explicitly: revert to the checkpoint's start_commit, drop the claim.
    git(fx.a, "checkout", "--", "docs/ai-workflow/WORKFLOW_STATE.json")
    (fx.a / "docs/ai-workflow/dry-run/c.txt").unlink()
    co.release_checkpoint(fx.a, "v2-1-dry-run", "S-CP3",
                          owner_token=co.resolve_claim(fx.a, "v2-1-dry-run")["owner_token"])
    check("S6b. after the discard nothing is IN_PROGRESS and no claim remains",
          fx.work_item(fx.a).get("current_checkpoint_id") is None
          and co.resolve_claim(fx.a, "v2-1-dry-run") is None)
    out = step1_fixed(fx, b)
    check("S6c. B may then legitimately fresh-start it and owns it",
          out["mode"] == "fresh"
          and co.resolve_claim(fx.a, "v2-1-dry-run")["worktree_root"] == str(b.resolve()))
    check("S6d. an adopted claim and a fresh claim are otherwise indistinguishable "
          "in behaviour -- `adopted` is provenance, not privilege",
          co.resolve_claim(fx.a, "v2-1-dry-run")["adopted"] is False)
finally:
    fx.cleanup()

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 8: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
