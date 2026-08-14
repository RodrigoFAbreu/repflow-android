"""Stress pass 10 (revision-64 authoring) -- the three blocking findings of the
external plan review of revision 63 (`GPT-R81-001`/`-002`/`-003`), each
reproduced against the revision-63 shape first and then re-run against the
revision-64 fix.

Every reproduction arm here is an executable statement about revision 63, not a
claim about code that no longer exists: `mutate_r63`, `release_r63` and
`take_over_r63` replicate exactly what revision 63's prototype did at those
three points, and each is run in the same fixture as its fixed counterpart.
"""

from __future__ import annotations

import json
import subprocess
import sys
import textwrap
from pathlib import Path

from harness import REGISTRY, Fixture, git, step1_fixed, step1f_commit, ws
import checkpoint_ownership as co

WID = "v2-1-dry-run"
RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f"  -- {detail}" if detail else ""))


def expect(name, fn, exc_type, detail=""):
    try:
        fn()
    except exc_type as exc:
        check(name, True, (detail + " " if detail else "") + str(exc)[:80])
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")
    else:
        check(name, False, "no exception raised")


# --- revision-63 arms, replicated exactly ---------------------------------

def mutate_r63(fx, root, checkpoint_id):
    """Revision 63's step 1d/1f mutation: no assertion, no guard -- ownership
    was resolved once at 1c and never re-checked."""
    state = ws.transition_checkpoint_in_progress(
        fx.read_state(root), WID, checkpoint_id, git(root, "rev-parse", "HEAD"), "tR63")
    fx.write_state(root, state)


def release_r63(root, on_read=None):
    """Revision 63's release: read the claim, check the worktree, then unlink
    the pathname. `on_read` injects the interleaving the finding describes."""
    existing = co.resolve_claim(root, WID)
    if existing is None:
        return
    if not co.claim_is_this_worktree(root, existing):
        raise co.CheckpointOwnedByOtherWorktreeError("foreign claim")
    if on_read is not None:
        on_read()
    co.claim_path(root, WID).unlink(missing_ok=True)


def take_over_r63(root, checkpoint_id, *, now, user_authorization):
    """Revision 63's takeover authorization: a literal naming the work item and
    the checkpoint *being claimed*, bound to no observed record at all."""
    if user_authorization != f"take over {WID} {checkpoint_id}":
        raise co.CheckpointClaimTakeoverRefusedError("bad authorization")
    previous = co.resolve_claim(root, WID)
    record = co._build_record(root, WID, checkpoint_id, now, taken_over_from={
        "checkpoint_id": previous.get("checkpoint_id") if previous else None})
    co._publish_replacing(co.claim_path(root, WID), record)
    return record


def authorize(root, checkpoint_id):
    ev = co.takeover_evidence(root, WID)
    return ev, co.takeover_authorization_literal(WID, ev, checkpoint_id)


# ---------------------------------------------------------------------------
# F1 -- GPT-R81-001: a displaced owner must not be able to write state
# ---------------------------------------------------------------------------
print("== F1. takeover vs. the displaced owner's step-1d state write ==")
for arm in ("revision 63 CONTROL", "revision 64"):
    fx = Fixture()
    try:
        fx.start_cp3_in_a(claim=True)
        b = fx.add_worktree_b()
        wi = fx.work_item(fx.a)
        sel = ws.select_next_checkpoint(wi, REGISTRY)
        mode, cp_id, token = co.resolve_ownership(          # A's step 1c: authorized
            fx.a, wi, WID, sel, verify_resume_safety=ws.verify_dirty_resume_safety, now="t1")
        check(f"F1[{arm}]. A resolves ownership and is authorized to resume",
              (mode, cp_id) == (co.RESUME, "S-CP3") and token is not None)
        ev, auth = authorize(b, "S-CP3")
        co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev, user_authorization=auth)
        check(f"F1b[{arm}]. B's authorized takeover now owns the claim",
              co.resolve_claim(b, WID)["worktree_root"] == str(b.resolve()))
        before = (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes()
        if arm.startswith("revision 63"):
            mutate_r63(fx, fx.a, cp_id)
            after = (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes()
            check("F1c[revision 63 CONTROL]. the displaced owner still wrote authoritative "
                  "state after ownership had transferred -- the reproduced defect",
                  after != before)
        else:
            def a_mutates():
                with co.owner_mutation(fx.a, WID, token, checkpoint_id=cp_id,
                                       step="1d-state-write", step_class=co.DESTRUCTIVE,
                                       now="t6"):
                    mutate_r63(fx, fx.a, cp_id)
            expect("F1c[revision 64]. the displaced owner is fenced: its assertion inside the "
                   "guard fails and it writes nothing", a_mutates,
                   co.CheckpointOwnedByOtherWorktreeError)
            after = (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes()
            check("F1d[revision 64]. zero A-side authoritative mutation, byte-compared",
                  after == before)
            check("F1e[revision 64]. and the refusal left no guard behind",
                  co.read_guard(fx.a, WID) is None)
    finally:
        fx.cleanup()

# ---------------------------------------------------------------------------
# F2 -- GPT-R81-001: a displaced owner must not be able to commit the checkpoint
# ---------------------------------------------------------------------------
print("\n== F2. takeover vs. the displaced owner's step-1f checkpoint commit ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    b = fx.add_worktree_b()
    ev, auth = authorize(b, "S-CP3")
    co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev, user_authorization=auth)
    head_before = git(fx.a, "rev-parse", "HEAD")
    expect("F2. the displaced owner cannot produce a completion commit",
           lambda: step1f_commit(fx, fx.a, "S-CP3", token, now="t9"),
           co.CheckpointOwnedByOtherWorktreeError)
    check("F2b. A's HEAD is unchanged -- no completion commit exists",
          git(fx.a, "rev-parse", "HEAD") == head_before)
    check("F2c. and the checkpoint is still IN_PROGRESS in A's own state file, not COMPLETE",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS")
    check("F2d. B, the new owner, can complete the same checkpoint",
          co.assert_claim_owner(b, WID, co.resolve_claim(b, WID)["owner_token"]) is not None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# F3 -- GPT-R81-001: release is compare-and-delete, not read-then-unlink
# ---------------------------------------------------------------------------
print("\n== F3. takeover interleaved with the displaced owner's release ==")
for arm in ("revision 63 CONTROL", "revision 64"):
    fx = Fixture()
    try:
        fx.start_cp3_in_a(claim=True)
        token = fx.owner_token
        b = fx.add_worktree_b()
        if arm.startswith("revision 63"):
            def interleave():
                ev, auth = authorize(b, "S-CP3")
                co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev,
                                   user_authorization=auth)
            release_r63(fx.a, on_read=interleave)
            check("F3[revision 63 CONTROL]. a takeover between the release's read and its "
                  "unlink let the displaced owner delete the NEW owner's claim -- the "
                  "reproduced defect", co.resolve_claim(fx.a, WID) is None)
        else:
            ev, auth = authorize(b, "S-CP3")
            co.take_over_claim(b, WID, "S-CP3", now="t5", evidence=ev, user_authorization=auth)
            new_token = co.resolve_claim(b, WID)["owner_token"]
            expect("F3[revision 64]. the displaced owner's release refuses",
                   lambda: co.release_checkpoint(fx.a, WID, "S-CP3", owner_token=token),
                   co.CheckpointOwnedByOtherWorktreeError)
            surviving = co.resolve_claim(fx.a, WID)
            check("F3b[revision 64]. the replacement owner's claim survives intact",
                  surviving is not None and surviving["owner_token"] == new_token)
            check("F3c[revision 64]. and the new owner can still release its own claim",
                  co.release_checkpoint(b, WID, "S-CP3", owner_token=new_token) is None
                  and co.resolve_claim(b, WID) is None)
    finally:
        fx.cleanup()

# ---------------------------------------------------------------------------
# F4 -- GPT-R81-001: the guard is the barrier, proved with real processes
# ---------------------------------------------------------------------------
print("\n== F4. two real processes: owner inside its guard vs. takeover ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    b = fx.add_worktree_b()
    here = Path(__file__).parent
    script = textwrap.dedent(f"""
        import sys, json
        sys.path.insert(0, {str(here)!r})
        import checkpoint_ownership as co
        from pathlib import Path
        root = Path(sys.argv[1])
        ev = co.takeover_evidence(root, {WID!r})
        auth = co.takeover_authorization_literal({WID!r}, ev, "S-CP3")
        try:
            co.take_over_claim(root, {WID!r}, "S-CP3", now="tP", evidence=ev,
                               user_authorization=auth,
                               guard_release_authorization=(
                                   co.guard_release_authorization_literal(ev["guard"])
                                   if ev.get("guard") else None))
            print(json.dumps({{"ok": True}}))
        except Exception as exc:
            print(json.dumps({{"ok": False, "err": type(exc).__name__, "msg": str(exc)[:90]}}))
    """)
    runner = fx.tmp / "takeover.py"
    runner.write_text(script)
    # A is inside its destructive step-1d window; B attempts a fully authorized takeover.
    with co.owner_mutation(fx.a, WID, token, checkpoint_id="S-CP3", step="1d-state-write",
                           step_class=co.DESTRUCTIVE, now="t6"):
        out = json.loads(subprocess.run([sys.executable, str(runner), str(b)],
                                        capture_output=True, text=True, check=True).stdout)
        check("F4. a takeover attempted while the owner holds a DESTRUCTIVE guard is refused "
              "outright -- no authorization releases it",
              out["ok"] is False and out["err"] == "CheckpointClaimTakeoverRefusedError",
              out.get("msg", ""))
        check("F4b. and the owner's claim is untouched while it is inside its window",
              co.resolve_claim(fx.a, WID)["owner_token"] == token)
    # Once the owner releases the guard, the same takeover succeeds.
    out = json.loads(subprocess.run([sys.executable, str(runner), str(b)],
                                    capture_output=True, text=True, check=True).stdout)
    check("F4c. once the owner leaves its window the same takeover proceeds normally",
          out["ok"] is True and co.resolve_claim(fx.a, WID)["worktree_root"] == str(b.resolve()),
          str(out))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# F5 -- GPT-R81-001: two simultaneous authorized takeovers, one winner
# ---------------------------------------------------------------------------
print("\n== F5. simultaneous authorized takeovers ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    displaced_token = fx.owner_token
    b = fx.add_worktree_b()
    c = fx.add_worktree_b("C")
    here = Path(__file__).parent
    runner = fx.tmp / "race_takeover.py"
    runner.write_text(textwrap.dedent(f"""
        import sys, json
        sys.path.insert(0, {str(here)!r})
        import checkpoint_ownership as co
        from pathlib import Path
        root = Path(sys.argv[1])
        ev = co.takeover_evidence(root, {WID!r})
        auth = co.takeover_authorization_literal({WID!r}, ev, "S-CP3")
        try:
            r = co.take_over_claim(root, {WID!r}, "S-CP3", now=sys.argv[2], evidence=ev,
                                   user_authorization=auth)
            print(json.dumps({{"ok": True, "worktree": r["worktree_root"],
                              "token": r["owner_token"]}}))
        except Exception as exc:
            print(json.dumps({{"ok": False, "err": type(exc).__name__}}))
    """))
    procs = [subprocess.Popen([sys.executable, str(runner), str(root), f"tR{i}"],
                              stdout=subprocess.PIPE, text=True)
             for i, root in enumerate((b, c))]
    outs = [json.loads(p.communicate()[0].strip()) for p in procs]
    winners = [o for o in outs if o["ok"]]
    check("F5. exactly one of two simultaneous authorized takeovers wins",
          len(winners) == 1, str(outs))
    claim = co.resolve_claim(fx.a, WID)
    check("F5b. the surviving claim is the winner's, complete and parseable",
          claim is not None and claim["worktree_root"] == winners[0]["worktree"]
          and claim["owner_token"] == winners[0]["token"])
    check("F5c. the loser refused on evidence or on the guard, never by force",
          all(o["err"] in ("CheckpointClaimTakeoverRefusedError",
                           "CheckpointOwnershipUnavailableError")
              for o in outs if not o["ok"]), str(outs))
    check("F5d. the winner can proceed: its own token asserts successfully",
          co.assert_claim_owner(Path(claim["worktree_root"]), WID, claim["owner_token"])
          is not None)
    expect("F5e. and the originally displaced owner is fenced from every mutation",
           lambda: co.release_checkpoint(fx.a, WID, "S-CP3", owner_token=displaced_token),
           co.CheckpointOwnedByOtherWorktreeError)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# F6 -- GPT-R81-001: guard crash recovery is decided from durable data alone
# ---------------------------------------------------------------------------
print("\n== F6. abandoned-guard recovery without a wall clock ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    token = fx.owner_token
    # (a) the owner's own leftover guard from an interrupted step
    co.acquire_guard(fx.a, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                     step="1d-state-write", step_class=co.DESTRUCTIVE, now="t6")
    lease = co.acquire_guard(fx.a, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                             step="1d-state-write", step_class=co.DESTRUCTIVE, now="t7")
    check("F6. an owner reclaims its own leftover guard deterministically, with no age "
          "threshold and no liveness probe", lease["lease_id"] is not None)
    co.release_guard(fx.a, WID, lease)
    # (b) a guard left behind by an epoch that a takeover has already ended
    b = fx.add_worktree_b()
    ev, auth = authorize(b, "S-CP3")
    co.take_over_claim(b, WID, "S-CP3", now="t8", evidence=ev, user_authorization=auth)
    new_token = co.resolve_claim(b, WID)["owner_token"]
    co.acquire_guard(fx.a, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                     step="1f-commit", step_class=co.DESTRUCTIVE, now="t9")   # superseded epoch
    lease2 = co.acquire_guard(b, WID, holder_owner_token=new_token, checkpoint_id="S-CP3",
                              step="1d-state-write", step_class=co.DESTRUCTIVE, now="t10")
    check("F6b. a superseded-epoch guard -- even a DESTRUCTIVE one -- is reclaimed by the "
          "current owner with no authorization and no wall clock, because only a rotation "
          "under this same guard can have ended that epoch", lease2["lease_id"] is not None)
    co.release_guard(b, WID, lease2)
    check("F6c. reclaiming is compare-and-delete: releasing a lease this session does not "
          "hold removes nothing",
          (lambda: (co.acquire_guard(b, WID, holder_owner_token=new_token,
                                     checkpoint_id="S-CP3", step="1f-release",
                                     step_class=co.ORDINARY, now="t11"),
                    co.release_guard(b, WID, {"lease_id": "0" * 32}),
                    co.read_guard(b, WID) is not None)[-1])())
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# F7 -- GPT-R81-002: authorization binds to the exact observed claim
# ---------------------------------------------------------------------------
print("\n== F7. takeover authorization is bound to the reviewed claim ==")
for arm in ("revision 63 CONTROL", "revision 64"):
    fx = Fixture()
    try:
        b = fx.add_worktree_b()
        co.claim_checkpoint(b, WID, "S-CP2", now="t1")     # the installed claim holds S-CP2
        if arm.startswith("revision 63"):
            take_over_r63(fx.a, "S-CP3", now="t5", user_authorization=f"take over {WID} S-CP3")
            check("F7[revision 63 CONTROL]. an authorization naming S-CP3 displaced a claim "
                  "holding S-CP2 that the user never reviewed -- the reproduced defect",
                  co.resolve_claim(fx.a, WID)["checkpoint_id"] == "S-CP3")
        else:
            expect("F7[revision 64]. the revision-63 literal no longer authorizes anything",
                   lambda: co.take_over_claim(fx.a, WID, "S-CP3", now="t5",
                                              user_authorization=f"take over {WID} S-CP3"),
                   co.CheckpointClaimTakeoverRefusedError)
            ev = co.takeover_evidence(fx.a, WID)
            wrong = (f"take over {WID} claim {ev['claim_observation_id']} holding S-CP3 "
                     f"as S-CP3")
            expect("F7b[revision 64]. an authorization that misnames the checkpoint the "
                   "observed claim holds is refused",
                   lambda: co.take_over_claim(fx.a, WID, "S-CP3", now="t5", evidence=ev,
                                              user_authorization=wrong),
                   co.CheckpointClaimTakeoverRefusedError)
            check("F7c[revision 64]. and the S-CP2 claim is untouched after every refusal",
                  co.resolve_claim(fx.a, WID)["checkpoint_id"] == "S-CP2")
            right = co.takeover_authorization_literal(WID, ev, "S-CP3")
            record = co.take_over_claim(fx.a, WID, "S-CP3", now="t5", evidence=ev,
                                        user_authorization=right)
            check("F7d[revision 64]. the correctly bound authorization names what it "
                  "displaces, so nothing is displaced silently",
                  record["taken_over_from"]["checkpoint_id"] == "S-CP2"
                  and record["taken_over_from"]["claim_observation_id"]
                  == ev["claim_observation_id"])
    finally:
        fx.cleanup()

# ---------------------------------------------------------------------------
# F8 -- GPT-R81-002: stale evidence, replay, and the exactness of taken_over_from
# ---------------------------------------------------------------------------
print("\n== F8. stale evidence and replay ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    co.claim_checkpoint(b, WID, "S-CP3", now="t1")
    ev, auth = authorize(fx.a, "S-CP3")                     # the human reads the evidence...
    displaced_before = co.claim_path(fx.a, WID).read_bytes()
    co.release_checkpoint(b, WID, "S-CP3",                  # ...and the holder moves on
                          owner_token=co.resolve_claim(b, WID)["owner_token"])
    co.claim_checkpoint(b, WID, "S-CP3", now="t2")
    expect("F8. a claim that changed between evidence and takeover is a stale-evidence "
           "refusal, not a silent displacement",
           lambda: co.take_over_claim(fx.a, WID, "S-CP3", now="t5", evidence=ev,
                                      user_authorization=auth),
           co.CheckpointClaimTakeoverRefusedError)
    check("F8b. and that refusal mutated nothing",
          co.resolve_claim(fx.a, WID)["claimed_at"] == "t2")
    ev2, auth2 = authorize(fx.a, "S-CP3")
    bytes_at_takeover = co.claim_path(fx.a, WID).read_bytes()
    record = co.take_over_claim(fx.a, WID, "S-CP3", now="t6", evidence=ev2,
                                user_authorization=auth2)
    check("F8c. taken_over_from describes the exact record atomically displaced, not an "
          "earlier read",
          record["taken_over_from"]["claim_observation_id"]
          == co.observation_id(bytes_at_takeover)
          != co.observation_id(displaced_before))
    check("F8d. the displaced token and checkpoint are recorded verbatim",
          record["taken_over_from"]["owner_token"] == ev2["observed_owner_token"]
          and record["taken_over_from"]["checkpoint_id"] == ev2["observed_checkpoint_id"])
    expect("F8e. the same authorization cannot be replayed against the claim it produced",
           lambda: co.take_over_claim(fx.a, WID, "S-CP3", now="t7", evidence=ev2,
                                      user_authorization=auth2),
           co.CheckpointClaimTakeoverRefusedError)
    check("F8f. takeover_count and previous_owner_tokens make the chain auditable",
          record["takeover_count"] == 1
          and record["previous_owner_tokens"] == [ev2["observed_owner_token"]])
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# F9 -- GPT-R81-002: the unreadable-record recovery is equally bound
# ---------------------------------------------------------------------------
print("\n== F9. corrupt-record recovery binds to an observation too ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    co.claim_path(fx.a, WID).write_text("{ not json")
    ev = co.takeover_evidence(fx.a, WID)
    check("F9. evidence reports the record unreadable AND names its observation id",
          ev["readable"] is False and ev["claim_observation_id"] != co.ABSENT_OBSERVATION)
    expect("F9b. a generic authorization does not recover it",
           lambda: co.take_over_claim(fx.a, WID, "S-CP3", now="t5", evidence=ev,
                                      user_authorization=f"take over {WID} S-CP3"),
           co.CheckpointClaimTakeoverRefusedError)
    co.claim_path(fx.a, WID).write_text("{ different junk")
    expect("F9c. an authorization for one corrupt record does not carry over to another",
           lambda: co.take_over_claim(fx.a, WID, "S-CP3", now="t5", evidence=ev,
                                      user_authorization=co.takeover_authorization_literal(
                                          WID, ev, "S-CP3")),
           co.CheckpointClaimTakeoverRefusedError)
    ev2 = co.takeover_evidence(fx.a, WID)
    record = co.take_over_claim(fx.a, WID, "S-CP3", now="t6", evidence=ev2,
                                user_authorization=co.takeover_authorization_literal(
                                    WID, ev2, "S-CP3"))
    check("F9d. the correctly bound authorization recovers it and records the recovery",
          record["taken_over_from"]["unreadable_record"] is True
          and record["taken_over_from"]["claim_observation_id"] == ev2["claim_observation_id"])
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# F10 -- GPT-R81-003: the durable-release recovery never yields a null checkpoint
# ---------------------------------------------------------------------------
print("\n== F10. durable-release recovery, run through the real step-1 procedure ==")
fx = Fixture()
try:
    # The exact crash window: the FINAL checkpoint's completion is durably
    # committed, and the process stopped before releasing the claim.
    fx.start_cp3_in_a(claim=True)
    step1f_commit(fx, fx.a, "S-CP3", fx.owner_token, now="t9", release=False)
    check("F10. the completion is durable and the claim survived the crash",
          co.committed_checkpoint_status(fx.a, WID, "S-CP3") == "COMPLETE"
          and co.resolve_claim(fx.a, WID) is not None)
    before = (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes()
    out = step1_fixed(fx, fx.a, now="t10")
    check("F10b. the next invocation reaches the terminal no-checkpoint outcome, never a "
          "mutation-capable FRESH carrying a null checkpoint id",
          out == {"selected": None, "mode": "no_checkpoint", "mutated": False}, str(out))
    check("F10c. it self-healed the leaked claim", co.resolve_claim(fx.a, WID) is None)
    check("F10d. and wrote no None checkpoint into the state file -- byte-identical",
          (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes() == before)
    check("F10e. the work item is at the normal post-checkpoint boundary the command's own "
          "no-checkpoint path expects",
          fx.work_item(fx.a)["phase"] == "SELF_REVIEWING_IMPLEMENTATION")
finally:
    fx.cleanup()

print("\n== F11. the same recovery when a concrete next checkpoint remains ==")
fx = Fixture()
try:
    state = fx.read_state(fx.a)
    wi = state["work_items"][WID]
    wi["checkpoints"] = {"S-CP1": {"status": "COMPLETE", "start_commit": "0" * 40}}
    wi["last_completed_checkpoint_id"] = "S-CP1"
    fx.write_state(fx.a, state)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "only S-CP1 complete")
    token = co.claim_checkpoint(fx.a, WID, "S-CP2", now="t1")["owner_token"]
    ws.write_worktree_identity(fx.a, WID, now="t1")
    with co.owner_mutation(fx.a, WID, token, checkpoint_id="S-CP2", step="1d-state-write",
                           step_class=co.DESTRUCTIVE, now="t1"):
        fx.write_state(fx.a, ws.transition_checkpoint_in_progress(
            fx.read_state(fx.a), WID, "S-CP2", git(fx.a, "rev-parse", "HEAD"), "t1"))
    step1f_commit(fx, fx.a, "S-CP2", token, now="t2", release=False)
    out = step1_fixed(fx, fx.a, now="t3")
    check("F11. release of X reveals the concrete next checkpoint Y and starts it",
          out == {"selected": "S-CP3", "mode": "fresh", "mutated": True}, str(out))
    claim = co.resolve_claim(fx.a, WID)
    check("F11b. the new claim names Y, never None",
          claim is not None and claim["checkpoint_id"] == "S-CP3")
    check("F11c. and the state file records Y IN_PROGRESS, with no None-keyed entry",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS"
          and None not in fx.work_item(fx.a)["checkpoints"]
          and "null" not in json.dumps(list(fx.work_item(fx.a)["checkpoints"])))
finally:
    fx.cleanup()

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 10: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
