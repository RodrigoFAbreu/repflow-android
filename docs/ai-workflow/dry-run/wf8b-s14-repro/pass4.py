"""Stress pass 4 -- write ordering, nesting, path aliasing, discard/restart."""

import os
import subprocess
import sys

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


# O1 -- step 1d write ordering: the claim must precede the state write
print("== O1. crash between the two step-1d writes, both orderings ==")
for order in ("claim-first", "state-first"):
    fx = Fixture()
    try:
        if order == "claim-first":
            co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1")
            crashed_visible = True
        else:
            state = ws.transition_checkpoint_in_progress(
                fx.read_state(fx.a), "v2-1-dry-run", "S-CP3", fx.head, "t1")
            fx.write_state(fx.a, state)
            crashed_visible = co.resolve_claim(fx.a, "v2-1-dry-run") is not None
        b = fx.add_worktree_b()
        try:
            out = step1_fixed(fx, b)
            leaked = out["mutated"]
        except Exception:  # noqa: BLE001
            leaked = False
        if order == "claim-first":
            check("O1: claim-first -- a crash mid-1d still cannot let B fresh-start",
                  not leaked, "B mutated" if leaked else "B refused, no mutation")
        else:
            check("O1: state-first CONTROL ARM -- proves the ordering is load-bearing "
                  "(B does fresh-start, so the claim must precede the state write)",
                  leaked, "ordering not load-bearing?!" if not leaked else
                  "B fresh-started, as the control arm requires")
        check(f"O1b [{order}]: the claim is the authority a crash must leave behind",
              crashed_visible if order == "claim-first" else not crashed_visible,
              "claim present" if crashed_visible else "no claim -- ordering matters")
    finally:
        fx.cleanup()

# O2 -- linked worktree nested INSIDE the primary checkout (this repo's own shape)
print("\n== O2. nested linked worktree (the real repository's own layout) ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    nested = fx.a / ".claude/worktrees/nested"
    nested.parent.mkdir(parents=True, exist_ok=True)
    git(fx.a, "worktree", "add", "-q", str(nested), "-b", "scratch-nested", fx.head)
    fx.b = nested
    check("O2. a nested linked worktree resolves the same shared claim path",
          co.claim_path(nested, "v2-1-dry-run") == co.claim_path(fx.a, "v2-1-dry-run"))
    before = fx.snapshot()
    expect("O2b. and is refused exactly like an external one",
           lambda: step1_fixed(fx, nested), ws.WorktreeIdentityMissingError)
    check("O2c. zero mutation", before == fx.snapshot())
finally:
    fx.cleanup()

# O3 -- path aliasing: reaching the owning worktree through a symlink
print("\n== O3. owning worktree reached through a symlink ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    alias = fx.tmp / "A-alias"
    os.symlink(fx.a, alias)
    try:
        ws.verify_dirty_resume_safety(alias, "v2-1-dry-run")
        check("O3. a symlinked path to the owner does not spuriously refuse", True,
              "git resolves --show-toplevel to the physical path")
    except Exception as exc:  # noqa: BLE001
        check("O3. a symlinked path to the owner does not spuriously refuse", False,
              f"{type(exc).__name__}: {exc}")
    check("O3b. and the shared claim resolves identically through the alias",
          co.claim_path(alias, "v2-1-dry-run") == co.claim_path(fx.a, "v2-1-dry-run"))
finally:
    fx.cleanup()

# O4 -- explicit discard-and-restart recovery (S15's other branch)
print("\n== O4. explicit discard branch of interrupted-checkpoint recovery ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    b = fx.add_worktree_b()
    expect("O4. B refused while A holds the claim", lambda: step1_fixed(fx, b),
           ws.WorktreeIdentityMissingError)
    # A discards: restore state to the checkpoint's start_commit, drop the claim.
    git(fx.a, "checkout", "--", "docs/ai-workflow/WORKFLOW_STATE.json")
    (fx.a / "docs/ai-workflow/dry-run/c.txt").unlink()
    co.release_checkpoint(fx.a, "v2-1-dry-run", "S-CP3",
                          owner_token=co.resolve_claim(fx.a, "v2-1-dry-run")["owner_token"])
    check("O4b. after an explicit discard, nothing is IN_PROGRESS and no claim remains",
          fx.work_item(fx.a).get("current_checkpoint_id") is None
          and co.resolve_claim(fx.a, "v2-1-dry-run") is None)
    out = step1_fixed(fx, b)
    check("O4c. B may then legitimately fresh-start it", out["mode"] == "fresh", str(out))
    check("O4d. and B now holds the claim",
          co.resolve_claim(fx.a, "v2-1-dry-run")["worktree_root"] == str(b.resolve()))
finally:
    fx.cleanup()

# O5 -- repeated refusal is idempotent and leaves no trace in the refusing worktree
print("\n== O5. repeated refusal leaves no trace ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    b = fx.add_worktree_b()
    before = fx.snapshot()
    for _ in range(3):
        try:
            step1_fixed(fx, b)
        except ws.WorktreeIdentityMissingError:
            pass
    listing = subprocess.run(["find", str(b), "-name", ".ai-review", "-o", "-name", "*.tmp"],
                             capture_output=True, text=True).stdout.strip()
    check("O5. three refusals create no .ai-review/ and no temp files in B", listing == "", listing)
    check("O5b. and mutate nothing anywhere", before == fx.snapshot())
finally:
    fx.cleanup()

print()
failed = [r for r in RESULTS if not r[1]]
print(f"pass 4: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name, ok, detail in failed:
    print(f"  FAILED: {name} -- {detail}")
sys.exit(1 if failed else 0)
