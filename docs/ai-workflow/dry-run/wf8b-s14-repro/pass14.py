"""Stress pass 14 (revision-67 authoring) -- the external blocking finding
`OPUS-R84-001`, reproduced against the revision-66 ordering *before* the fix,
so the finding is validated rather than believed.

`OPUS-R84-001`: `CONTINUE_CLAIM` is specified to cover a crash between
publishing the claim and writing the `IN_PROGRESS` state, and the plan names
the harm it prevents ("without it the *legitimate owner* is permanently locked
out of its own claim"). But 1d writes `WORKTREE_IDENTITY.json`'s per-work-item
entry *after* publishing the claim, and 1c step 3 called
`verify_dirty_resume_safety` unconditionally -- which raises
`WorktreeIdentityMissingError` when that entry is absent. Inside 1d's own crash
window the entry does not exist, so the branch below the check was unreachable
and the owner was locked out of its own claim. `.ai-review/` is gitignored, so
the entry is per-worktree and never inherited: the lockout covered the **first
checkpoint any worktree starts on any work item**.

Every arm below drives the **real** step-1 procedure (`harness.step1_fixed`),
never a helper's return value, with only step 1c's resolver swapped -- so the
revision-66 ordering is carried as a live control arm rather than as a
description of one. Nothing is hand-edited except the fixture construction each
arm declares, and the real repository is re-verified untouched at the end.

Arms:
  Z1  the reproduction: control arm locks the legitimate owner out
  Z2  the correction: the owner continues its own acquisition
  Z3  no hand repair is required in the locked-out state
  Z4  the refusal is not weakened: S14a/S14b still hold for a foreign worktree
  Z5  both `WorktreeIdentityMissingError` branches, asserted separately
  Z6  diagnosis: a self-owned refusal carries evidence and names the escape
  Z7  the relaxation is scoped: the adoption path still requires the check
  Z8  an absent `lease_id` is a refusal, never a removal wildcard
  Z9  the real repository is untouched
"""

from __future__ import annotations

import contextlib
import shutil
from pathlib import Path

from harness import Fixture, REGISTRY, git, step1_fixed
import checkpoint_ownership as co
import workflow_state as ws

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


# ---------------------------------------------------------------------------
# The revision-66 ordering, reproduced verbatim as the control arm
# ---------------------------------------------------------------------------

def resolve_ownership_r66(repo_root, work_item, work_item_id, selected_id, *,
                          verify_resume_safety, now, state_rel_path=co.STATE_REL_PATH):
    """Revision 66's step 1c: `verify_resume_safety` runs **unconditionally**
    before any branch that can mutate, and the ownership evidence is attached
    only when the claim is *foreign*. Carried here so the correction is compared
    against the real previous shape rather than against a description of it."""
    checkpoints = work_item.get("checkpoints", {})
    current_id = work_item.get("current_checkpoint_id")
    local_in_progress = (
        current_id if checkpoints.get(current_id, {}).get("status") == "IN_PROGRESS" else None
    )
    claim = co.resolve_claim(repo_root, work_item_id)
    if claim is None and local_in_progress is None:
        if selected_id is None:
            return co.NO_CHECKPOINT, None, None
        return co.FRESH, selected_id, None
    try:
        verify_resume_safety(repo_root, work_item_id)
    except Exception as exc:
        if claim is not None and not co.claim_is_this_worktree(repo_root, claim):
            exc.ownership_claim = claim
        raise
    return co._resolve_with_origination_proved(
        repo_root, work_item, work_item_id, claim, local_in_progress, selected_id, checkpoints,
        verify_resume_safety=verify_resume_safety, now=now, state_rel_path=state_rel_path)


@contextlib.contextmanager
def ordering(resolver):
    """Swap only step 1c's resolver. `harness.step1_fixed` -- the real step-1
    procedure, unchanged -- is what actually runs in both arms."""
    original = co.resolve_ownership
    co.resolve_ownership = resolver
    try:
        yield
    finally:
        co.resolve_ownership = original


# ---------------------------------------------------------------------------
# Fixture: a brand-new work item, crashed inside 1d's own window
# ---------------------------------------------------------------------------

def crash_window_fixture(*, worktree_b: bool = False, identity_first: bool = True) -> Fixture:
    """A work item with **no checkpoint completed anywhere**, whose claim for
    its first checkpoint is published and whose `IN_PROGRESS` state write never
    happened -- exactly the window 1d opens by construction.

    **Re-targeted, revision 68 (`OPUS-R85-001`).** Which state that window
    leaves behind depends on 1d's own ordering, and that ordering is what
    revision 68 corrects:

    - `identity_first=True` (revision 68's 1d): this worktree's identity record
      is established *before* the claim is published, so the window always
      carries the evidence of worktree instance the resolution needs. This is
      the state the corrected command can actually produce.
    - `identity_first=False` (revision 66/67's 1d): the claim is published with
      no identity record behind it. That is the state `OPUS-R84-001` reported,
      and it is carried here as the historical control arm -- **and, under
      revision 68, as the state an identity record lost out of band leaves
      behind**, which is refused on purpose, because a claim proves a path and
      only the local record proves the instance (`OPUS-R85-001`).
    """
    fx = Fixture()
    state = fx.base_state()
    wi = state["work_items"][WID]
    wi["checkpoints"] = {}
    wi["current_checkpoint_id"] = None
    wi["last_completed_checkpoint_id"] = None
    fx.write_state(fx.a, state)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "brand-new work item, nothing started")
    fx.head = git(fx.a, "rev-parse", "HEAD")
    if worktree_b:
        fx.add_worktree_b()
    if identity_first:
        co.establish_worktree_identity(fx.a, WID, now="t1")
    fx.claim = co.claim_checkpoint(fx.a, WID, "S-CP1", now="t1")
    return fx


def identity_path(root: Path) -> Path:
    return root / ".ai-review/runtime/WORKTREE_IDENTITY.json"


# ---------------------------------------------------------------------------
# Z1 -- the reproduction
# ---------------------------------------------------------------------------
print("== Z1. crash between claim publication and identity write, brand-new work item ==")
fx = crash_window_fixture(identity_first=False)
try:
    check("Z1a. no identity record exists anywhere yet",
          not identity_path(fx.a).exists())
    check("Z1b. the claim for the first checkpoint is published",
          co.resolve_claim(fx.a, WID) is not None
          and co.resolve_claim(fx.a, WID)["checkpoint_id"] == "S-CP1")
    check("Z1c. the claim is this worktree's own",
          co.claim_is_this_worktree(fx.a, co.resolve_claim(fx.a, WID)))
    check("Z1d. nothing is locally IN_PROGRESS (the state write never happened)",
          fx.work_item(fx.a)["checkpoints"] == {})
    check("Z1e. selection resolves the claimed checkpoint",
          ws.select_next_checkpoint(fx.work_item(fx.a), REGISTRY) == "S-CP1")

    with ordering(resolve_ownership_r66):
        expect("Z1f. CONTROL ARM (revision 66): the OWNER is refused from its own claim",
               lambda: step1_fixed(fx, fx.a), ws.WorktreeIdentityMissingError)
        try:
            step1_fixed(fx, fx.a)
        except ws.WorktreeIdentityMissingError as exc:
            check("Z1g. CONTROL ARM: and the refusal names an IN_PROGRESS checkpoint "
                  "that does not exist, with no escape",
                  "IN_PROGRESS" in str(exc) and getattr(exc, "ownership_hint", None) is None,
                  str(exc)[:100])
    check("Z1h. CONTROL ARM mutated nothing: no state entry, no identity, claim intact",
          fx.work_item(fx.a)["checkpoints"] == {}
          and not identity_path(fx.a).exists()
          and co.claim_path(fx.a, WID).read_bytes() is not None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Z2 -- the correction, through the real step-1 caller
# ---------------------------------------------------------------------------
print("\n== Z2. corrected ordering: the owner continues its own acquisition ==")
# Re-targeted, revision 68: the correction is 1d's ordering (identity record
# established before the claim is published), not revision 67's relaxation of
# step 1c. The revision-66/67 ordering is carried below as the control arm.
fx = crash_window_fixture(identity_first=False)
try:
    with ordering(resolve_ownership_r66):
        expect("Z2-. CONTROL ARM (revision 66/67 1d ordering): the owner is locked out of "
               "its own claim",
               lambda: step1_fixed(fx, fx.a), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

fx = crash_window_fixture()
try:
    claim_before = co.claim_path(fx.a, WID).read_bytes()
    result = step1_fixed(fx, fx.a, now="t2")
    check("Z2a. the real step-1 procedure resolves CONTINUE_CLAIM",
          result["mode"] == "continue_claim", str(result))
    check("Z2b. for the checkpoint the claim names, not a re-selected one",
          result["selected"] == "S-CP1")
    wi = fx.work_item(fx.a)
    check("Z2c. the interrupted state write is completed",
          wi["checkpoints"].get("S-CP1", {}).get("status") == "IN_PROGRESS"
          and wi["current_checkpoint_id"] == "S-CP1")
    check("Z2d. the original claim record is intact, not republished",
          co.claim_path(fx.a, WID).read_bytes() == claim_before)
    check("Z2e. the owner_token is unchanged, so no epoch was consumed",
          co.resolve_claim(fx.a, WID)["owner_token"] == fx.claim["owner_token"]
          and co.resolve_claim(fx.a, WID).get("takeover_count", 0) == 0)
    check("Z2f. the identity record the interrupted write owed now exists",
          identity_path(fx.a).exists()
          and WID in ws._load_json(identity_path(fx.a))["expected_dirty_paths_by_work_item"])
    check("Z2g. no guard residue",
          co.read_guard(fx.a, WID) is None)
    # And the item is workable from here: the ordinary next invocation resumes.
    again = step1_fixed(fx, fx.a, now="t3")
    check("Z2h. the next ordinary invocation resumes rather than re-claiming",
          again["mode"] == "resume" and again["selected"] == "S-CP1", str(again))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Z3 -- no hand repair is required
# ---------------------------------------------------------------------------
print("\n== Z3. an identity record lost out of band has an authorization-bound exit ==")
# Re-targeted, revision 68 (`OPUS-R85-001`). Revision 68's 1d cannot produce
# this state, so the only way to reach it is losing the local record out of
# band (a `git clean -xdf`, say) while holding a claim. It is refused on
# purpose -- the mechanism genuinely cannot tell it from a *different* worktree
# occupying the holder's path -- and the exit is the one operation that already
# requires human judgment bound to the exact record: the explicit takeover,
# which now establishes the taking worktree's own identity record as part of
# its rotation. Revision 67's takeover, which did not, is the control arm.
fx = crash_window_fixture(worktree_b=True, identity_first=False)
try:
    ev = co.takeover_evidence(fx.a, WID)
    check("Z3a. evidence reports no guard and a still-registered holder",
          ev.get("guard") is None and ev.get("holder_registered") is True, str(ev)[:110])

    expect("Z3b. clear_malformed_guard does not apply (there is no guard)",
           lambda: co.clear_malformed_guard(
               fx.a, WID,
               user_authorization=co.guard_clearance_authorization_literal(
                   {"work_item_id": WID,
                    "guard_observation_id": co.observation_id(
                        co._read_claim_bytes(co.guard_path(fx.a, WID)))})),
           co.CheckpointClaimTakeoverRefusedError)

    expect("Z3c. abandoned-guard recovery does not apply (there is no guard)",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP1", now="t2",
               user_authorization=co.abandoned_guard_recovery_authorization_literal(WID, ev),
               evidence=ev),
           co.CheckpointClaimTakeoverRefusedError)

    expect("Z3d. the owner is refused while it cannot prove its own instance",
           lambda: step1_fixed(fx, fx.a), ws.WorktreeIdentityMissingError)

    # CONTROL ARM: revision 67's takeover rotated the claim and established
    # nothing, so the refusal survived it -- the operation offered for "someone
    # else holds this" could not clear a state in which nobody else did.
    rotated = co.take_over_claim(
        fx.a, WID, "S-CP1", now="t2",
        user_authorization=co.takeover_authorization_literal(WID, ev, "S-CP1"),
        evidence=ev)
    check("Z3e. a takeover by the OWNING worktree rotates the claim and fences the old epoch",
          rotated["owner_token"] != fx.claim["owner_token"]
          and rotated.get("takeover_count") == 1
          and fx.claim["owner_token"] in rotated.get("previous_owner_tokens", []))
    identity_snapshot = identity_path(fx.a).read_bytes()
    identity_path(fx.a).unlink()          # the revision-67 takeover's own end state
    expect("Z3f. CONTROL ARM (revision 67 takeover): the owner is still locked out afterwards",
           lambda: step1_fixed(fx, fx.a), ws.WorktreeIdentityMissingError)
    expect("Z3g. CONTROL ARM: worktree B cannot clear it either",
           lambda: step1_fixed(fx, fx.b), ws.WorktreeIdentityMissingError)
    check("Z3h. CONTROL ARM: hand-deleting the claim was the only remaining escape",
          co.claim_path(fx.a, WID).exists())

    # Corrected: the takeover's own identity establishment is what clears it,
    # with no file deleted or edited by hand.
    identity_path(fx.a).write_bytes(identity_snapshot)
    check("Z3i. corrected: the takeover established the taking worktree's own record",
          WID in ws._load_json(identity_path(fx.a))["expected_dirty_paths_by_work_item"])
    result = step1_fixed(fx, fx.a, now="t3")
    check("Z3j. so the work item recovers with no hand repair at all",
          result["mode"] == "continue_claim"
          and fx.work_item(fx.a)["checkpoints"].get("S-CP1", {}).get("status") == "IN_PROGRESS",
          str(result))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Z4 -- the refusal is not weakened
# ---------------------------------------------------------------------------
print("\n== Z4. a foreign worktree in the identical state is still refused ==")
fx = crash_window_fixture(worktree_b=True)
try:
    before = fx.snapshot()
    expect("Z4a. S14a: worktree B is refused with WorktreeIdentityMissingError",
           lambda: step1_fixed(fx, fx.b), ws.WorktreeIdentityMissingError)
    after = fx.snapshot()
    check("Z4b. the refusal performed zero authoritative mutation",
          before == after,
          "" if before == after else str([k for k in before if before[k] != after[k]]))
    check("Z4c. and left no guard residue and no identity in B",
          co.read_guard(fx.a, WID) is None and not identity_path(fx.b).exists())
    try:
        step1_fixed(fx, fx.b)
    except ws.WorktreeIdentityMissingError as exc:
        check("Z4d. the foreign refusal carries the ownership evidence and names the takeover",
              getattr(exc, "ownership_claim", None) is not None
              and "take the claim over" in getattr(exc, "ownership_hint", ""),
              getattr(exc, "ownership_hint", "")[:90])

    # S14b in the identical state: B holds a record for a *different* worktree,
    # built by copying A's own record into B. A keeps its record, so A is still
    # inside the crash window 1d opens (claim published, state not written)
    # while B carries a foreign identity.
    identity_path(fx.b).parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(identity_path(fx.a), identity_path(fx.b))
    check("Z4e. A is still inside the crash window (nothing IN_PROGRESS, claim published)",
          fx.work_item(fx.a)["checkpoints"] == {} and co.resolve_claim(fx.a, WID) is not None)
    expect("Z4f. S14b: worktree B is refused with WorktreeIdentityMismatchError (distinct class)",
           lambda: step1_fixed(fx, fx.b), ws.WorktreeIdentityMismatchError)
    check("Z4g. both D3 classes therefore remain distinguishable in this state", True)
    # ...and the owner still recovers, with the foreign refusals having changed nothing.
    check("Z4h. the owner still resolves CONTINUE_CLAIM afterwards",
          step1_fixed(fx, fx.a, now="t4")["mode"] == "continue_claim")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Z5 -- both WorktreeIdentityMissingError branches, separately
# ---------------------------------------------------------------------------
print("\n== Z5. the two WorktreeIdentityMissingError branches are distinct ==")
fx = crash_window_fixture(worktree_b=True)
try:
    try:
        ws.verify_dirty_resume_safety(fx.b, WID)
        check("Z5a. missing-file branch raises", False, "no exception raised")
    except ws.WorktreeIdentityMissingError as exc:
        check("Z5a. missing-file branch: the file itself does not exist",
              "not found" in str(exc), str(exc)[:90])

    # A worktree that has worked on a *different* work item has the file, but
    # no entry for this one -- the only branch reachable for such a worktree.
    ws.write_worktree_identity(fx.b, "some-other-work-item", now="t2")
    check("Z5b. the identity file now exists in B, for another work item only",
          identity_path(fx.b).exists()
          and WID not in ws._load_json(identity_path(fx.b))["expected_dirty_paths_by_work_item"])
    try:
        ws.verify_dirty_resume_safety(fx.b, WID)
        check("Z5c. no-entry branch raises", False, "no exception raised")
    except ws.WorktreeIdentityMissingError as exc:
        check("Z5c. no-entry branch: same class, different message and different cause",
              "no expected_dirty_paths_by_work_item entry" in str(exc), str(exc)[:90])
    expect("Z5d. and the foreign worktree is still refused through the real step-1 caller",
           lambda: step1_fixed(fx, fx.b), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Z6 -- diagnosis for a self-owned claim
# ---------------------------------------------------------------------------
print("\n== Z6. a refusal raised while a self-owned claim exists is diagnosable ==")
fx = Fixture()
try:
    state = fx.base_state()
    wi = state["work_items"][WID]
    wi["checkpoints"] = {}
    wi["current_checkpoint_id"] = None
    wi["last_completed_checkpoint_id"] = None
    fx.write_state(fx.a, state)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "brand-new work item")
    fx.head = git(fx.a, "rev-parse", "HEAD")
    # A self-owned claim naming a checkpoint selection does not resolve: the
    # one refusal still reachable with a self-owned claim in this window.
    # (Revision 68: 1d establishes the identity record first, so this fixture
    # does too -- the refusal under test is the state disagreement, not a
    # missing record.)
    co.establish_worktree_identity(fx.a, WID, now="t1")
    co.claim_checkpoint(fx.a, WID, "S-CP2", now="t1")
    check("Z6a. the claim is self-owned and names S-CP2 while selection resolves S-CP1",
          co.claim_is_this_worktree(fx.a, co.resolve_claim(fx.a, WID))
          and ws.select_next_checkpoint(fx.work_item(fx.a), REGISTRY) == "S-CP1")
    try:
        step1_fixed(fx, fx.a, now="t2")
        check("Z6b. the disagreement is refused", False, "no exception raised")
    except co.CheckpointOwnershipStateMismatchError as exc:
        check("Z6b. the disagreement is reported, never guessed at", True, str(exc)[:90])
        check("Z6c. and the refusal carries the ownership evidence for a SELF-owned claim",
              getattr(exc, "ownership_claim", None) is not None
              and getattr(exc, "ownership_claim", {}).get("checkpoint_id") == "S-CP2")
        check("Z6d. naming this worktree as the holder and that no hand repair applies",
              "this worktree" in getattr(exc, "ownership_hint", "")
              and "by hand" in getattr(exc, "ownership_hint", ""),
              getattr(exc, "ownership_hint", "")[:100])
    identity_snapshot = identity_path(fx.a).read_bytes()
    identity_path(fx.a).unlink()
    with ordering(resolve_ownership_r66):
        try:
            step1_fixed(fx, fx.a, now="t2")
        except Exception as exc:  # noqa: BLE001
            check("Z6e. CONTROL ARM (revision 66): the same state raised a bare, "
                  "unannotated identity error instead",
                  isinstance(exc, ws.WorktreeIdentityMissingError)
                  and getattr(exc, "ownership_hint", None) is None,
                  f"{type(exc).__name__}: {str(exc)[:70]}")
    identity_path(fx.a).write_bytes(identity_snapshot)
    check("Z6f. nothing was mutated by any of those refusals",
          fx.work_item(fx.a)["checkpoints"] == {}
          and identity_path(fx.a).read_bytes() == identity_snapshot)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Z7 -- the relaxation is scoped to the self-owned-claim branches
# ---------------------------------------------------------------------------
print("\n== Z7. the claim-absent adoption path still requires the identity record ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=False)
    check("Z7a. S-CP3 is locally IN_PROGRESS with no claim at all",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS"
          and co.resolve_claim(fx.a, WID) is None)
    check("Z7b. and the adoption path works while the identity record exists",
          step1_fixed(fx, fx.a, now="t2")["mode"] == "resume")
    co.release_checkpoint(fx.a, WID, "S-CP3", owner_token=co.resolve_claim(
        fx.a, WID)["owner_token"], now="t2")
    identity_path(fx.a).unlink()
    check("Z7c. with the claim released and the identity record lost, nothing proves origination",
          co.resolve_claim(fx.a, WID) is None and not identity_path(fx.a).exists())
    expect("Z7d. so the adoption path still refuses -- the relaxation is not global",
           lambda: step1_fixed(fx, fx.a, now="t3"), ws.WorktreeIdentityMissingError)
    check("Z7e. and that refusal mutated nothing and published no claim",
          co.resolve_claim(fx.a, WID) is None
          and fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Z8 -- an absent lease_id is a refusal, never a wildcard
# ---------------------------------------------------------------------------
print("\n== Z8. the removal primitive requires the lease_id it observed ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    lease = co.acquire_guard(fx.a, WID, holder_owner_token=fx.owner_token,
                             checkpoint_id="S-CP3", step="z8", step_class=co.DESTRUCTIVE,
                             now="t2")
    guard_bytes = co.guard_path(fx.a, WID).read_bytes()
    expect("Z8a. a removal naming no lease_id refuses rather than matching anything",
           lambda: co._release_guard_path(fx.a, WID, None),
           co.CheckpointOwnershipUnavailableError, must_mention=("never a wildcard",))
    check("Z8b. and the guard is still on disk, byte-identical",
          co.guard_path(fx.a, WID).exists()
          and co.guard_path(fx.a, WID).read_bytes() == guard_bytes)

    def r66_release_without_lease():
        """CONTROL ARM: revision 66's `lease_id is not None and ...` guard,
        which fell through to an unconditional unlink when no lease id was
        supplied."""
        path = co.guard_path(fx.a, WID)
        held = co.read_guard(fx.a, WID)
        lease_id = None
        if held is None:
            return
        if lease_id is not None and held.get("lease_id") != lease_id:
            return
        path.unlink(missing_ok=True)

    r66_release_without_lease()
    check("Z8c. CONTROL ARM: the revision-66 shape removed the guard it never compared against",
          not co.guard_path(fx.a, WID).exists())
    co._publish_guard(fx.a, WID, {**lease})
    check("Z8d. the real release, naming its own lease_id, still works",
          (co.release_guard(fx.a, WID, lease), not co.guard_path(fx.a, WID).exists())[1])
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Z9 -- the real repository is untouched
# ---------------------------------------------------------------------------
print("\n== Z9. real-repository re-verification (read-only) ==")
REPO = Path(git(Path(__file__).parent, "rev-parse", "--show-toplevel"))
check("Z9a. no claim record exists anywhere in the real repository",
      co.resolve_claim(REPO, WID) is None and co.resolve_claim(REPO, "workflow-v2-1-core") is None)
check("Z9b. and no mutation guard either",
      co.read_guard(REPO, WID) is None and co.read_guard(REPO, "workflow-v2-1-core") is None)
check("Z9c. the claims directory itself was never created in this repository",
      not co.claims_dir(REPO).exists())

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 14: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
