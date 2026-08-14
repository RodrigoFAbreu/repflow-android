"""FROZEN control arm: the revision-62 draft of the shared-claim design.

This is the design exactly as `WF8B_S14_FINDING_...md` section 5 proposed it and
as passes 1-5 originally exercised it. It is kept, unmodified, so that pass 6's
defect reproductions stay executable forever rather than becoming a claim about
code that no longer exists -- the same "control arm" discipline `pass4.py`'s O1
already uses.

Do not fix anything here. The corrected design is `checkpoint_ownership.py`.
"""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path

CLAIMS_RELDIR = "ai-workflow/checkpoint-claims"
CLAIM_SCHEMA_VERSION = 1


class CheckpointOwnedByOtherWorktreeError(Exception):
    """Already claimed by a different worktree of this repository."""


class CheckpointOwnershipStateMismatchError(Exception):
    """This worktree's own claim cannot be reconciled with its own state."""


class CheckpointOwnershipUnavailableError(Exception):
    """The shared claim record cannot be read or written."""


def _run(args: list[str], cwd: Path) -> str:
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, check=True).stdout


def _git_identity(repo_root: Path) -> tuple[str, str, str]:
    repo_root_out = _run(["git", "rev-parse", "--show-toplevel"], cwd=repo_root).strip()
    raw = _run(["git", "rev-parse", "--git-common-dir"], cwd=repo_root).strip()
    common = raw if Path(raw).is_absolute() else str((repo_root / raw).resolve())
    return repo_root_out, common, repo_root_out


def claims_dir(repo_root: Path) -> Path:
    _, common_dir, _ = _git_identity(repo_root)
    return Path(common_dir) / CLAIMS_RELDIR


def claim_path(repo_root: Path, work_item_id: str) -> Path:
    token = hashlib.sha256(work_item_id.encode()).hexdigest()
    return claims_dir(repo_root) / f"{token}.json"


def _validate_claim(path: Path, data, work_item_id: str) -> dict:
    if not isinstance(data, dict) or data.get("schema_version") != CLAIM_SCHEMA_VERSION:
        raise CheckpointOwnershipUnavailableError(
            f"{path} has an unsupported shape/schema_version")
    required = ("work_item_id", "checkpoint_id", "repo_root", "git_common_dir",
                "worktree_root", "claimed_at")
    missing = [field for field in required if not isinstance(data.get(field), str)]
    if missing:
        raise CheckpointOwnershipUnavailableError(f"{path} is missing/malformed fields {missing}")
    if data["work_item_id"] != work_item_id:
        raise CheckpointOwnershipUnavailableError(
            f"{path} records work item {data['work_item_id']!r}, not {work_item_id!r}")
    return data


def resolve_claim(repo_root: Path, work_item_id: str) -> dict | None:
    path = claim_path(repo_root, work_item_id)
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as exc:
        raise CheckpointOwnershipUnavailableError(
            f"{path} exists but could not be read as JSON ({exc})") from exc
    return _validate_claim(path, data, work_item_id)


def claim_is_this_worktree(repo_root: Path, claim: dict) -> bool:
    repo_root_id, common, worktree_root = _git_identity(repo_root)
    return (claim.get("repo_root"), claim.get("git_common_dir"), claim.get("worktree_root")) == (
        repo_root_id, common, worktree_root)


def claim_checkpoint(repo_root: Path, work_item_id: str, checkpoint_id: str, *, now: str,
                     _preread: dict | None = None) -> dict:
    path = claim_path(repo_root, work_item_id)
    repo_root_id, common, worktree_root = _git_identity(repo_root)
    record = {
        "schema_version": CLAIM_SCHEMA_VERSION,
        "work_item_id": work_item_id,
        "checkpoint_id": checkpoint_id,
        "repo_root": repo_root_id,
        "git_common_dir": common,
        "worktree_root": worktree_root,
        "claimed_at": now,
    }
    payload = json.dumps(record, indent=2, sort_keys=True) + "\n"
    if _preread is not None and not claim_is_this_worktree(repo_root, _preread):
        raise CheckpointOwnedByOtherWorktreeError(
            f"{work_item_id!r} is already claimed by {_preread.get('worktree_root')!r}")
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o644)
    except FileExistsError:
        existing = resolve_claim(repo_root, work_item_id)
        if existing is not None and not claim_is_this_worktree(repo_root, existing):
            raise CheckpointOwnedByOtherWorktreeError(
                f"{work_item_id!r} checkpoint {existing.get('checkpoint_id')!r} is already "
                f"claimed by worktree {existing.get('worktree_root')!r} (this worktree is "
                f"{worktree_root!r})") from None
        _replace_own_claim(path, payload)
        return record
    except OSError as exc:
        raise CheckpointOwnershipUnavailableError(f"cannot write {path} ({exc})") from exc
    try:
        with os.fdopen(fd, "w") as handle:
            handle.write(payload)
    except OSError as exc:
        path.unlink(missing_ok=True)
        raise CheckpointOwnershipUnavailableError(f"cannot write {path} ({exc})") from exc
    return record


def _replace_own_claim(path: Path, payload: str) -> None:
    try:
        fd, tmp = tempfile.mkstemp(dir=str(path.parent), prefix=".claim-", suffix=".tmp")
        with os.fdopen(fd, "w") as handle:
            handle.write(payload)
        os.replace(tmp, path)
    except OSError as exc:
        raise CheckpointOwnershipUnavailableError(f"cannot refresh {path} ({exc})") from exc


def release_checkpoint(repo_root: Path, work_item_id: str, checkpoint_id: str) -> None:
    existing = resolve_claim(repo_root, work_item_id)
    if existing is None:
        return
    if not claim_is_this_worktree(repo_root, existing):
        raise CheckpointOwnedByOtherWorktreeError(
            f"refusing to release {work_item_id!r}'s claim held by "
            f"{existing.get('worktree_root')!r} from a different worktree")
    claim_path(repo_root, work_item_id).unlink(missing_ok=True)


def classify_selection(repo_root: Path, work_item: dict, work_item_id: str, selected_id: str,
                       *, verify_resume_safety, now: str = "tX") -> str:
    checkpoints = work_item.get("checkpoints", {})
    current_id = work_item.get("current_checkpoint_id")
    local_in_progress = (
        current_id if checkpoints.get(current_id, {}).get("status") == "IN_PROGRESS" else None
    )
    claim = resolve_claim(repo_root, work_item_id)

    if claim is None:
        if local_in_progress is None:
            return "fresh"
        verify_resume_safety(repo_root, work_item_id)
        if selected_id != local_in_progress:
            raise CheckpointOwnershipStateMismatchError(
                f"{work_item_id!r} records {local_in_progress!r} IN_PROGRESS but selection "
                f"resolved {selected_id!r}")
        return "resume"

    verify_resume_safety(repo_root, work_item_id)

    claimed_id = claim.get("checkpoint_id")
    if local_in_progress is not None:
        if local_in_progress != claimed_id or selected_id != claimed_id:
            raise CheckpointOwnershipStateMismatchError(
                f"this worktree claims {work_item_id!r} checkpoint {claimed_id!r}, its "
                f"WORKFLOW_STATE.json records {local_in_progress!r} IN_PROGRESS, and selection "
                f"resolved {selected_id!r}")
        return "resume"

    if checkpoints.get(claimed_id, {}).get("status") == "COMPLETE":
        release_checkpoint(repo_root, work_item_id, claimed_id)
        return "fresh"
    raise CheckpointOwnershipStateMismatchError(
        f"this worktree holds a claim on {work_item_id!r} checkpoint {claimed_id!r}, but this "
        f"worktree's WORKFLOW_STATE.json records its status as "
        f"{checkpoints.get(claimed_id, {}).get('status')!r} with nothing IN_PROGRESS")
