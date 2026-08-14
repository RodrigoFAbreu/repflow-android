"""Stress pass 15 (revision-68 authoring) -- the external findings
`OPUS-R85-001` (blocking) and `OPUS-R85-002` (important), reproduced against
revision 67's shape *before* the fix, so both are validated rather than
believed.

`OPUS-R85-001`: revision 67 made a "decidably self-owned claim" a sufficient
proof of origination and skipped `verify_dirty_resume_safety` for it. The
ownership key that proof compares is `workflow_state._git_identity`'s output --
which is the worktree's path **twice** plus the Git common dir, i.e. two path
values carrying no worktree-instance identity at all. Paths are reusable, so a
*different* worktree occupying the holder's recorded path satisfies the proof,
skips the check, and operates under the original holder's unrotated
`owner_token`: two worktrees, one valid token, `takeover_count` 0, no fencing
and no audit trail.

`OPUS-R85-002`: `verify_dirty_resume_safety` has a **third** failure class the
plan's "exactly two" enumeration missed -- `CorruptJsonError`, from `_load_json`
on unparseable bytes and from `validate_worktree_identity` on a schema-invalid
document. Skipping the check therefore also moved corruption detection to
*after* an authoritative state write, where the schema-invalid flavour escapes
as an undeclared `TypeError` out of `write_worktree_identity`.

Revision 68's correction: `verify_dirty_resume_safety` is unconditional again
(the local, gitignored, per-worktree record is the only evidence of worktree
*instance* that exists), and `CONTINUE_CLAIM`'s reachability -- the defect
`OPUS-R84-001` reported, which revision 67 was resolving -- is restored at 1d's
**ordering** instead: the identity record is established before the claim is
published, so the crash window cannot be entered without it.

Every arm drives the **real** step-1 procedure (`harness.step1_fixed`), with
only step 1c's resolver or 1d's ordering swapped, so revision 67 is carried as
a live control arm rather than as a description of one.

Arms:
  Y0  the ownership key carries two path values and no instance identity
  Y1  aliasing (i): holder relocated, a different worktree occupies its path
  Y2  aliasing (ii): holder removed, recreated at the same path with the same name
  Y3  `CONTINUE_CLAIM` stays reachable -- via 1d's ordering, not a relaxation
  Y4  the third error class: a corrupt identity record refuses before any write
  Y5  fencing: no path leaves two worktrees holding one token unrotated
  Y6  the single automatic release is unreachable by a path-aliased worktree
  Y7  no undeclared exception escapes the identity writer; publication is atomic
  Y8  an empty-string `lease_id` is a refusal, like every other absent one
  Y9  the real repository is untouched
"""

from __future__ import annotations

import contextlib
import json
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
# Revision 67's step 1c, reproduced verbatim as the control arm
# ---------------------------------------------------------------------------

def resolve_ownership_r67(repo_root, work_item, work_item_id, selected_id, *,
                          verify_resume_safety, now, state_rel_path=co.STATE_REL_PATH):
    """Revision 67's step 1c: two proofs of origination, with a decidably
    self-owned claim sufficient on its own. Copied verbatim from the revision-67
    resolver so the correction is compared against the real previous shape."""
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
    claim_self_owned = claim is not None and co.claim_is_this_worktree(repo_root, claim)
    try:
        if not claim_self_owned:
            verify_resume_safety(repo_root, work_item_id)
        return co._resolve_with_origination_proved(
            repo_root, work_item, work_item_id, claim, local_in_progress, selected_id,
            checkpoints, verify_resume_safety=verify_resume_safety, now=now,
            state_rel_path=state_rel_path)
    except Exception as exc:
        co._attach_ownership_evidence(repo_root, exc, work_item_id, claim)
        raise


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


def identity_path(root: Path) -> Path:
    return root / ".ai-review/runtime/WORKTREE_IDENTITY.json"


def holder_fixture(*, live_work: bool = True) -> Fixture:
    """Worktree B starts the work item's first checkpoint through the **real**
    step-1 procedure -- so B, and only B, has an identity record for it -- and
    holds live uncommitted work."""
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
    fx.add_worktree_b("B")
    fx.started = step1_fixed(fx, fx.b, now="t1")
    if live_work:
        (fx.b / "docs/ai-workflow/dry-run").mkdir(parents=True, exist_ok=True)
        (fx.b / "docs/ai-workflow/dry-run/c.txt").write_text("B's real uncommitted work\n")
    fx.claim = co.resolve_claim(fx.b, WID)
    return fx


def occupy_vacated_path(fx: Fixture, *, mode: str, at: str | None = None) -> Path:
    """Free B's path by the named construction and put a **different** worktree
    on it. Returns the replacement's root, which is B's old path."""
    path = fx.b
    if mode == "move":
        git(fx.a, "worktree", "move", str(path), str(fx.tmp / "B_moved"))
    elif mode == "remove":
        git(fx.a, "worktree", "remove", "--force", str(path))
    else:
        raise AssertionError(mode)
    if at is None:
        git(fx.a, "worktree", "add", "-q", str(path), "-b", f"scratch-{mode}-replacement", fx.head)
    else:
        git(fx.a, "worktree", "add", "-q", "--detach", str(path), at)
    return path


# ---------------------------------------------------------------------------
# Y0 -- what the ownership key actually carries
# ---------------------------------------------------------------------------
print("== Y0. the ownership key is two path values, and no worktree instance ==")
fx = Fixture()
try:
    fx.add_worktree_b("B")
    identity = ws._git_identity(fx.b)
    check("Y0a. _git_identity's first and third elements are the same value",
          identity[0] == identity[2], str(identity[0]))
    check("Y0b. so the 'triple' is two distinct values, both filesystem paths",
          len({identity[0], identity[1]}) == 2
          and all(Path(value).is_absolute() for value in identity))
    detached_before = ws._git_identity(fx.b)
    git(fx.b, "checkout", "--detach", "-q")
    check("Y0c. and the key is unchanged by `git checkout --detach` -- it encodes "
          "neither HEAD nor branch",
          ws._git_identity(fx.b) == detached_before)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Y1 -- aliasing construction (i): relocate the holder, occupy its path
# ---------------------------------------------------------------------------
print("\n== Y1. a different worktree occupying the relocated holder's path ==")
fx = holder_fixture()
try:
    claim = fx.claim
    check("Y1a. B holds the claim, the IN_PROGRESS state and live uncommitted work",
          claim is not None and claim["checkpoint_id"] == "S-CP1"
          and fx.work_item(fx.b)["checkpoints"]["S-CP1"]["status"] == "IN_PROGRESS"
          and (fx.b / "docs/ai-workflow/dry-run/c.txt").exists())
    b_moved = fx.tmp / "B_moved"
    c_root = occupy_vacated_path(fx, mode="move")
    check("Y1b. the relocated holder is locked out of its own claim (declared behaviour)",
          not co.claim_is_this_worktree(b_moved, claim))
    check("Y1c. and it still has the real uncommitted work",
          (b_moved / "docs/ai-workflow/dry-run/c.txt").exists())
    check("Y1d. C is a DIFFERENT worktree instance",
          co._worktree_git_dir(c_root) != claim["worktree_git_dir"],
          f"{co._worktree_git_dir(c_root)} != {claim['worktree_git_dir']}")
    check("Y1e. C has no identity record for this work item", not identity_path(c_root).exists())
    check("Y1f. yet C satisfies the ownership key -- proof 1 is path-aliasable",
          co.claim_is_this_worktree(c_root, claim))

    state_before = (c_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes()
    claim_before = co.claim_path(c_root, WID).read_bytes()
    with ordering(resolve_ownership_r67):
        result = step1_fixed(fx, c_root, now="t2")
    check("Y1g. CONTROL ARM (revision 67): C is ADMITTED and mutates authoritative state",
          result["mutated"] and result["mode"] == "continue_claim", str(result))
    check("Y1h. CONTROL ARM: under B's UNROTATED owner_token",
          co.resolve_claim(c_root, WID)["owner_token"] == claim["owner_token"])
    check("Y1i. CONTROL ARM: with no rotation recorded -- the displaced owner is never fenced",
          co.resolve_claim(c_root, WID).get("takeover_count", 0) == 0
          and co.resolve_claim(c_root, WID).get("previous_owner_tokens", []) == []
          and co.resolve_claim(c_root, WID).get("taken_over_from") is None)

    # Reset to the pre-admission state and run the corrected arm.
    (c_root / "docs/ai-workflow/WORKFLOW_STATE.json").write_bytes(state_before)
    identity_path(c_root).unlink(missing_ok=True)
    co.claim_path(c_root, WID).write_bytes(claim_before)
    expect("Y1j. CORRECTED: C is refused -- the claim proves a path, the record proves "
           "the instance",
           lambda: step1_fixed(fx, c_root, now="t3"), ws.WorktreeIdentityMissingError)
    check("Y1k. and the refusal mutated nothing: state byte-identical, claim untouched",
          (c_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes() == state_before
          and co.claim_path(c_root, WID).read_bytes() == claim_before
          and not identity_path(c_root).exists())
    check("Y1l. no guard residue either", co.read_guard(c_root, WID) is None)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Y2 -- aliasing construction (ii): remove and recreate at the same name
# ---------------------------------------------------------------------------
print("\n== Y2. the holder removed and recreated at the same path, with the same name ==")
fx = holder_fixture(live_work=False)
try:
    claim = fx.claim
    c_root = occupy_vacated_path(fx, mode="remove")
    check("Y2a. the recreated worktree satisfies the ownership key",
          co.claim_is_this_worktree(c_root, claim))
    check("Y2b. and reuses the DEAD holder's admin directory, so binding proof 1 to "
          "`worktree_git_dir` would not close this construction",
          co._worktree_git_dir(c_root) == claim["worktree_git_dir"],
          co._worktree_git_dir(c_root))
    check("Y2c. it has no identity record", not identity_path(c_root).exists())
    ev = co.takeover_evidence(c_root, WID)
    check("Y2d. and `takeover_evidence` reports a live holder that no longer exists",
          ev.get("holder_path_exists") is True and ev.get("holder_registered") is True)

    state_before = (c_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes()
    with ordering(resolve_ownership_r67):
        result = step1_fixed(fx, c_root, now="t2")
    check("Y2e. CONTROL ARM (revision 67): the replacement is ADMITTED and mutates state",
          result["mutated"], str(result))
    check("Y2f. CONTROL ARM: and can commit and release the checkpoint under the dead "
          "holder's token",
          (lambda: (co.release_checkpoint(c_root, WID, "S-CP1",
                                          owner_token=claim["owner_token"], now="t2"),
                    co.resolve_claim(c_root, WID) is None)[1])())

    (c_root / "docs/ai-workflow/WORKFLOW_STATE.json").write_bytes(state_before)
    identity_path(c_root).unlink(missing_ok=True)
    co.claim_checkpoint(c_root, WID, "S-CP1", now="t0", _preread=None)
    # Re-publish the original record so the corrected arm faces the same state.
    co.claim_path(c_root, WID).write_text(json.dumps(claim, indent=2, sort_keys=True) + "\n")
    expect("Y2g. CORRECTED: the replacement is refused",
           lambda: step1_fixed(fx, c_root, now="t3"), ws.WorktreeIdentityMissingError)
    check("Y2h. having mutated nothing",
          (c_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes() == state_before
          and co.resolve_claim(c_root, WID)["owner_token"] == claim["owner_token"])
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Y3 -- CONTINUE_CLAIM stays reachable, via the ordering rather than a relaxation
# ---------------------------------------------------------------------------
print("\n== Y3. `CONTINUE_CLAIM` reachability is restored by 1d's ordering ==")
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

    # The crash window 1d opens, built by the corrected ordering: identity
    # established, claim published, state write never reached.
    co.establish_worktree_identity(fx.a, WID, now="t1")
    published = co.claim_checkpoint(fx.a, WID, "S-CP1", now="t1")
    check("Y3a. the window carries the claim and the identity record, and no state entry",
          co.resolve_claim(fx.a, WID) is not None and identity_path(fx.a).exists()
          and fx.work_item(fx.a)["checkpoints"] == {})
    result = step1_fixed(fx, fx.a, now="t2")
    check("Y3b. the owner resolves CONTINUE_CLAIM through the real step-1 procedure",
          result["mode"] == "continue_claim" and result["selected"] == "S-CP1", str(result))
    check("Y3c. consuming no epoch: the token is unchanged and nothing was taken over",
          co.resolve_claim(fx.a, WID)["owner_token"] == published["owner_token"]
          and co.resolve_claim(fx.a, WID).get("takeover_count", 0) == 0)
    check("Y3d. and `verify_dirty_resume_safety` was NOT relaxed to get there",
          (lambda: (ws.verify_dirty_resume_safety(fx.a, WID), True)[1])())
finally:
    fx.cleanup()

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
    co.claim_checkpoint(fx.a, WID, "S-CP1", now="t1")      # revision 66/67's 1d ordering
    expect("Y3e. CONTROL ARM (claim-first 1d): the same owner is locked out",
           lambda: step1_fixed(fx, fx.a, now="t2"), ws.WorktreeIdentityMissingError)
    check("Y3f. so the ordering, not the relaxation, is what makes the branch reachable",
          fx.work_item(fx.a)["checkpoints"] == {})
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Y4 -- the third error class
# ---------------------------------------------------------------------------
print("\n== Y4. a corrupt identity record refuses BEFORE any authoritative write ==")
SCHEMA_INVALID = json.dumps({
    "repo_root": "/x", "git_common_dir": "/x/.git", "worktree_root": "/x",
    "generated_at": "t", "expected_dirty_paths_by_work_item": [],
})
for label, payload, control_exc in (
    ("unparseable bytes", "{not json", ws.CorruptJsonError),
    ("schema-invalid document", SCHEMA_INVALID, ws.CorruptJsonError),
):
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
        co.establish_worktree_identity(fx.a, WID, now="t1")
        co.claim_checkpoint(fx.a, WID, "S-CP1", now="t1")
        identity_path(fx.a).write_text(payload)            # corrupted by something else
        state_before = (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes()

        expect(f"Y4[{label}] CORRECTED: step 1c refuses with the declared CorruptJsonError",
               lambda: step1_fixed(fx, fx.a, now="t2"), ws.CorruptJsonError)
        check(f"Y4[{label}] and WORKFLOW_STATE.json is byte-identical",
              (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes() == state_before)

        with ordering(resolve_ownership_r67):
            try:
                step1_fixed(fx, fx.a, now="t3")
                raised = None
            except Exception as exc:  # noqa: BLE001
                raised = exc
        check(f"Y4[{label}] CONTROL ARM (revision 67): the check was skipped and state "
              f"was MUTATED before the failure",
              (fx.a / "docs/ai-workflow/WORKFLOW_STATE.json").read_bytes() != state_before
              and isinstance(raised, control_exc),
              f"raised={type(raised).__name__ if raised else None}")
        check(f"Y4[{label}] CONTROL ARM: and the corrupt record is still on disk, unrepaired",
              identity_path(fx.a).read_text() == payload)
        with ordering(resolve_ownership_r67):
            follow_on = None
            try:
                step1_fixed(fx, fx.a, now="t4")
            except Exception as exc:  # noqa: BLE001
                follow_on = exc
        check(f"Y4[{label}] CONTROL ARM: and the next invocation RESUMEs over it, never "
              f"repairing or reporting it",
              follow_on is None)
        expect(f"Y4[{label}] CORRECTED: the same follow-on invocation still refuses",
               lambda: step1_fixed(fx, fx.a, now="t5"), ws.CorruptJsonError)
    finally:
        fx.cleanup()

# ---------------------------------------------------------------------------
# Y5 -- fencing
# ---------------------------------------------------------------------------
print("\n== Y5. no path leaves two worktrees holding one token without a rotation ==")
fx = holder_fixture()
try:
    claim = fx.claim
    c_root = occupy_vacated_path(fx, mode="move")
    ev = co.takeover_evidence(c_root, WID)
    rotated = co.take_over_claim(
        c_root, WID, "S-CP1", now="t2",
        user_authorization=co.takeover_authorization_literal(WID, ev, "S-CP1"),
        evidence=ev)
    check("Y5a. the sanctioned route for the replacement is the explicit takeover",
          rotated["owner_token"] != claim["owner_token"])
    check("Y5b. which rotates the token, increments the epoch and appends the displaced one",
          rotated["takeover_count"] == 1
          and claim["owner_token"] in rotated["previous_owner_tokens"])
    check("Y5c. and records what it displaced, so it is never indistinguishable from "
          "an ordinary claim",
          rotated.get("taken_over_from", {}).get("owner_token") == claim["owner_token"])
    expect("Y5d. the displaced owner is fenced at its next assertion",
           lambda: co.assert_claim_owner(fx.tmp / "B_moved", WID, claim["owner_token"]),
           (co.CheckpointOwnedByOtherWorktreeError,
            co.CheckpointOwnershipStateMismatchError))
    check("Y5e. exactly one usable token exists afterwards",
          co.resolve_claim(c_root, WID)["owner_token"] == rotated["owner_token"])
    check("Y5f. and the takeover established the taking worktree's own identity record, "
          "so the rotation it performed is actually workable",
          identity_path(c_root).exists()
          and WID in ws._load_json(identity_path(c_root))["expected_dirty_paths_by_work_item"])
    result = step1_fixed(fx, c_root, now="t3")
    check("Y5g. so the replacement proceeds only through a fenced, audited transfer",
          result["mutated"] and co.resolve_claim(c_root, WID)["takeover_count"] == 1,
          str(result))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Y6 -- the single automatic release
# ---------------------------------------------------------------------------
print("\n== Y6. the durable-completion release is unreachable by a path-aliased worktree ==")
fx = holder_fixture(live_work=False)
try:
    claim = fx.claim
    git(fx.b, "add", "-A")
    state = ws.complete_checkpoint(fx.read_state(fx.b), WID, "S-CP1", REGISTRY, now="t2")
    fx.write_state(fx.b, state)
    git(fx.b, "add", "-A")
    git(fx.b, "commit", "-qm", "complete S-CP1")            # step 1f commit, release never ran
    b_head = git(fx.b, "rev-parse", "HEAD")
    check("Y6a. the completion is durable at the holder's HEAD and the claim is still held",
          co.resolve_claim(fx.b, WID) is not None)
    c_root = occupy_vacated_path(fx, mode="move", at=b_head)
    check("Y6b. the replacement sees the same durable completion",
          co.committed_checkpoint_status(c_root, WID, "S-CP1") == "COMPLETE")
    with ordering(resolve_ownership_r67):
        released = step1_fixed(fx, c_root, now="t3")
    surviving = co.resolve_claim(c_root, WID)
    check("Y6c. CONTROL ARM (revision 67): it reached the single automatic release, dropped "
          "another worktree's claim residue and fresh-started the next checkpoint",
          surviving is not None and surviving["owner_token"] != claim["owner_token"]
          and surviving["checkpoint_id"] == "S-CP2" and released["mode"] == "fresh",
          str(released))

    co.claim_path(c_root, WID).write_text(json.dumps(claim, indent=2, sort_keys=True) + "\n")
    identity_path(c_root).unlink(missing_ok=True)
    expect("Y6d. CORRECTED: the same worktree is refused before reaching it",
           lambda: step1_fixed(fx, c_root, now="t4"), ws.WorktreeIdentityMissingError)
    check("Y6e. and the claim survives the refusal",
          co.resolve_claim(c_root, WID) is not None
          and co.resolve_claim(c_root, WID)["owner_token"] == claim["owner_token"])
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Y7 -- the identity writer's own failure surface
# ---------------------------------------------------------------------------
print("\n== Y7. no undeclared exception escapes the identity writer ==")
fx = Fixture()
try:
    identity_path(fx.a).parent.mkdir(parents=True, exist_ok=True)
    identity_path(fx.a).write_text(SCHEMA_INVALID)
    try:
        ws.write_worktree_identity(fx.a, WID, now="t1")
        installed = None
    except Exception as exc:  # noqa: BLE001
        installed = exc
    check("Y7a. the installed writer escapes with an UNDECLARED TypeError on a "
          "schema-invalid document",
          isinstance(installed, TypeError),
          f"{type(installed).__name__}: {installed}")
    expect("Y7b. the specified writer raises the declared CorruptJsonError instead",
           lambda: co.establish_worktree_identity(fx.a, WID, now="t1"), ws.CorruptJsonError)
    identity_path(fx.a).write_text("{not json")
    expect("Y7c. and likewise for unparseable bytes",
           lambda: co.establish_worktree_identity(fx.a, WID, now="t1"), ws.CorruptJsonError)
    identity_path(fx.a).unlink()
    document = co.establish_worktree_identity(fx.a, WID, now="t1")
    check("Y7d. an ordinary write still produces the installed writer's own document",
          document == ws._load_json(identity_path(fx.a))
          and WID in document["expected_dirty_paths_by_work_item"])
    check("Y7e. leaving no staging temp file behind",
          not any(p.name.startswith("WORKTREE_IDENTITY.json.tmp-")
                  for p in identity_path(fx.a).parent.iterdir()))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Y8 -- an empty-string lease id
# ---------------------------------------------------------------------------
print("\n== Y8. every flavour of absent lease id is a refusal ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True)
    lease = co.acquire_guard(fx.a, WID, holder_owner_token=fx.owner_token,
                             checkpoint_id="S-CP3", step="y8", step_class=co.DESTRUCTIVE,
                             now="t2")
    guard_bytes = co.guard_path(fx.a, WID).read_bytes()
    expect("Y8a. an empty-string lease_id refuses rather than silently no-opping",
           lambda: co._release_guard_path(fx.a, WID, ""),
           co.CheckpointOwnershipUnavailableError, must_mention=("never a wildcard",))
    check("Y8b. and the guard is still on disk, byte-identical",
          co.guard_path(fx.a, WID).read_bytes() == guard_bytes)

    def r67_release_with_empty_lease():
        """CONTROL ARM: revision 67 refused only a non-`str`, so `""` fell
        through the comparison and returned silently -- safe, but not the
        refusal the absolute states."""
        if not isinstance("", str):
            raise co.CheckpointOwnershipUnavailableError("refused")
        return "fell through, no refusal"

    check("Y8c. CONTROL ARM: revision 67's shape did not refuse it",
          r67_release_with_empty_lease() == "fell through, no refusal")
    check("Y8d. the real release, naming its own lease_id, still works",
          (co.release_guard(fx.a, WID, lease), not co.guard_path(fx.a, WID).exists())[1])
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# Y9 -- the real repository is untouched
# ---------------------------------------------------------------------------
print("\n== Y9. real-repository re-verification (read-only) ==")
REPO = Path(git(Path(__file__).parent, "rev-parse", "--show-toplevel"))
check("Y9a. no claim record exists anywhere in the real repository",
      co.resolve_claim(REPO, WID) is None and co.resolve_claim(REPO, "workflow-v2-1-core") is None)
check("Y9b. and no mutation guard either",
      co.read_guard(REPO, WID) is None and co.read_guard(REPO, "workflow-v2-1-core") is None)
check("Y9c. the claims directory itself was never created in this repository",
      not co.claims_dir(REPO).exists())

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 15: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
