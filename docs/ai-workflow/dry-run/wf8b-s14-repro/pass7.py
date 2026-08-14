"""Stress pass 7 (revision-63 authoring) -- adversarial attack on the surface
pass 6's fixes introduced: adoption, `CONTINUE_CLAIM`, the durability gate, and
the explicit takeover. A fix that opens a new hole is not a fix.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import textwrap
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
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")
    else:
        check(name, False, "no exception raised")


# ---------------------------------------------------------------------------
# T1 -- takeover atomicity: an authorized takeover that fails must not leave the
#       work item unclaimed
# ---------------------------------------------------------------------------
print("== T1. a failing takeover must not silently unprotect the work item ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    co.claim_checkpoint(b, "v2-1-dry-run", "S-CP3", now="t1")
    before = co.resolve_claim(fx.a, "v2-1-dry-run")
    check("T1. B holds the claim", before["worktree_root"] == str(b.resolve()))
    # Make publication of the replacement impossible while the old claim exists.
    ev = co.takeover_evidence(fx.a, "v2-1-dry-run")   # revision 64, GPT-R81-002
    auth = co.takeover_authorization_literal("v2-1-dry-run", ev, "S-CP3")
    claims = co.claims_dir(fx.a)
    os.chmod(claims, 0o500)
    try:
        try:
            co.take_over_claim(fx.a, "v2-1-dry-run", "S-CP3", now="t5", evidence=ev,
                               user_authorization=auth)
            outcome = "succeeded"
        except Exception as exc:  # noqa: BLE001
            outcome = f"{type(exc).__name__}"
    finally:
        os.chmod(claims, 0o700)
    after = co.resolve_claim(fx.a, "v2-1-dry-run")
    check("T1b. a takeover that cannot publish leaves the previous claim in place "
          "rather than deleting it and failing (fail-safe, not fail-open)",
          after is not None, f"takeover {outcome}; claim now {after}")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# T2 -- `CONTINUE_CLAIM` when the registry is exhausted (selection returns None)
# ---------------------------------------------------------------------------
print("\n== T2. self-claim with no local entry while selection returns None ==")
fx = Fixture()
try:
    state = fx.read_state(fx.a)
    state["work_items"]["v2-1-dry-run"]["checkpoints"]["S-CP3"] = {
        "status": "COMPLETE", "start_commit": "0" * 40}
    fx.write_state(fx.a, state)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "all complete")
    # A claim naming a checkpoint that has no local entry at all, while the
    # registry is exhausted: `selected_id` is None.
    co.claim_checkpoint(fx.a, "v2-1-dry-run", "S-CP-GHOST", now="t1")
    ws.write_worktree_identity(fx.a, "v2-1-dry-run", now="t1")
    sel = ws.select_next_checkpoint(fx.work_item(fx.a), REGISTRY)
    check("T2. selection really does return None here", sel is None)
    outcome, checkpoint_id, _token = co.resolve_ownership(
        fx.a, fx.work_item(fx.a), "v2-1-dry-run", sel,
        verify_resume_safety=ws.verify_dirty_resume_safety, now="t2")
    check("T2b. ownership resolution names the checkpoint the caller must act on, "
          "so a `None` selection can never be written into the state file",
          outcome == co.CONTINUE_CLAIM and checkpoint_id == "S-CP-GHOST",
          f"{outcome} {checkpoint_id}")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# T3 -- genuinely concurrent acquisition from two OS processes
# ---------------------------------------------------------------------------
print("\n== T3. two real processes racing for the same work item ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    here = Path(__file__).parent
    script = textwrap.dedent(f"""
        import sys, json
        sys.path.insert(0, {str(here)!r})
        sys.path.insert(0, {str(Path(co.__file__).parent)!r})
        import checkpoint_ownership as co
        from pathlib import Path
        try:
            r = co.claim_checkpoint(Path(sys.argv[1]), "v2-1-dry-run", "S-CP3", now=sys.argv[2])
            print(json.dumps({{"ok": True, "worktree": r["worktree_root"]}}))
        except Exception as exc:
            print(json.dumps({{"ok": False, "err": type(exc).__name__}}))
    """)
    runner = fx.tmp / "race.py"
    runner.write_text(script)
    procs = [subprocess.Popen([sys.executable, str(runner), str(root), f"t{i}"],
                              stdout=subprocess.PIPE, text=True)
             for i, root in enumerate((fx.a, b))]
    outs = [json.loads(p.communicate()[0].strip()) for p in procs]
    winners = [o for o in outs if o["ok"]]
    losers = [o for o in outs if not o["ok"]]
    check("T3. exactly one process wins the claim", len(winners) == 1, str(outs))
    check("T3b. the loser is refused with the owned-by-other class",
          len(losers) == 1 and losers[0]["err"] == "CheckpointOwnedByOtherWorktreeError",
          str(losers))
    claim = co.resolve_claim(fx.a, "v2-1-dry-run")
    check("T3c. the surviving record is the winner's, complete and parseable",
          claim is not None and claim["worktree_root"] == winners[0]["worktree"])
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# T4 -- takeover authorization cannot be bypassed
# ---------------------------------------------------------------------------
print("\n== T4. takeover authorization ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    co.claim_checkpoint(b, "v2-1-dry-run", "S-CP3", now="t1")
    for label, auth in (("absent", None), ("empty", ""), ("wrong checkpoint",
                        "take over v2-1-dry-run S-CP2"), ("truthy junk", "yes")):
        expect(f"T4. takeover refused with {label} authorization",
               lambda a=auth: co.take_over_claim(fx.a, "v2-1-dry-run", "S-CP3", now="t5",
                                                 user_authorization=a),
               co.CheckpointClaimTakeoverRefusedError)
    check("T4b. and B's claim survived every refusal untouched",
          co.resolve_claim(fx.a, "v2-1-dry-run")["worktree_root"] == str(b.resolve()))
    ev = co.takeover_evidence(fx.a, "v2-1-dry-run")
    check("T4c. evidence reports the holder as a LIVE registered worktree, so a "
          "human is told the holder still exists rather than shown an orphan",
          ev["holder_registered"] is True and ev["holder_path_exists"] is True)
    ev = co.takeover_evidence(fx.a, "v2-1-dry-run")  # revision 64, GPT-R81-002:
    co.take_over_claim(fx.a, "v2-1-dry-run", "S-CP3", now="t5", evidence=ev,
                       user_authorization=co.takeover_authorization_literal(
                           "v2-1-dry-run", ev, "S-CP3"))
    claim = co.resolve_claim(fx.a, "v2-1-dry-run")
    check("T4d. an authorized takeover records what it displaced",
          claim["taken_over_from"]["worktree_root"] == str(b.resolve())
          and claim["taken_over_from"]["holder_registered"] is True)
    check("T4e. and no authoritative workflow state was written by it",
          fx.work_item(fx.a).get("current_checkpoint_id") is None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# T5 -- takeover after the holder worktree is really gone (required case 5)
# ---------------------------------------------------------------------------
print("\n== T5. holder worktree removed, then taken over ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    co.claim_checkpoint(b, "v2-1-dry-run", "S-CP3", now="t1")
    git(fx.a, "worktree", "remove", "--force", str(b))
    ev = co.takeover_evidence(fx.a, "v2-1-dry-run")
    check("T5. evidence shows the holder is gone and unregistered",
          ev["holder_registered"] is False and ev["holder_path_exists"] is False)
    # Amended after the first run: `WorktreeIdentityMissingError` is the correct
    # class here (a claim asks "are you the owner?", and D3's two classes are the
    # two ways of failing to answer -- collapsing them would break S14a/S14b).
    # What was genuinely missing is that the refusal never named the holder.
    try:
        step1_fixed(fx, fx.a)
        check("T5b. a live foreign claim is never auto-released", False, "no refusal")
    except ws.WorktreeIdentityMissingError as exc:
        check("T5b. it is STILL not released automatically -- no age, heartbeat or "
              "liveness inference ever drops a claim", True, str(exc)[:60])
        check("T5b-i. and the refusal now carries the ownership evidence: who holds "
              "it, on what checkpoint, and that an explicit takeover is the escape",
              getattr(exc, "ownership_claim", None) is not None
              and "take the claim over explicitly" in getattr(exc, "ownership_hint", ""),
              getattr(exc, "ownership_hint", "<no hint attached>")[:80])
    ev = co.takeover_evidence(fx.a, "v2-1-dry-run")  # revision 64, GPT-R81-002:
    co.take_over_claim(fx.a, "v2-1-dry-run", "S-CP3", now="t5", evidence=ev,
                       user_authorization=co.takeover_authorization_literal(
                           "v2-1-dry-run", ev, "S-CP3"))
    check("T5c. after the explicit takeover A owns it and can proceed",
          co.claim_is_this_worktree(fx.a, co.resolve_claim(fx.a, "v2-1-dry-run")))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# T6 -- release ordering and release failure (required case 12)
# ---------------------------------------------------------------------------
print("\n== T6. release ordering and failure ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    state = ws.complete_checkpoint(fx.read_state(fx.a), "v2-1-dry-run", "S-CP3",
                                   {"checkpoints": [{"id": "S-CP1"}, {"id": "S-CP2"},
                                                    {"id": "S-CP3"}]}, now="t9")
    fx.write_state(fx.a, state)
    check("T6. before the commit, the completion is not durable, so the claim must "
          "not be releasable-by-inference",
          co.committed_checkpoint_status(fx.a, "v2-1-dry-run", "S-CP3") != "COMPLETE")
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "complete S-CP3")
    check("T6b. after the commit it is durable",
          co.committed_checkpoint_status(fx.a, "v2-1-dry-run", "S-CP3") == "COMPLETE")
    claims = co.claims_dir(fx.a)
    os.chmod(claims, 0o500)
    try:
        expect("T6c. a release that cannot unlink reports a typed ownership error, "
               "never a bare OSError the caller has no contract for",
               lambda: co.release_checkpoint(
                   fx.a, "v2-1-dry-run", "S-CP3",
                   owner_token=co.resolve_claim(fx.a, "v2-1-dry-run")["owner_token"]),
               co.CheckpointOwnershipUnavailableError)
    finally:
        os.chmod(claims, 0o700)
    outcome, cp_id, _tok = co.resolve_ownership(fx.a, fx.work_item(fx.a), "v2-1-dry-run", None,
                                                verify_resume_safety=ws.verify_dirty_resume_safety,
                                                now="t10")
    # AMENDED, revision 64 (`GPT-R81-003`): revision 63 asserted `FRESH` here, which
    # is a mutation-capable outcome carrying no checkpoint id once the registry is
    # exhausted. The self-heal is unchanged; the outcome is now the terminal one.
    check("T6d. the next invocation self-heals the leaked claim, because and only "
          "because the completion is durable, and reports the terminal no-checkpoint "
          "outcome rather than a mutable FRESH with a null checkpoint id",
          outcome == co.NO_CHECKPOINT and cp_id is None
          and co.resolve_claim(fx.a, "v2-1-dry-run") is None, f"{outcome} {cp_id}")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# T7 -- adoption cannot be used to steal
# ---------------------------------------------------------------------------
print("\n== T7. adoption against an existing foreign claim ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    co.claim_checkpoint(b, "v2-1-dry-run", "S-CP3", now="t1")
    fx.start_cp3_in_a(claim=False)   # A also has local dirty IN_PROGRESS
    expect("T7. adoption refuses when another worktree already holds the claim",
           lambda: co.adopt_claim(fx.a, "v2-1-dry-run", fx.work_item(fx.a), now="t2",
                                  verify_resume_safety=ws.verify_dirty_resume_safety),
           co.CheckpointOwnedByOtherWorktreeError)
    check("T7b. B's claim is untouched", co.resolve_claim(fx.a, "v2-1-dry-run")["claimed_at"] == "t1")
finally:
    fx.cleanup()

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 7: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
