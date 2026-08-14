"""Stress pass 3 -- concurrency, durability, and non-regression of the
properties the approved design already guarantees."""

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


# C1 -- concurrent claim (TOCTOU on the shared record)
print("== C1. two worktrees claiming the same work item concurrently ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    stale = co.resolve_claim(fx.a, "v2-1-dry-run")   # A reads: unclaimed...
    co.claim_checkpoint(b, "v2-1-dry-run", "S-CP3", now="tB")   # ...B claims first...
    assert stale is None
    try:
        co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="tA")
        holder = co.resolve_claim(fx.a, "v2-1-dry-run")["worktree_root"]
        check("C1. a stale-read claim cannot overwrite the winner's claim",
              holder == str(b.resolve()), f"holder={holder}")
    except co.CheckpointOwnedByOtherWorktreeError as exc:
        check("C1. a stale-read claim is refused, winner keeps the claim",
              co.resolve_claim(fx.a, "v2-1-dry-run")["worktree_root"] == str(b.resolve()),
              str(exc)[:70])
finally:
    fx.cleanup()

# C2 -- the shared record survives ordinary Git maintenance
print("\n== C2. durability across Git maintenance ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    b = fx.add_worktree_b()
    subprocess.run(["git", "gc", "--prune=now", "-q"], cwd=fx.a, check=True, capture_output=True)
    subprocess.run(["git", "worktree", "prune"], cwd=fx.a, check=True, capture_output=True)
    check("C2. claim survives `git gc` + `git worktree prune`",
          co.resolve_claim(b, "v2-1-dry-run") is not None)
    expect("C2b. and B still refuses afterwards",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

# C3 -- foreign worktree checked out at an OLDER commit
print("\n== C3. foreign worktree at a different (older) commit ==")
fx = Fixture()
try:
    old_head = fx.head
    (fx.a / "docs/ai-workflow/marker.txt").write_text("later\n")
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "later commit")
    fx.head = git(fx.a, "rev-parse", "HEAD")
    fx.start_cp3_in_a(claim=True)
    b = fx.tmp / "B-old"
    git(fx.a, "worktree", "add", "-q", str(b), "-b", "scratch-old", old_head)
    fx.b = b
    sel = ws.select_next_checkpoint(fx.work_item(b), REGISTRY)
    check("C3. B at an older commit still selects S-CP3", sel == "S-CP3", str(sel))
    before = fx.snapshot()
    expect("C3b. and is still refused, not fresh-started",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
    check("C3c. zero mutation from that refusal", before == fx.snapshot())
finally:
    fx.cleanup()

# C4 -- a third worktree is equally refused
print("\n== C4. third worktree ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    fx.add_worktree_b("B")
    c = fx.tmp / "C"
    git(fx.a, "worktree", "add", "-q", str(c), "-b", "scratch-C", fx.head)
    fx.b = c
    before = fx.snapshot()
    expect("C4. worktree C is refused identically", lambda: step1_fixed(fx, c),
           ws.WorktreeIdentityMissingError)
    check("C4b. zero mutation", before == fx.snapshot())
finally:
    fx.cleanup()

# C5 -- the approved guarantees the fix must not weaken
print("\n== C5. non-regression of already-approved behaviour ==")
fx = Fixture()
try:
    # D-Selection determinism (missing-test item 28): unchanged, still pure.
    wi = fx.work_item(fx.a)
    check("C5. select_next_checkpoint stays pure/deterministic (untouched by the fix)",
          ws.select_next_checkpoint(dict(wi), REGISTRY)
          == ws.select_next_checkpoint(dict(wi), REGISTRY) == "S-CP3")
    # A clean (COMPLETE) checkpoint stays portable anywhere -- D3's own words.
    b = fx.add_worktree_b()
    check("C5b. with no claim, a foreign worktree fresh-starts normally (no new false lock)",
          step1_fixed(fx, b)["mode"] == "fresh")
    # And having done so legitimately, it now owns it.
    check("C5c. that legitimate fresh start now claims ownership",
          co.resolve_claim(fx.a, "v2-1-dry-run")["worktree_root"] == str(b.resolve()))
    expect("C5d. so A is now the one refused (symmetry, not primary-worktree privilege)",
           lambda: step1_fixed(fx, fx.a), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

print()
failed = [r for r in RESULTS if not r[1]]
print(f"pass 3: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name, ok, detail in failed:
    print(f"  FAILED: {name} -- {detail}")
sys.exit(1 if failed else 0)
