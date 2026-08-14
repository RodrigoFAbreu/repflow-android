"""Prototype of `D-Checkpoint-Ownership` (plan revision 63).

Nothing here is installed into the repository -- this module exists so the
design can be stress-tested against real Git worktrees before any plan-revision
text is written.

The defect it addresses: `/milestone-implement`'s `[2.1 step 1]` decides
resume-vs-fresh-start from the *invoking worktree's own* `WORKFLOW_STATE.json`.
The `IN_PROGRESS` transition (step 1d) is never committed -- step 1f's
checkpoint commit flips it straight to `COMPLETE` -- so an interrupted
checkpoint is invisible outside the worktree that started it, and any other
worktree of the same repository classifies the very same checkpoint as a fresh
start and mutates.

The fix: record the *claim* (not the work) in a repository-shared, never
committed location -- one file per work item under
`$(git rev-parse --git-common-dir)/ai-workflow/checkpoint-claims/`, which every
linked worktree of a repository shares by construction -- and make ownership
resolution consult it before classifying. The claim only routes the invocation
into the existing resume branch; the actual refusal is still produced by the
existing `verify_dirty_resume_safety`, so both of its error classes stay
reachable and distinguishable.

**Authority model.** `WORKFLOW_STATE.json` remains the sole authority on
checkpoint/workflow status. The claim is coordination state only: it answers
exactly one question -- "may this worktree treat this work item as an
unclaimed fresh start?" -- and is never read to decide what a checkpoint's
status *is*.

Revision-63 corrections over the revision-62 draft (each reproduced first in
`pass6.py`, then re-verified in `pass7.py`-`pass10.py`):

* **A1/A2 (blocking)** -- adoption. The draft only ever published a claim at a
  fresh start, so every checkpoint interrupted before the mechanism landed --
  including the real `S-CP3` that `S14` is written against -- stayed
  unprotected forever. `adopt_claim` closes it, and the resume branch adopts
  automatically once `verify_dirty_resume_safety` has proved the caller is the
  originating worktree.
* **A3 (blocking)** -- a claim published with no `IN_PROGRESS` state written
  yet permanently locked the *legitimate owner* out of its own claim.
  `CONTINUE_CLAIM` lets the owner, and only the owner, finish its own
  interrupted acquisition.
* **A4 (important)** -- `O_CREAT | O_EXCL` gives exclusivity but not content
  atomicity, so a crash mid-write left a torn record that failed closed for
  every worktree with no recovery. Publication is now a same-directory temp
  file plus `os.link`, which is atomic in both respects.
* **A5 (important)** -- the claim path was never required to be a regular
  file, so a symlink planted there was followed out of the claims directory
  and its target accepted as an authoritative claim.
* **A6 (important, found in pass 7)** -- the one automatic release branch keyed
  on working-tree `COMPLETE`, which is not a durable fact; it is now gated on
  the checkpoint being `COMPLETE` in the *committed* state at `HEAD`.

Revision-64 corrections over revision 63 (external plan review `GPT-R81-*`,
each reproduced first in `pass10.py`):

* **`GPT-R81-001` (blocking)** -- a claim that is atomically *replaceable* is
  not a claim that *fences*. Revision 63 resolved ownership once at step 1c and
  then let steps 1d/1f mutate and commit with nothing re-asserting ownership, so
  an explicit takeover between them left two worktrees both authorized. Fixed by
  the same primitive family `D-Approval-Commits` already uses for the approval
  transaction: a durable `owner_token` on the claim, a fixed-path no-replace
  `os.link` **mutation/handoff guard** per work item, and the one fixed window
  `acquire guard -> assert_claim_owner(T) -> mutate -> release guard` that
  ordinary mutation and takeover both contend for. Release becomes
  compare-and-delete inside that same window.
* **`GPT-R81-002` (blocking)** -- takeover authorization named a work item and a
  checkpoint, never the claim record the human actually reviewed, so the same
  literal could displace a holder or checkpoint nobody looked at. Fixed by
  binding the authorization to a `claim_observation_id` (sha256 over the exact
  claim bytes, `"absent"` when there is none) and re-reading it under the guard
  before rotating.
* **`GPT-R81-003` (blocking)** -- the durable-release branch returned
  `(FRESH, selected_id)`, which is `(FRESH, None)` once the registry is
  exhausted: a mutation-capable outcome carrying no checkpoint id. Fixed by the
  distinct terminal `NO_CHECKPOINT` outcome; no mutation branch can now receive
  `checkpoint_id is None`.

Revision-65 corrections over revision 64 (external plan review `OPUS-R82-*`,
each reproduced first against this module, then re-verified in `pass12.py`):

* **`OPUS-R82-001` (important)** -- a `"destructive"` guard left behind by a
  crashed session whose worktree was then deleted was recoverable by **no**
  documented operation: `take_over_claim` refused on the class,
  `clear_malformed_guard` refused because the guard was well-formed, and the
  only remaining escape was hand-deleting the lease file. Fixed by
  `recover_abandoned_destructive_guard`: distinct operation, distinct literal
  naming the abandoned step, bound to the guard's **and** the claim's
  observation ids, and gated on the holder no longer being a registered
  worktree -- a durable human-made fact, not a liveness guess.
* **`OPUS-R82-002` (important)** -- the `"destructive"` class was documented as
  never breakable "by anyone, under any authorization", while the same-token
  reclamation branch broke exactly that, with no authorization, for any session
  presenting the claim's (world-readable) token. The reclamation is right and
  stays; what was wrong was its scope. The guard now records
  `holder_worktree_git_dir` and reclamation of a current-epoch guard requires it
  to match, so "never breakable by another worktree" is enforced by the guard
  itself rather than being a downstream consequence of step 1c. Two sessions
  inside one worktree still share one identity and one token and are honestly
  **not** fenced from each other -- documented as the mechanism's scope limit
  rather than claimed as serialization.
"""

from __future__ import annotations

import contextlib
import errno
import fcntl
import hashlib
import json
import os
import secrets
import stat
import subprocess
import tempfile
from collections.abc import Mapping
from pathlib import Path

CLAIMS_RELDIR = "ai-workflow/checkpoint-claims"
CLAIM_SCHEMA_VERSION = 3
STATE_REL_PATH = "docs/ai-workflow/WORKFLOW_STATE.json"

# Ownership resolution outcomes (the return values of `resolve_ownership`).
RESUME = "resume"
FRESH = "fresh"
CONTINUE_CLAIM = "continue_claim"
# T2/`GPT-R81-003`: the terminal outcome. Distinct from `FRESH` precisely
# because it can never reach a mutation branch -- it re-enters
# `/milestone-implement`'s existing "no checkpoint remains" path (step 1b's
# `None` branch, which skips to step 2).
NO_CHECKPOINT = "no_checkpoint"

# Guard step classes (`D-Approval-Commits`' rule, applied to this contract):
# a mutation is `"destructive"` -- never breakable, by anyone, under any
# authorization -- if its already-issued completion could still be externally
# visible after a replacement owner has finished. The checkpoint commit and
# every `WORKFLOW_STATE.json` write qualify; claim publication, the identity
# file, release and takeover itself do not.
ORDINARY = "ordinary"
DESTRUCTIVE = "destructive"


class CheckpointOwnedByOtherWorktreeError(Exception):
    """Raised when claiming or releasing a work item's checkpoint that is
    already claimed by a different worktree of this repository."""


class CheckpointOwnershipStateMismatchError(Exception):
    """Raised when this worktree's own claim cannot be reconciled with this
    worktree's own `WORKFLOW_STATE.json` -- reported, never guessed."""


class CheckpointOwnershipUnavailableError(Exception):
    """Raised when the shared claim record cannot be read or written --
    fails closed rather than proceeding unprotected."""


class CheckpointOriginationUnprovableError(Exception):
    """Raised (revision 69, `OPUS-R86-001`) when this worktree's local state
    records a checkpoint `IN_PROGRESS` that this worktree cannot prove it
    originated, because the **same** `IN_PROGRESS` is present in the
    `WORKFLOW_STATE.json` committed at `HEAD` and could therefore have been
    supplied by an ordinary checkout rather than by this worktree's own step
    1d.

    Distinct from the other three on purpose: nobody else necessarily holds
    the item (`CheckpointOwnedByOtherWorktreeError`), this worktree holds no
    claim to reconcile against (`CheckpointOwnershipStateMismatchError`), and
    every record involved is perfectly readable
    (`CheckpointOwnershipUnavailableError`). The escape is the explicit
    takeover, which is user-authorized and audited -- exactly the judgment
    adoption is not allowed to make on its own."""


class CheckpointClaimTakeoverRefusedError(Exception):
    """Raised when an explicit takeover is attempted without the authorization
    the takeover contract requires."""


def _run(args: list[str], cwd: Path) -> str:
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, check=True).stdout


def _git_identity(repo_root: Path) -> tuple[str, str, str]:
    """Same triple, resolved the same way, as `workflow_state._git_identity`.

    Ownership equality is deliberately this exact triple and nothing else, so
    that a claim's notion of "this worktree" is byte-identical to
    `verify_dirty_resume_safety`'s. Keeping them identical is what makes the
    two mechanisms impossible to disagree with each other.
    """
    repo_root_out = _run(["git", "rev-parse", "--show-toplevel"], cwd=repo_root).strip()
    raw = _run(["git", "rev-parse", "--git-common-dir"], cwd=repo_root).strip()
    common = raw if Path(raw).is_absolute() else str((repo_root / raw).resolve())
    return repo_root_out, common, repo_root_out


def _worktree_git_dir(repo_root: Path) -> str:
    """The per-worktree admin directory (`$GIT_COMMON_DIR/worktrees/<name>` for
    a linked worktree, `$GIT_COMMON_DIR` for the primary one).

    Recorded as **diagnostic** identity only, never as the ownership key: it
    survives `git worktree move`, which the recorded `worktree_root` does not,
    and that is exactly what lets a takeover tell "the holder was deleted" from
    "the holder was relocated" instead of guessing.
    """
    return _run(["git", "rev-parse", "--absolute-git-dir"], cwd=repo_root).strip()


def claims_dir(repo_root: Path) -> Path:
    _, common_dir, _ = _git_identity(repo_root)
    return Path(common_dir) / CLAIMS_RELDIR


def claim_path(repo_root: Path, work_item_id: str) -> Path:
    """One file per work item, named by digest so no `work_item_id` value can
    ever escape the claims directory (the same "token, not a path" discipline
    `D-Bundle-Manifest`'s `bundles/<token>` already uses)."""
    token = hashlib.sha256(work_item_id.encode()).hexdigest()
    return claims_dir(repo_root) / f"{token}.json"


def guard_path(repo_root: Path, work_item_id: str) -> Path:
    """The mutation/handoff guard (`GPT-R81-001`): one **fixed** pathname per
    work item, deliberately not token-scoped, because its whole purpose is to
    be the single object an owner's mutation and a takeover's rotation contend
    for. Same digest naming, same directory, same symlink refusals as the claim
    itself."""
    token = hashlib.sha256(work_item_id.encode()).hexdigest()
    return claims_dir(repo_root) / f"{token}.lease"


def guard_mutation_lock_path(repo_root: Path, work_item_id: str) -> Path:
    """`OPUS-R83-001`: the stable object every guard **mutation** serializes on.

    Deliberately a *different* file from the guard itself, and deliberately one
    that is **created once and never unlinked**. Locking the guard file would be
    useless for exactly the reason the finding exists: the guard's whole
    lifecycle is create-and-remove, and two processes holding `flock` on two
    different inodes that briefly shared one pathname are not serialized at all.
    """
    token = hashlib.sha256(work_item_id.encode()).hexdigest()
    return claims_dir(repo_root) / f"{token}.guardlock"


@contextlib.contextmanager
def guard_mutation_lock(repo_root: Path, work_item_id: str):
    """Hold `D1`'s process-scoped `fcntl.flock` primitive across a guard
    mutation, so **compare-and-remove is one indivisible step** rather than two
    statements a preemption can be scheduled between (`OPUS-R83-001`).

    This is *not* the fence and must never be mistaken for one. It is held for a
    handful of syscalls, entirely inside one guard operation; the **guard** is
    what spans a mutation window. Lock ordering is therefore trivially safe: it
    is never held across a `WORKFLOW_STATE.json` write, so it is never held at
    the same time as `D1`'s state-file lock and the existing guard-then-`flock`
    rule is untouched.

    Advisory, so `os.link`'s `EEXIST` exclusivity is retained underneath it as
    defense in depth rather than replaced by it. Released by the kernel on
    process death, which is what keeps a crash inside a guard mutation from
    becoming a second class of permanent lockout.
    """
    path = guard_mutation_lock_path(repo_root, work_item_id)
    path.parent.mkdir(parents=True, exist_ok=True)
    _assert_not_symlink(path.parent, "claims directory")
    _assert_not_symlink(path, "guard mutation lock")
    fd = os.open(path, os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0), 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        yield
    finally:
        os.close(fd)


# ---------------------------------------------------------------------------
# Reading
# ---------------------------------------------------------------------------


def _assert_not_symlink(path: Path, what: str) -> None:
    """A5: the claims directory and the claim file must both be real objects.

    Never follow a link out of the claims directory -- a claim reached through
    one is not this repository's coordination state, whatever it contains.
    """
    try:
        st = os.lstat(path)
    except FileNotFoundError:
        return
    except OSError as exc:
        raise CheckpointOwnershipUnavailableError(f"cannot stat {path} ({exc})") from exc
    if stat.S_ISLNK(st.st_mode):
        raise CheckpointOwnershipUnavailableError(
            f"{what} {path} is a symbolic link -- refusing to read or publish checkpoint "
            f"ownership through a link out of the claims directory"
        )


def _read_claim_bytes(path: Path) -> bytes | None:
    _assert_not_symlink(path.parent, "claims directory")
    _assert_not_symlink(path, "claim record")
    try:
        fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    except FileNotFoundError:
        return None
    except OSError as exc:
        if exc.errno in (errno.ELOOP, errno.EMLINK):
            raise CheckpointOwnershipUnavailableError(
                f"{path} is a symbolic link -- refusing to resolve checkpoint ownership "
                f"through it"
            ) from exc
        raise CheckpointOwnershipUnavailableError(f"cannot read {path} ({exc})") from exc
    try:
        with os.fdopen(fd, "rb") as handle:
            return handle.read()
    except OSError as exc:
        # A directory `open`s successfully and fails at `fdopen`/`read` with
        # `EISDIR`. Through revision 65 this escaped as a raw
        # `IsADirectoryError`, so `takeover_evidence` crashed with an undeclared
        # exception type instead of reporting (`OPUS-R83-002`, manifestation B).
        try:
            os.close(fd)
        except OSError:
            pass
        raise CheckpointOwnershipUnavailableError(
            f"cannot read {path} ({exc}) -- refusing to resolve checkpoint ownership "
            f"against a record whose bytes cannot be read") from exc


def _validate_claim(path: Path, data, work_item_id: str) -> dict:
    if not isinstance(data, dict) or data.get("schema_version") != CLAIM_SCHEMA_VERSION:
        raise CheckpointOwnershipUnavailableError(
            f"{path} has an unsupported shape/schema_version "
            f"(expected {CLAIM_SCHEMA_VERSION})")
    required = ("work_item_id", "checkpoint_id", "repo_root", "git_common_dir",
                "worktree_root", "worktree_git_dir", "claimed_at", "owner_token")
    missing = [field for field in required if not isinstance(data.get(field), str)]
    if missing:
        raise CheckpointOwnershipUnavailableError(f"{path} is missing/malformed fields {missing}")
    if data["work_item_id"] != work_item_id:
        raise CheckpointOwnershipUnavailableError(
            f"{path} records work item {data['work_item_id']!r}, not {work_item_id!r}")
    return data


def resolve_claim(repo_root: Path, work_item_id: str) -> dict | None:
    """The one read every invocation performs. `None` == unclaimed.

    Every failure mode -- unreadable, torn, wrong schema, wrong work item,
    reached through a symlink -- raises rather than returning `None`, so an
    undecidable claim can never be mistaken for an absent one.
    """
    path = claim_path(repo_root, work_item_id)
    raw = _read_claim_bytes(path)
    if raw is None:
        return None
    try:
        data = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise CheckpointOwnershipUnavailableError(
            f"{path} exists but could not be read as JSON ({exc}) -- refusing to start or "
            f"resume any checkpoint against an unreadable claim record; clear it with the "
            f"explicit takeover operation, never by guessing"
        ) from exc
    return _validate_claim(path, data, work_item_id)


ABSENT_OBSERVATION = "absent"

# `OPUS-R83-002`: the observation of a record whose *bytes* cannot be read is
# hashed under its own domain tag, so it can never collide with the sha256 of
# some real record's content. An authorization bound to "there is a symlink
# here" is therefore never satisfiable by a byte-readable record, and vice
# versa.
UNREADABLE_OBSERVATION_DOMAIN = b"unreadable-checkpoint-record-v1\x00"

# Which unreadable kinds a rotation (`os.rename` onto the name) can actually
# replace. A symlink and an unreadable regular file are replaced by the rename
# itself, which never follows the link and never writes through it. A directory
# is not: `rename` refuses, and this design never removes a directory it did
# not create.
REPLACEABLE_UNREADABLE_KINDS = frozenset({"symlink", "unreadable-file", "not-a-regular-file"})


def describe_unreadable_record(path: Path) -> dict | None:
    """What `lstat` alone can say about a record whose bytes cannot be read
    (`OPUS-R83-002`). Returns `None` when the record is absent or genuinely
    byte-readable -- in which case `observation_id` over its bytes applies, as
    before.

    Deliberately derived from durable, re-verifiable facts only, and for a
    symlink from the raw link target rather than anything read *through* it:
    the target is never opened, so an off-tree file is never touched.
    """
    try:
        st = os.lstat(path)
    except FileNotFoundError:
        return None
    except OSError as exc:
        return {"kind": "unstattable", "errno": errno.errorcode.get(exc.errno, str(exc.errno))}
    if stat.S_ISLNK(st.st_mode):
        try:
            target = os.readlink(path)
        except OSError:
            target = None
        return {"kind": "symlink", "link_target": target}
    if stat.S_ISDIR(st.st_mode):
        return {"kind": "directory"}
    if not stat.S_ISREG(st.st_mode):
        return {"kind": "not-a-regular-file", "st_mode": stat.S_IFMT(st.st_mode)}
    try:
        fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    except OSError as exc:
        return {"kind": "unreadable-file",
                "errno": errno.errorcode.get(exc.errno, str(exc.errno)),
                "st_mode": stat.S_IMODE(st.st_mode)}
    os.close(fd)
    return None


def unreadable_observation_id(descriptor: Mapping) -> str:
    payload = json.dumps(descriptor, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(UNREADABLE_OBSERVATION_DOMAIN + payload).hexdigest()


def observation_id(raw: bytes | None) -> str:
    """`GPT-R81-002`: the immutable identity of *the exact record a human
    reviewed*. Defined over raw bytes rather than parsed content, so an
    unreadable or torn record has an equally binding observation instead of
    falling back to a reusable generic authorization. `"absent"` is itself an
    observation: authorizing a takeover of "no claim" must not survive somebody
    publishing one in the meantime."""
    if raw is None:
        return ABSENT_OBSERVATION
    return hashlib.sha256(raw).hexdigest()


def observe_claim(repo_root: Path,
                  work_item_id: str) -> tuple[str, dict | None, str | None, dict | None]:
    """Read the claim **and** its observation id in one pass, reporting rather
    than raising on an undecidable record: returns
    `(observation_id, parsed_claim_or_None, error_or_None, unreadable_descriptor_or_None)`.

    `resolve_ownership` still uses `resolve_claim`, which fails closed. This
    exists for the two operations that must be able to *describe* a record they
    refuse to act on: `takeover_evidence` and the takeover's own re-read.

    **Extended, `OPUS-R83-002`**: a record whose bytes cannot be read at all --
    a symlink, a directory, an `EACCES` file -- is now *described* rather than
    raised through. Through revision 65 the plan advertised an
    observation-bound recovery for an unreadable record while evidence
    gathering itself raised for this flavour of unreadable, so the recovery was
    unreachable and item 365(c)'s obligation could not be discharged.
    """
    path = claim_path(repo_root, work_item_id)
    try:
        raw = _read_claim_bytes(path)
    except CheckpointOwnershipUnavailableError as exc:
        descriptor = describe_unreadable_record(path)
        if descriptor is None:
            raise
        return unreadable_observation_id(descriptor), None, str(exc), descriptor
    oid = observation_id(raw)
    if raw is None:
        return oid, None, None, None
    try:
        data = json.loads(raw)
        return oid, _validate_claim(path, data, work_item_id), None, None
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        return oid, None, f"unparseable claim record ({exc})", None
    except CheckpointOwnershipUnavailableError as exc:
        return oid, None, str(exc), None


def claim_is_this_worktree(repo_root: Path, claim: dict) -> bool:
    repo_root_id, common, worktree_root = _git_identity(repo_root)
    return (claim.get("repo_root"), claim.get("git_common_dir"), claim.get("worktree_root")) == (
        repo_root_id, common, worktree_root)


# ---------------------------------------------------------------------------
# Publication
# ---------------------------------------------------------------------------


def _build_record(repo_root: Path, work_item_id: str, checkpoint_id: str, now: str,
                  *, adopted: bool = False, taken_over_from: dict | None = None,
                  takeover_count: int = 0,
                  previous_owner_tokens: tuple[str, ...] | list[str] = ()) -> dict:
    repo_root_id, common, worktree_root = _git_identity(repo_root)
    record = {
        "schema_version": CLAIM_SCHEMA_VERSION,
        "work_item_id": work_item_id,
        "checkpoint_id": checkpoint_id,
        "repo_root": repo_root_id,
        "git_common_dir": common,
        "worktree_root": worktree_root,
        "worktree_git_dir": _worktree_git_dir(repo_root),
        "claimed_at": now,
        "adopted": adopted,
        # `GPT-R81-001`: ownership is durable data a fresh session can present,
        # not a process-lifetime property. Minted here and nowhere else; a
        # takeover rotates it, which is what makes the displaced owner's next
        # assertion fail.
        "owner_token": secrets.token_hex(16),
        "takeover_count": takeover_count,
        "previous_owner_tokens": list(previous_owner_tokens),
    }
    if taken_over_from is not None:
        record["taken_over_from"] = taken_over_from
    return record


def _stage(path: Path, record: dict, *, allow_unreadable_target: bool = False) -> Path:
    """Stage the payload in the claims directory. The claims **directory** must
    always be a real directory -- that assertion is never relaxed.

    `allow_unreadable_target` (`OPUS-R83-002`) relaxes the assertion on the
    final *name* only, and only for the two authorized operations that exist to
    replace an undecidable record. It is safe precisely because the staging
    happens at a fresh temp name and the publication is `os.rename` onto the
    final one: the link is replaced, never followed and never written through.
    """
    payload = json.dumps(record, indent=2, sort_keys=True) + "\n"
    _assert_not_symlink(path.parent, "claims directory")
    if not allow_unreadable_target:
        _assert_not_symlink(path, "claim record")
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp_name = tempfile.mkstemp(dir=str(path.parent), prefix=".claim-", suffix=".tmp")
        with os.fdopen(fd, "w") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    except OSError as exc:
        raise CheckpointOwnershipUnavailableError(
            f"cannot stage a claim next to {path} ({exc}) -- refusing to start a checkpoint "
            f"whose claim cannot be made discoverable to this repository's other worktrees"
        ) from exc
    return Path(tmp_name)


def _publish_replacing(path: Path, record: dict, *,
                       allow_unreadable_target: bool = False) -> None:
    """T1: the takeover's publication. `os.rename` over the existing name is
    atomic and never leaves the path absent, so an authorized takeover that
    fails mid-way leaves the *previous* claim in force rather than silently
    unprotecting the work item. Never used for ordinary acquisition, which must
    fail rather than replace."""
    tmp = _stage(path, record, allow_unreadable_target=allow_unreadable_target)
    try:
        os.rename(tmp, path)
    except OSError as exc:
        tmp.unlink(missing_ok=True)
        raise CheckpointOwnershipUnavailableError(
            f"cannot publish the replacement claim at {path} ({exc}) -- the previous claim is "
            f"left in force; nothing was released"
        ) from exc


def _publish_exclusive(path: Path, record: dict) -> None:
    """A4: atomic in **both** senses -- complete content, and create-if-absent.

    Write the whole payload to a temp file in the same directory, then `os.link`
    it into place: the link either creates the final name with fully-written
    content, or fails `EEXIST` because somebody else won. There is no window in
    which a reader can observe a partially-written claim, so a crash can never
    leave a record that fails closed for everybody.
    """
    tmp = _stage(path, record)
    try:
        os.link(tmp, path)
    except FileExistsError:
        raise
    except OSError as exc:
        raise CheckpointOwnershipUnavailableError(
            f"cannot publish {path} ({exc}) -- refusing to start a checkpoint whose claim "
            f"cannot be made discoverable to this repository's other worktrees"
        ) from exc
    finally:
        tmp.unlink(missing_ok=True)


def _claim_or_refuse(repo_root: Path, work_item_id: str, record: dict) -> dict:
    path = claim_path(repo_root, work_item_id)
    try:
        _publish_exclusive(path, record)
    except FileExistsError:
        existing = resolve_claim(repo_root, work_item_id)
        _, _, worktree_root = _git_identity(repo_root)
        if existing is not None and not claim_is_this_worktree(repo_root, existing):
            raise CheckpointOwnedByOtherWorktreeError(
                f"{work_item_id!r} checkpoint {existing.get('checkpoint_id')!r} is already "
                f"claimed by worktree {existing.get('worktree_root')!r} (this worktree is "
                f"{worktree_root!r}) -- resume it there, or take the claim over explicitly"
            ) from None
        if existing is not None and existing.get("checkpoint_id") != record["checkpoint_id"]:
            raise CheckpointOwnershipStateMismatchError(
                f"this worktree already claims {work_item_id!r} checkpoint "
                f"{existing.get('checkpoint_id')!r}; refusing to silently repoint it at "
                f"{record['checkpoint_id']!r}"
            ) from None
        return existing if existing is not None else record
    return record


# ---------------------------------------------------------------------------
# The mutation/handoff guard -- `GPT-R81-001`
# ---------------------------------------------------------------------------


def read_guard(repo_root: Path, work_item_id: str) -> dict | None:
    """The guard body, or `None` if the guard is not held. Fails closed on a
    torn or symlinked guard exactly as the claim does -- an undecidable guard
    is never read as an absent one."""
    path = guard_path(repo_root, work_item_id)
    raw = _read_claim_bytes(path)
    if raw is None:
        return None
    try:
        data = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise CheckpointOwnershipUnavailableError(
            f"{path} exists but could not be read as JSON ({exc}) -- refusing to mutate "
            f"checkpoint state behind an undecidable mutation guard") from exc
    if not isinstance(data, dict) or not isinstance(data.get("lease_id"), str):
        raise CheckpointOwnershipUnavailableError(f"{path} is not a well-formed mutation guard")
    return data


def _publish_guard(repo_root: Path, work_item_id: str, body: dict) -> None:
    _publish_exclusive(guard_path(repo_root, work_item_id), body)


def _current_owner_token(repo_root: Path, work_item_id: str) -> tuple[str | None, bool]:
    """`(current owner_token, epoch_is_decidable)`. `None` with `True` means
    there is genuinely no claim, so **any** guard is superseded. An undecidable
    claim returns `False`, and a guard whose epoch cannot be judged is then
    never reclaimed -- the fail-closed direction."""
    try:
        claim = resolve_claim(repo_root, work_item_id)
    except CheckpointOwnershipUnavailableError:
        return None, False
    return (claim.get("owner_token") if claim is not None else None), True


def acquire_guard(repo_root: Path, work_item_id: str, *, holder_owner_token: str | None,
                  checkpoint_id: str | None, step: str, step_class: str, now: str,
                  role: str = "owner", authorized_lease_id: str | None = None) -> dict:
    """One acquisition attempt. **No retry loop, no timeout, no wall clock** --
    every branch below decides from durable data alone.

    - **superseded epoch** (`holder_owner_token` is not the claim's current
      token): the guard was provably left by a session whose ownership has
      already been rotated away, since only a rotation changes that token and a
      rotation only ever happens while holding this same guard. Reclaimed with
      no authorization, then acquisition is retried exactly **once**. This is
      the branch every recovery ultimately lands on, so it is deliberately not
      narrowed to a worktree.
    - **`role="owner"`, same token *and* same worktree as the caller**: the
      caller's own leftover guard from an interrupted earlier step. Reclaimed
      and retried once; the window's first act is `assert_claim_owner`, so a
      stale belief about ownership is caught immediately regardless.
      `holder_worktree_git_dir` is a required conjunct (`OPUS-R82-002`): the
      token is world-readable coordination data that any worktree can read out
      of the claim file, so token equality alone let a *foreign* worktree
      reclaim a live `"destructive"` guard, leaving "never breakable by another
      worktree" true only as a consequence of step 1c rather than of the guard.
      Within one worktree it is honestly **not** a fence -- two sessions there
      share one identity and one token, and the second reclaims the first's
      guard; see `recover_abandoned_destructive_guard` for why that is the
      lesser of the two evils.
    - **`role="takeover"`, current epoch**: `"destructive"` refuses
      unconditionally -- the owner is inside a commit or a state write and no
      takeover authorization releases that. `"ordinary"` releases only when the
      observed `lease_id` is exactly the one the user's guard-release
      authorization quoted; a *different* `lease_id` proves the owner released
      and re-acquired, i.e. is demonstrably live, and refuses.
    - **`role="recovery"`, current epoch** (`OPUS-R82-001`): the one path that
      may reclaim a `"destructive"` guard from another worktree, reachable
      *only* from `recover_abandoned_destructive_guard`, which has already
      established that the holder worktree is no longer registered and has
      bound the user's authorization to both durable observations. The
      `lease_id` must still be exactly the one authorized; anything else means
      the guard changed under the operation and refuses.
    - anything else refuses, naming the held guard.
    """
    body = {
        "lease_id": secrets.token_hex(16),
        "holder_owner_token": holder_owner_token,
        # `OPUS-R82-002`: which **worktree** holds the window, recorded on the
        # same terms the claim records it. The stable admin dir, not the path,
        # so a `git worktree move` of the holder does not turn its own leftover
        # guard into an unreclaimable one.
        "holder_worktree_git_dir": _worktree_git_dir(repo_root),
        "work_item_id": work_item_id,
        "checkpoint_id": checkpoint_id,
        "step": step,
        "step_class": step_class,
        "acquired_at": now,
    }
    if step_class not in (ORDINARY, DESTRUCTIVE):
        raise CheckpointOwnershipUnavailableError(f"unknown guard step_class {step_class!r}")
    # `OPUS-R83-001`: the reclaim-and-republish sequence below removes a guard
    # and publishes another, and those two steps must be indivisible with
    # respect to the `lease_id` this session compared against. Everything from
    # the first observation to the publication runs inside the serialization.
    with guard_mutation_lock(repo_root, work_item_id):
        return _acquire_guard_locked(repo_root, work_item_id, body=body,
                                     holder_owner_token=holder_owner_token, role=role,
                                     authorized_lease_id=authorized_lease_id)


def _acquire_guard_locked(repo_root: Path, work_item_id: str, *, body: dict,
                          holder_owner_token: str | None, role: str,
                          authorized_lease_id: str | None) -> dict:
    """`acquire_guard`'s decision and publication, run under
    `guard_mutation_lock`. Split out so the lock is taken exactly once by the
    operation rather than re-entered by each helper (`OPUS-R83-001`)."""
    try:
        _publish_guard(repo_root, work_item_id, body)
        return body
    except FileExistsError:
        pass

    held = read_guard(repo_root, work_item_id)
    if held is None:                       # released between the two operations
        _publish_guard(repo_root, work_item_id, body)
        return body

    current, decidable = _current_owner_token(repo_root, work_item_id)
    reclaim = False
    if decidable and held.get("holder_owner_token") != current:
        reclaim = True                     # superseded epoch
    elif (role == "owner" and held.get("holder_owner_token") == holder_owner_token
          and held.get("holder_worktree_git_dir") == _worktree_git_dir(repo_root)):
        reclaim = True                     # this worktree's own leftover guard
    elif role == "owner" and held.get("holder_owner_token") == holder_owner_token:
        # `OPUS-R82-002`: same token, different worktree. The token proves the
        # epoch, never the holder, so this is the case where "never breakable by
        # another worktree" has to be enforced by the guard itself.
        raise CheckpointOwnershipUnavailableError(
            f"{work_item_id!r}'s mutation guard is held by worktree "
            f"{held.get('holder_worktree_git_dir')!r} (lease {held.get('lease_id')!r}, step "
            f"{held.get('step')!r}, class {held.get('step_class')!r}); this worktree is "
            f"{_worktree_git_dir(repo_root)!r} -- presenting the claim's token is not being the "
            f"holder, and no worktree breaks another worktree's window here")
    elif role == "takeover":
        if held.get("step_class") == DESTRUCTIVE:
            raise CheckpointClaimTakeoverRefusedError(
                f"{work_item_id!r}'s owner is inside the destructive step "
                f"{held.get('step')!r} (lease {held.get('lease_id')!r}) -- no takeover "
                f"authorization breaks that window; resume in the owning session, or, if that "
                f"worktree is gone, use the abandoned-guard recovery")
        if authorized_lease_id is not None and authorized_lease_id == held.get("lease_id"):
            reclaim = True                 # the authorized break, exactly as quoted
        else:
            raise CheckpointClaimTakeoverRefusedError(
                f"{work_item_id!r}'s mutation guard is held (lease {held.get('lease_id')!r}, step "
                f"{held.get('step')!r}) and does not match the authorized lease "
                f"{authorized_lease_id!r} -- the owner is live; refusing to break it")
    elif role == "recovery":
        # `OPUS-R82-001`. Reachable only from `recover_abandoned_destructive_guard`,
        # which has already established the holder worktree is deregistered and
        # bound the authorization to both observations. The lease must still be
        # exactly the authorized one: a different one means something re-acquired
        # the guard while the user was reading the evidence.
        if authorized_lease_id is not None and authorized_lease_id == held.get("lease_id"):
            reclaim = True
        else:
            raise CheckpointClaimTakeoverRefusedError(
                f"{work_item_id!r}'s mutation guard (lease {held.get('lease_id')!r}) is no longer "
                f"the abandoned one the recovery authorized ({authorized_lease_id!r}) -- "
                f"refusing, having mutated nothing")
    if not reclaim:
        raise CheckpointOwnershipUnavailableError(
            f"{work_item_id!r}'s mutation guard is held by lease {held.get('lease_id')!r} "
            f"(step {held.get('step')!r}, class {held.get('step_class')!r}) -- refusing to mutate "
            f"checkpoint state concurrently with its owner")

    _release_guard_path_locked(repo_root, work_item_id, held.get("lease_id"))
    try:
        _publish_guard(repo_root, work_item_id, body)
    except FileExistsError as exc:
        raise CheckpointOwnershipUnavailableError(
            f"{work_item_id!r}'s mutation guard was re-acquired by another session during "
            f"reclamation -- refusing, having mutated nothing") from exc
    return body


def _release_guard_path_locked(repo_root: Path, work_item_id: str,
                               lease_id: str) -> None:
    """Compare-and-delete on `lease_id`: never remove a guard this session does
    not hold (the same discipline release applies to the claim itself).

    **Caller must hold `guard_mutation_lock`** (`OPUS-R83-001`). Through
    revision 65 this was a bare read-then-`unlink`, and the `unlink` named the
    *pathname* rather than the identity the comparison had just established --
    so a guard published between the two statements was removed instead,
    including a live `"destructive"` one belonging to the work item's current
    owner in another worktree. Reproduced in `pass13.py` `I2`/`I3`.

    Both branches leaked, and both are closed by the same serialization: `held
    is None` unlinked unconditionally, and a matching `lease_id` unlinked
    whatever now occupied the name.

    G2 (pass 11): an **undecidable** guard is left in place rather than
    deleted. A release path that silently drops a record it could not read
    would be the one place in this design where fail-closed quietly became
    fail-open; the documented recovery is `clear_malformed_guard`.

    **An absent `lease_id` is a refusal, never a wildcard** (revision 67,
    `OPUS-R84` non-blocking observation 1). Revision 66 closed the `held is
    None` branch but left the caller-side `lease_id is None` case falling
    straight through the comparison to an unconditional `unlink`. No
    in-contract caller reaches it -- `acquire_guard` always mints a `lease_id`
    and `read_guard` rejects a body whose `lease_id` is not a string -- but it
    contradicts the absolute the design now states ("every removal of a guard,
    on every path, names the `lease_id` it just observed"), and it is a trap
    for `WF8b`'s implementation. The lease id is required, and `None` refuses.

    **And so does the empty string** (revision 68, `OPUS-R85` non-blocking
    observation 1). Revision 67 refused only a non-`str` value, so `""` fell
    through the comparison and silently no-opped. Nothing was removed, so the
    safety property held -- but "absent" in the sense the absolute means it
    covers `""` as squarely as `None`, and a primitive whose refusal depends on
    which flavour of absent it was handed is exactly the ambiguity this
    statement exists to remove.
    """
    if not isinstance(lease_id, str) or not lease_id:
        raise CheckpointOwnershipUnavailableError(
            f"releasing {work_item_id!r}'s mutation guard requires the exact lease_id being "
            f"released, got {lease_id!r} -- an absent lease id is a refusal, never a wildcard "
            f"that removes whatever guard is present")
    path = guard_path(repo_root, work_item_id)
    try:
        held = read_guard(repo_root, work_item_id)
    except CheckpointOwnershipUnavailableError:
        return
    if held is None:
        # Nothing was compared, so there is nothing this session may remove.
        # Unlinking here is what deleted a guard published in the same instant.
        return
    if held.get("lease_id") != lease_id:
        return
    path.unlink(missing_ok=True)


def _release_guard_path(repo_root: Path, work_item_id: str, lease_id: str) -> None:
    """The locked compare-and-delete, for callers that do not already hold the
    guard mutation lock."""
    with guard_mutation_lock(repo_root, work_item_id):
        _release_guard_path_locked(repo_root, work_item_id, lease_id)


def guard_clearance_authorization_literal(evidence: dict) -> str:
    return (f"clear malformed checkpoint guard {evidence.get('work_item_id')} "
            f"observation {evidence.get('guard_observation_id')}")


def clear_malformed_guard(repo_root: Path, work_item_id: str, *,
                          user_authorization: str | None) -> None:
    """G2 (pass 11): the defined recovery for a guard this implementation
    cannot have written -- torn, wrong shape, or otherwise undecidable.

    Publication is a same-directory temp file plus `os.link`, so a partially
    written guard is not producible here; a guard that *is* undecidable was
    corrupted by something else, and it fails closed for every session
    including the legitimate owner. Left there, that is a permanent lockout
    with no escape -- exactly the defect `A4` fixed for the claim record.

    The escape is explicit, user-authorized and observation-bound, never
    automatic and never time-based: the literal must quote the exact
    `guard_observation_id` the evidence reported, and the bytes must still
    hash to it at the moment of removal. A guard that has changed since the
    evidence was read is not the one the user authorized clearing.
    """
    path = guard_path(repo_root, work_item_id)
    raw = _read_claim_bytes(path)
    evidence = {"work_item_id": work_item_id, "guard_observation_id": observation_id(raw)}
    if user_authorization != guard_clearance_authorization_literal(evidence):
        raise CheckpointClaimTakeoverRefusedError(
            f"clearing a malformed mutation guard requires the literal authorization "
            f"{guard_clearance_authorization_literal(evidence)!r} -- refusing to remove a "
            f"guard on an inference")
    # `OPUS-R83-001`: the removal is atomic with respect to the observation it
    # was authorized against. Without the lock, a guard cleared by one session
    # and legitimately republished by another between this re-read and the
    # `unlink` was removed by *this* operation, which the user authorized
    # against entirely different bytes.
    with guard_mutation_lock(repo_root, work_item_id):
        current = _read_claim_bytes(path)
        if observation_id(current) != evidence["guard_observation_id"]:
            raise CheckpointClaimTakeoverRefusedError(
                f"{work_item_id!r}'s mutation guard changed between the evidence the user "
                f"authorized ({evidence['guard_observation_id']}) and this clearance "
                f"({observation_id(current)}) -- refusing, having removed nothing")
        try:
            read_guard(repo_root, work_item_id)
        except CheckpointOwnershipUnavailableError:
            path.unlink(missing_ok=True)
            return
    raise CheckpointClaimTakeoverRefusedError(
        f"{work_item_id!r}'s mutation guard is well-formed -- this operation only ever "
        f"removes an undecidable record; use the ordinary takeover contract instead")


def release_guard(repo_root: Path, work_item_id: str, lease: dict) -> None:
    _release_guard_path(repo_root, work_item_id, lease.get("lease_id"))


def assert_claim_owner(repo_root: Path, work_item_id: str, owner_token: str) -> dict:
    """Re-read the claim at its final pathname and require its `owner_token` to
    equal exactly the token this session holds. On any failure -- absent claim,
    undecidable claim, rotated token -- stop immediately and mutate nothing.

    This is the mechanism by which an owner whose claim was taken over refuses
    instead of continuing: after a takeover the claim carries a rotated token,
    so the displaced owner's very next assertion fails. It is meaningful only
    **inside** the guard; called outside it, it is a check-before-use, which is
    exactly the shape `GPT-R81-001` reproduced as insufficient.
    """
    claim = resolve_claim(repo_root, work_item_id)
    if claim is None:
        raise CheckpointOwnershipStateMismatchError(
            f"this session holds {work_item_id!r} owner token {owner_token!r} but no claim "
            f"record exists -- refusing to mutate")
    if claim.get("owner_token") != owner_token:
        if not claim_is_this_worktree(repo_root, claim):
            raise CheckpointOwnedByOtherWorktreeError(
                f"{work_item_id!r}'s claim now belongs to worktree "
                f"{claim.get('worktree_root')!r} with owner token {claim.get('owner_token')!r}; "
                f"this session holds {owner_token!r} and is therefore no longer the owner -- "
                f"refusing to write authoritative state or commit")
        raise CheckpointOwnershipStateMismatchError(
            f"{work_item_id!r}'s claim carries owner token {claim.get('owner_token')!r}, not the "
            f"{owner_token!r} this session holds -- ownership was rotated; refusing to mutate")
    return claim


@contextlib.contextmanager
def owner_mutation(repo_root: Path, work_item_id: str, owner_token: str, *,
                   checkpoint_id: str | None, step: str, step_class: str, now: str):
    """The one fixed window shape every ownership-bearing mutation runs inside:

        acquire the guard -> assert_claim_owner(T) *under* the guard ->
        perform the mutation -> release the guard

    Every pause, stall or crash between the assertion and the mutation is
    therefore inside a window a takeover cannot enter. Windows are strictly
    non-nested: a session holds at most one guard at a time, and the guard
    covers the mutation only -- never a following verification, which is a pure
    read.
    """
    lease = acquire_guard(repo_root, work_item_id, holder_owner_token=owner_token,
                          checkpoint_id=checkpoint_id, step=step, step_class=step_class,
                          now=now, role="owner")
    try:
        yield assert_claim_owner(repo_root, work_item_id, owner_token)
    finally:
        release_guard(repo_root, work_item_id, lease)


def _workflow_state():
    """The repository's real `scripts/workflow_state.py`, imported lazily so
    this module stays free-standing for every caller that does not need it --
    including the real OS subprocesses passes 10-13 spawn, which put only this
    directory on `sys.path`."""
    try:
        import workflow_state
    except ModuleNotFoundError:
        import sys
        sys.path.insert(0, str(Path(__file__).resolve().parents[4] / "scripts"))
        import workflow_state
    return workflow_state


def identity_document_path(repo_root: Path) -> Path:
    return repo_root / _workflow_state().WORKTREE_IDENTITY_PATH


def identity_document_lock_path(repo_root: Path) -> Path:
    """The serialization object for `WORKTREE_IDENTITY.json` (new, revision 69,
    `OPUS-R86-002`).

    Beside the document, one per **worktree** rather than per work item --
    which is the whole point: the document is shared by every work item, so a
    per-work-item object cannot serialize its writers by construction. Stable
    and **never unlinked**, exactly like the `.guardlock` object `D1`'s
    state-file serialization uses, because a lock that can be removed while
    another writer holds it serializes nothing."""
    return identity_document_path(repo_root).with_name("WORKTREE_IDENTITY.lock")


@contextlib.contextmanager
def identity_document_lock(repo_root: Path):
    """Hold `fcntl.flock(LOCK_EX)` across the document's whole
    load -> validate -> mutate -> publish sequence.

    **This is a leaf lock.** No other lock in this design is ever acquired
    while it is held -- not the mutation guard, not `.guardlock`, not `D1`'s
    state-file `flock` -- and it is never held across a `WORKFLOW_STATE.json`
    write, a claim publication or a guard acquisition. So the existing
    guard-then-`flock` ordering rule cannot be inverted through it, and
    deadlock is impossible by construction rather than by convention.

    Advisory, like every `flock`: it serializes this design's own writers,
    which is what `OPUS-R86-002` is about. It is not a defence against an
    unrelated process rewriting the file, and the atomic `os.replace`
    publication below is what keeps *that* case a lost update rather than a
    torn document."""
    path = identity_document_lock_path(repo_root)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_CREAT | os.O_RDWR, 0o644)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        yield
    finally:
        os.close(fd)


def load_worktree_identity(full_path: Path):
    """The identity document's reader, with **absence established by
    `exists()` rather than by a falsy parse** (new, revision 69,
    `OPUS-R86-004`).

    `workflow_state._load_json` returns `None` for a missing file *and* for a
    document whose entire content is JSON `null`, so those two states were
    indistinguishable to every caller: the reader reported a `null` document
    as `WorktreeIdentityMissingError` (collapsing the "corrupt" and "missing"
    classes the design separates), and the writer's `_load_json(...) or {}`
    silently overwrote it. A corrupt document is never repaired by overwrite
    here; it refuses."""
    ws = _workflow_state()
    if not full_path.exists():
        return None
    data = ws._load_json(full_path)              # declared CorruptJsonError if unparseable
    if data is None:
        raise ws.CorruptJsonError(
            f"{full_path}: the document is JSON `null`, which is not an identity record -- "
            f"a corrupt record is refused, never silently replaced")
    return data


def validate_worktree_identity_strict(data) -> None:
    """`validate_worktree_identity` with its two undeclared escapes closed
    (new, revision 69, `OPUS-R86-004`).

    The installed validator calls `data.get(...)` before establishing that
    `data` is a mapping, so `[]`, `"hello"`, `42` and `true` -- all squarely
    "a schema-invalid document" -- exit it, and therefore exit
    `verify_dirty_resume_safety` itself, as `AttributeError`. It then does
    `set(entry)` on each snapshot member, so a non-iterable member exits as
    `TypeError`. Both defeat the deterministic refusal classification the
    whole error taxonomy is built on, and both happen **in the validator**,
    which is why "validate before mutate" alone does not close them."""
    ws = _workflow_state()
    if not isinstance(data, Mapping):
        raise ws.CorruptJsonError(
            f"WORKTREE_IDENTITY.json must be a JSON object, not {type(data).__name__}")
    expected = data.get("expected_dirty_paths_by_work_item")
    if isinstance(expected, Mapping):
        for item_id, entries in expected.items():
            if isinstance(entries, list):
                for entry in entries:
                    if not isinstance(entry, Mapping):
                        raise ws.CorruptJsonError(
                            f"expected_dirty_paths_by_work_item[{item_id!r}] entry must be a "
                            f"JSON object, not {type(entry).__name__}")
    ws.validate_worktree_identity(data)


def verify_dirty_resume_safety_strict(repo_root: Path, work_item_id: str, *, path=None) -> None:
    """`WF8b`'s specified hardening of the **reader** half (new, revision 69,
    `OPUS-R86-004`).

    `OPUS-R85-002`'s obligation was stated on the writer alone, and that is not
    where the escape is: step 1c's load-bearing refusal is
    `verify_dirty_resume_safety`, which validates a document it did not write,
    so the validator's own undeclared exits (`AttributeError` on a non-mapping
    document, `TypeError` on a non-iterable snapshot member) escape the
    *refusal path itself* -- with `_attach_ownership_evidence`'s ownership
    evidence and escape hint attached to an exception the operator reads as a
    crash. The refusal classification is closed over document shape here:

    | document shape                 | class                            |
    | ------------------------------ | -------------------------------- |
    | absent                         | `WorktreeIdentityMissingError`   |
    | unparseable bytes              | `CorruptJsonError`               |
    | JSON `null`                    | `CorruptJsonError`               |
    | non-mapping (`[]`, `"x"`, `1`) | `CorruptJsonError`               |
    | mapping, schema-invalid        | `CorruptJsonError`               |
    | valid, no entry for this item  | `WorktreeIdentityMissingError`   |
    | valid, different worktree      | `WorktreeIdentityMismatchError`  |
    | valid, this worktree, entry    | returns `None`                   |

    Every row is asserted in `pass16.py` `W3`, and the set is exhaustive over
    document shape rather than sampled."""
    ws = _workflow_state()
    full_path = repo_root / (path or ws.WORKTREE_IDENTITY_PATH)
    data = load_worktree_identity(full_path)     # absent -> None; null/unparseable -> CorruptJsonError
    if data is not None:
        validate_worktree_identity_strict(data)  # non-mapping/schema-invalid -> CorruptJsonError
    # Absence, the missing per-work-item entry and the identity comparison are
    # the installed function's own, unchanged: this hardening adds refusal
    # classes, it never removes or relaxes one.
    kwargs = {"path": path} if path is not None else {}
    ws.verify_dirty_resume_safety(repo_root, work_item_id, **kwargs)


def build_worktree_identity_document(repo_root: Path, work_item_id: str, *, now: str,
                                     existing) -> dict:
    """The document `write_worktree_identity` would write, **computed without
    writing anything** (new, revision 69, `OPUS-R86-005`).

    Splitting the computation from the write is the whole correction: revision
    68's wrapper delegated to `write_worktree_identity`, which writes the final
    pathname with a plain `write_text`, and only *then* staged a temp file and
    `os.replace`d it -- so the writer the plan called "asserted incapable of
    producing a torn record" wrote the real pathname non-atomically on every
    single call. The snapshot itself is still not re-implemented: it is
    `_hash_dirty_paths`, the installed helper, called directly."""
    ws = _workflow_state()
    document = dict(existing) if existing is not None else {}
    repo_root_id, git_common_dir, worktree_root = ws._git_identity(repo_root)
    document["repo_root"] = repo_root_id
    document["git_common_dir"] = git_common_dir
    document["worktree_root"] = worktree_root
    expected = dict(document.get("expected_dirty_paths_by_work_item") or {})
    expected[work_item_id] = ws._hash_dirty_paths(repo_root)
    document["expected_dirty_paths_by_work_item"] = expected
    document["generated_at"] = now
    return document


def establish_worktree_identity(repo_root: Path, work_item_id: str, *, now: str):
    """`WF8b`'s specified hardening of `write_worktree_identity`, prototyped
    here rather than in `scripts/workflow_state.py` because this round is
    plan-only (revision 68, `OPUS-R85-002`; corrected in revision 69,
    `OPUS-R86-002`/`-004`/`-005`).

    Four properties the installed writer does not have, all load-bearing now
    that the identity record is the *sole* evidence of worktree instance
    (`OPUS-R85-001`):

    1. **The whole read-modify-write is serialized** against every other
       writer of this document (`OPUS-R86-002`). The document holds *every*
       work item's entry, and the per-work-item mutation guard cannot serialize
       two work items by construction, so without this an interleaved write for
       an unrelated work item silently deletes the entry that is now the only
       proof of origination -- and its owner is then refused at its own next
       step 1c, which is `OPUS-R84-001`'s lockout produced by activity on
       another item entirely.
    2. **It validates the document it loaded before mutating it**, through
       `validate_worktree_identity_strict`, so every corrupt-document exit is
       the declared `CorruptJsonError` -- including the non-mapping shapes the
       installed validator exits as `AttributeError`/`TypeError`
       (`OPUS-R86-004`).
    3. **Absence is `exists()`, not a falsy parse**, so a JSON `null` document
       refuses instead of being silently overwritten (`OPUS-R86-004`).
    4. **The final pathname is written exactly once, by `os.replace`**
       (`OPUS-R86-005`), from a same-directory temp file with a per-call unique
       name -- never `os.getpid()` alone, which collides between two threads of
       one process and, under the concurrency of (1), made one writer
       `os.replace` a temp file the other had already consumed.
    """
    full_path = identity_document_path(repo_root)
    full_path.parent.mkdir(parents=True, exist_ok=True)
    with identity_document_lock(repo_root):
        existing = load_worktree_identity(full_path)
        if existing is not None:
            validate_worktree_identity_strict(existing)
        document = build_worktree_identity_document(
            repo_root, work_item_id, now=now, existing=existing)
        validate_worktree_identity_strict(document)
        tmp = full_path.with_name(f"{full_path.name}.tmp-{os.getpid()}-{secrets.token_hex(8)}")
        try:
            tmp.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n")
            os.replace(tmp, full_path)
        except BaseException:
            tmp.unlink(missing_ok=True)
            raise
    return document


def claim_checkpoint(repo_root: Path, work_item_id: str, checkpoint_id: str, *, now: str,
                     _preread: dict | None = None) -> dict:
    """Acquire a fresh-start claim. Called at step 1d **after** this worktree's
    own identity record is established and **before**
    `transition_checkpoint_in_progress` -- both orderings are load-bearing, not
    stylistic (`pass4.py` O1 runs both arms of the second and shows the
    state-first variant reopens the hole exactly; `pass15.py` `Y3` runs both
    arms of the first and shows the claim-first variant locks the owner out).

    `_preread` is a test seam that injects a deliberately stale read to prove
    the atomic publication, not the pre-read, is the authority.
    """
    record = _build_record(repo_root, work_item_id, checkpoint_id, now)
    if _preread is not None and not claim_is_this_worktree(repo_root, _preread):
        raise CheckpointOwnedByOtherWorktreeError(
            f"{work_item_id!r} is already claimed by {_preread.get('worktree_root')!r}")
    return _claim_or_refuse(repo_root, work_item_id, record)


def adopt_claim(repo_root: Path, work_item_id: str, work_item: dict, *, now: str,
                verify_resume_safety, state_rel_path: str = STATE_REL_PATH,
                origination_guard: bool = True) -> dict:
    """A1/A2: publish a claim for a checkpoint this worktree *already* holds
    `IN_PROGRESS` but never claimed -- the one-time migration path for every
    checkpoint interrupted before this mechanism existed, and the reason the
    real `S-CP3` can be protected without recreating it.

    Guarded so it can only ever run in the originating worktree:

    1. the local state must actually record a checkpoint `IN_PROGRESS`;
    2. `verify_dirty_resume_safety` must pass for this worktree (the exact
       gate `D3` already defines -- a foreign worktree fails it here for the
       same reasons, and with the same error classes, that it fails it
       everywhere else). It stays **first** among the origination checks, so
       the classes `S14` depends on are the ones a foreign worktree still
       sees;
    3. that same `IN_PROGRESS` must **not** be present in the
       `WORKFLOW_STATE.json` committed at `HEAD` (new, revision 69,
       `OPUS-R86-001`) -- see below;
    4. no claim may exist yet (an existing foreign claim refuses; an existing
       self-claim is returned unchanged, so adoption is idempotent).

    **Why (3) exists.** Through revision 68 adoption trusted *local*
    `IN_PROGRESS` as evidence that this worktree originated the checkpoint. It
    is not. The design's supporting claim -- "the `IN_PROGRESS` transition is
    written at step 1d and never committed, so no checkout can supply it" --
    is false against this repository at the revision-68 head: five of the last
    commits carry `workflow-v2-1-core`'s own `WF8b` checkpoint `IN_PROGRESS`,
    because a whole-tree checkpoint commit for **one** work item stages
    another work item's in-flight transition along with it, and nothing in the
    design forbids that. Once a checkout can supply `IN_PROGRESS`, a worktree
    that legitimately holds an identity entry for the work item -- from having
    worked an *earlier* checkpoint of it -- satisfies (1), (3) and (4) for a
    checkpoint it never started, adopts it, publishes a claim with
    `adopted: true`, and locks the true originator out with "resume it there"
    pointing at a worktree that has nothing to resume. That is `WF8B-S14-001`
    itself, reproduced in `pass16.py` `W1` against the real step-1 caller.

    Only the **uncommitted** delta is origination evidence, which is exactly
    what (2) tests -- the same durable-vs-working-tree distinction the design
    already applies at the single automatic release (`committed_checkpoint_
    status`, A6). When the committed state agrees with the local one, the
    local statement is fully explained by the checkout and proves nothing, so
    adoption refuses for **every** worktree, including the originating one:
    the mechanism genuinely cannot tell them apart, and inventing a
    tiebreak would be the same guess this design refuses everywhere else. The
    escape is the explicit takeover, which is user-authorized, evidence-bound
    and audited, and which applies to an absent claim exactly as it does to a
    present one (the partition's "readable **or absent**" row).

    `origination_guard=False` is a test seam only: it carries revision 68's
    unguarded adoption as a live control arm, so `pass16.py` `W1` asserts the
    correction rather than asserting a story about it.
    """
    checkpoints = work_item.get("checkpoints", {})
    current_id = work_item.get("current_checkpoint_id")
    if current_id is None or checkpoints.get(current_id, {}).get("status") != "IN_PROGRESS":
        raise CheckpointOwnershipStateMismatchError(
            f"{work_item_id!r} records no IN_PROGRESS checkpoint in this worktree -- there is "
            f"nothing to adopt (adoption never invents an ownership fact)")
    verify_resume_safety(repo_root, work_item_id)
    durable = committed_checkpoint_status(repo_root, work_item_id, current_id,
                                          state_rel_path=state_rel_path)
    if origination_guard and durable == "IN_PROGRESS":
        raise CheckpointOriginationUnprovableError(
            f"{work_item_id!r} checkpoint {current_id!r} is IN_PROGRESS in this worktree's state "
            f"**and** in the state committed at HEAD, so this worktree's copy of that fact could "
            f"have come from an ordinary checkout rather than from its own step 1d -- refusing to "
            f"adopt a claim on origination this worktree cannot prove. Only the uncommitted "
            f"transition is evidence. Resume in the worktree holding the uncommitted work, or, if "
            f"that worktree is gone or is this one, take the claim over explicitly after "
            f"reviewing the takeover evidence")
    record = _build_record(repo_root, work_item_id, current_id, now, adopted=True)
    return _claim_or_refuse(repo_root, work_item_id, record)


def release_checkpoint(repo_root: Path, work_item_id: str, checkpoint_id: str, *,
                       owner_token: str, now: str = "release") -> None:
    """Release this session's own claim -- called at step 1f **after** the
    checkpoint commit exists, and by the explicit discard branch of
    interrupted-checkpoint recovery.

    `GPT-R81-001`: this is a **compare-and-delete**, not a read-then-unlink.
    The token the caller holds is asserted *inside* the mutation guard and the
    unlink happens in that same window, so a takeover interleaved between the
    read and the delete can no longer let a displaced owner remove the
    replacement owner's claim: after the rotation the assertion fails and
    nothing is unlinked. Revision 63 unlinked on a pathname alone, which was
    reproduced dropping the new owner's claim.
    """
    existing = resolve_claim(repo_root, work_item_id)
    if existing is None:
        return
    if not claim_is_this_worktree(repo_root, existing):
        raise CheckpointOwnedByOtherWorktreeError(
            f"refusing to release {work_item_id!r}'s claim held by "
            f"{existing.get('worktree_root')!r} from a different worktree")
    try:
        with owner_mutation(repo_root, work_item_id, owner_token,
                            checkpoint_id=checkpoint_id, step="1f-release",
                            step_class=ORDINARY, now=now):
            claim_path(repo_root, work_item_id).unlink(missing_ok=True)
    except OSError as exc:
        # T6c: a release that cannot complete is an ownership fault with a
        # defined contract (the next self-invocation re-heals it against the
        # durable completion), not a bare OSError the caller must guess at.
        raise CheckpointOwnershipUnavailableError(
            f"cannot release {work_item_id!r}'s claim ({exc}) -- the checkpoint's own "
            f"completion is unaffected; the claim is released on the next invocation from "
            f"this worktree once the completion is durable"
        ) from exc


# ---------------------------------------------------------------------------
# Durability -- the only fact an automatic release is ever allowed to rest on
# ---------------------------------------------------------------------------


def committed_checkpoint_status(repo_root: Path, work_item_id: str, checkpoint_id: str,
                                *, state_rel_path: str = STATE_REL_PATH) -> str | None:
    """The checkpoint's status in the `WORKFLOW_STATE.json` **committed at
    `HEAD`** -- never the working tree's.

    A6: this is the difference between "the checkpoint is done" and "somebody
    typed that it is done". The single automatic release branch below is
    allowed to rest on a durable fact and on nothing else.
    """
    try:
        blob = _run(["git", "show", f"HEAD:{state_rel_path}"], cwd=repo_root)
    except subprocess.CalledProcessError:
        return None
    try:
        state = json.loads(blob)
    except json.JSONDecodeError:
        return None
    item = state.get("work_items", {}).get(work_item_id)
    if not isinstance(item, dict):
        return None
    return item.get("checkpoints", {}).get(checkpoint_id, {}).get("status")


# ---------------------------------------------------------------------------
# Explicit takeover -- never automatic, always evidence-first
# ---------------------------------------------------------------------------


def registered_worktrees(repo_root: Path) -> list[dict]:
    """`git worktree list --porcelain`, parsed -- the shared registry every
    linked worktree can read, used to tell a deleted holder from a live one."""
    out = _run(["git", "worktree", "list", "--porcelain"], cwd=repo_root)
    entries, current = [], {}
    for line in out.splitlines():
        if not line.strip():
            if current:
                entries.append(current)
                current = {}
            continue
        key, _, value = line.partition(" ")
        current[key] = value
    if current:
        entries.append(current)
    return entries


def local_identity_observation(repo_root: Path) -> dict:
    """What the **taking** worktree's own identity document is, as a durable,
    re-verifiable observation (new, revision 69, `OPUS-R86-003`).

    A rotating operation must establish this worktree's own identity record
    (revision 68), and that write owes validate-before-mutate -- so when the
    document is undecidable the operation has to refuse, and revision 68
    refused *after* rotating. Ordering the establishment before the rotation
    fixes the mutation, but on its own it leaves the corrupt-record state with
    exactly one exit: hand-deleting the document, which is the class of
    undocumented manual repair this design exists to eliminate.

    So the state is *observed* here and repaired only under its own
    authorization component. It is an observation, never a conclusion, on the
    same terms as `claim_observation_id`: the id is the sha256 of the exact
    bytes the human is shown, so the authorization it derives is bound to that
    document and to no other, and is non-replayable once the repair changes
    those bytes."""
    path = identity_document_path(repo_root)
    try:
        raw = _read_claim_bytes(path)
    except CheckpointOwnershipUnavailableError:
        descriptor = describe_unreadable_record(path)
        return {"state": "undecidable", "path": str(path),
                "identity_observation_id": (unreadable_observation_id(descriptor)
                                            if descriptor is not None else ABSENT_OBSERVATION),
                "unreadable": descriptor, "error": "the document is not a readable regular file"}
    if raw is None:
        return {"state": "absent", "path": str(path),
                "identity_observation_id": ABSENT_OBSERVATION}
    oid = observation_id(raw)
    try:
        data = load_worktree_identity(path)
        validate_worktree_identity_strict(data)
    except Exception as exc:  # noqa: BLE001 -- every decidability failure is one state
        return {"state": "undecidable", "path": str(path), "identity_observation_id": oid,
                "error": f"{type(exc).__name__}: {exc}"}
    return {"state": "valid", "path": str(path), "identity_observation_id": oid}


def repair_worktree_identity(repo_root: Path, work_item_id: str, *, now: str) -> dict:
    """The authorized repair-by-overwrite of this worktree's **own** identity
    document (new, revision 69, `OPUS-R86-003`).

    Deliberately a separate entry point from `establish_worktree_identity`,
    which must keep refusing: a corrupt record is never silently replaced
    (`OPUS-R86-004`). This one discards the undecidable document and builds a
    fresh one, and it is reachable only from a rotating operation whose
    authorization literal carried the repair component bound to that exact
    document's observation. It publishes through the same lock and the same
    single `os.replace` as every other write of this file."""
    full_path = identity_document_path(repo_root)
    full_path.parent.mkdir(parents=True, exist_ok=True)
    with identity_document_lock(repo_root):
        document = build_worktree_identity_document(
            repo_root, work_item_id, now=now, existing=None)
        validate_worktree_identity_strict(document)
        tmp = full_path.with_name(f"{full_path.name}.tmp-{os.getpid()}-{secrets.token_hex(8)}")
        try:
            tmp.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n")
            os.replace(tmp, full_path)
        except BaseException:
            tmp.unlink(missing_ok=True)
            raise
    return document


def _establish_or_repair_identity(repo_root: Path, work_item_id: str, *, now: str,
                                  evidence: dict, operation: str) -> None:
    """The identity half of both rotating operations, run **before** the
    rotation publishes (`OPUS-R86-003`).

    A decidable document is established the ordinary way. An undecidable one is
    repaired only if the authorization the caller already validated carried the
    repair component, and only if the document is still byte-identical to the
    one that component named -- the same stale-evidence rule the claim rotation
    itself obeys."""
    observed = evidence.get("local_identity") or local_identity_observation(repo_root)
    if observed.get("state") != "undecidable":
        establish_worktree_identity(repo_root, work_item_id, now=now)
        return
    fresh = local_identity_observation(repo_root)
    if fresh.get("identity_observation_id") != observed.get("identity_observation_id"):
        raise CheckpointClaimTakeoverRefusedError(
            f"this worktree's own identity document changed between the evidence the user "
            f"authorized ({observed.get('identity_observation_id')}) and this {operation} "
            f"({fresh.get('identity_observation_id')}) -- refusing, having mutated nothing; "
            f"present fresh evidence and obtain a fresh authorization")
    repair_worktree_identity(repo_root, work_item_id, now=now)


def takeover_evidence(repo_root: Path, work_item_id: str) -> dict:
    """Everything a human needs to decide a takeover, gathered without
    changing anything. Deliberately reports rather than concludes."""
    path = claim_path(repo_root, work_item_id)
    oid, claim, error, unreadable = observe_claim(repo_root, work_item_id)
    gpath = guard_path(repo_root, work_item_id)
    try:
        guard_oid = observation_id(_read_claim_bytes(gpath))
    except CheckpointOwnershipUnavailableError:
        # Same treatment as the claim: a guard record whose bytes cannot be read
        # still gets a defined, bindable observation (`OPUS-R83-002`).
        guard_descriptor = describe_unreadable_record(gpath)
        guard_oid = (unreadable_observation_id(guard_descriptor)
                     if guard_descriptor is not None else ABSENT_OBSERVATION)
    evidence = {"claim_path": str(path), "readable": error is None, "claim": None,
                "holder_path_exists": None, "holder_registered": None,
                "holder_current_path": None,
                # `GPT-R81-002`: the identity the user's authorization binds to.
                # Always present -- including for an absent or unreadable record,
                # neither of which may fall back to a reusable generic
                # authorization.
                "claim_observation_id": oid,
                # `OPUS-R83-002`: how the record is unreadable, when it is --
                # `None` for an absent, readable or merely torn one. This is what
                # tells the operator whether any documented operation can replace
                # it, since a directory at the claim path cannot be rotated over.
                "claim_unreadable": unreadable,
                "claim_replaceable": unreadable is None or (
                    unreadable.get("kind") in REPLACEABLE_UNREADABLE_KINDS),
                "observed_checkpoint_id": claim.get("checkpoint_id") if claim else None,
                "observed_owner_token": claim.get("owner_token") if claim else None,
                "work_item_id": work_item_id,
                "guard": None,
                "guard_observation_id": guard_oid,
                "guard_holder_registered": None,
                "guard_error": None,
                # `OPUS-R86-003`: the taking worktree's own identity document,
                # which every rotation must establish. Reported so the operator
                # sees that a repair is part of what they are authorizing.
                "local_identity": local_identity_observation(repo_root)}
    try:
        evidence["guard"] = read_guard(repo_root, work_item_id)
    except CheckpointOwnershipUnavailableError as exc:
        evidence["guard_error"] = str(exc)
    registered = registered_worktrees(repo_root)

    def _registered_match(recorded_root, recorded_git_dir):
        """Whether a currently registered worktree still corresponds to the
        recorded holder, matched on the stable admin dir as well as the path."""
        for entry in registered:
            wt_path = entry.get("worktree")
            if wt_path is None:
                continue
            try:
                admin = _worktree_git_dir(Path(wt_path)) if Path(wt_path).exists() else None
            except subprocess.CalledProcessError:
                admin = None
            if wt_path == recorded_root or (admin and recorded_git_dir
                                            and admin == recorded_git_dir):
                return True, wt_path
        return False, None

    # `OPUS-R83-002`: the guard records its own `holder_worktree_git_dir`
    # (revision 65), so the deregistration fact is derivable **without** a
    # readable claim -- which is what lets the abandoned-guard recovery own the
    # undecidable-claim case instead of routing it to an operation that cannot
    # act on it.
    guard = evidence["guard"]
    if guard is not None:
        guard_registered, guard_path_now = _registered_match(
            None, guard.get("holder_worktree_git_dir"))
        evidence["guard_holder_registered"] = guard_registered
        evidence["guard_holder_current_path"] = guard_path_now
    if error is not None:
        evidence["error"] = error
        return evidence
    if claim is None:
        evidence["claim"] = None
        return evidence
    evidence["claim"] = claim
    holder_root = Path(claim["worktree_root"])
    evidence["holder_path_exists"] = holder_root.exists()
    matched, wt_path = _registered_match(claim["worktree_root"], claim["worktree_git_dir"])
    evidence["holder_registered"] = matched
    if matched:
        evidence["holder_current_path"] = wt_path
    return evidence


def takeover_authorization_literal(work_item_id: str, evidence: dict, checkpoint_id: str) -> str:
    """The exact literal a user must produce, derived **from the evidence they
    were shown** (`GPT-R81-002`). It names the observed record's identity and
    the checkpoint that record holds, so it cannot be written from memory,
    cannot be replayed against a later record, and cannot displace a holder or
    checkpoint nobody reviewed.

    `OPUS-R86-003`, revision 69: when the taking worktree's **own** identity
    document is undecidable, the rotation cannot establish the record it owes
    without discarding that document -- so the literal grows a component naming
    it, and the user authorizes the repair explicitly rather than having it
    happen as a side effect. The component is absent for a valid or absent
    document, so every other literal is unchanged."""
    displaced = evidence.get("observed_checkpoint_id") or "none"
    literal = (f"take over {work_item_id} claim {evidence.get('claim_observation_id')} "
               f"holding {displaced} as {checkpoint_id}")
    return literal + _identity_repair_clause(evidence)


def _identity_repair_clause(evidence: dict) -> str:
    identity = evidence.get("local_identity") or {}
    if identity.get("state") != "undecidable":
        return ""
    return f" repairing identity {identity.get('identity_observation_id')}"


def guard_release_authorization_literal(guard: dict) -> str:
    return (f"release checkpoint guard {guard.get('lease_id')} step {guard.get('step')} "
            f"class {guard.get('step_class')}")


def take_over_claim(repo_root: Path, work_item_id: str, checkpoint_id: str, *, now: str,
                    user_authorization: str | None, evidence: dict | None = None,
                    guard_release_authorization: str | None = None) -> dict:
    """The ordinary way a claim this worktree does not own is removed -- and the
    only one that ever applies while the holder worktree is still registered.
    (`recover_abandoned_destructive_guard` is the single other route, for a
    destructive window abandoned by a worktree that no longer exists;
    `clear_malformed_guard` removes an undecidable guard, never a claim.)

    Never reachable from `/milestone-implement`. Never triggered by a timeout,
    a heartbeat, an age threshold, or an inference that the holder "looks
    gone" -- every one of those is an assumption that can discard a live claim,
    and this design does not make any of them.

    Revision 64 (`GPT-R81-001`/`-002`) makes it a fenced, evidence-bound
    handoff rather than an atomic overwrite:

    1. **Observe** -- `takeover_evidence`, whose `claim_observation_id` is the
       sha256 of the exact record bytes the human is shown.
    2. **Authorize** -- the literal must be exactly
       `takeover_authorization_literal(...)` for *that* observation. A claim
       naming a different checkpoint therefore produces a different literal,
       which is the direct fix for "authorization for X displaced a claim for
       Y". When a guard was observed, a separate guard-release authorization
       quoting its `lease_id`/`step`/`step_class` is required too, and a
       `"destructive"` guard is refused outright.
    3. **Acquire the same mutation guard** every owner mutation acquires, so
       `reverify -> rotate` is mutually exclusive with `assert -> mutate`.
    4. **Re-verify under the guard** -- re-read the raw bytes and require the
       observation id to be unchanged. Anything else is stale evidence:
       refuse, having mutated nothing.
    5. **Rotate** by atomic replace, minting a fresh `owner_token`,
       incrementing `takeover_count`, and recording the exact displaced record
       in `taken_over_from`. The displaced owner's next `assert_claim_owner`
       fails, so it can no longer write state, commit, or release.
    """
    if evidence is None:
        evidence = takeover_evidence(repo_root, work_item_id)
    expected = takeover_authorization_literal(work_item_id, evidence, checkpoint_id)
    if user_authorization != expected:
        raise CheckpointClaimTakeoverRefusedError(
            f"explicit takeover requires the literal authorization {expected!r} derived from the "
            f"evidence just presented -- refusing to remove a claim on an inference, on a "
            f"remembered literal, or on evidence the user did not review"
        )
    observed_oid = evidence.get("claim_observation_id")
    guard = evidence.get("guard")
    if guard is not None:
        if guard.get("step_class") == DESTRUCTIVE:
            raise CheckpointClaimTakeoverRefusedError(
                f"the holder is inside destructive step {guard.get('step')!r} -- no takeover "
                f"authorization breaks that window; resume in the owning session, or, if that "
                f"worktree is gone, use the abandoned-guard recovery")
        if guard_release_authorization != guard_release_authorization_literal(guard):
            raise CheckpointClaimTakeoverRefusedError(
                f"a mutation guard was observed; takeover additionally requires the literal "
                f"{guard_release_authorization_literal(guard)!r}")

    path = claim_path(repo_root, work_item_id)
    # `OPUS-R83-002`: a record whose bytes cannot be read is exactly the case
    # this operation's corrupt-record path exists for, so refusing on the
    # symlink *here* is what made that path unreachable. Publication is
    # `os.rename` onto the name, which replaces the link itself and never
    # follows it. A directory, which `rename` cannot replace, is refused
    # explicitly instead of being attempted and failing obscurely.
    if not evidence.get("claim_replaceable", True):
        descriptor = evidence.get("claim_unreadable") or {}
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s claim path is a {descriptor.get('kind')!r}, which no documented "
            f"operation replaces: a rotation publishes by `os.rename` onto that name, and this "
            f"design never removes a directory it did not create. Remove {str(path)!r} yourself "
            f"once you have confirmed it holds nothing of yours, then re-run the takeover")
    if evidence.get("claim_unreadable") is None:
        _assert_not_symlink(path, "claim record")

    lease = acquire_guard(repo_root, work_item_id,
                          holder_owner_token=evidence.get("observed_owner_token"),
                          checkpoint_id=checkpoint_id, step="takeover", step_class=ORDINARY,
                          now=now, role="takeover",
                          authorized_lease_id=guard.get("lease_id") if guard else None)
    try:
        # (4) re-verify **under the guard**: the record must still be byte-for-byte
        # the one the authorization named.
        current_oid, previous, _error, _unreadable = observe_claim(repo_root, work_item_id)
        if current_oid != observed_oid:
            raise CheckpointClaimTakeoverRefusedError(
                f"{work_item_id!r}'s claim changed between the evidence the user authorized "
                f"({observed_oid}) and this takeover ({current_oid}) -- refusing, having mutated "
                f"nothing; present fresh evidence and obtain a fresh authorization")
        taken_over_from = None
        if previous is not None:
            taken_over_from = {
                "worktree_root": previous.get("worktree_root"),
                "worktree_git_dir": previous.get("worktree_git_dir"),
                "checkpoint_id": previous.get("checkpoint_id"),
                "claimed_at": previous.get("claimed_at"),
                "owner_token": previous.get("owner_token"),
                "claim_observation_id": current_oid,
                "holder_registered": evidence.get("holder_registered"),
                "authorized_at": now,
            }
        elif current_oid != ABSENT_OBSERVATION:
            taken_over_from = {"unreadable_record": True, "claim_observation_id": current_oid,
                               "authorized_at": now}
        record = _build_record(
            repo_root, work_item_id, checkpoint_id, now, taken_over_from=taken_over_from,
            takeover_count=(previous or {}).get("takeover_count", 0) + 1,
            previous_owner_tokens=list((previous or {}).get("previous_owner_tokens", []))
            + ([previous["owner_token"]] if previous else []))
        # `OPUS-R85-001`, revision 68: the rotation makes *this* worktree the
        # owner, so it establishes this worktree's own local identity record for
        # the work item. Without it the taker is refused at its very next step
        # 1c -- `verify_dirty_resume_safety` is unconditional again, and a
        # worktree that has never run 1d for this item has no entry -- which
        # would leave the takeover an operation that succeeds at transferring
        # ownership and still cannot be worked from. It is also what gives a
        # worktree that lost its own record while holding a claim a documented,
        # authorization-bound, fenced exit instead of a hand repair. This writes
        # no authoritative workflow *status*: the record is local, gitignored,
        # and carries no checkpoint state.
        #
        # `OPUS-R86-003`, revision 69: it runs **before** the rotation, not
        # after. Revision 68 established the record after `_publish_replacing`,
        # with the guard released in `finally` and no compensating action -- so
        # the operation the plan names as the sanctioned repair for a corrupt
        # identity record refused *on that record* having already rotated the
        # claim, burned the displaced token and incremented `takeover_count`,
        # and every retry rotated again. Its only real exit was hand-deleting
        # the record the same paragraph called an escape from nothing. Ordering
        # the establishment first makes the whole operation atomic in the sense
        # that matters: a document this worktree cannot validate or publish
        # refuses with the claim byte-identical and the epoch chain untouched.
        # The reverse residue is benign and is the same one 1d already accepts:
        # an identity entry that outlives a failed rotation admits this worktree
        # to no branch on its own. An *undecidable* document is repaired here
        # rather than refused, but only under the repair component the
        # authorization above already had to carry -- so the one hand step the
        # corrupt-record state used to require is gone without any silent
        # overwrite replacing it.
        _establish_or_repair_identity(repo_root, work_item_id, now=now,
                                      evidence=evidence, operation="takeover")
        # T1: atomic replace, never unlink-then-create -- a takeover that fails to
        # publish must leave the previous claim in force, not leave the work item
        # silently unclaimed.
        _publish_replacing(path, record,
                           allow_unreadable_target=evidence.get("claim_unreadable") is not None)
        return record
    finally:
        release_guard(repo_root, work_item_id, lease)


def abandoned_guard_recovery_authorization_literal(work_item_id: str, evidence: dict) -> str:
    """Distinct from the takeover literal in every component, and bound to
    **both** durable observations plus the destructive step being abandoned, so
    it can neither be written from memory nor reused for an ordinary takeover
    (`OPUS-R82-001`)."""
    guard = evidence.get("guard") or {}
    return (f"recover abandoned destructive guard {work_item_id} step {guard.get('step')} "
            f"guard {evidence.get('guard_observation_id')} "
            f"claim {evidence.get('claim_observation_id')}"
            + _identity_repair_clause(evidence))


def recover_abandoned_destructive_guard(repo_root: Path, work_item_id: str, checkpoint_id: str, *,
                                        now: str, user_authorization: str | None,
                                        evidence: dict | None = None) -> dict:
    """The one escape from a `"destructive"` guard left behind by a holder
    worktree that no longer exists (`OPUS-R82-001`).

    Without it, `take_over_claim`'s unconditional destructive refusal and
    `clear_malformed_guard`'s well-formed refusal combine into a work item no
    documented operation can start, resume or hand over -- recoverable only by
    hand-deleting a file, which is exactly the class of undocumented manual
    repair this design exists to eliminate.

    It does **not** weaken the destructive class, and it introduces no liveness
    guess: there is still no clock, no heartbeat, no age threshold and no "the
    holder looks gone" inference. The precondition is a durable, **human-made**
    fact instead -- the holder worktree is not in `git worktree list`, i.e. the
    operator (or the machine's loss) deregistered it. While it is still
    registered this refuses and names what to do instead, so a live destructive
    window is never broken.

    Mechanically it is a takeover whose guard reclamation is `role="recovery"`:
    once the claim rotates, the abandoned guard is superseded by construction,
    so the existing epoch rule -- not a second removal primitive -- is what
    finally clears it. The displaced session is fenced by that same rotation at
    its next `assert_claim_owner`, exactly as any other displaced owner is.
    """
    if evidence is None:
        evidence = takeover_evidence(repo_root, work_item_id)
    guard = evidence.get("guard")
    if evidence.get("guard_error") is not None or guard is None:
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r} has no well-formed mutation guard to recover "
            f"({evidence.get('guard_error') or 'no guard is held'}) -- an undecidable guard is "
            f"cleared by `clear_malformed_guard`, and a work item with no guard needs the "
            f"ordinary takeover, not this operation")
    if guard.get("step_class") != DESTRUCTIVE:
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s guard is {guard.get('step_class')!r}, not {DESTRUCTIVE!r} -- an "
            f"ordinary guard is released by the takeover's own guard-release authorization; this "
            f"operation exists only for the window that authorization can never break")
    claim = evidence.get("claim")
    # `OPUS-R83-002`. Through revision 65 *any* unparsed claim was routed to the
    # takeover here -- but the takeover refuses unconditionally on a
    # `"destructive"` guard, so (undecidable claim + destructive guard) had all
    # three operations refusing in a cycle and no exit at all. Reproduced in
    # `pass13.py` `I4`. The two cases are now separated, because only one of
    # them is genuinely the takeover's:
    #
    #   * claim **absent**   -> the guard is superseded by construction
    #     (`_current_owner_token` returns `(None, True)`), so reclamation rule 1
    #     already clears it with no authorization. Still not this operation's.
    #   * claim **undecidable** -> owned *here*, bound to the undecidable
    #     record's own `claim_observation_id`, which `observe_claim` now defines
    #     for every flavour of unreadable. The deregistration precondition is
    #     read from the **guard's** `holder_worktree_git_dir` rather than from
    #     the claim, which is exactly what that field was added for.
    claim_undecidable = evidence.get("error") is not None
    if claim is None and not claim_undecidable:
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r} has a guard but no claim at all -- a guard with no claim is "
            f"superseded by construction and is already reclaimable with no authorization; "
            f"this operation is only for an abandoned window of the current epoch")
    if claim_undecidable and not evidence.get("claim_replaceable", True):
        descriptor = evidence.get("claim_unreadable") or {}
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s claim path is a {descriptor.get('kind')!r}, which no documented "
            f"operation replaces: a rotation publishes by `os.rename` onto that name, and this "
            f"design never removes a directory it did not create. Remove "
            f"{evidence.get('claim_path')!r} yourself once you have confirmed it holds nothing "
            f"of yours, then re-run this recovery")
    if claim_undecidable:
        # The guard is the only readable record, so the checkpoint it names is
        # the only non-guessed one available. An operator-supplied id that
        # disagrees is refused rather than reconciled.
        if guard.get("checkpoint_id") != checkpoint_id:
            raise CheckpointClaimTakeoverRefusedError(
                f"{work_item_id!r}'s claim is undecidable, so the abandoned guard's own "
                f"checkpoint {guard.get('checkpoint_id')!r} is the only durable one -- refusing "
                f"to recover it as {checkpoint_id!r}")
        if evidence.get("guard_holder_registered") is not False:
            raise CheckpointClaimTakeoverRefusedError(
                f"{work_item_id!r}'s guard is held by worktree "
                f"{guard.get('holder_worktree_git_dir')!r}, which is still registered "
                f"(currently {evidence.get('guard_holder_current_path')!r}) -- a registered "
                f"holder's destructive window is never broken from outside, and the claim being "
                f"undecidable does not change that. Resume in that worktree, move it back to "
                f"its recorded path if it was relocated, or `git worktree remove` it if you know "
                f"it is dead and re-run this recovery")
    if claim is not None and guard.get("holder_owner_token") != claim.get("owner_token"):
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s guard belongs to a superseded epoch (guard token "
            f"{guard.get('holder_owner_token')!r}, claim token {claim.get('owner_token')!r}) -- "
            f"it is already reclaimable with no authorization; this operation is only for a "
            f"guard of the **current** epoch")
    if claim is not None and claim_is_this_worktree(repo_root, claim):
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s claim is held by this worktree -- an interrupted step of this "
            f"worktree's own epoch is reclaimed by simply re-entering it; nothing is abandoned "
            f"from here")
    if claim is not None and evidence.get("holder_registered") is not False:
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s holder is still a registered worktree of this repository "
            f"(recorded {claim.get('worktree_root')!r}, currently "
            f"{evidence.get('holder_current_path')!r}) -- a registered holder's destructive "
            f"window is never broken from outside. Resume in that worktree (a session there "
            f"reclaims its own interrupted guard), move it back to its recorded path if it was "
            f"relocated, or `git worktree remove` it if you know it is dead and re-run this "
            f"recovery")
    expected = abandoned_guard_recovery_authorization_literal(work_item_id, evidence)
    if user_authorization != expected:
        raise CheckpointClaimTakeoverRefusedError(
            f"recovering an abandoned destructive guard requires the literal authorization "
            f"{expected!r}, derived from the evidence just presented -- refusing to break a "
            f"destructive window on an inference or on a remembered literal")

    observed_oid = evidence.get("claim_observation_id")
    path = claim_path(repo_root, work_item_id)
    if not claim_undecidable:
        _assert_not_symlink(path, "claim record")
    # When the claim *is* undecidable the symlink refusal is deliberately not
    # applied here (`OPUS-R83-002`): the recovery publishes by `os.rename` onto
    # the name, which replaces the link itself and never follows it, so the
    # off-tree target is never read, written or removed. Refusing here instead
    # is what made the advertised recovery unreachable for a symlinked record.

    # Re-verified against **freshly read** durable state, and deliberately
    # *before* the guard is reclaimed rather than under it (found by `pass12.py`
    # `H3e`): reclaiming first and refusing afterwards would destroy the
    # holder's `"destructive"` guard on the very path that exists to protect it,
    # so a worktree re-registered while the user was reading the evidence would
    # have its live window broken by an operation that then reported refusal.
    # The narrow window this leaves -- a re-registration between these checks and
    # the reclamation -- is closed by the reclamation itself, which requires the
    # `lease_id` to still be exactly the abandoned one: a resumed holder's first
    # act is to reclaim its own interrupted guard, which publishes a new lease.
    fresh = takeover_evidence(repo_root, work_item_id)
    fresh_registered = (fresh.get("guard_holder_registered") if claim_undecidable
                        else fresh.get("holder_registered"))
    if fresh_registered is not False:
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s holder worktree was re-registered at "
            f"{fresh.get('guard_holder_current_path') or fresh.get('holder_current_path')!r} "
            f"after the evidence was taken -- refusing, having mutated nothing")
    if fresh.get("guard_observation_id") != evidence.get("guard_observation_id"):
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s mutation guard changed between the evidence the user authorized "
            f"({evidence.get('guard_observation_id')}) and this recovery "
            f"({fresh.get('guard_observation_id')}) -- refusing, having mutated nothing")
    if fresh.get("claim_observation_id") != observed_oid:
        raise CheckpointClaimTakeoverRefusedError(
            f"{work_item_id!r}'s claim changed between the evidence the user authorized "
            f"({observed_oid}) and this recovery ({fresh.get('claim_observation_id')}) -- "
            f"refusing, having mutated nothing; present fresh evidence and obtain a fresh "
            f"authorization")

    lease = acquire_guard(repo_root, work_item_id,
                          holder_owner_token=evidence.get("observed_owner_token"),
                          checkpoint_id=checkpoint_id, step="recover-abandoned-guard",
                          step_class=ORDINARY, now=now, role="recovery",
                          authorized_lease_id=guard.get("lease_id"))
    try:
        # Once more under the guard, for the rotation's own binding. Every
        # sanctioned rotation runs inside this guard, so a claim that changed
        # while the `lease_id` did not was edited by something outside this
        # contract entirely.
        current_oid, previous, _error, current_unreadable = observe_claim(
            repo_root, work_item_id)
        if current_oid != observed_oid:
            raise CheckpointClaimTakeoverRefusedError(
                f"{work_item_id!r}'s claim changed between the evidence the user authorized "
                f"({observed_oid}) and this recovery ({current_oid}) -- refusing; the claim was "
                f"modified outside this contract, since no sanctioned rotation is possible "
                f"without the guard this operation holds")
        previous = previous or {}
        taken_over_from = {
            "worktree_root": previous.get("worktree_root"),
            "worktree_git_dir": previous.get("worktree_git_dir"),
            "checkpoint_id": previous.get("checkpoint_id"),
            "claimed_at": previous.get("claimed_at"),
            "owner_token": previous.get("owner_token"),
            "claim_observation_id": current_oid,
            "holder_registered": False,
            "authorized_at": now,
            # `OPUS-R83-002`: an undecidable predecessor cannot contribute an
            # epoch chain, and the recovered claim says so explicitly rather
            # than presenting a silently reset one as if it were continuous.
            "claim_was_undecidable": claim_undecidable,
            "claim_unreadable": current_unreadable,
            "epoch_chain_lost": claim_undecidable,
            # Provenance, so a recovered claim is never indistinguishable from an
            # ordinary takeover afterwards.
            "recovered_from_abandoned_guard": {
                "lease_id": guard.get("lease_id"),
                "step": guard.get("step"),
                "step_class": guard.get("step_class"),
                "guard_observation_id": evidence.get("guard_observation_id"),
                "holder_worktree_git_dir": guard.get("holder_worktree_git_dir"),
            },
        }
        displaced_tokens = list(previous.get("previous_owner_tokens", []))
        if previous.get("owner_token"):
            displaced_tokens.append(previous["owner_token"])
        elif guard.get("holder_owner_token"):
            # The guard is the only surviving record of the displaced epoch.
            displaced_tokens.append(guard["holder_owner_token"])
        record = _build_record(
            repo_root, work_item_id, checkpoint_id, now, taken_over_from=taken_over_from,
            takeover_count=previous.get("takeover_count", 0) + 1,
            previous_owner_tokens=displaced_tokens)
        # `OPUS-R85-001`, revision 68: same reason as the takeover's -- the
        # rotation makes this worktree the owner, so it establishes this
        # worktree's own local identity record. Both rotating operations do it,
        # and neither writes any authoritative workflow *status*.
        # `OPUS-R86-003`, revision 69: and both do it **before** the rotation,
        # for the same reason -- this operation had the identical
        # `_publish_replacing` -> `establish` -> `finally: release` shape and
        # failed the identical way, and carries the same authorized repair.
        _establish_or_repair_identity(repo_root, work_item_id, now=now,
                                      evidence=evidence, operation="recovery")
        _publish_replacing(path, record, allow_unreadable_target=claim_undecidable)
        return record
    finally:
        release_guard(repo_root, work_item_id, lease)


# ---------------------------------------------------------------------------
# Ownership resolution -- `/milestone-implement` step 1c
# ---------------------------------------------------------------------------


def resolve_ownership(repo_root: Path, work_item: dict, work_item_id: str, selected_id: str | None,
                      *, verify_resume_safety, now: str,
                      state_rel_path: str = STATE_REL_PATH,
                      adopt_origination_guard: bool = True,
                      ) -> tuple[str, str | None, str | None]:
    """`/milestone-implement` step 1c, replacing the resume-only resume-safety
    call. `select_next_checkpoint` (step 1b) stays pure and untouched.

    Returns `(outcome, checkpoint_id, owner_token)` where `outcome` is `RESUME`,
    `FRESH`, `CONTINUE_CLAIM` or the terminal `NO_CHECKPOINT`; raises whenever
    the two authorities disagree in a way that cannot be resolved without a
    human. The checkpoint id is returned rather than left for the caller to
    re-derive from `selected_id`, because `CONTINUE_CLAIM` and the
    durable-release branch are both reachable while `selected_id` is `None`
    (T2) -- a caller that used `selected_id` there would write a `None`
    checkpoint id into the state file.

    `GPT-R81-003`: **every mutation-capable outcome carries a non-`None`
    checkpoint id.** When no checkpoint remains selectable -- the exhausted
    registry, reachable both at entry and after the durable release --
    the outcome is `NO_CHECKPOINT`, which re-enters the command's existing
    "nothing to implement this invocation" path and cannot reach step 1d.

    `GPT-R81-001`: the third element is the `owner_token` the caller must
    present to every subsequent ownership-bearing mutation (`owner_mutation`).
    It is `None` only for `NO_CHECKPOINT` and for `FRESH`, where the token does
    not exist yet -- step 1d mints it by acquiring the claim, before any state
    write.

    The ordering inside is the security property: whenever *either* authority
    says this work item is live, `verify_resume_safety` runs **before** any
    branch that could mutate, so a foreign worktree is refused -- with `D3`'s
    own distinguishable error classes, three of them since revision 68
    (`OPUS-R85-002`) and closed over document shape since revision 69
    (`OPUS-R86-004`) -- before it can reach a fresh start.
    """
    checkpoints = work_item.get("checkpoints", {})
    current_id = work_item.get("current_checkpoint_id")
    local_in_progress = (
        current_id if checkpoints.get(current_id, {}).get("status") == "IN_PROGRESS" else None
    )
    claim = resolve_claim(repo_root, work_item_id)

    if claim is None and local_in_progress is None:
        # `GPT-R81-003`: an exhausted registry is terminal, never a mutable
        # `FRESH` carrying no checkpoint id.
        if selected_id is None:
            return NO_CHECKPOINT, None, None
        return FRESH, selected_id, None

    # Contended: this worktree must prove it is the originating one first, and
    # the **local identity record is the only evidence of origination that
    # exists** (`OPUS-R85-001`, revision 68). `verify_resume_safety` therefore
    # runs unconditionally here, exactly as it did through revision 66.
    #
    # Revision 67 made a decidably self-owned claim a second, sufficient proof
    # and skipped the check for it. That is unsound, and the error is in
    # "self-owned". `claim_is_this_worktree` compares the claim's `(repo_root,
    # git_common_dir, worktree_root)` ownership key -- which `_git_identity`
    # populates with the worktree's path *twice* plus the common dir, i.e. two
    # path values and no instance identity at all. Paths are reusable: a
    # worktree relocated by `git worktree move` and replaced at the path it
    # vacated, or removed and recreated at the same path with the same name,
    # produces a byte-identical ownership key while being a different worktree
    # holding none of the original's work. Under revision 67 such a replacement
    # satisfied proof 1, skipped `verify_dirty_resume_safety`, and then mutated
    # authoritative state under the original holder's unrotated `owner_token` --
    # two worktrees holding one valid token, `takeover_count` 0, no fencing and
    # no audit trail. `pass15.py` `Y1`/`Y2` reproduce both constructions with
    # revision 67's resolver carried as the live control arm.
    #
    # The claim proves a **path**; only the local, gitignored, per-worktree
    # `WORKTREE_IDENTITY.json` -- which a replacement worktree does not inherit,
    # because it lives inside the working tree -- proves the **instance**. The
    # two are not interchangeable, so the check is not relaxed for either.
    #
    # `CONTINUE_CLAIM`'s reachability (`OPUS-R84-001`, still resolved) is
    # restored at the **ordering** instead: step 1d establishes this worktree's
    # identity record *before* publishing the claim, so the crash window between
    # publication and the state write can no longer be entered without the
    # record already existing. See `harness.step1_fixed`.
    #
    # Keeping the check unconditional is also what keeps `D3`'s error classes
    # the ones a foreign worktree actually sees (`S14a` missing identity, `S14b`
    # mismatched identity) instead of collapsing them into a single "owned by
    # someone else", and what keeps its third class -- `CorruptJsonError`, from
    # an unparseable or schema-invalid record (`OPUS-R85-002`) -- firing here,
    # before any authoritative mutation, rather than out of the middle of 1d.
    #
    # T5 / `OPUS-R84-001`: raising any of them bare was still misdiagnosable --
    # the message talks about resuming an `IN_PROGRESS` checkpoint even when this
    # worktree has no local `IN_PROGRESS` checkpoint at all, and it never names
    # who does hold the claim or what the escape is. *Every* refusal raised
    # while a decidable claim is present carries the evidence and names the
    # escape that applies, for a self-owned claim as well as a foreign one,
    # without changing the class any scenario depends on. That generalization is
    # revision 67's and is kept.
    try:
        verify_resume_safety(repo_root, work_item_id)
        return _resolve_with_origination_proved(
            repo_root, work_item, work_item_id, claim, local_in_progress, selected_id,
            checkpoints, verify_resume_safety=verify_resume_safety, now=now,
            state_rel_path=state_rel_path,
            adopt_origination_guard=adopt_origination_guard)
    except Exception as exc:
        _attach_ownership_evidence(repo_root, exc, work_item_id, claim)
        raise


def _attach_ownership_evidence(repo_root: Path, exc: Exception, work_item_id: str,
                               claim: dict | None) -> None:
    """`OPUS-R84-001`: annotate every refusal raised while a **decidable** claim
    is present with the claim record and the escape that applies. Revision 66
    annotated only the *foreign* case, so the self-owned crash window produced a
    bare message about resuming an `IN_PROGRESS` checkpoint that did not exist,
    naming no way out at all."""
    if claim is None or getattr(exc, "ownership_claim", None) is not None:
        return
    exc.ownership_claim = claim
    if claim_is_this_worktree(repo_root, claim):
        exc.ownership_hint = (
            f"{work_item_id!r} checkpoint {claim.get('checkpoint_id')!r} is claimed by "
            f"this worktree ({claim.get('worktree_root')!r}); continue it here -- no takeover "
            f"applies, and nothing needs to be removed by hand"
        )
    else:
        exc.ownership_hint = (
            f"{work_item_id!r} checkpoint {claim.get('checkpoint_id')!r} is claimed by "
            f"worktree {claim.get('worktree_root')!r}; resume it there, or take the claim "
            f"over explicitly after reviewing the takeover evidence"
        )


def _resolve_with_origination_proved(repo_root: Path, work_item: dict, work_item_id: str,
                                     claim: dict | None, local_in_progress: str | None,
                                     selected_id: str | None, checkpoints: dict, *,
                                     verify_resume_safety, now: str, state_rel_path: str,
                                     adopt_origination_guard: bool = True,
                                     ) -> tuple[str, str | None, str | None]:
    """The contended branches of step 1c, reached only once this worktree has
    proved it is the originating one by step 3's **single** proof, the local
    identity record (revision 68 withdrew revision 67's second proof;
    `OPUS-R85-001`). Adoption owes one further origination test of its own,
    which this branch does not duplicate -- see `adopt_claim` and
    `OPUS-R86-001`."""
    if claim is None:
        # A1/A2: locally interrupted with no shared claim -- a checkpoint
        # started before this mechanism existed, or a claim lost out of band.
        # Adopt it now, so the very next foreign invocation is protected.
        if selected_id is not None and selected_id != local_in_progress:
            raise CheckpointOwnershipStateMismatchError(
                f"{work_item_id!r} records {local_in_progress!r} IN_PROGRESS but selection "
                f"resolved {selected_id!r}")
        adopted = adopt_claim(repo_root, work_item_id, work_item, now=now,
                              verify_resume_safety=verify_resume_safety,
                              state_rel_path=state_rel_path,
                              origination_guard=adopt_origination_guard)
        return RESUME, local_in_progress, adopted.get("owner_token")

    if not claim_is_this_worktree(repo_root, claim):
        # Reachable only when `verify_resume_safety` *passed* against a foreign
        # claim -- a worktree with a valid identity record of its own for this
        # work item whose claim was since taken over. Every other foreign case
        # was already refused there with `S14a`/`S14b`. Kept as a fail-closed
        # backstop rather than an assumption it cannot happen.
        raise CheckpointOwnedByOtherWorktreeError(
            f"{work_item_id!r} is claimed by worktree {claim.get('worktree_root')!r}")

    claimed_id = claim.get("checkpoint_id")

    if local_in_progress is not None:
        if local_in_progress != claimed_id or (selected_id is not None and selected_id != claimed_id):
            raise CheckpointOwnershipStateMismatchError(
                f"this worktree claims {work_item_id!r} checkpoint {claimed_id!r}, its "
                f"WORKFLOW_STATE.json records {local_in_progress!r} IN_PROGRESS, and selection "
                f"resolved {selected_id!r} -- reporting rather than guessing which is right")
        return RESUME, claimed_id, claim.get("owner_token")

    # Nothing is locally IN_PROGRESS, and the claim is this worktree's own.
    local_status = checkpoints.get(claimed_id, {}).get("status")

    if local_status is None:
        # A3: crashed between publishing the claim and writing the state. The
        # owner finishes its own acquisition -- this releases nothing and
        # discards nothing, so it is not a stale-claim release.
        if selected_id is not None and selected_id != claimed_id:
            raise CheckpointOwnershipStateMismatchError(
                f"this worktree claims {work_item_id!r} checkpoint {claimed_id!r} with no local "
                f"state entry, but selection resolved {selected_id!r} -- reporting rather than "
                f"guessing")
        return CONTINUE_CLAIM, claimed_id, claim.get("owner_token")

    if local_status == "COMPLETE":
        # The single automatic release in the whole design: a self-owned claim
        # on a checkpoint whose completion is **durable** (committed at HEAD),
        # i.e. a crash between step 1f's commit and its release. A6: the
        # working tree saying `COMPLETE` is not enough.
        durable = committed_checkpoint_status(repo_root, work_item_id, claimed_id,
                                              state_rel_path=state_rel_path)
        if durable == "COMPLETE":
            release_checkpoint(repo_root, work_item_id, claimed_id,
                               owner_token=claim.get("owner_token"), now=now)
            # `GPT-R81-003`: the post-release result is non-ambiguous. Selection
            # already ran against a local state recording this checkpoint
            # `COMPLETE`, so `selected_id` is either the concrete next
            # checkpoint or `None` because the registry is exhausted -- and the
            # exhausted case is terminal, not a `FRESH` with a `None` id.
            if selected_id is None:
                return NO_CHECKPOINT, None, None
            return FRESH, selected_id, None
        raise CheckpointOwnershipStateMismatchError(
            f"this worktree's claim on {work_item_id!r} checkpoint {claimed_id!r} shows "
            f"{local_status!r} in the working tree but {durable!r} in the committed state at "
            f"HEAD -- refusing to release a claim whose completion is not durable")

    raise CheckpointOwnershipStateMismatchError(
        f"this worktree holds a claim on {work_item_id!r} checkpoint {claimed_id!r}, but this "
        f"worktree's WORKFLOW_STATE.json records its status as {local_status!r} with nothing "
        f"IN_PROGRESS -- reporting rather than guessing whether to resume or discard")


def classify_selection(repo_root: Path, work_item: dict, work_item_id: str, selected_id,
                       *, verify_resume_safety, now: str = "tX") -> str:
    """Backwards-compatible shim for passes 1-5, which predate the
    `CONTINUE_CLAIM` outcome."""
    return resolve_ownership(repo_root, work_item, work_item_id, selected_id,
                             verify_resume_safety=verify_resume_safety, now=now)[0]
