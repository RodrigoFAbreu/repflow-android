#!/usr/bin/env python3
"""Workflow v2.1 state-file schema and validator core (WF1a), extended
with work-item routing and the registry/mapping generator (WF1b).

Implements the `docs/ai-workflow/WORKFLOW_V2_PLAN.md` D3 schema for
`docs/ai-workflow/WORKFLOW_CONFIG.json` (repository-level default,
pre-activation fail-safe, `supported_versions` enforcement),
`docs/ai-workflow/WORKFLOW_STATE.json` (multi-item work-item map,
`checkpoints` map as the sole writable checkpoint-status record), the
local-only `.ai-review/runtime/WORKTREE_IDENTITY.json` schema (D3;
its writer is WF2, not built here), and D-Commit-Provenance's exact,
scoped, ancestry-limited `Workflow-Checkpoint`/`Workflow-Work-Item`
trailer search with its duplicate-trailer tie-break.

WF1b adds D1's work-item routing (create-or-resume semantics for
`/milestone-plan`, completion/reset), and D-Registry/D4b's registry/
mapping JSON generator (the sole writer either file should ever have,
running the coverage/topological-order checks at generation time rather
than leaving them to be discovered at review time).

Still out of scope here (owned by later checkpoints named in the plan's
own checkpoint table): a full phase-entry/exit transition graph
(D-States, applied to `MILESTONE_WORKFLOW.md` by WF4a-ii/`WF4a-iv`),
`WORKTREE_IDENTITY.json`'s writer (WF2), and the approval/commit-lifecycle
helpers (WF4a-iii).

Stdlib-only, mirroring `scripts/workflow_fingerprint.py`'s own
`docs/TECHNICAL_DECISIONS.md`-recorded constraint.

Run the hermetic suite: python3 scripts/workflow_state_test.py
Run the real-repository demonstration: python3 scripts/workflow_state_demo_test.py
"""

from __future__ import annotations

import copy
import json
import subprocess
from pathlib import Path

from workflow_fingerprint import (  # noqa: F401 - re-exported for callers
    InvalidWorkItemIdError,
    InvalidWorkItemTypeError,
    validate_work_item_id,
    validate_work_item_type,
)

DEFAULT_CONFIG_PATH = Path("docs/ai-workflow/WORKFLOW_CONFIG.json")
DEFAULT_STATE_PATH = Path("docs/ai-workflow/WORKFLOW_STATE.json")
WORKTREE_IDENTITY_PATH = Path(".ai-review/runtime/WORKTREE_IDENTITY.json")

SCHEMA_VERSION = 1

# The fail-safe value D3 names explicitly: "Missing or corrupt config
# before activation -> validator fails closed to default_workflow_version:
# '1'" (resolves OPUS-R6-015).
PRE_ACTIVATION_FAILSAFE_VERSION = "1"

WORK_ITEM_TYPES = frozenset({"process", "product"})
# work_item_kind is a controlled vocabulary distinct from work_item_type
# (resolves GPT-R9-013); "synthetic" is WF8b's isolated dry-run item.
WORK_ITEM_KINDS = frozenset({"process", "product", "synthetic"})

CHECKPOINT_STATUSES = frozenset({"IN_PROGRESS", "COMPLETE"})

# Only MILESTONE_COMPLETE is terminal -- LEGACY_READY is explicitly
# "dormant, not terminal" (D-Legacy phase 1, resolves GPT-R9-005).
TERMINAL_PHASES = frozenset({"MILESTONE_COMPLETE"})

# Union of the current v1 state machine (docs/ai-workflow/
# MILESTONE_WORKFLOW.md, unmodified by this work item) and the v2.1-only
# phases this plan adds (D-States/D-Plan-Review-Stages/D-Legacy) -- a
# single validator must accept either vocabulary since a work item's
# governing_workflow_version selects which one its own commands use
# (D-Self-Governance's dual-mode branching). Full entry/exit transition
# enforcement between these phases is out of WF1a's scope (see module
# docstring) -- this is an allowlist, not a transition graph.
KNOWN_PHASES = frozenset({
    # v1 (docs/ai-workflow/MILESTONE_WORKFLOW.md, unchanged by this work item)
    "PLANNING",
    "SELF_REVIEWING_PLAN",
    "AWAITING_EXTERNAL_PLAN_REVIEW",
    "REVISING_PLAN",
    "IMPLEMENTING",
    "SELF_REVIEWING_IMPLEMENTATION",
    "AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW",
    "APPLYING_REVIEW_FEEDBACK",
    "AWAITING_FUNCTIONAL_REVIEW",
    "FIXING_FUNCTIONAL_FINDINGS",
    "AWAITING_USER_ACCEPTANCE",
    "MILESTONE_COMPLETE",
    # v2.1-only additions (D-States, D-Plan-Review-Stages)
    "AWAITING_LOCAL_PLAN_REVIEW",
    "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW",
    "AWAITING_PLAN_APPROVAL",
    "AWAITING_TECHNICAL_APPROVAL",
    # D-Legacy phase 1 -- dormant, not terminal
    "LEGACY_READY",
})


class CorruptJsonError(Exception):
    """Raised for unparseable JSON in a tracked workflow file -- D3's
    'corrupt/unparseable JSON (fails closed)' validator-reject rule."""


class UnknownPhaseError(Exception):
    """Raised when a work item's `phase` is not in `KNOWN_PHASES`."""


class UnknownCheckpointStatusError(Exception):
    """Raised when a checkpoint's `status` is not in `CHECKPOINT_STATUSES`."""


class ActiveWorkItemInvalidError(Exception):
    """Raised when `active_work_item_id` names a nonexistent entry or one
    whose phase is terminal (D3's validator-reject rule)."""


class WorkItemIdKeyMismatchError(Exception):
    """Raised when a `work_items` entry's own `work_item_id` field
    disagrees with its containing map key (restated per OPUS-R10-015)."""


class MultipleInProgressCheckpointsError(Exception):
    """Raised when a single work item has more than one `IN_PROGRESS`
    checkpoint at once (D3's validator-reject rule)."""


class CheckpointDependencyNotCompleteError(Exception):
    """Raised when a checkpoint is `COMPLETE` while the registry names a
    dependency of its that is not (D3's validator-reject rule)."""


class CheckpointNotReachableError(Exception):
    """Raised when a checkpoint marked `COMPLETE` has no reachable commit
    carrying its `Workflow-Checkpoint` trailer -- D3's "completion without
    a reachable commit (D-Commit-Provenance)" validator-reject rule."""


class AmbiguousCheckpointTrailerError(Exception):
    """Raised when more than one commit in range carries the same
    (`Workflow-Checkpoint`, `Workflow-Work-Item`) trailer pair and the
    first-parent-ancestor tie-break does not resolve to exactly one
    (D-Commit-Provenance's stated recovery: a `Workflow-Supersedes:`
    trailer or an explicit `WORKFLOW_STATE.json` annotation, never a
    silent pick)."""


class NonTopologicalRegistryOrderError(Exception):
    """Raised when a registry's `checkpoints` array does not list every
    checkpoint after all of its own `depends_on` entries (D-Selection
    point 3; missing-test items 29/73)."""


class UnmappedRequirementError(Exception):
    """Raised when a requirement in the mapping JSON names a
    `checkpoint_id` absent from the registry (D3's validator-reject rule,
    resolves OPUS-R10-006)."""


class UnownedCheckpointError(Exception):
    """Raised when a registry checkpoint owns zero requirements in the
    mapping JSON (D3's validator-reject rule, resolves OPUS-R10-006)."""


class PlanReviewStagesInvalidForVersionError(Exception):
    """Raised when `plan_review_stages` is non-null on a work item whose
    `governing_workflow_version` is not `"2.1"` (resolves GPT-R11-001)."""


class ManualStageWithoutLocalStageError(Exception):
    """Raised when `manual_external_plan_review` is recorded while
    `local_model_plan_review` is absent (resolves GPT-R11-001)."""


class StageVerdictNotApproveError(Exception):
    """Raised when a recorded plan-review stage's `verdict` is anything
    other than `"APPROVE"` (resolves GPT-R14-010, missing-test item 116)."""


class ConfigMissingAfterActivationError(Exception):
    """Raised when `WORKFLOW_CONFIG.json` is missing or corrupt *after*
    Workflow v2.1 activation -- a hard stop, never a silent downgrade
    (resolves OPUS-R6-015)."""


class UnsupportedGoverningVersionError(Exception):
    """Raised when a work item's `governing_workflow_version` is outside
    the config's `supported_versions` (resolves OPUS-R6-024, optional)."""


class WorkItemTerminalReuseError(Exception):
    """Raised when routing names a `work_item_id` that already exists at a
    terminal phase -- ids are not reused after `MILESTONE_COMPLETE` (D1)."""


def _run(args: list[str], cwd: Path) -> str:
    result = subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)
    return result.stdout


def _load_json(path: Path):
    try:
        text = path.read_text()
    except FileNotFoundError:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise CorruptJsonError(f"{path}: {exc}") from exc


# ---------------------------------------------------------------------------
# D-Commit-Provenance: exact, scoped, ancestry-limited trailer search
# ---------------------------------------------------------------------------


def _commit_trailers(repo_root: Path, commit: str) -> dict[str, str]:
    """Parse a commit's trailers via `git interpret-trailers`, the same
    plain-Git-metadata mechanism D-Commit-Provenance relies on -- never a
    substring `git log --grep` match."""
    body = _run(["git", "log", "-1", "--format=%B", commit], cwd=repo_root)
    parsed = subprocess.run(
        ["git", "interpret-trailers", "--parse"],
        cwd=repo_root, input=body, capture_output=True, text=True, check=True,
    ).stdout
    trailers: dict[str, str] = {}
    for line in parsed.splitlines():
        if ":" not in line:
            continue
        key, _, value = line.partition(":")
        trailers[key.strip()] = value.strip()
    return trailers


def _first_parent_commits_ordered(repo_root: Path, head: str = "HEAD") -> list[str]:
    """First-parent ancestors of `head`, newest first (git log's natural
    order) -- used both for the tie-break's ancestor-membership test and
    for "most recent by first-parent ancestry" event search."""
    out = _run(["git", "log", "--first-parent", "--format=%H", head], cwd=repo_root)
    return [line for line in out.splitlines() if line]


def discover_checkpoint_commits(
    repo_root: Path, work_item_id: str, base_commit: str, head: str = "HEAD",
) -> dict[str, str]:
    """Full D-Commit-Provenance search: every commit reachable in
    `base_commit..head` carrying an exact `Workflow-Checkpoint: <id>` +
    `Workflow-Work-Item: <work_item_id>` trailer pair, requiring exactly
    one match per checkpoint id after the stated tie-break (prefer a
    first-parent ancestor of `head`). Returns `{checkpoint_id: commit_sha}`.
    Genuine ambiguity (more than one first-parent-ancestor match) raises
    rather than silently picking (resolves OPUS-R6-022; missing-test items
    50, 62, 64)."""
    out = _run(["git", "log", "--format=%H", f"{base_commit}..{head}"], cwd=repo_root)
    commits = [line for line in out.splitlines() if line]

    matches_by_id: dict[str, list[str]] = {}
    for commit in commits:
        trailers = _commit_trailers(repo_root, commit)
        if trailers.get("Workflow-Work-Item") != work_item_id:
            continue
        checkpoint_id = trailers.get("Workflow-Checkpoint")
        if not checkpoint_id:
            continue
        matches_by_id.setdefault(checkpoint_id, []).append(commit)

    first_parent = set(_first_parent_commits_ordered(repo_root, head))
    resolved: dict[str, str] = {}
    for checkpoint_id, candidates in matches_by_id.items():
        if len(candidates) == 1:
            resolved[checkpoint_id] = candidates[0]
            continue
        tie_broken = [c for c in candidates if c in first_parent]
        if len(tie_broken) == 1:
            resolved[checkpoint_id] = tie_broken[0]
        else:
            raise AmbiguousCheckpointTrailerError(
                f"checkpoint {checkpoint_id!r} for work item {work_item_id!r} has "
                f"{len(candidates)} trailer matches in {base_commit}..{head}, and "
                f"{len(tie_broken)} remain after the first-parent-ancestor "
                f"tie-break (candidates: {candidates}); needs a Workflow-Supersedes "
                f"trailer or an explicit WORKFLOW_STATE.json annotation"
            )
    return resolved


def verify_checkpoint_completions(
    work_item: dict, repo_root: Path, base_commit: str, head: str = "HEAD",
) -> None:
    """D3's "completion without a reachable commit" validator-reject rule:
    every checkpoint recorded `COMPLETE` in state must have a reachable,
    exactly-matching trailer commit."""
    work_item_id = work_item["work_item_id"]
    discovered = discover_checkpoint_commits(repo_root, work_item_id, base_commit, head)
    for checkpoint_id, entry in work_item.get("checkpoints", {}).items():
        if entry.get("status") == "COMPLETE" and checkpoint_id not in discovered:
            raise CheckpointNotReachableError(
                f"{work_item_id}/{checkpoint_id} is COMPLETE in state but no "
                f"commit in {base_commit}..{head} carries a matching "
                f"Workflow-Checkpoint/Workflow-Work-Item trailer pair"
            )


# ---------------------------------------------------------------------------
# D-Self-Governance: activation/rollback event search
# ---------------------------------------------------------------------------


def find_latest_activation_event(repo_root: Path, head: str = "HEAD") -> tuple[str, str] | None:
    """Most recent `Workflow-Activation`/`Workflow-Rollback` trailer event
    by first-parent ancestry from `head` (resolves GPT-R9-007's missing-
    config recovery rule: "the latest event", not just "any activation
    trailer"). Returns `(kind, commit)` where `kind` is `"activation"` or
    `"rollback"`, or `None` if neither has ever landed."""
    for commit in _first_parent_commits_ordered(repo_root, head):
        trailers = _commit_trailers(repo_root, commit)
        if "Workflow-Activation" in trailers:
            return ("activation", commit)
        if "Workflow-Rollback" in trailers:
            return ("rollback", commit)
    return None


def is_activated(repo_root: Path, head: str = "HEAD") -> bool:
    event = find_latest_activation_event(repo_root, head)
    return event is not None and event[0] == "activation"


# ---------------------------------------------------------------------------
# WORKFLOW_CONFIG.json
# ---------------------------------------------------------------------------


def default_config() -> dict:
    return {
        "schema_version": SCHEMA_VERSION,
        "default_workflow_version": PRE_ACTIVATION_FAILSAFE_VERSION,
        "supported_versions": ["1", "2.1"],
    }


def validate_config(config: dict) -> None:
    if config.get("schema_version") != SCHEMA_VERSION:
        raise CorruptJsonError(f"unknown config schema_version: {config.get('schema_version')!r}")
    supported = config.get("supported_versions")
    if not isinstance(supported, list) or not all(isinstance(v, str) for v in supported):
        raise CorruptJsonError(f"supported_versions must be a list of strings: {supported!r}")
    default_version = config.get("default_workflow_version")
    if default_version not in supported:
        raise CorruptJsonError(
            f"default_workflow_version {default_version!r} not in supported_versions {supported!r}"
        )


def load_config(repo_root: Path, config_path: Path = DEFAULT_CONFIG_PATH) -> dict:
    """Load `WORKFLOW_CONFIG.json` with D3's pre-/post-activation fail-safe
    rule: missing or corrupt before activation -> default to
    `default_workflow_version: "1"`; missing or corrupt after activation
    -> hard stop (resolves OPUS-R6-015)."""
    try:
        config = _load_json(repo_root / config_path)
    except CorruptJsonError:
        config = None
    if config is not None:
        validate_config(config)
        return config
    if is_activated(repo_root):
        raise ConfigMissingAfterActivationError(
            f"{config_path} is missing or corrupt, and Workflow v2.1 is activated -- "
            f"restore or recreate it from the activation commit's own tree, "
            f"per D-Self-Governance's missing-config recovery rule"
        )
    return default_config()


def validate_governing_version(governing_workflow_version: str, config: dict) -> None:
    """Resolves OPUS-R6-024 (optional): a work item's governing version
    must be one of the config's supported_versions, checked at creation."""
    supported = config.get("supported_versions", [])
    if governing_workflow_version not in supported:
        raise UnsupportedGoverningVersionError(
            f"governing_workflow_version {governing_workflow_version!r} not in "
            f"supported_versions {supported!r}"
        )


# ---------------------------------------------------------------------------
# Registry: topological order + registry x mapping bidirectional coverage
# ---------------------------------------------------------------------------


def validate_registry_topological_order(registry: dict) -> None:
    """D-Selection point 3: the registry JSON's `checkpoints` array order
    is normative and must be a valid topological order of the
    `depends_on` column (missing-test items 29/73)."""
    seen: set[str] = set()
    for entry in registry["checkpoints"]:
        checkpoint_id = entry["id"]
        for dependency in entry.get("depends_on", []):
            if dependency not in seen:
                raise NonTopologicalRegistryOrderError(
                    f"checkpoint {checkpoint_id!r} depends on {dependency!r}, which does "
                    f"not appear earlier in the registry's checkpoints array"
                )
        seen.add(checkpoint_id)


def validate_registry_mapping_coverage(registry: dict, mapping: dict) -> None:
    """D3's validator-reject rule (resolves OPUS-R10-006): every
    requirement names at least one real checkpoint, and every checkpoint
    owns at least one requirement."""
    checkpoint_ids = {entry["id"] for entry in registry["checkpoints"]}
    owned: set[str] = set()
    for requirement_id, requirement in mapping.get("requirements", {}).items():
        for checkpoint_id in requirement.get("checkpoint_ids", []):
            if checkpoint_id not in checkpoint_ids:
                raise UnmappedRequirementError(
                    f"{requirement_id} names checkpoint {checkpoint_id!r}, absent from the registry"
                )
            owned.add(checkpoint_id)
    unowned = checkpoint_ids - owned
    if unowned:
        raise UnownedCheckpointError(f"checkpoints owned by no requirement: {sorted(unowned)}")


def render_registry_markdown(registry: dict) -> str:
    """A generated, human-readable view of the registry JSON (D-Registry).
    Never itself hashed -- reformatting this output must never change
    `review_content_id`, since only the JSON file is a protected path."""
    lines = ["| id | name | depends_on | complexity | session_target |",
             "| --- | --- | --- | --- | --- |"]
    for entry in registry["checkpoints"]:
        deps = ", ".join(entry.get("depends_on", [])) or "-"
        lines.append(
            f"| {entry['id']} | {entry['name']} | {deps} | "
            f"{entry['complexity']} | {entry['session_target']} |"
        )
    return "\n".join(lines) + "\n"


def generate_registry(work_item_id: str, plan_revision: int, checkpoints: list[dict]) -> dict:
    """Builds the registry JSON structure (D-Registry) and validates
    D-Selection point 3 (topological order) before returning -- a bad
    order fails at generation time, never silently written."""
    validate_work_item_id(work_item_id)
    registry = {
        "schema_version": SCHEMA_VERSION,
        "work_item_id": work_item_id,
        "plan_revision": plan_revision,
        "checkpoints": checkpoints,
    }
    validate_registry_topological_order(registry)
    return registry


def generate_mapping(work_item_id: str, requirements: dict, *, registry: dict) -> dict:
    """Builds the requirements-mapping JSON (D4b) and validates
    bidirectional registry x mapping coverage before returning (resolves
    `OPUS-R10-006`: the coverage query runs at generation time, not review
    time -- an unmapped requirement or unowned checkpoint fails here,
    before either file is ever written)."""
    validate_work_item_id(work_item_id)
    mapping = {
        "schema_version": SCHEMA_VERSION,
        "work_item_id": work_item_id,
        "requirements": requirements,
    }
    validate_registry_mapping_coverage(registry, mapping)
    return mapping


def write_registry_and_mapping(
    repo_root: Path, registry_path: Path, mapping_path: Path, registry: dict, mapping: dict,
) -> None:
    """The sole writer D-Registry names for either file: byte-for-byte, no
    independent reformatting (the exact lesson `OPUS-R8-002` already
    taught this design once). Re-validates immediately before writing as a
    fail-closed guard against a stale or hand-built argument, even though
    `generate_registry`/`generate_mapping` should already have validated
    their own output."""
    validate_registry_topological_order(registry)
    validate_registry_mapping_coverage(registry, mapping)
    (repo_root / registry_path).write_text(json.dumps(registry, indent=2) + "\n")
    (repo_root / mapping_path).write_text(json.dumps(mapping, indent=2) + "\n")


# ---------------------------------------------------------------------------
# D1: work-item routing (create-or-resume) and completion/reset
# ---------------------------------------------------------------------------


def default_work_item(
    *, work_item_id: str, work_item_type: str, work_item_kind: str,
    plan_path: str, registry_path: str, governing_workflow_version: str,
    plan_revision: int, last_transition: str,
) -> dict:
    """D3's full per-item field list, defaulted for a freshly created work
    item (D1). `governing_workflow_version` is the caller's concern to fix
    from the then-current repository-level default -- this function never
    reads config itself (D-Self-Governance: fixed at creation, never
    re-read afterward)."""
    validate_work_item_id(work_item_id)
    validate_work_item_type(work_item_type)
    if work_item_kind not in WORK_ITEM_KINDS:
        raise InvalidWorkItemTypeError(f"unknown work_item_kind: {work_item_kind!r}")
    return {
        "work_item_type": work_item_type,
        "work_item_kind": work_item_kind,
        "work_item_id": work_item_id,
        "parent_work_item_id": None,
        "plan_path": plan_path,
        "registry_path": registry_path,
        "governing_workflow_version": governing_workflow_version,
        "phase": "PLANNING",
        "plan_revision": plan_revision,
        "implementation_revision": None,
        "functional_review_round": None,
        "base_commit": None,
        "reviewed_implementation_head": None,
        "current_checkpoint_id": None,
        "last_completed_checkpoint_id": None,
        "checkpoints": {},
        "current_bundle_id": None,
        "plan_approval": None,
        "technical_approval": None,
        "plan_review_stages": None,
        "functional_acceptance_status": None,
        "blocking_decisions": [],
        "state_revision": 1,
        "last_transition": last_transition,
    }


def route_work_item(
    state: dict, config: dict, *, work_item_id: str, work_item_type: str,
    work_item_kind: str, plan_path: str, registry_path: str,
    plan_revision: int, now: str,
) -> dict:
    """D1's routing text, in full: `/milestone-plan` creates or updates the
    item under `work_items[id]`. Returns a new state dict (does not mutate
    the input, so a caller can inspect the pre-route state on failure).

    - A fresh id creates a new `work_items[id]` entry, fixing
      `governing_workflow_version` from the config's current
      `default_workflow_version` (validated against `supported_versions`,
      `OPUS-R6-024`) and never re-read afterward.
    - An id naming an existing *non-terminal* entry resumes it: only
      `plan_revision`/`state_revision`/`last_transition` advance --
      identity fields (`work_item_type`/`kind`/`governing_workflow_version`/
      `parent_work_item_id`) are immutable after creation.
    - An id naming an existing *terminal* entry is a hard error: ids are
      not reused after `MILESTONE_COMPLETE`.
    - `active_work_item_id` is set to this id only if no other item is
      currently active (or this item already is) -- routing never steals
      focus from unrelated in-flight, non-terminal work (D1's "resume-focus
      pointer, not an execution lock").
    """
    validate_work_item_id(work_item_id)
    validate_work_item_type(work_item_type)
    if work_item_kind not in WORK_ITEM_KINDS:
        raise InvalidWorkItemTypeError(f"unknown work_item_kind: {work_item_kind!r}")

    new_state = copy.deepcopy(state)
    work_items = new_state.setdefault("work_items", {})
    existing = work_items.get(work_item_id)

    if existing is not None and existing.get("phase") in TERMINAL_PHASES:
        raise WorkItemTerminalReuseError(
            f"{work_item_id!r} already exists at a terminal phase "
            f"({existing.get('phase')!r}) -- use a new work_item_id"
        )

    if existing is None:
        validate_governing_version(config["default_workflow_version"], config)
        work_items[work_item_id] = default_work_item(
            work_item_id=work_item_id, work_item_type=work_item_type,
            work_item_kind=work_item_kind, plan_path=plan_path,
            registry_path=registry_path,
            governing_workflow_version=config["default_workflow_version"],
            plan_revision=plan_revision, last_transition=now,
        )
    else:
        existing["plan_revision"] = plan_revision
        existing["state_revision"] = existing.get("state_revision", 1) + 1
        existing["last_transition"] = now

    active = new_state.get("active_work_item_id")
    if active is None or active == work_item_id:
        new_state["active_work_item_id"] = work_item_id
    # else: a different item is active and non-terminal -- left alone;
    # repointing active_work_item_id is always an explicit, separate act.

    return new_state


def complete_work_item(state: dict, work_item_id: str, now: str) -> dict:
    """D1's completion/reset text: on `MILESTONE_COMPLETE` (or
    process-completion archival), the entry's phase becomes terminal and,
    if it was `active_work_item_id`, that pointer resets to `null` so the
    next `/milestone-plan` creates a fresh entry and claims the pointer.
    Returns a new state dict."""
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    work_item["phase"] = "MILESTONE_COMPLETE"
    work_item["current_checkpoint_id"] = None
    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    if new_state.get("active_work_item_id") == work_item_id:
        new_state["active_work_item_id"] = None
    return new_state


# ---------------------------------------------------------------------------
# WORKFLOW_STATE.json
# ---------------------------------------------------------------------------


def _validate_plan_review_stages(work_item: dict) -> None:
    stages = work_item.get("plan_review_stages")
    if stages is None:
        return
    if work_item.get("governing_workflow_version") != "2.1":
        raise PlanReviewStagesInvalidForVersionError(
            f"{work_item['work_item_id']}: plan_review_stages is non-null but "
            f"governing_workflow_version is {work_item.get('governing_workflow_version')!r}, "
            f"not \"2.1\""
        )
    local = stages.get("local_model_plan_review")
    manual = stages.get("manual_external_plan_review")
    if manual is not None and local is None:
        raise ManualStageWithoutLocalStageError(
            f"{work_item['work_item_id']}: manual_external_plan_review is recorded "
            f"while local_model_plan_review is absent"
        )
    for stage_name, stage in (("local_model_plan_review", local), ("manual_external_plan_review", manual)):
        if stage is not None and stage.get("verdict") != "APPROVE":
            raise StageVerdictNotApproveError(
                f"{work_item['work_item_id']}.{stage_name}.verdict is "
                f"{stage.get('verdict')!r}, expected \"APPROVE\" -- only a completed "
                f"APPROVE is ever recorded at either stage (GPT-R14-010)"
            )


def _validate_work_item(work_item_id: str, work_item: dict) -> None:
    if work_item.get("work_item_id") != work_item_id:
        raise WorkItemIdKeyMismatchError(
            f"work_items[{work_item_id!r}].work_item_id == {work_item.get('work_item_id')!r}"
        )
    validate_work_item_id(work_item_id)
    validate_work_item_type(work_item["work_item_type"])
    kind = work_item.get("work_item_kind")
    if kind is not None and kind not in WORK_ITEM_KINDS:
        raise InvalidWorkItemTypeError(f"unknown work_item_kind: {kind!r}")
    phase = work_item.get("phase")
    if phase not in KNOWN_PHASES:
        raise UnknownPhaseError(f"work_items[{work_item_id!r}].phase == {phase!r}")

    in_progress = []
    for checkpoint_id, entry in work_item.get("checkpoints", {}).items():
        status = entry.get("status")
        if status not in CHECKPOINT_STATUSES:
            raise UnknownCheckpointStatusError(f"checkpoints[{checkpoint_id!r}].status == {status!r}")
        if status == "IN_PROGRESS":
            in_progress.append(checkpoint_id)
    if len(in_progress) > 1:
        raise MultipleInProgressCheckpointsError(
            f"work_items[{work_item_id!r}] has multiple IN_PROGRESS checkpoints: {in_progress}"
        )

    _validate_plan_review_stages(work_item)


def validate_state(state: dict, *, registry: dict | None = None) -> None:
    """D3's "Validator rejects" list, to the extent checkable from schema
    and (optionally) the registry alone. Corrupt/unparseable JSON is the
    caller's concern (`_load_json`/`CorruptJsonError`) -- this function
    receives already-parsed data."""
    if state.get("schema_version") != SCHEMA_VERSION:
        raise CorruptJsonError(f"unknown state schema_version: {state.get('schema_version')!r}")

    work_items = state.get("work_items", {})
    for work_item_id, work_item in work_items.items():
        _validate_work_item(work_item_id, work_item)

    active_id = state.get("active_work_item_id")
    if active_id is not None:
        active_item = work_items.get(active_id)
        if active_item is None or active_item.get("phase") in TERMINAL_PHASES:
            raise ActiveWorkItemInvalidError(
                f"active_work_item_id {active_id!r} names a nonexistent or terminal-phase entry"
            )

    if registry is not None:
        depends_on_by_id = {entry["id"]: entry.get("depends_on", []) for entry in registry["checkpoints"]}
        for work_item in work_items.values():
            checkpoints = work_item.get("checkpoints", {})
            for checkpoint_id, entry in checkpoints.items():
                if entry["status"] != "COMPLETE":
                    continue
                for dependency in depends_on_by_id.get(checkpoint_id, []):
                    dep_status = checkpoints.get(dependency, {}).get("status")
                    if dep_status != "COMPLETE":
                        raise CheckpointDependencyNotCompleteError(
                            f"{work_item['work_item_id']}/{checkpoint_id} is COMPLETE but its "
                            f"dependency {dependency!r} is {dep_status!r}"
                        )


# ---------------------------------------------------------------------------
# .ai-review/runtime/WORKTREE_IDENTITY.json (schema only -- WF2 writes it)
# ---------------------------------------------------------------------------


def validate_worktree_identity(data: dict) -> None:
    """Schema check for D3's local-only, gitignored WORKTREE_IDENTITY.json.
    No writer is implemented here -- D3 names the writer as the command
    that transitions a checkpoint to IN_PROGRESS (WF2's
    `/milestone-implement`, or the bootstrap command for this work item's
    own checkpoints), not WF1a."""
    for key in ("repo_root", "git_common_dir", "worktree_root", "generated_at"):
        if not isinstance(data.get(key), str) or not data[key]:
            raise CorruptJsonError(f"WORKTREE_IDENTITY.json missing/invalid {key!r}")
    expected = data.get("expected_dirty_paths_by_work_item")
    if not isinstance(expected, dict):
        raise CorruptJsonError("WORKTREE_IDENTITY.json.expected_dirty_paths_by_work_item must be a dict")
    for work_item_id, entries in expected.items():
        if not isinstance(entries, list):
            raise CorruptJsonError(f"expected_dirty_paths_by_work_item[{work_item_id!r}] must be a list")
        for entry in entries:
            if set(entry) != {"path", "sha256"}:
                raise CorruptJsonError(
                    f"expected_dirty_paths_by_work_item[{work_item_id!r}] entry has unexpected keys: {entry!r}"
                )
