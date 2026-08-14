"""Stress pass 11 (revision-64 authoring) -- adversarial attack on what pass
10's fixes introduced. A fix that opens a new hole is not a fix, and the guard
is a brand-new shared object with its own failure surface: it can be planted,
corrupted, symlinked, abandoned, or used to lock a legitimate owner out.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import textwrap
from pathlib import Path

from harness import Fixture, git, step1_fixed, ws
import checkpoint_ownership as co

WID = "v2-1-dry-run"
RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f"  -- {detail}" if detail else ""))


def expect(name, fn, exc_type):
    try:
        fn()
    except exc_type as exc:
        check(name, True, str(exc)[:80])
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")
    else:
        check(name, False, "no exception raised")


def authorize(root, checkpoint_id):
    ev = co.takeover_evidence(root, WID)
    return ev, co.takeover_authorization_literal(WID, ev, checkpoint_id)


# ---------------------------------------------------------------------------
# G1 -- a planted foreign guard must not lock the legitimate owner out
# ---------------------------------------------------------------------------
print("== G1. a guard planted by a session that owns nothing ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    co.acquire_guard(fx.a, WID, holder_owner_token="f" * 32, checkpoint_id="S-CP3",
                     step="1f-commit", step_class=co.DESTRUCTIVE, now="tX")
    with co.owner_mutation(fx.a, WID, token, checkpoint_id="S-CP3", step="1d-state-write",
                           step_class=co.DESTRUCTIVE, now="t2"):
        pass
    check("G1. a guard whose holder token is not the claim's current token is superseded by "
          "construction, so it cannot become a permanent false lock",
          co.read_guard(fx.a, WID) is None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# G2 -- an externally corrupted guard: fail closed, but with a defined recovery
# ---------------------------------------------------------------------------
print("\n== G2. a corrupted guard record ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    co.guard_path(fx.a, WID).write_text("{ not json")

    def owner_tries():
        with co.owner_mutation(fx.a, WID, token, checkpoint_id="S-CP3",
                               step="1d-state-write", step_class=co.DESTRUCTIVE, now="t2"):
            pass
    expect("G2. an undecidable guard fails closed for the owner rather than being read as "
           "absent", owner_tries, co.CheckpointOwnershipUnavailableError)
    check("G2b. and the corrupt guard is still there -- a release path never silently "
          "deletes a record it could not decide",
          co.guard_path(fx.a, WID).exists())
    ev = co.takeover_evidence(fx.a, WID)
    check("G2c. the evidence names the malformed guard and its observation id, so the "
          "recovery is decidable rather than guesswork",
          ev.get("guard_error") is not None
          and ev.get("guard_observation_id") not in (None, co.ABSENT_OBSERVATION))
    expect("G2d. and clearing it requires its own literal authorization -- never automatic",
           lambda: co.clear_malformed_guard(fx.a, WID, user_authorization="just clear it"),
           co.CheckpointClaimTakeoverRefusedError)
    co.clear_malformed_guard(
        fx.a, WID, user_authorization=co.guard_clearance_authorization_literal(ev))
    with co.owner_mutation(fx.a, WID, token, checkpoint_id="S-CP3", step="1d-state-write",
                           step_class=co.DESTRUCTIVE, now="t3"):
        pass
    check("G2e. once explicitly cleared, the legitimate owner proceeds normally",
          co.read_guard(fx.a, WID) is None
          and co.resolve_claim(fx.a, WID)["owner_token"] == token)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# G3 -- the guard path is not followed through a symlink either
# ---------------------------------------------------------------------------
print("\n== G3. a symlink planted at the guard path ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    outside = fx.tmp / "outside.json"
    outside.write_text(json.dumps({"lease_id": "0" * 32, "holder_owner_token": "z" * 32,
                                   "step": "evil", "step_class": "ordinary"}))
    co.guard_path(fx.a, WID).symlink_to(outside)
    expect("G3. a symlinked guard fails closed on read",
           lambda: co.read_guard(fx.a, WID), co.CheckpointOwnershipUnavailableError)
    expect("G3b. and on publication",
           lambda: co.acquire_guard(fx.a, WID, holder_owner_token=fx.owner_token,
                                    checkpoint_id="S-CP3", step="1d-state-write",
                                    step_class=co.DESTRUCTIVE, now="t2"),
           co.CheckpointOwnershipUnavailableError)
    check("G3c. the off-tree target is untouched", json.loads(outside.read_text())["step"]
          == "evil")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# G4 -- placement: asserting *before* the guard is not a barrier
# ---------------------------------------------------------------------------
print("\n== G4. assert-then-guard (the shape GPT-R81-001 rejects) vs. guard-then-assert ==")
for arm in ("assert outside the guard CONTROL", "assert inside the guard"):
    fx = Fixture()
    try:
        fx.start_cp3_in_a(claim=True)
        token = fx.owner_token
        b = fx.add_worktree_b()
        state_before = (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes()
        if arm.startswith("assert outside"):
            co.assert_claim_owner(fx.a, WID, token)            # check-before-use
            ev, auth = authorize(b, "S-CP3")
            co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev, user_authorization=auth)
            fx.write_state(fx.a, ws.transition_checkpoint_in_progress(
                fx.read_state(fx.a), WID, "S-CP3", git(fx.a, "rev-parse", "HEAD"), "t6"))
            check("G4[CONTROL]. an assertion made before the window is stale by the time the "
                  "mutation lands -- the displaced owner still wrote",
                  (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes() != state_before)
        else:
            def guarded():
                with co.owner_mutation(fx.a, WID, token, checkpoint_id="S-CP3",
                                       step="1d-state-write", step_class=co.DESTRUCTIVE,
                                       now="t6"):
                    fx.write_state(fx.a, ws.transition_checkpoint_in_progress(
                        fx.read_state(fx.a), WID, "S-CP3", git(fx.a, "rev-parse", "HEAD"), "t6"))
            ev, auth = authorize(b, "S-CP3")
            co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev, user_authorization=auth)
            expect("G4. re-asserting inside the window refuses instead", guarded,
                   co.CheckpointOwnedByOtherWorktreeError)
            check("G4b. and nothing was written",
                  (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes() == state_before)
    finally:
        fx.cleanup()

# ---------------------------------------------------------------------------
# G5 -- the guard is released on every exit path, including a failing mutation
# ---------------------------------------------------------------------------
print("\n== G5. guard lifetime ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token

    def boom():
        with co.owner_mutation(fx.a, WID, token, checkpoint_id="S-CP3",
                               step="1d-state-write", step_class=co.DESTRUCTIVE, now="t2"):
            raise RuntimeError("the mutation itself failed")
    expect("G5. a mutation that raises inside the window still releases the guard", boom,
           RuntimeError)
    check("G5b. no guard is left behind", co.read_guard(fx.a, WID) is None)
    check("G5c. and the next window opens normally",
          (lambda: [co.acquire_guard(fx.a, WID, holder_owner_token=token,
                                     checkpoint_id="S-CP3", step="1f-release",
                                     step_class=co.ORDINARY, now="t3"),
                    co.read_guard(fx.a, WID) is not None][-1])())
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# G6 -- an authorized ordinary-guard break still fences the broken session
# ---------------------------------------------------------------------------
print("\n== G6. authorized break of an ORDINARY guard ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    b = fx.add_worktree_b()
    stale = co.acquire_guard(fx.a, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                             step="1f-release", step_class=co.ORDINARY, now="t2")
    ev, auth = authorize(b, "S-CP3")
    expect("G6. without the guard-release literal, the takeover refuses even for an ordinary "
           "guard",
           lambda: co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev,
                                      user_authorization=auth),
           co.CheckpointClaimTakeoverRefusedError)
    co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev, user_authorization=auth,
                       guard_release_authorization=co.guard_release_authorization_literal(
                           ev["guard"]))
    check("G6b. with it, the takeover proceeds",
          co.resolve_claim(b, WID)["worktree_root"] == str(b.resolve()))
    co.release_guard(fx.a, WID, stale)
    check("G6c. and the broken session cannot delete the new owner's guard state: its own "
          "release is compare-and-delete on the lease it holds",
          co.resolve_claim(b, WID) is not None)
    expect("G6d. while every mutation it attempts is fenced",
           lambda: co.release_checkpoint(fx.a, WID, "S-CP3", owner_token=token),
           co.CheckpointOwnedByOtherWorktreeError)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# G7 -- a guard whose lease was quoted, then replaced: the owner is live
# ---------------------------------------------------------------------------
print("\n== G7. the quoted lease no longer matches ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    b = fx.add_worktree_b()
    first = co.acquire_guard(fx.a, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                             step="1f-release", step_class=co.ORDINARY, now="t2")
    ev, auth = authorize(b, "S-CP3")
    quoted = co.guard_release_authorization_literal(ev["guard"])
    co.release_guard(fx.a, WID, first)
    co.acquire_guard(fx.a, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                     step="1f-release", step_class=co.ORDINARY, now="t3")   # owner progressed
    expect("G7. an owner that released and re-acquired between authorization and takeover is "
           "demonstrably live, so the takeover refuses rather than breaking a fresh guard",
           lambda: co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev,
                                      user_authorization=auth,
                                      guard_release_authorization=quoted),
           co.CheckpointClaimTakeoverRefusedError)
    check("G7b. the owner's claim is untouched",
          co.resolve_claim(fx.a, WID)["owner_token"] == token)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# G8 -- same-worktree concurrency: honestly NOT fenced (the header said
# "serialized" through revision 64, which `OPUS-R82-002` correctly read as a
# claim this mechanism does not have; the checks below always asserted the
# reclamation, and pass12 `H5a`/`H5b` now assert it as the contract)
# ---------------------------------------------------------------------------
print("\n== G8. two sessions in the SAME worktree (a stated scope limit) ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    here = Path(__file__).parent
    runner = fx.tmp / "same_worktree.py"
    runner.write_text(textwrap.dedent(f"""
        import sys, json
        sys.path.insert(0, {str(here)!r})
        import checkpoint_ownership as co
        from pathlib import Path
        try:
            with co.owner_mutation(Path(sys.argv[1]), {WID!r}, sys.argv[2],
                                   checkpoint_id="S-CP3", step="1d-state-write",
                                   step_class=co.DESTRUCTIVE, now="tS"):
                print(json.dumps({{"ok": True}}))
        except Exception as exc:
            print(json.dumps({{"ok": False, "err": type(exc).__name__}}))
    """))
    co.acquire_guard(fx.a, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                     step="1f-commit", step_class=co.DESTRUCTIVE, now="t2")
    out = json.loads(subprocess.run([sys.executable, str(runner), str(fx.a), token],
                                    capture_output=True, text=True, check=True).stdout)
    check("G8. a second session in the same worktree holding the same token reclaims its "
          "own epoch's guard -- the contract is cross-WORKTREE ownership and says so, it "
          "does not pretend to fence two sessions that share one identity",
          out["ok"] is True, str(out))
    out2 = json.loads(subprocess.run([sys.executable, str(runner), str(fx.a), "9" * 32],
                                     capture_output=True, text=True, check=True).stdout)
    check("G8b. but a session presenting a token the claim does not carry is refused, "
          "whatever worktree it runs in",
          out2["ok"] is False and out2["err"] == "CheckpointOwnershipStateMismatchError",
          str(out2))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# G9 -- the whole S14/S15 path still holds, end to end, under the guard
# ---------------------------------------------------------------------------
print("\n== G9. S14 -> S15 regression under revision 64 ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    b = fx.add_worktree_b()
    before = fx.snapshot()
    expect("G9. S14: the foreign worktree is still refused by verify_dirty_resume_safety",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
    after = fx.snapshot()
    check("G9b. and its refusal mutated nothing anywhere, byte-compared", before == after)
    check("G9c. no guard was created by the refusal either",
          co.read_guard(fx.a, WID) is None)
    out = step1_fixed(fx, fx.a)
    check("G9d. S15: the originating worktree still resumes", out["mode"] == "resume")
    check("G9e. resuming created no guard residue", co.read_guard(fx.a, WID) is None)
    check("G9f. the claim record is untouched by the resume",
          co.resolve_claim(fx.a, WID)["owner_token"] == fx.owner_token)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# G10 -- the real repository is still untouched by any of this
# ---------------------------------------------------------------------------
print("\n== G10. real-repository re-verification (read-only) ==")
REPO = Path(git(Path(__file__).parent, "rev-parse", "--show-toplevel"))
check("G10. no claim record exists anywhere in the real repository",
      co.resolve_claim(REPO, WID) is None and co.resolve_claim(REPO, "workflow-v2-1-core") is None)
check("G10b. and no mutation guard either",
      co.read_guard(REPO, WID) is None and co.read_guard(REPO, "workflow-v2-1-core") is None)
claims = co.claims_dir(REPO)
check("G10c. the claims directory itself was never created in this repository",
      not claims.exists(), str(claims))
state = json.loads((REPO / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
cp3 = state["work_items"][WID]["checkpoints"].get("S-CP3")
check("G10d. the live interrupted S-CP3 is preserved exactly as found",
      cp3 == {"status": "IN_PROGRESS", "start_commit": "8375b64f9ad9ad44afe7574841a62457f5d83cea"},
      json.dumps(cp3))

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 11: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
