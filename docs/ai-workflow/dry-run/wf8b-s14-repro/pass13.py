"""Stress pass 13 (revision-66 authoring) -- the two external findings
`OPUS-R83-001` and `OPUS-R83-002`, each reproduced against the revision-65
shape *before* the fix, so the finding is validated rather than believed.

`OPUS-R83-001`: the guard's release is a read-then-unlink-**by-name**, so the
removal is not atomic with respect to the `lease_id` comparison it claims to
be a "compare-and-delete" of. A guard published between the two statements is
deleted instead -- including a live `"destructive"` one belonging to the work
item's *current* owner in another worktree -- after which a correctly bound
takeover breaks that window, because the refusal is driven by evidence that
can no longer see a guard.

`OPUS-R83-002`: revision 65's new "those three operations are exhaustive"
claim is false. An undecidable claim held together with a well-formed
`"destructive"` guard leaves all three sanctioned operations refusing in a
cycle, and a claim record that cannot be read as bytes at all (a symlink)
cannot even be *observed*, so the observation-bound recovery the plan
advertises for an unreadable record is unreachable for it.

Every arm below is built from sanctioned operations only. Nothing is
hand-edited except the external corruption the design itself budgets a
recovery for, and the only thing pinned is the schedule *inside* two adjacent
statements -- exactly the preemption the code permits.
"""

from __future__ import annotations

import json
import os
import threading
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


def revision65_release(repo_root, work_item_id, lease_id):
    """The revision-65 release primitive, reproduced verbatim as the **control
    arm**: read, compare, then `unlink` the *pathname*. Carried here so the
    corrected version is compared against the real previous shape rather than
    against a description of it."""
    path = co.guard_path(repo_root, work_item_id)
    try:
        held = co.read_guard(repo_root, work_item_id)
    except co.CheckpointOwnershipUnavailableError:
        return
    if held is not None and lease_id is not None and held.get("lease_id") != lease_id:
        return
    path.unlink(missing_ok=True)


class InterleaveAtGuardRead:
    """Pin the schedule between the release's `read_guard` and its `unlink` --
    the one instant `OPUS-R83-001` depends on.

    `side_effect` is started in another thread at that instant and is *not*
    waited for, because whether it can complete there is the whole question.
    Under the corrected primitive it cannot: the serialization holds it off
    until the removal is done, which is what makes compare-and-remove one step.
    `completed_during` records the answer.
    """

    def __init__(self, side_effect, *, skip=0, settle=0.35):
        self.side_effect = side_effect
        self.skip = skip
        self.settle = settle
        self.seen = 0
        self.fired = False
        self.completed_during = None
        self.thread = None
        self.error = None

    def _run(self):
        try:
            self.side_effect()
        except BaseException as exc:  # noqa: BLE001
            self.error = exc
        finally:
            self.done.set()

    def __enter__(self):
        self.original = co.read_guard
        self.done = threading.Event()

        def patched(repo_root, work_item_id):
            result = self.original(repo_root, work_item_id)
            self.seen += 1
            if not self.fired and self.seen > self.skip:
                self.fired = True
                self.thread = threading.Thread(target=self._run, daemon=True)
                self.thread.start()
                # Give the other session every chance to land in this instant.
                self.completed_during = self.done.wait(self.settle)
            return result

        co.read_guard = patched
        return self

    def __exit__(self, *exc):
        co.read_guard = self.original
        if self.thread is not None:
            self.thread.join(timeout=10)
        return False


# ---------------------------------------------------------------------------
# I1 -- the release primitive itself: compare-and-delete is two statements
# ---------------------------------------------------------------------------
print("== I1. release deletes a guard it never compared against ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    token_a = co.claim_checkpoint(fx.a, WID, "S-CP3", now="t1")["owner_token"]
    lease_a = co.acquire_guard(fx.a, WID, holder_owner_token=token_a, checkpoint_id="S-CP3",
                               step="1d-state-write", step_class=co.ORDINARY, now="t2")

    # A legitimate, fully authorized takeover from B rotates the claim. A is now
    # a displaced owner holding a superseded guard -- a state the design says is
    # both reachable and safe.
    evidence = co.takeover_evidence(b, WID)
    token_b = co.take_over_claim(
        b, WID, "S-CP3", now="t3",
        user_authorization=co.takeover_authorization_literal(WID, evidence, "S-CP3"),
        evidence=evidence,
        guard_release_authorization=co.guard_release_authorization_literal(evidence["guard"]),
    )["owner_token"]
    check("I1a. the takeover rotated the ownership epoch", token_b != token_a)

    # B, the current owner, opens its own destructive window.
    lease_b = co.acquire_guard(b, WID, holder_owner_token=token_b, checkpoint_id="S-CP3",
                               step="1f-commit", step_class=co.DESTRUCTIVE, now="t4")
    check("I1b. the current owner is inside a destructive window",
          co.read_guard(b, WID)["lease_id"] == lease_b["lease_id"])

    # A wakes, is fenced by its assertion, and releases in its `finally`.
    expect("I1c. the displaced owner is fenced by its assertion (the fence works)",
           lambda: co.assert_claim_owner(fx.a, WID, token_a),
           Exception)
    co.release_guard(fx.a, WID, lease_a)

    held = co.read_guard(fx.a, WID)
    check("I1d. a stale release does NOT remove the current owner's live guard",
          held is not None and held["lease_id"] == lease_b["lease_id"],
          "guard present" if held else "guard was deleted by the displaced owner's release")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# I2 -- the same release, with the one instant the code permits pinned
# ---------------------------------------------------------------------------
print("\n== I2. ...and with the preemption between the compare and the delete ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    token_a = co.claim_checkpoint(fx.a, WID, "S-CP3", now="t1")["owner_token"]
    lease_a = co.acquire_guard(fx.a, WID, holder_owner_token=token_a, checkpoint_id="S-CP3",
                               step="1d-state-write", step_class=co.ORDINARY, now="t2")
    evidence = co.takeover_evidence(b, WID)
    token_b = co.take_over_claim(
        b, WID, "S-CP3", now="t3",
        user_authorization=co.takeover_authorization_literal(WID, evidence, "S-CP3"),
        evidence=evidence,
        guard_release_authorization=co.guard_release_authorization_literal(evidence["guard"]),
    )["owner_token"]

    # A is descheduled for one instant inside its release. In that instant B --
    # the legitimate current owner -- opens its own destructive window: A's guard
    # is superseded, so reclamation rule 1 removes it with no authorization and B
    # publishes its own. Both of B's actions are sanctioned and unaided.
    state = {}

    def b_opens_its_destructive_window():
        state["lease_b"] = co.acquire_guard(
            b, WID, holder_owner_token=token_b, checkpoint_id="S-CP3",
            step="1f-commit", step_class=co.DESTRUCTIVE, now="t5")
        co.assert_claim_owner(b, WID, token_b)

    with InterleaveAtGuardRead(b_opens_its_destructive_window) as pin:
        co.release_guard(fx.a, WID, lease_a)
    check("I2a. the interleaving actually fired", pin.fired)
    check("I2a2. the replacement could NOT be published inside the removal -- "
          "compare-and-remove is one step, not two",
          pin.completed_during is False,
          "the other session landed between the comparison and the removal"
          if pin.completed_during else "serialized")
    if pin.error is not None:
        check("I2a3. the other session completed normally once the removal was done", False,
              f"{type(pin.error).__name__}: {pin.error}")

    lease_b = state["lease_b"]
    held = co.read_guard(fx.a, WID)
    check("I2b. the current owner's live DESTRUCTIVE guard survives the stale release",
          held is not None and held["lease_id"] == lease_b["lease_id"],
          "present" if held else "DELETED -- B is inside a destructive window with no guard")

    # The end-to-end consequence, which is what makes this Important rather than
    # cosmetic: with no guard on disk the takeover's destructive refusal is
    # driven by evidence that cannot see the window it exists to refuse.
    fresh = co.takeover_evidence(b, WID)
    check("I2c. takeover_evidence still reports the live destructive window",
          fresh.get("guard") is not None,
          f"guard={fresh.get('guard')}")

    # The harm itself, attempted exactly ONCE so the result is the real one: a
    # third worktree, shown evidence with no guard, produces the correct literal.
    c = fx.tmp / "C"
    git(fx.a, "worktree", "add", "-q", str(c), "-b", "scratch-C", fx.head)
    ev_c = co.takeover_evidence(c, WID)
    try:
        token_c = co.take_over_claim(
            c, WID, "S-CP3", now="t6",
            user_authorization=co.takeover_authorization_literal(WID, ev_c, "S-CP3"),
            evidence=ev_c)["owner_token"]
        broke, why = True, f"C now owns the claim (token {token_c[:8]})"
    except co.CheckpointClaimTakeoverRefusedError as exc:
        broke, why = False, str(exc)[:100]
    check("I2d. a correctly bound takeover from a third worktree is still REFUSED "
          "on the destructive class", not broke, why)

    # B is still inside the window it already asserted into, so if the takeover
    # succeeded two worktrees each hold what the design calls exclusive ownership.
    still_inside = co.read_guard(b, WID) is None or True
    try:
        co.assert_claim_owner(b, WID, token_b)
        b_still_owner = True
    except Exception:  # noqa: BLE001
        b_still_owner = False
    check("I2e. no second worktree holds the claim while B is mid-destructive-window",
          not (broke and still_inside),
          "B was fenced only AFTER entering its window" if broke and not b_still_owner else "")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# I2' -- the control arm: the revision-65 shape, same schedule, same fixture
# ---------------------------------------------------------------------------
print("\n== I2'. control arm: the read-then-unlink shape DOES lose the guard ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    token_a = co.claim_checkpoint(fx.a, WID, "S-CP3", now="t1")["owner_token"]
    lease_a = co.acquire_guard(fx.a, WID, holder_owner_token=token_a, checkpoint_id="S-CP3",
                               step="1d-state-write", step_class=co.ORDINARY, now="t2")
    evidence = co.takeover_evidence(b, WID)
    token_b = co.take_over_claim(
        b, WID, "S-CP3", now="t3",
        user_authorization=co.takeover_authorization_literal(WID, evidence, "S-CP3"),
        evidence=evidence,
        guard_release_authorization=co.guard_release_authorization_literal(evidence["guard"]),
    )["owner_token"]
    state = {}

    def b_opens_its_destructive_window_ctl():
        state["lease_b"] = co.acquire_guard(
            b, WID, holder_owner_token=token_b, checkpoint_id="S-CP3",
            step="1f-commit", step_class=co.DESTRUCTIVE, now="t5")

    with InterleaveAtGuardRead(b_opens_its_destructive_window_ctl) as pin:
        revision65_release(fx.a, WID, lease_a["lease_id"])
    check("I2'a. the control arm's replacement DID land inside the removal, "
          "because nothing serialized it", pin.completed_during is True)
    held = co.read_guard(fx.a, WID)
    check("I2'b. ...and the control arm deleted the current owner's live destructive guard "
          "-- the defect this revision fixes",
          held is None,
          "guard survived; the control arm no longer reproduces the defect"
          if held else "deleted, as reported")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# I3 -- the reclamation path has the identical window
# ---------------------------------------------------------------------------
print("\n== I3. the reclamation path (`acquire_guard`) has the same window ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    token_a = co.claim_checkpoint(fx.a, WID, "S-CP3", now="t1")["owner_token"]
    evidence = co.takeover_evidence(b, WID)
    token_b = co.take_over_claim(
        b, WID, "S-CP3", now="t3",
        user_authorization=co.takeover_authorization_literal(WID, evidence, "S-CP3"),
        evidence=evidence)["owner_token"]
    # The displaced session A wakes, opens a window, and dies inside it without
    # releasing. Its guard is now superseded by construction: rule 1 says any
    # session holding the current token reclaims it, from any worktree, with no
    # authorization.
    stale = co.acquire_guard(fx.a, WID, holder_owner_token=token_a, checkpoint_id="S-CP3",
                             step="1d-state-write", step_class=co.ORDINARY, now="t4")
    check("I3z. a superseded guard is on disk, reclaimable by rule 1",
          (co.read_guard(fx.a, WID) or {}).get("lease_id") == stale["lease_id"])

    # Two sessions in the owning worktree both reclaim it -- the ordinary
    # same-worktree case the design explicitly permits (rules 1 and 2). The
    # second is preempted between `_release_guard_path`'s comparison and its
    # unlink; `skip=1` because `acquire_guard` reads the guard once itself
    # before delegating the removal.
    state = {}

    def sibling_session_reclaims_and_enters(_state=state):
        """The other session completes its own sanctioned reclamation in that
        instant and enters a destructive window. Nothing here is hand-edited:
        it is `acquire_guard` on the same superseded guard."""
        _state["lease_sib"] = co.acquire_guard(
            b, WID, holder_owner_token=token_b, checkpoint_id="S-CP3",
            step="1f-commit", step_class=co.DESTRUCTIVE, now="t5")
        co.assert_claim_owner(b, WID, token_b)

    observed = {}
    try:
        with InterleaveAtGuardRead(sibling_session_reclaims_and_enters, skip=1) as pin:
            mine = co.acquire_guard(b, WID, holder_owner_token=token_b, checkpoint_id="S-CP3",
                                    step="1d-state-write", step_class=co.ORDINARY, now="t6")
            # Read back *before* the sibling is joined: this is what the
            # reclaimer's own removal-and-republish actually produced.
            observed["mine"] = mine
            observed["on_disk"] = co.read_guard(b, WID)
        reclaimed_ok = True
    except co.CheckpointOwnershipUnavailableError:
        reclaimed_ok = False
    check("I3y. the interleaving fired inside the removal", pin.fired)
    check("I3x. the sibling could not publish inside the reclaim's own removal",
          pin.completed_during is False)
    check("I3a. the reclaimer's removal-and-republish is indivisible: the guard on disk "
          "when it returned is exactly the one it published, never a third party's",
          reclaimed_ok
          and (observed.get("on_disk") or {}).get("lease_id") == observed["mine"]["lease_id"],
          f"on_disk={(observed.get('on_disk') or {}).get('lease_id', 'none')[:8]} "
          f"mine={observed.get('mine', {}).get('lease_id', 'none')[:8]}")
    # The sibling then reclaims in turn -- and that is reclamation **rule 2**,
    # the documented same-worktree scope limit (item 372(f)), not a lost update:
    # it compared against the guard it removed. The distinction is the point.
    check("I3b. the sibling's own later reclamation is rule 2, and it compared against "
          "the guard it removed",
          (co.read_guard(b, WID) or {}).get("lease_id")
          == (state.get("lease_sib") or {}).get("lease_id"),
          "same-worktree rule-2 reclamation, exactly as documented")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# I4 -- OPUS-R83-002 manifestation A: the three-operation cycle
# ---------------------------------------------------------------------------
print("\n== I4. undecidable claim + well-formed destructive guard ==")
fx = Fixture()
try:
    b = fx.add_worktree_b()
    token_b = co.claim_checkpoint(b, WID, "S-CP3", now="t1")["owner_token"]
    lease_b = co.acquire_guard(b, WID, holder_owner_token=token_b, checkpoint_id="S-CP3",
                               step="1f-commit", step_class=co.DESTRUCTIVE, now="t2")
    # The external corruption the design already budgets a recovery for
    # (`clear_malformed_guard` exists for exactly this class, on the guard side).
    co.claim_path(fx.a, WID).write_bytes(b'{"schema_version": 3, "work_i')
    git(fx.a, "worktree", "remove", "--force", str(b))

    ev = co.takeover_evidence(fx.a, WID)
    check("I4a. evidence reports the undecidable claim and the well-formed guard",
          ev["readable"] is False and ev["guard"] is not None
          and ev["guard"]["step_class"] == co.DESTRUCTIVE,
          f"readable={ev['readable']} guard_class={ev['guard'] and ev['guard']['step_class']}")

    expect("I4b. take_over_claim refuses on the destructive class, naming the recovery",
           lambda: co.take_over_claim(
               fx.a, WID, "S-CP3", now="t3",
               user_authorization=co.takeover_authorization_literal(WID, ev, "S-CP3"),
               evidence=ev),
           co.CheckpointClaimTakeoverRefusedError, ("abandoned-guard recovery",))
    expect("I4c. clear_malformed_guard refuses, because the guard is not the problem",
           lambda: co.clear_malformed_guard(
               fx.a, WID,
               user_authorization=co.guard_clearance_authorization_literal(ev)),
           co.CheckpointClaimTakeoverRefusedError, ("well-formed",))

    # Exactly one of the three owns this state, and it is the recovery.
    recovered = co.recover_abandoned_destructive_guard(
        fx.a, WID, "S-CP3", now="t3",
        user_authorization=co.abandoned_guard_recovery_authorization_literal(WID, ev),
        evidence=ev)
    check("I4d. the abandoned-guard recovery OWNS the state and succeeds",
          recovered.get("checkpoint_id") == "S-CP3" and isinstance(recovered.get("owner_token"), str))
    check("I4e. the recovered claim is never indistinguishable from an ordinary one",
          recovered["taken_over_from"]["recovered_from_abandoned_guard"]["lease_id"]
          == lease_b["lease_id"]
          and recovered["taken_over_from"]["claim_was_undecidable"] is True
          and recovered["taken_over_from"]["epoch_chain_lost"] is True)
    check("I4f. the displaced epoch survives in the chain even though the claim was torn",
          token_b in recovered.get("previous_owner_tokens", []))
    check("I4g. the abandoned guard is now superseded, cleared by the epoch rule",
          (co.read_guard(fx.a, WID) or {}).get("holder_owner_token") != recovered["owner_token"]
          or co.read_guard(fx.a, WID) is None)
    # ...and the work item is workable again by the ordinary path.
    co.acquire_guard(fx.a, WID, holder_owner_token=recovered["owner_token"],
                     checkpoint_id="S-CP3", step="1d-state-write",
                     step_class=co.ORDINARY, now="t4")
    check("I4h. the work item is startable again by an ordinary guarded mutation",
          co.assert_claim_owner(fx.a, WID, recovered["owner_token"]) is not None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# I5 -- OPUS-R83-002 manifestation B: a claim that cannot be read as bytes
# ---------------------------------------------------------------------------
print("\n== I5. a claim record that cannot be observed at all ==")
fx = Fixture()
try:
    co.claims_dir(fx.a).mkdir(parents=True, exist_ok=True)
    offtree = fx.tmp / "offtree.json"
    offtree.write_text('{"not": "this repository\'s coordination state"}\n')
    os.symlink(offtree, co.claim_path(fx.a, WID))

    reported = {}
    try:
        reported["evidence"] = co.takeover_evidence(fx.a, WID)
    except co.CheckpointOwnershipUnavailableError as exc:
        reported["error"] = str(exc)
    check("I5a. takeover_evidence RETURNS a report for an unobservable record",
          "evidence" in reported,
          reported.get("error", "")[:100])
    check("I5b. ...with a defined observation identity the authorization can bind to",
          isinstance(reported.get("evidence", {}).get("claim_observation_id"), str),
          "no evidence to derive an authorization from" if "evidence" not in reported else "")

    # The fail-closed half is asserted BEFORE anything replaces the record: the
    # operations that resolve ownership must still refuse, and only the
    # observation-bound recovery may act.
    for name, fn in (
        ("resolve_claim", lambda: co.resolve_claim(fx.a, WID)),
        ("claim_checkpoint", lambda: co.claim_checkpoint(fx.a, WID, "S-CP3", now="t1")),
    ):
        expect(f"I5d. {name} still fails closed on the symlink (correct)",
               fn, co.CheckpointOwnershipUnavailableError, ("symbolic link",))

    ev = reported.get("evidence")
    if ev is not None:
        check("I5b2. the unreadable kind is reported, so the operator is told what it is",
              (ev.get("claim_unreadable") or {}).get("kind") == "symlink",
              json.dumps(ev.get("claim_unreadable")))
        check("I5b3. the unreadable observation is domain-separated from any byte hash",
              ev["claim_observation_id"] != co.observation_id(offtree.read_bytes()))
        # ...and the recovery the plan advertises is actually reachable.
        recovered = co.take_over_claim(
            fx.a, WID, "S-CP3", now="t2",
            user_authorization=co.takeover_authorization_literal(WID, ev, "S-CP3"),
            evidence=ev)
        check("I5b4. the observation-bound takeover recovers the record",
              co.resolve_claim(fx.a, WID) is not None
              and recovered["taken_over_from"]["claim_observation_id"]
              == ev["claim_observation_id"])
        expect("I5b5. ...and that authorization is not replayable against the claim it produced",
               lambda: co.take_over_claim(
                   fx.a, WID, "S-CP3", now="t3",
                   user_authorization=co.takeover_authorization_literal(WID, ev, "S-CP3"),
                   evidence=ev),
               co.CheckpointClaimTakeoverRefusedError)
    check("I5c. the off-tree symlink target is untouched, and the link itself was replaced "
          "rather than written through",
          json.loads(offtree.read_text()) == {"not": "this repository's coordination state"}
          and not co.claim_path(fx.a, WID).is_symlink())

    # A directory and an unreadable file are the same class.
    for label, make in (
        ("directory", lambda p: p.mkdir()),
        ("unreadable file", lambda p: (p.write_bytes(b"{}"), p.chmod(0o000))),
    ):
        fx2 = Fixture()
        try:
            co.claims_dir(fx2.a).mkdir(parents=True, exist_ok=True)
            make(co.claim_path(fx2.a, WID))
            try:
                ev = co.takeover_evidence(fx2.a, WID)
                ok, detail = True, (f"kind={(ev.get('claim_unreadable') or {}).get('kind')} "
                                    f"replaceable={ev.get('claim_replaceable')}")
            except Exception as exc:  # noqa: BLE001
                ok, detail = False, f"{type(exc).__name__}: {str(exc)[:70]}"
            check(f"I5e. takeover_evidence returns a report for a {label} at the claim path",
                  ok, detail)
            if ok:
                # Whichever way it is decided, the operator must be *told*: the
                # state is either recoverable by a documented operation or
                # explicitly outside every one of them, with the action named.
                try:
                    co.take_over_claim(
                        fx2.a, WID, "S-CP3", now="t1",
                        user_authorization=co.takeover_authorization_literal(WID, ev, "S-CP3"),
                        evidence=ev)
                    outcome = "recovered by the takeover"
                except co.CheckpointClaimTakeoverRefusedError as exc:
                    outcome = ("refused, naming the operator action"
                               if "Remove" in str(exc) else f"refused: {str(exc)[:60]}")
                check(f"I5f. a {label} has a stated outcome rather than a dead end",
                      outcome in ("recovered by the takeover",
                                  "refused, naming the operator action"), outcome)
        finally:
            p = co.claim_path(fx2.a, WID)
            if p.exists() and p.is_file():
                p.chmod(0o600)
            fx2.cleanup()
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# I6 -- the real repository is untouched
# ---------------------------------------------------------------------------
print("\n== I6. real-repository re-verification (read-only) ==")
REPO = Path(git(Path(__file__).parent, "rev-parse", "--show-toplevel"))
check("I6a. no claim record exists anywhere in the real repository",
      co.resolve_claim(REPO, WID) is None and co.resolve_claim(REPO, "workflow-v2-1-core") is None)
check("I6b. and no mutation guard either",
      co.read_guard(REPO, WID) is None and co.read_guard(REPO, "workflow-v2-1-core") is None)
check("I6c. the claims directory itself was never created in this repository",
      not co.claims_dir(REPO).exists())

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 13: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
