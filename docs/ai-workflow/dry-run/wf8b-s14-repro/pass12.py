"""Stress pass 12 (revision-65 authoring) -- the two external findings
`OPUS-R82-001` and `OPUS-R82-002`, each reproduced against the revision-64
shape before the fix and re-asserted against the corrected one.

`OPUS-R82-001`: a `"destructive"` guard abandoned by a crashed session whose
worktree was then deleted had **no** documented escape -- the takeover refused
on the class, the malformed-guard clearance refused because the guard was
well-formed, and the only remaining route was hand-deleting a file.

`OPUS-R82-002`: the same class was documented as never breakable "by anyone,
under any authorization" while the same-token reclamation branch broke exactly
that, with no authorization, for any session that could read the claim's
world-readable token -- including one in a *different* worktree.
"""

from __future__ import annotations

import json
import subprocess
import sys
import textwrap
from pathlib import Path

from harness import Fixture, git
import checkpoint_ownership as co

WID = "v2-1-dry-run"
RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f"  -- {detail}" if detail else ""))


def expect(name, fn, exc_type, must_mention=()):
    try:
        fn()
    except exc_type as exc:
        missing = [frag for frag in must_mention if frag not in str(exc)]
        check(name, not missing, str(exc)[:110] if not missing else f"message omits {missing}")
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")
    else:
        check(name, False, "no exception raised")


def abandon_destructive_guard_in_b(fx, *, step="1f-commit"):
    """The exact crash the finding describes: worktree B owns the checkpoint,
    enters step 1f's destructive window, and dies inside it."""
    b = fx.add_worktree_b()
    token = co.claim_checkpoint(b, WID, "S-CP3", now="t1")["owner_token"]
    lease = co.acquire_guard(b, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                             step=step, step_class=co.DESTRUCTIVE, now="t2")
    return b, token, lease


# ---------------------------------------------------------------------------
# H1 -- the abandoned destructive guard, holder worktree deleted
# ---------------------------------------------------------------------------
print("== H1. destructive guard abandoned by a DELETED holder worktree ==")
fx = Fixture()
try:
    b, dead_token, dead_lease = abandon_destructive_guard_in_b(fx)
    git(fx.a, "worktree", "remove", "--force", str(b))
    check("H1a. the holder worktree is genuinely gone", not b.exists())

    ev = co.takeover_evidence(fx.a, WID)
    check("H1b. the evidence reports a deregistered holder rather than concluding anything",
          ev["holder_registered"] is False and ev["holder_path_exists"] is False
          and ev["guard"]["step_class"] == co.DESTRUCTIVE)

    # The two documented revision-64 escapes, both still correct, both still refusing.
    expect("H1c. the ordinary takeover still refuses on the destructive class even with a "
           "complete, correctly bound authorization -- unchanged, and still right",
           lambda: co.take_over_claim(
               fx.a, WID, "S-CP3", now="t3",
               user_authorization=co.takeover_authorization_literal(WID, ev, "S-CP3"),
               evidence=ev,
               guard_release_authorization=co.guard_release_authorization_literal(ev["guard"])),
           co.CheckpointClaimTakeoverRefusedError, must_mention=("abandoned-guard recovery",))
    expect("H1d. the malformed-guard clearance still refuses a well-formed guard -- unchanged, "
           "and still right",
           lambda: co.clear_malformed_guard(
               fx.a, WID,
               user_authorization=co.guard_clearance_authorization_literal(
                   {"work_item_id": WID, "guard_observation_id": ev["guard_observation_id"]})),
           co.CheckpointClaimTakeoverRefusedError)
    check("H1e. CONTROL (revision 64): after both documented escapes the guard is still held and "
          "the work item is unstartable by every documented operation",
          co.read_guard(fx.a, WID) is not None)

    # The revision-65 escape.
    lit = co.abandoned_guard_recovery_authorization_literal(WID, ev)
    check("H1f. the recovery literal is distinct from the takeover literal and names the "
          "abandoned destructive step plus BOTH observations",
          lit != co.takeover_authorization_literal(WID, ev, "S-CP3")
          and "1f-commit" in lit and ev["guard_observation_id"] in lit
          and ev["claim_observation_id"] in lit, lit)
    recovered = co.recover_abandoned_destructive_guard(
        fx.a, WID, "S-CP3", now="t4", user_authorization=lit, evidence=ev)
    check("H1g. the recovery succeeds and rotates the claim to this worktree",
          recovered["owner_token"] != dead_token
          and co.claim_is_this_worktree(fx.a, co.resolve_claim(fx.a, WID)))
    prov = recovered["taken_over_from"]["recovered_from_abandoned_guard"]
    check("H1h. the recovered claim records what it displaced, so a recovery is never "
          "indistinguishable from an ordinary claim or an ordinary takeover afterwards",
          prov["lease_id"] == dead_lease["lease_id"] and prov["step"] == "1f-commit"
          and prov["step_class"] == co.DESTRUCTIVE
          and recovered["taken_over_from"]["owner_token"] == dead_token
          and dead_token in recovered["previous_owner_tokens"])
    check("H1i. the abandoned guard is gone -- cleared by the epoch rule the rotation makes "
          "true, not by a second removal primitive",
          co.read_guard(fx.a, WID) is None)
    # The displaced token is dead in both directions, which is `D-Checkpoint-Ownership`'s own
    # two-error-class rule: rotated-in-place inside the new owner's worktree, foreign anywhere
    # else. The crashed session's own worktree no longer exists, so worktree C stands in for
    # any session that still holds the stale token.
    expect("H1j. inside the recovering worktree the displaced token is a rotated-token mismatch",
           lambda: co.assert_claim_owner(fx.a, WID, dead_token),
           co.CheckpointOwnershipStateMismatchError, must_mention=(dead_token,))
    c = fx.tmp / "C"
    git(fx.a, "worktree", "add", "-q", str(c), "-b", "scratch-C", fx.head)
    expect("H1j2. and any other worktree presenting it is fenced as a foreign owner -- the "
           "crashed session cannot write state, commit or release if it ever comes back",
           lambda: co.assert_claim_owner(c, WID, dead_token),
           co.CheckpointOwnedByOtherWorktreeError)
    with co.owner_mutation(fx.a, WID, recovered["owner_token"], checkpoint_id="S-CP3",
                           step="1f-commit", step_class=co.DESTRUCTIVE, now="t5"):
        pass
    check("H1k. and the recovered worktree can now actually do the guarded work -- the lockout "
          "is gone, not merely reported differently",
          co.read_guard(fx.a, WID) is None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# H2 -- the same, with the holder RELOCATED by `git worktree move`
# ---------------------------------------------------------------------------
print("\n== H2. destructive guard abandoned by a RELOCATED holder worktree ==")
fx = Fixture()
try:
    b, dead_token, _ = abandon_destructive_guard_in_b(fx)
    moved = fx.tmp / "B-moved"
    git(fx.a, "worktree", "move", str(b), str(moved))
    ev = co.takeover_evidence(fx.a, WID)
    check("H2a. a relocated holder is distinguishable from a deleted one: still registered, "
          "recorded path gone, current path reported",
          ev["holder_registered"] is True and ev["holder_path_exists"] is False
          and ev["holder_current_path"] == str(moved), str(ev["holder_current_path"]))
    before = co.claim_path(fx.a, WID).read_bytes()
    expect("H2b. the recovery refuses a still-registered holder and names the operator actions "
           "instead of dead-ending -- no liveness guess is made either way",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP3", now="t3",
               user_authorization=co.abandoned_guard_recovery_authorization_literal(WID, ev),
               evidence=ev),
           co.CheckpointClaimTakeoverRefusedError,
           must_mention=("git worktree remove", "move it back"))
    check("H2c. that refusal mutated nothing", co.claim_path(fx.a, WID).read_bytes() == before
          and co.read_guard(fx.a, WID) is not None)
    # The operator's own decision -- deregistering the worktree -- is the durable fact the
    # recovery binds to. Nothing infers it.
    git(fx.a, "worktree", "remove", "--force", str(moved))
    ev2 = co.takeover_evidence(fx.a, WID)
    recovered = co.recover_abandoned_destructive_guard(
        fx.a, WID, "S-CP3", now="t4",
        user_authorization=co.abandoned_guard_recovery_authorization_literal(WID, ev2),
        evidence=ev2)
    check("H2d. once the operator deregisters the relocated worktree the same bound recovery "
          "applies -- the relocated case has a defined, documented outcome on both branches",
          recovered["owner_token"] != dead_token and co.read_guard(fx.a, WID) is None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# H3 -- the recovery never breaks a live destructive window
# ---------------------------------------------------------------------------
print("\n== H3. a live destructive window is still never broken ==")
fx = Fixture()
try:
    b, live_token, _ = abandon_destructive_guard_in_b(fx)
    ev = co.takeover_evidence(fx.a, WID)
    before = co.claim_path(fx.a, WID).read_bytes()
    expect("H3a. holder worktree still registered (the ordinary crash-in-a-live-worktree case): "
           "refused",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP3", now="t3",
               user_authorization=co.abandoned_guard_recovery_authorization_literal(WID, ev),
               evidence=ev),
           co.CheckpointClaimTakeoverRefusedError, must_mention=("still a registered worktree",))
    check("H3b. and that refusal mutated nothing", co.claim_path(fx.a, WID).read_bytes() == before)

    # The interesting race: deregistered when the human read the evidence, re-registered by
    # the time they authorized. The re-check happens UNDER the guard, not at evidence time.
    git(fx.a, "worktree", "remove", "--force", str(b))
    stale_ev = co.takeover_evidence(fx.a, WID)
    check("H3c. evidence taken while the holder was deregistered would authorize the recovery",
          stale_ev["holder_registered"] is False)
    git(fx.a, "worktree", "add", "-q", str(b), "-b", "scratch-B2", fx.head)
    before = co.claim_path(fx.a, WID).read_bytes()
    expect("H3d. a holder re-registered between the evidence and the authorization is refused "
           "under the guard, not at evidence time",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP3", now="t4",
               user_authorization=co.abandoned_guard_recovery_authorization_literal(
                   WID, stale_ev),
               evidence=stale_ev),
           co.CheckpointClaimTakeoverRefusedError, must_mention=("re-registered",))
    check("H3e. and that refusal mutated nothing, leaving the abandoned guard in place",
          co.claim_path(fx.a, WID).read_bytes() == before
          and co.read_guard(fx.a, WID) is not None)
    check("H3f. the live owner's token is still the claim's token -- nothing was rotated",
          co.resolve_claim(fx.a, WID)["owner_token"] == live_token)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# H4 -- the recovery's authorization binding and its refusals
# ---------------------------------------------------------------------------
print("\n== H4. binding, non-replayability, and wrong-operation refusals ==")
fx = Fixture()
try:
    b, dead_token, _ = abandon_destructive_guard_in_b(fx)
    git(fx.a, "worktree", "remove", "--force", str(b))
    ev = co.takeover_evidence(fx.a, WID)
    before = co.claim_path(fx.a, WID).read_bytes()
    for label, auth in (
        ("absent", None),
        ("empty", ""),
        ("the takeover's own literal", co.takeover_authorization_literal(WID, ev, "S-CP3")),
        ("a literal naming a different step",
         co.abandoned_guard_recovery_authorization_literal(
             WID, {**ev, "guard": {**ev["guard"], "step": "1d-state-write"}})),
        ("a literal bound to a different guard observation",
         co.abandoned_guard_recovery_authorization_literal(
             WID, {**ev, "guard_observation_id": "0" * 64})),
        ("a literal bound to a different claim observation",
         co.abandoned_guard_recovery_authorization_literal(
             WID, {**ev, "claim_observation_id": "0" * 64})),
    ):
        expect(f"H4. {label} is refused",
               lambda auth=auth: co.recover_abandoned_destructive_guard(
                   fx.a, WID, "S-CP3", now="t3", user_authorization=auth, evidence=ev),
               co.CheckpointClaimTakeoverRefusedError)
    check("H4g. every authorization refusal left the claim and the guard untouched",
          co.claim_path(fx.a, WID).read_bytes() == before and co.read_guard(fx.a, WID) is not None)

    good = co.abandoned_guard_recovery_authorization_literal(WID, ev)
    co.recover_abandoned_destructive_guard(fx.a, WID, "S-CP3", now="t4",
                                           user_authorization=good, evidence=ev)
    expect("H4h. the same authorization is not replayable -- the rotation changed the bytes it "
           "was bound to, and the guard it named is gone",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP3", now="t5", user_authorization=good),
           co.CheckpointClaimTakeoverRefusedError)
finally:
    fx.cleanup()

fx = Fixture()
try:
    b, token, _ = abandon_destructive_guard_in_b(fx, step="1f-commit")
    co.release_guard(b, WID, co.read_guard(b, WID))
    co.acquire_guard(b, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                     step="takeover", step_class=co.ORDINARY, now="t2")
    git(fx.a, "worktree", "remove", "--force", str(b))
    ev = co.takeover_evidence(fx.a, WID)
    expect("H4i. an ORDINARY guard is refused by this operation, which points at the takeover's "
           "own guard-release authorization -- one operation per state, never two routes to one",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP3", now="t3",
               user_authorization=co.abandoned_guard_recovery_authorization_literal(WID, ev),
               evidence=ev),
           co.CheckpointClaimTakeoverRefusedError, must_mention=("ordinary guard is released",))
finally:
    fx.cleanup()

fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    co.acquire_guard(fx.a, WID, holder_owner_token="f" * 32, checkpoint_id="S-CP3",
                     step="1f-commit", step_class=co.DESTRUCTIVE, now="t2")
    ev = co.takeover_evidence(fx.a, WID)
    expect("H4j. a SUPERSEDED-epoch destructive guard is refused by this operation too: it is "
           "already reclaimable with no authorization, and a second route to it would be a way "
           "to normalize breaking the class",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP3", now="t3",
               user_authorization=co.abandoned_guard_recovery_authorization_literal(WID, ev),
               evidence=ev),
           co.CheckpointClaimTakeoverRefusedError, must_mention=("superseded epoch",))
finally:
    fx.cleanup()

fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    co.acquire_guard(fx.a, WID, holder_owner_token=fx.owner_token, checkpoint_id="S-CP3",
                     step="1f-commit", step_class=co.DESTRUCTIVE, now="t2")
    ev = co.takeover_evidence(fx.a, WID)
    expect("H4k. a current-epoch destructive guard held by THIS worktree is refused -- an "
           "interrupted step of one's own epoch is re-entered, never 'recovered'",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP3", now="t3",
               user_authorization=co.abandoned_guard_recovery_authorization_literal(WID, ev),
               evidence=ev),
           co.CheckpointClaimTakeoverRefusedError, must_mention=("held by this worktree",))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# H5 -- `OPUS-R82-002`: the corrected scope of the never-breakable class
# ---------------------------------------------------------------------------
print("\n== H5. what 'never breakable' actually means, asserted at the mechanism ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    here = Path(__file__).parent
    runner = fx.tmp / "second_session.py"
    runner.write_text(textwrap.dedent(f"""
        import sys, json
        sys.path.insert(0, {str(here)!r})
        import checkpoint_ownership as co
        from pathlib import Path
        try:
            lease = co.acquire_guard(Path(sys.argv[1]), {WID!r},
                                     holder_owner_token=sys.argv[2], checkpoint_id="S-CP3",
                                     step="1f-commit", step_class=co.DESTRUCTIVE, now="tS")
            print(json.dumps({{"ok": True, "lease": lease["lease_id"]}}))
        except Exception as exc:
            print(json.dumps({{"ok": False, "err": type(exc).__name__}}))
    """))

    l1 = co.acquire_guard(fx.a, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                          step="1f-commit", step_class=co.DESTRUCTIVE, now="t2")
    out = json.loads(subprocess.run([sys.executable, str(runner), str(fx.a), token],
                                    capture_output=True, text=True, check=True).stdout)
    check("H5a. two sessions sharing ONE worktree identity and token do NOT serialize: the "
          "second, in a real OS process, reclaims the first's live destructive guard with no "
          "authorization. This is the stated scope limit, asserted as it is",
          out["ok"] is True and out["lease"] != l1["lease_id"], str(out))
    check("H5b. CONTROL ARM (the shape the plan must NOT claim): the two windows are open "
          "simultaneously, so 'they serialize on the guard' is false",
          co.read_guard(fx.a, WID)["lease_id"] == out["lease"]
          and co.assert_claim_owner(fx.a, WID, token) is not None)

    # The other half: a DIFFERENT worktree presenting the token it read from the claim file.
    b = fx.add_worktree_b()
    held = co.read_guard(fx.a, WID)
    token_from_file = co.resolve_claim(b, WID)["owner_token"]
    check("H5c. the token really is world-readable coordination data any worktree can present",
          token_from_file == token)
    check("H5d. CONTROL ARM (revision 64): its entire reclamation condition -- token equality "
          "alone -- is satisfied by that foreign worktree, so revision 64 broke a live "
          "destructive window here",
          held["holder_owner_token"] == token_from_file)
    out2 = json.loads(subprocess.run([sys.executable, str(runner), str(b), token_from_file],
                                     capture_output=True, text=True, check=True).stdout)
    check("H5e. revision 65 refuses it at the guard itself, before any mutation -- 'never "
          "breakable by another worktree' is now mechanically true rather than a consequence "
          "of step 1c",
          out2["ok"] is False and out2["err"] == "CheckpointOwnershipUnavailableError", str(out2))
    check("H5f. and the first session's window survived that refusal intact",
          co.read_guard(fx.a, WID)["lease_id"] == out["lease"])
finally:
    fx.cleanup()

fx = Fixture()
try:
    # Item 368(f)'s superseded-epoch rule, re-asserted against the CORRECTED absolute. It is
    # a reclamation of a *destructive* guard, by a different worktree, with no authorization,
    # and it must stay: it is the crash-recovery path for a session that acquired a guard and
    # died before its own assertion could fence it.
    b = fx.add_worktree_b()
    displaced = co.claim_checkpoint(b, WID, "S-CP3", now="t1")["owner_token"]
    ev = co.takeover_evidence(fx.a, WID)
    new_owner = co.take_over_claim(
        fx.a, WID, "S-CP3", now="t2",
        user_authorization=co.takeover_authorization_literal(WID, ev, "S-CP3"), evidence=ev)
    # B, not yet aware it was displaced, acquires a destructive guard and dies before its own
    # `assert_claim_owner` runs -- the one way a superseded destructive guard really arises.
    orphan = co.acquire_guard(b, WID, holder_owner_token=displaced, checkpoint_id="S-CP3",
                              step="1f-commit", step_class=co.DESTRUCTIVE, now="t3")
    check("H5g. the orphaned guard is destructive and was left by a DIFFERENT worktree",
          co.read_guard(fx.a, WID)["step_class"] == co.DESTRUCTIVE
          and orphan["holder_worktree_git_dir"] != co._worktree_git_dir(fx.a))
    with co.owner_mutation(fx.a, WID, new_owner["owner_token"], checkpoint_id="S-CP3",
                           step="1f-commit", step_class=co.DESTRUCTIVE, now="t4"):
        pass
    check("H5h. the current owner reclaims it with no authorization, across worktrees, because "
          "the epoch -- not the worktree -- proves the holder's ownership was already rotated "
          "away; narrowing THIS branch by worktree would recreate the lockout",
          co.read_guard(fx.a, WID) is None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# H6 -- the real repository is still untouched
# ---------------------------------------------------------------------------
print("\n== H6. real-repository re-verification (read-only) ==")
REPO = Path(git(Path(__file__).parent, "rev-parse", "--show-toplevel"))
check("H6a. no claim record exists anywhere in the real repository",
      co.resolve_claim(REPO, WID) is None and co.resolve_claim(REPO, "workflow-v2-1-core") is None)
check("H6b. and no mutation guard either",
      co.read_guard(REPO, WID) is None and co.read_guard(REPO, "workflow-v2-1-core") is None)
check("H6c. the claims directory itself was never created in this repository",
      not co.claims_dir(REPO).exists())
state = json.loads((REPO / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
cp3 = state["work_items"][WID]["checkpoints"].get("S-CP3")
check("H6d. the live interrupted S-CP3 is preserved exactly as found",
      cp3 == {"status": "IN_PROGRESS", "start_commit": "8375b64f9ad9ad44afe7574841a62457f5d83cea"},
      json.dumps(cp3))

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 12: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
