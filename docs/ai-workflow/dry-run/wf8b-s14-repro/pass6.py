"""Stress pass 6 (revision-63 authoring) -- five defects in the revision-62 draft.

Every section runs **both arms**: the frozen revision-62 draft
(`checkpoint_ownership_r62draft`, which must still exhibit the defect) and the
corrected revision-63 design (`checkpoint_ownership`, which must not). Neither
arm is a claim about code that no longer exists.

Passes 1-5 always arranged the fixture with `start_cp3_in_a(claim=True)`, i.e.
they assumed the interrupted checkpoint had been started *after* the claim
mechanism existed. The real repository's `S-CP3` was started before it exists,
so its live configuration -- `A dirty IN_PROGRESS + NO claim` -- is a shape no
earlier pass ever built. A1/A2 build exactly that shape.
"""

from __future__ import annotations

import json

from harness import Fixture, step1_fixed, step1_with, ws
import checkpoint_ownership as co
import checkpoint_ownership_r62draft as draft

RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f"  -- {detail}" if detail else ""))


def expect(name, fn, exc_type):
    try:
        fn()
    except exc_type as exc:
        check(name, True, str(exc)[:90])
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")
    else:
        check(name, False, "no exception raised")


# ---------------------------------------------------------------------------
# A1 (BLOCKING) -- the real repository's live shape: dirty IN_PROGRESS, no claim
# ---------------------------------------------------------------------------
print("== A1. checkpoint interrupted BEFORE the claim mechanism existed ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=False)          # <-- the real S-CP3's actual shape
    wi_a = fx.work_item(fx.a)
    check("A1. A really is dirty-IN_PROGRESS on S-CP3, with no shared claim",
          wi_a["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS"
          and co.resolve_claim(fx.a, "v2-1-dry-run") is None)
    b = fx.add_worktree_b()
    result = step1_with(draft, fx, b)
    check("A1b. DRAFT ARM (the defect): worktree B fresh-starts the checkpoint A "
          "holds interrupted, and mutates its own authoritative state",
          result["mode"] == "fresh" and result["mutated"]
          and fx.work_item(b)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS")
finally:
    fx.cleanup()

fx = Fixture()
try:
    fx.start_cp3_in_a(claim=False)
    b = fx.add_worktree_b()
    before = fx.snapshot()
    expect("A1c. FIXED ARM: A's legitimate resume adopts the claim, so B is then "
           "refused instead of fresh-starting",
           lambda: (step1_fixed(fx, fx.a), step1_fixed(fx, b)),
           ws.WorktreeIdentityMissingError)
    check("A1d. and the adoption is recorded as such, not as a fresh claim",
          co.resolve_claim(fx.a, "v2-1-dry-run")["adopted"] is True)
    after = fx.snapshot()
    check("A1e. B's refusal mutated nothing authoritative",
          {k: v for k, v in before.items() if k.startswith("B_")}
          == {k: v for k, v in after.items() if k.startswith("B_")})
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# A2 (BLOCKING) -- explicit adoption without resuming (the S14 setup need)
# ---------------------------------------------------------------------------
print("\n== A2. adopting a pre-existing interrupted checkpoint without implementing it ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=False)
    before = fx.snapshot()
    record = co.adopt_claim(fx.a, "v2-1-dry-run", fx.work_item(fx.a), now="t2",
                            verify_resume_safety=ws.verify_dirty_resume_safety)
    check("A2. adoption publishes a claim for exactly the IN_PROGRESS checkpoint",
          record["checkpoint_id"] == "S-CP3" and record["adopted"] is True)
    after = fx.snapshot()
    check("A2b. and mutates NO authoritative state -- state file, identity file, "
          "scratch file, HEAD and `git status` all byte-identical",
          all(before[k] == after[k] for k in
              ("A_state_bytes", "A_identity_bytes", "A_status", "A_head", "A_scratch")))
    check("A2c. adoption is idempotent (re-running returns the same claim)",
          co.adopt_claim(fx.a, "v2-1-dry-run", fx.work_item(fx.a), now="t3",
                         verify_resume_safety=ws.verify_dirty_resume_safety
                         )["claimed_at"] == "t2")
    b = fx.add_worktree_b()
    expect("A2d. and B is now refused with the S14a class, having never seen a claim "
           "published by any command it ran",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

fx = Fixture()
try:
    b = fx.add_worktree_b()
    fx.start_cp3_in_a(claim=False)
    expect("A2e. a FOREIGN worktree cannot adopt (guard 2 is `verify_dirty_resume_safety`)",
           lambda: co.adopt_claim(b, "v2-1-dry-run", fx.work_item(fx.a), now="t2",
                                  verify_resume_safety=ws.verify_dirty_resume_safety),
           ws.WorktreeIdentityMissingError)
    expect("A2f. and adoption never invents an ownership fact when nothing is IN_PROGRESS",
           lambda: co.adopt_claim(fx.a, "v2-1-dry-run", fx.base_state()["work_items"]["v2-1-dry-run"],
                                  now="t2", verify_resume_safety=ws.verify_dirty_resume_safety),
           co.CheckpointOwnershipStateMismatchError)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# A3 (BLOCKING) -- claim acquired, IN_PROGRESS state never written
# ---------------------------------------------------------------------------
print("\n== A3. claim acquired, IN_PROGRESS state never written ==")
fx = Fixture()
try:
    draft.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1")
    ws.write_worktree_identity(fx.a, "v2-1-dry-run", now="t1")
    expect("A3. DRAFT ARM (the defect): the LEGITIMATE OWNER is permanently refused "
           "by its own claim, with no documented way to clear it",
           lambda: step1_with(draft, fx, fx.a), draft.CheckpointOwnershipStateMismatchError)
    check("A3b. and the claim is still there afterwards, so it refuses identically forever",
          draft.resolve_claim(fx.a, "v2-1-dry-run") is not None)
finally:
    fx.cleanup()

fx = Fixture()
try:
    co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1")
    ws.write_worktree_identity(fx.a, "v2-1-dry-run", now="t1")
    result = step1_fixed(fx, fx.a)
    check("A3c. FIXED ARM: the owner CONTINUEs its own interrupted acquisition",
          result["mode"] == "continue_claim" and result["selected"] == "S-CP3", str(result))
    check("A3d. and the state write it was interrupted before now exists",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS")
    check("A3e. continuing re-published no claim -- the original is intact, not replaced",
          co.resolve_claim(fx.a, "v2-1-dry-run")["claimed_at"] == "t1")
    b = fx.add_worktree_b()
    expect("A3f. and B was never able to slip through that window either",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# A4 (IMPORTANT) -- a partially-written claim record
# ---------------------------------------------------------------------------
print("\n== A4. partially-written claim record ==")
fx = Fixture()
try:
    path = draft.claim_path(fx.a, "v2-1-dry-run")
    path.parent.mkdir(parents=True, exist_ok=True)
    full = json.dumps({"schema_version": 1, "work_item_id": "v2-1-dry-run",
                       "checkpoint_id": "S-CP3", "repo_root": str(fx.a),
                       "git_common_dir": "x", "worktree_root": str(fx.a),
                       "claimed_at": "t1"}, indent=2)
    path.write_text(full[: len(full) // 2])   # crash mid-write under O_CREAT|O_EXCL
    expect("A4. DRAFT ARM (the defect): a torn record fails closed for the owner too, "
           "and the draft names no recovery -- the work item is unstartable everywhere",
           lambda: draft.resolve_claim(fx.a, "v2-1-dry-run"),
           draft.CheckpointOwnershipUnavailableError)
finally:
    fx.cleanup()

fx = Fixture()
try:
    check("A4b. FIXED ARM: publication is a same-directory temp file plus `os.link`, "
          "so a reader can never observe a partial claim",
          co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1")["checkpoint_id"] == "S-CP3"
          and co.resolve_claim(fx.a, "v2-1-dry-run") is not None)
    check("A4c. and no staging temp file is left behind",
          not [p for p in co.claims_dir(fx.a).iterdir() if p.name.startswith(".claim-")])
    # A record corrupted by something other than this writer still fails closed,
    # but is now explicitly recoverable.
    co.claim_path(fx.a, "v2-1-dry-run").write_text("{ not json")
    expect("A4d. an externally corrupted record still fails closed",
           lambda: co.resolve_claim(fx.a, "v2-1-dry-run"),
           co.CheckpointOwnershipUnavailableError)
    ev = co.takeover_evidence(fx.a, "v2-1-dry-run")
    check("A4e. and the takeover evidence reports it as unreadable rather than absent",
          ev["readable"] is False)
    ev = co.takeover_evidence(fx.a, "v2-1-dry-run")  # revision 64, GPT-R81-002:
    co.take_over_claim(fx.a, "v2-1-dry-run", "S-CP3", now="t5", evidence=ev,
                       user_authorization=co.takeover_authorization_literal(
                           "v2-1-dry-run", ev, "S-CP3"))
    claim = co.resolve_claim(fx.a, "v2-1-dry-run")
    check("A4f. explicit takeover recovers it and records what it displaced",
          claim["taken_over_from"]["unreadable_record"] is True)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# A5 (IMPORTANT) -- a symlink planted at the claim path
# ---------------------------------------------------------------------------
print("\n== A5. symlink planted at the claim path ==")
for arm, module in (("DRAFT", draft), ("FIXED", co)):
    fx = Fixture()
    try:
        path = module.claim_path(fx.a, "v2-1-dry-run")
        path.parent.mkdir(parents=True, exist_ok=True)
        target = fx.tmp / "outside.json"
        target.write_text(json.dumps({
            "schema_version": module.CLAIM_SCHEMA_VERSION, "work_item_id": "v2-1-dry-run",
            "checkpoint_id": "S-CP3", "repo_root": str(fx.a),
            "git_common_dir": str(fx.a / ".git"), "worktree_root": str(fx.a),
            "worktree_git_dir": str(fx.a / ".git"), "claimed_at": "t1"}) + "\n")
        path.symlink_to(target)
        if arm == "DRAFT":
            claim = module.resolve_claim(fx.a, "v2-1-dry-run")
            check("A5. DRAFT ARM (the defect): the reader follows a symlink out of the "
                  "claims directory and accepts its target as an authoritative claim",
                  claim is not None and claim["checkpoint_id"] == "S-CP3")
        else:
            expect("A5b. FIXED ARM: a claim path that is a symlink fails closed, never "
                   "resolved through",
                   lambda: module.resolve_claim(fx.a, "v2-1-dry-run"),
                   co.CheckpointOwnershipUnavailableError)
            expect("A5c. and publication through it is refused too",
                   lambda: module.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP3", now="t1"),
                   co.CheckpointOwnershipUnavailableError)
            check("A5d. the off-tree target is untouched by the refusal", target.exists())
    finally:
        fx.cleanup()

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 6: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
