"""Stress pass 1 -- reproduce the defect, then the seven properties the fix
must hold, against real Git worktrees."""

import shutil
import sys

from harness import Fixture, co, git, step1_fixed, step1_today, ws

RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" -- {detail}" if detail else ""))


print("== A. reproduce today's behaviour (no fix) ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=False)
    b = fx.add_worktree_b()
    before = fx.snapshot()
    out = step1_today(fx, b)
    check("today: worktree B classifies the claimed checkpoint as a FRESH START",
          out == {"selected": "S-CP3", "mode": "fresh", "mutated": True}, str(out))
    check("today: B durably wrote S-CP3 IN_PROGRESS into its own WORKFLOW_STATE.json",
          fx.work_item(b)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS")
    check("today: B created its own WORKTREE_IDENTITY.json",
          (b / ".ai-review/runtime/WORKTREE_IDENTITY.json").exists())
    check("today: two worktrees now both hold S-CP3 IN_PROGRESS",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS"
          and fx.work_item(b)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS")
finally:
    fx.cleanup()

print("\n== B. the fix, against the seven required properties ==")
fx = Fixture()
try:
    # 1. worktree A owns a dirty interrupted checkpoint
    fx.start_cp3_in_a(claim=True)
    check("1. A owns a dirty interrupted checkpoint (uncommitted IN_PROGRESS + claim)",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS"
          and co.resolve_claim(fx.a, "v2-1-dry-run")["worktree_root"] == str(fx.a.resolve()))
    check("1b. the IN_PROGRESS transition is genuinely uncommitted",
          "S-CP3" not in git(fx.a, "show", "HEAD:docs/ai-workflow/WORKFLOW_STATE.json"))

    # 2. fresh worktree B has only committed repository state
    b = fx.add_worktree_b()
    check("2. B sees only committed state (no S-CP3, no identity file)",
          "S-CP3" not in (b / "docs/ai-workflow/WORKFLOW_STATE.json").read_text()
          and not (b / ".ai-review/runtime/WORKTREE_IDENTITY.json").exists())
    check("2b. B nonetheless discovers A's claim via the shared git common dir",
          co.resolve_claim(b, "v2-1-dry-run") is not None
          and co.resolve_claim(b, "v2-1-dry-run")["worktree_root"] == str(fx.a.resolve()))

    before = fx.snapshot()

    # 3 + 4a. B cannot fresh-start; missing-identity case is distinguishable (S14a)
    try:
        step1_fixed(fx, b)
        check("3/4a. S14a raises WorktreeIdentityMissingError", False, "no exception raised")
    except ws.WorktreeIdentityMissingError as exc:
        check("3/4a. S14a: B refuses with WorktreeIdentityMissingError (not a fresh start)",
              True, str(exc)[:90])
    except Exception as exc:  # noqa: BLE001
        check("3/4a. S14a raises WorktreeIdentityMissingError", False, f"{type(exc).__name__}: {exc}")

    # 5a. refusal produced zero authoritative mutation
    after = fx.snapshot()
    check("5a. S14a refusal mutated nothing (A state/identity/scratch, B state, HEADs, claim)",
          before == after, "" if before == after else
          str([k for k in before if before[k] != after[k]]))

    # 4b. mismatched-identity case stays distinguishable (S14b)
    (b / ".ai-review/runtime").mkdir(parents=True, exist_ok=True)
    shutil.copy(fx.a / ".ai-review/runtime/WORKTREE_IDENTITY.json",
                b / ".ai-review/runtime/WORKTREE_IDENTITY.json")
    before_b = fx.snapshot()
    try:
        step1_fixed(fx, b)
        check("4b. S14b raises WorktreeIdentityMismatchError", False, "no exception raised")
    except ws.WorktreeIdentityMismatchError as exc:
        check("4b. S14b: B refuses with WorktreeIdentityMismatchError (distinct class)",
              True, str(exc)[:90])
    except Exception as exc:  # noqa: BLE001
        check("4b. S14b raises WorktreeIdentityMismatchError", False, f"{type(exc).__name__}: {exc}")
    after_b = fx.snapshot()
    check("5b. S14b refusal mutated nothing authoritative", before_b == after_b,
          "" if before_b == after_b else str([k for k in before_b if before_b[k] != after_b[k]]))

    # 7. no artificial copying of WORKFLOW_STATE.json was required
    check("7. B's WORKFLOW_STATE.json was never copied/edited (still committed bytes)",
          (b / "docs/ai-workflow/WORKFLOW_STATE.json").read_text()
          == git(b, "show", "HEAD:docs/ai-workflow/WORKFLOW_STATE.json") + "\n")

    # 6. A can subsequently perform S15 recovery
    out = step1_fixed(fx, fx.a)
    check("6a. A still classifies S-CP3 as a RESUME after both refusals",
          out == {"selected": "S-CP3", "mode": "resume", "mutated": False}, str(out))
    state = ws.complete_checkpoint(fx.read_state(fx.a), "v2-1-dry-run", "S-CP3",
                                   {"checkpoints": [{"id": "S-CP1"}, {"id": "S-CP2"}, {"id": "S-CP3"}]},
                                   now="t9")
    fx.write_state(fx.a, state)
    co.release_checkpoint(fx.a, "v2-1-dry-run", "S-CP3",
                          owner_token=co.resolve_claim(fx.a, "v2-1-dry-run")["owner_token"])
    check("6b. S15 recovery completes S-CP3 and releases the shared claim",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "COMPLETE"
          and co.resolve_claim(fx.a, "v2-1-dry-run") is None
          and fx.work_item(fx.a)["phase"] == "SELF_REVIEWING_IMPLEMENTATION")
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "complete S-CP3")
    b2 = fx.tmp / "B"
    check("6c. after release, another worktree is free again (no false lock)",
          co.resolve_claim(b2, "v2-1-dry-run") is None)
finally:
    fx.cleanup()

print()
failed = [r for r in RESULTS if not r[1]]
print(f"pass 1: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
sys.exit(1 if failed else 0)
