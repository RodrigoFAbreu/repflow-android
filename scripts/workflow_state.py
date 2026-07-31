#!/usr/bin/env python3
"""Workflow v2.1 state-file schema and validator core (WF1a), extended
with work-item routing and the registry/mapping generator (WF1b), D2/
D-States' approval-record schema and gate logic (WF4a-ii), and the
approval/checkpoint-completion Git-lifecycle mechanics (WF4a-iii).

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

WF4a-ii adds D2's unified `plan_approval`/`technical_approval` record
schema, the non-circular gate-reachability check for both new D-States
gates (`AWAITING_PLAN_APPROVAL`/`AWAITING_TECHNICAL_APPROVAL`), the
EXTERNAL_APPROVE/USER_OVERRIDE basis decision, and the mechanism-
independent user-only guard's second control (literal, specific
`user_confirmation` text).

WF4a-iii adds D-Commit-Provenance's trailer search generalized beyond
checkpoint trailers to the `Workflow-Plan-Approval`/
`Workflow-Technical-Approval` trailers `/approve-review` creates
(`discover_approval_commits`, sharing the same first-parent-ancestor
tie-break and genuine-ambiguity recovery as `discover_checkpoint_commits`
via one private helper), D-States' "Recomputation rule, stated per
stage" (`approval_review_content_id`/`approval_is_current`, resolving
`OPUS-R6-003`'s self-invalidation defect), the full `IMPLEMENTING` entry
condition D-Approval-Commits names (`implementing_entry_reachable`:
ancestry to the discovered plan-approval commit, plus freshness), the
"no protected path is dirty" gate condition's actual Git-derived boolean
(`any_protected_path_dirty`, consumed by WF4a-ii's own
`technical_approval_gate_reachable`), and WFR-06's post-approval-commit
manifest-match verification (`verify_post_approval_manifest_match`).
WF4a-iv adds the two-stage local-then-manual-external plan-review
protocol's own ledger writers (D-Plan-Review-Stages): `record_local_plan_review`/
`record_manual_plan_review` implement the six-row verdict/state transition
table (`/review-plan`'s and `/record-manual-plan-review`'s sole state
write set, resolving `GPT-R12-001/002/003`), each guarded by its own
precondition validator (`WrongGoverningVersionForPlanReviewStageError`,
`WrongPhaseForPlanReviewStageError`, `StaleReviewContentIdError` (hard),
`WrongReviewerRoleError`, `MissingLocalApprovalForManualStageError`,
`DuplicateManualStageIngestionError`); `check_manual_stage_bundle_id_advisory`
implements the manual stage's advisory-only (never blocking) `bundle_id`
mismatch warning (`OPUS-R14-005`); `transition_to_awaiting_local_plan_review`
implements `/apply-plan-review`'s `"2.1"`-only revised exit step (D-Plan-
Review-Stages, resolves `GPT-R11-003`/`-007`) -- the sole writer that
re-enters `AWAITING_LOCAL_PLAN_REVIEW` after an accepted plan edit, at
either stage's `REVISE` origin, since the recomputation rule already makes
a prior stage's ledger entry read as stale the moment the edit changes
`review_content_id` (no explicit ledger clear needed). Still not this
checkpoint's concern: `/approve-review`'s own commit-creation step (the
Claude session running `git commit` per `.claude/commands/approve-review.md`,
never a Python-side commit writer, consistent with this module's decide-
and-validate-only design).

Still out of scope here (owned by later checkpoints named in the plan's
own checkpoint table): `WORKTREE_IDENTITY.json`'s writer (WF2).

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
from typing import Mapping

import workflow_fingerprint as fingerprint
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

# D2's unified plan_approval/technical_approval record shape.
APPROVAL_STATUSES = frozenset({"CURRENT", "STALE"})
APPROVAL_BASES = frozenset({"EXTERNAL_APPROVE", "USER_OVERRIDE", "LEGACY_V1"})
# Narrowed per OPUS-R10-014: no_content_id removed -- the only basis that
# uses waivers (LEGACY_V1) always backfills a real content ID at import
# (D-Legacy), so no reachable state can ever emit it.
WAIVED_GUARANTEES = frozenset({"no_bundle_id", "no_telemetry"})
# D2's mechanism-independent guard names "plan"/"implementation" explicitly
# for /approve-review; "acceptance" extends the identical mechanism to
# /accept-milestone's AWAITING_USER_ACCEPTANCE gate, which the same guard
# sentence names in the same breath without itself enumerating a third
# stage keyword (a real gap in the plan text, resolved here rather than
# left unimplemented -- flagged to the user in this checkpoint's report).
APPROVAL_STAGES = frozenset({"plan", "implementation", "acceptance"})

# D-Plan-Review-Stages' verdict/state transition table: the only three
# verdicts either `/review-plan` or `/record-manual-plan-review` ever
# ingest from REVIEW_FEEDBACK.md's `Status:` field.
PLAN_REVIEW_VERDICTS = frozenset({"APPROVE", "REVISE", "BLOCK"})

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


class AmbiguousApprovalTrailerError(Exception):
    """Raised when more than one commit in range carries the same
    (`Workflow-Plan-Approval` or `Workflow-Technical-Approval`,
    `Workflow-Work-Item`) trailer pair and the first-parent-ancestor
    tie-break does not resolve to exactly one -- the same genuine-
    ambiguity recovery `AmbiguousCheckpointTrailerError` names, extended
    by WF4a-iii to the approval trailers (resolves `OPUS-R6-022`,
    missing-test item 39)."""


class PostApprovalManifestMismatchError(Exception):
    """Raised when a just-created approval commit's own committed content
    does not recompute to the `review_content_id` its approval record
    claims -- WFR-06's post-commit parity check, run once immediately
    after `/approve-review` creates the commit, never trusted on the
    record's word alone."""


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


class UnknownPlanReviewVerdictError(Exception):
    """Raised when a verdict passed to `record_local_plan_review`/
    `record_manual_plan_review` is not one of `PLAN_REVIEW_VERDICTS`."""


class WrongGoverningVersionForPlanReviewStageError(Exception):
    """Raised when `/review-plan` or `/record-manual-plan-review` is
    invoked against a work item whose `governing_workflow_version` is not
    `"2.1"` -- v1 items have no two-stage plan-review protocol to run
    (D-Plan-Review-Stages, missing-test item 90's v2.1-side refusal)."""


class WrongPhaseForPlanReviewStageError(Exception):
    """Raised when `/review-plan` is invoked outside `phase ==
    AWAITING_LOCAL_PLAN_REVIEW`, or `/record-manual-plan-review` outside
    `phase == AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` -- covers "already
    completed this round" (the phase has already moved on) and a local
    `REVISE`'s work item never reaching the manual stage (WFR-41)."""


class StaleReviewContentIdError(Exception):
    """Raised when the bundle/feedback's `review_content_id` does not
    match the freshly recomputed current one -- hard, blocks ingestion at
    either stage (D-Plan-Review-Stages transition table; distinct from the
    manual stage's advisory-only `bundle_id` check, `OPUS-R14-005`)."""


class WrongReviewerRoleError(Exception):
    """Raised when `REVIEW_FEEDBACK.md`'s declared `Reviewer role:` does
    not match the stage being ingested (e.g. a local-role or unlabeled
    file handed to `/record-manual-plan-review`, or vice versa)."""


class MissingLocalApprovalForManualStageError(Exception):
    """Raised when `/record-manual-plan-review` is asked to ingest an
    `APPROVE` while no current `local_model_plan_review` `APPROVE` is
    recorded for the same `review_content_id` -- a restated invariant,
    since entry to `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` already requires
    it; defends against a corrupted or hand-edited state file."""


class DuplicateManualStageIngestionError(Exception):
    """Raised when a `manual_external_plan_review` stage is already
    recorded against the current `review_content_id` -- rejects duplicate
    ingestion (a second invocation after a completed `APPROVE`/`REVISE`
    normally fails the phase precondition first; this only fires for a
    hand-edited or race-condition state, GPT-R12-002/-003)."""


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


class InvalidApprovalRecordError(Exception):
    """Raised when a `plan_approval`/`technical_approval` record does not
    match D2's shape, including the plan-stage's permanently-null
    `reviewed_content_commit` rule (GPT-R9-006) and the `waived_guarantees`
    controlled vocabulary (OPUS-R6-025/OPUS-R10-014)."""


class BlockCannotApproveError(Exception):
    """Raised when the most recently reviewed round's status is `BLOCK` --
    D-States: `BLOCK` never reaches either approval basis by any path."""


class UserConfirmationRejectedError(Exception):
    """Raised when literal `user_confirmation` text is missing, empty, or
    does not name the exact `work_item_id` and stage being approved
    (resolves OPUS-R6-010, corrected by GPT-R9-008/OPUS-R10-010/GPT-R11-004)."""


class LegacyReconciliationError(Exception):
    """D-Legacy's branch-reconciliation precondition failed: either the
    reviewed legacy commit is not a reachable ancestor of head, or the
    reachable `docs/ACTIVE_MILESTONE.md` content does not contain the
    caller-expected substring. Re-checked at both import (WF-M8a) and
    adoption (WF-M8b) time -- never trusted once and cached."""


class LegacyImportAlreadyExistsError(Exception):
    """Raised when D-Legacy's import step is invoked for a `work_item_id`
    that already has an entry -- import is a one-time act, never a silent
    re-import or resume (unlike `route_work_item`'s ordinary
    create-or-resume semantics)."""


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


def _discover_trailer_commits(
    repo_root: Path, trailer_key: str, work_item_id: str, base_commit: str,
    head: str, *, ambiguous_error_cls: type[Exception],
) -> dict[str, str]:
    """Generic D-Commit-Provenance search shared by checkpoint- and
    approval-trailer discovery (WF4a-iii generalizes the WF1a checkpoint-
    only search): every commit reachable in `base_commit..head` carrying
    an exact `trailer_key: <value>` + `Workflow-Work-Item: <work_item_id>`
    trailer pair, requiring exactly one match per trailer value after the
    stated tie-break (prefer a first-parent ancestor of `head`). Returns
    `{trailer_value: commit_sha}`. Genuine ambiguity (more than one
    first-parent-ancestor match) raises `ambiguous_error_cls` rather than
    silently picking (resolves OPUS-R6-022; missing-test items 39, 50,
    62, 64)."""
    out = _run(["git", "log", "--format=%H", f"{base_commit}..{head}"], cwd=repo_root)
    commits = [line for line in out.splitlines() if line]

    matches_by_value: dict[str, list[str]] = {}
    for commit in commits:
        trailers = _commit_trailers(repo_root, commit)
        if trailers.get("Workflow-Work-Item") != work_item_id:
            continue
        value = trailers.get(trailer_key)
        if not value:
            continue
        matches_by_value.setdefault(value, []).append(commit)

    first_parent = set(_first_parent_commits_ordered(repo_root, head))
    resolved: dict[str, str] = {}
    for value, candidates in matches_by_value.items():
        if len(candidates) == 1:
            resolved[value] = candidates[0]
            continue
        tie_broken = [c for c in candidates if c in first_parent]
        if len(tie_broken) == 1:
            resolved[value] = tie_broken[0]
        else:
            raise ambiguous_error_cls(
                f"{trailer_key} {value!r} for work item {work_item_id!r} has "
                f"{len(candidates)} trailer matches in {base_commit}..{head}, and "
                f"{len(tie_broken)} remain after the first-parent-ancestor "
                f"tie-break (candidates: {candidates}); needs a Workflow-Supersedes "
                f"trailer or an explicit WORKFLOW_STATE.json annotation"
            )
    return resolved


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
    return _discover_trailer_commits(
        repo_root, "Workflow-Checkpoint", work_item_id, base_commit, head,
        ambiguous_error_cls=AmbiguousCheckpointTrailerError,
    )


def discover_approval_commits(
    repo_root: Path, trailer_key: str, work_item_id: str, base_commit: str, head: str = "HEAD",
) -> dict[str, str]:
    """The approval-trailer counterpart of `discover_checkpoint_commits`
    (WF4a-iii, resolves `OPUS-R6-022` for approval commits too): searches
    for `trailer_key` (`"Workflow-Plan-Approval"` or
    `"Workflow-Technical-Approval"`) instead of `"Workflow-Checkpoint"`.
    Returns `{review_content_id: commit_sha}` -- a work item can carry more
    than one approval commit per stage over its lifetime (an approval that
    later staled and was re-approved after a plan revision), each keyed by
    the distinct `review_content_id` it approved."""
    return _discover_trailer_commits(
        repo_root, trailer_key, work_item_id, base_commit, head,
        ambiguous_error_cls=AmbiguousApprovalTrailerError,
    )


def discover_plan_approval_commit(
    repo_root: Path, work_item_id: str, review_content_id: str, base_commit: str, head: str = "HEAD",
) -> str | None:
    """The specific plan-approval commit carrying
    `Workflow-Plan-Approval: <review_content_id>` for this work item, or
    `None` if none is reachable."""
    matches = discover_approval_commits(repo_root, "Workflow-Plan-Approval", work_item_id, base_commit, head)
    return matches.get(review_content_id)


def discover_technical_approval_commit(
    repo_root: Path, work_item_id: str, review_content_id: str, base_commit: str, head: str = "HEAD",
) -> str | None:
    """The specific technical-approval commit carrying
    `Workflow-Technical-Approval: <review_content_id>` for this work item,
    or `None` if none is reachable."""
    matches = discover_approval_commits(repo_root, "Workflow-Technical-Approval", work_item_id, base_commit, head)
    return matches.get(review_content_id)


def _is_ancestor(repo_root: Path, ancestor: str, descendant: str) -> bool:
    """Whether `ancestor` is `descendant` itself or a (non-first-parent-
    restricted) ancestor of it -- used by `implementing_entry_reachable`
    to confirm current HEAD is the plan-approval commit or a checkpoint-
    commit descendant of it (D-Approval-Commits)."""
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, descendant],
        cwd=repo_root, capture_output=True,
    )
    return result.returncode == 0


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
# WF4a-iii: D-States' "Recomputation rule, stated per stage" and
# D-Approval-Commits' IMPLEMENTING entry condition (resolves OPUS-R6-003;
# missing-test items 9, 10, 11)
# ---------------------------------------------------------------------------


def approval_review_content_id(
    repo_root: Path, *, stage: str, base_commit: str, work_item_type: str,
    work_item_id: str, plan_revision: int | None = None, head: str = "HEAD",
    protected: frozenset[str] = fingerprint.PLAN_STAGE_PROTECTED,
    excluded_paths: Mapping[str, str] = fingerprint.PLAN_STAGE_EXCLUDED_PATHS,
    excluded_prefixes: Mapping[str, str] = fingerprint.PLAN_STAGE_EXCLUDED_PREFIXES,
    artifacts_path: Path = fingerprint.DEFAULT_ARTIFACTS_PATH,
) -> str:
    """Recomputes the current `review_content_id` at `head` for the given
    approval `stage`, commit-source (never worktree-source -- this checks
    committed content, exactly what a fresh session sees, never
    uncommitted local edits). D-States' "Recomputation rule, stated per
    stage": `stage="plan"` hashes only the plan-stage projection
    (protected plan/audit/decisions documents), invariant under checkpoint
    commits because those never touch those documents; `stage=
    "implementation"` hashes the implementation-stage projection (source/
    test/build/migration/workflow-command files, WF4a-i's scope), loaded
    from the tracked artifact-declarations file rather than adapted from
    the plan-stage sets (`OPUS-R20-003`: the two stages' sets are
    near-inverses, never derived from one another). `plan_revision` is
    required for `stage="plan"` and ignored for `stage="implementation"`
    -- `implementation_revision` is never part of either projection
    (D-States: "meaningless before implementation starts and mutating
    during it")."""
    if stage == "plan":
        if plan_revision is None:
            raise ValueError("plan_revision is required for stage='plan'")
        digest, _ = fingerprint.compute_review_content_id_plan_stage_at_commit(
            repo_root, base_commit, head, work_item_type, work_item_id, plan_revision,
            protected, excluded_paths, excluded_prefixes,
        )
        return digest
    if stage == "implementation":
        impl_protected_paths, impl_protected_prefixes, impl_excluded_paths, impl_excluded_prefixes = (
            fingerprint.load_implementation_stage_classification(repo_root, artifacts_path)
        )
        digest, _ = fingerprint.compute_review_content_id_implementation_stage_at_commit(
            repo_root, base_commit, head, work_item_type, work_item_id,
            impl_protected_paths, impl_protected_prefixes, impl_excluded_paths, impl_excluded_prefixes,
        )
        return digest
    raise InvalidApprovalRecordError(f"unknown approval stage: {stage!r}")


def approval_is_current(
    repo_root: Path, work_item: dict, *, stage: str, base_commit: str, head: str = "HEAD",
) -> bool:
    """D2/D-States durability check for either approval record: `status ==
    CURRENT` plus a freshly recomputed, stage-appropriate
    `review_content_id` matching `approved_review_content_id` exactly.
    `False` whenever no record exists yet or its status is already
    `STALE` -- this function only ever detects fresh staleness, it never
    clears a status a caller previously set. Missing-test item 10: a
    plan-document edit after a checkpoint commit changes the plan-stage
    projection, so this returns `False`. Missing-test item 11: an
    `implementation_revision` bump touches no hashed field of either
    projection, so this keeps returning `True`."""
    record_field = "plan_approval" if stage == "plan" else "technical_approval"
    record = work_item.get(record_field)
    if record is None or record.get("status") != "CURRENT":
        return False
    current_id = approval_review_content_id(
        repo_root, stage=stage, base_commit=base_commit,
        work_item_type=work_item["work_item_type"], work_item_id=work_item["work_item_id"],
        plan_revision=work_item.get("plan_revision"), head=head,
    )
    return current_id == record["approved_review_content_id"]


def implementing_entry_reachable(
    repo_root: Path, work_item: dict, base_commit: str, head: str = "HEAD",
) -> bool:
    """D-Approval-Commits' `IMPLEMENTING` entry condition, in full:
    current HEAD (derived live) is the plan-approval commit or a
    checkpoint-commit descendant of it, `plan_approval.status ==
    CURRENT`, and a freshly recomputed plan-stage `review_content_id`
    matches `plan_approval.approved_review_content_id` (missing-test item
    9: true at checkpoints 1, 2, and N in a fresh session, since the
    plan-approval commit stays a first-ancestor-chain ancestor of every
    later checkpoint commit and the plan-stage projection stays
    unchanged by them)."""
    plan_approval = work_item.get("plan_approval")
    if plan_approval is None or plan_approval.get("status") != "CURRENT":
        return False
    approval_commit = discover_plan_approval_commit(
        repo_root, work_item["work_item_id"],
        plan_approval["approved_review_content_id"], base_commit, head,
    )
    if approval_commit is None or not _is_ancestor(repo_root, approval_commit, head):
        return False
    return approval_is_current(repo_root, work_item, stage="plan", base_commit=base_commit, head=head)


def verify_post_approval_manifest_match(
    repo_root: Path, work_item: dict, *, stage: str, base_commit: str, commit: str,
) -> None:
    """WFR-06: "the committed plan exactly matches the reviewed working-
    tree content after the plan-approval commit", generalized to either
    approval stage. Recomputes `review_content_id` from `commit`'s own
    committed content and asserts it equals the approval record's
    `approved_review_content_id` exactly -- run once, immediately after
    `/approve-review` creates the commit, never trusted on the record's
    word alone."""
    record_field = "plan_approval" if stage == "plan" else "technical_approval"
    record = work_item[record_field]
    expected = record["approved_review_content_id"]
    actual = approval_review_content_id(
        repo_root, stage=stage, base_commit=base_commit,
        work_item_type=work_item["work_item_type"], work_item_id=work_item["work_item_id"],
        plan_revision=work_item.get("plan_revision"), head=commit,
    )
    if actual != expected:
        raise PostApprovalManifestMismatchError(
            f"{work_item['work_item_id']}/{stage}: commit {commit} recomputes to "
            f"{actual!r}, expected {expected!r} (approved_review_content_id)"
        )


# ---------------------------------------------------------------------------
# WF4a-iii: D-States' "no protected path is dirty" gate condition
# ---------------------------------------------------------------------------


def _dirty_paths(repo_root: Path) -> set[str]:
    """Paths with uncommitted changes relative to HEAD -- staged,
    unstaged, and untracked-but-not-ignored -- used by the
    `AWAITING_TECHNICAL_APPROVAL` entry condition's "no protected path is
    dirty" clause (D3). Distinct from `workflow_fingerprint`'s own
    `base_commit..worktree` diff, which measures change since plan
    approval, not uncommitted state."""
    changed = _run(["git", "diff", "--name-only", "-z", "HEAD"], cwd=repo_root)
    untracked = _run(["git", "ls-files", "--others", "--exclude-standard", "-z"], cwd=repo_root)
    paths = {p for p in changed.split("\x00") if p}
    paths |= {p for p in untracked.split("\x00") if p}
    return paths


def any_protected_path_dirty(
    repo_root: Path,
    protected_paths: Mapping[str, str],
    protected_prefixes: Mapping[str, str],
    excluded_paths: Mapping[str, str],
    excluded_prefixes: Mapping[str, str],
) -> bool:
    """`AWAITING_TECHNICAL_APPROVAL`'s entry condition (D-States): "no
    protected path is dirty", `WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json`
    dirtiness never blocking this by construction -- both are declared
    `excluded_paths` in the implementation-stage classification, never
    `protected` (missing-test item 16). Fails closed (`UnclassifiedPathError`)
    on a dirty path neither set names, the same discipline
    `classify_path_implementation_stage` already applies to the
    `base..worktree` diff."""
    for path in sorted(_dirty_paths(repo_root)):
        classification = fingerprint.classify_path_implementation_stage(
            path, protected_paths, protected_prefixes, excluded_paths, excluded_prefixes
        )
        if classification == "protected":
            return True
    return False


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
    plan_path: str, registry_path: str | None, governing_workflow_version: str,
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
# D-States: non-circular gate-reachability for AWAITING_PLAN_APPROVAL /
# AWAITING_TECHNICAL_APPROVAL (never reads plan_approval/technical_approval
# themselves -- resolves OPUS-R6-004/-011)
# ---------------------------------------------------------------------------


def approval_gate_reachable(latest_round_status: str) -> bool:
    """Shared entry condition for both new gates: reachable whenever the
    most recently reviewed round's status is `REVISE` or `APPROVE` (never
    `BLOCK`), regardless of whether any approval record exists yet -- a
    `REVISE` round with zero blocking findings left reaches the gate
    exactly as readily as an `APPROVE` round (missing-test items 12/27)."""
    return latest_round_status in ("REVISE", "APPROVE")


def technical_approval_gate_reachable(
    *, latest_round_status: str, protected_path_dirty: bool,
    head_matches_reviewed_implementation_head: bool,
) -> bool:
    """`AWAITING_TECHNICAL_APPROVAL`'s entry condition: the shared
    reachability rule above, plus "no protected path is dirty" (D3;
    `WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json` dirtiness never blocks
    this) and current committed content matching
    `reviewed_implementation_head` exactly."""
    return (
        approval_gate_reachable(latest_round_status)
        and not protected_path_dirty
        and head_matches_reviewed_implementation_head
    )


def plan_approval_gate_reachable(
    *, latest_round_status: str, governing_workflow_version: str,
    plan_review_stages: dict | None, current_review_content_id: str,
) -> bool:
    """`AWAITING_PLAN_APPROVAL`'s entry condition: the shared reachability
    rule above, plus -- for a `governing_workflow_version: "2.1"` work item
    only (resolves GPT-R11-001/-003) -- the `plan_review_stages` ledger
    must record both `local_model_plan_review` and
    `manual_external_plan_review` completed (`verdict: APPROVE`) against
    the *current* plan-stage `review_content_id`. A `"1"` item's condition
    is exactly the shared rule, unchanged."""
    if not approval_gate_reachable(latest_round_status):
        return False
    if governing_workflow_version != "2.1":
        return True
    if plan_review_stages is None:
        return False
    if plan_review_stages.get("review_content_id") != current_review_content_id:
        return False
    local = plan_review_stages.get("local_model_plan_review")
    manual = plan_review_stages.get("manual_external_plan_review")
    return (
        local is not None and local.get("verdict") == "APPROVE"
        and manual is not None and manual.get("verdict") == "APPROVE"
    )


# ---------------------------------------------------------------------------
# D2: unified plan_approval/technical_approval record -- shape, basis
# decision, and the mechanism-independent user-only guard's second control
# ---------------------------------------------------------------------------


def validate_user_confirmation(text: str, *, work_item_id: str, stage: str) -> None:
    """Second of the two named, real user-only-guard mechanisms (D2): the
    text must be non-empty and must literally name both the exact
    `work_item_id` and the exact `stage` being approved. A trust boundary,
    not a cryptographic guarantee (D2) -- rejects an empty/generic string
    and a confirmation naming the wrong item/stage, but a legitimate literal
    repeat across two different, genuine approvals is accepted (deliberately
    not a novelty check, per OPUS-R10-010/GPT-R11-004)."""
    if stage not in APPROVAL_STAGES:
        raise InvalidApprovalRecordError(f"unknown approval stage: {stage!r}")
    if not text or not text.strip():
        raise UserConfirmationRejectedError(
            f"user_confirmation is empty -- must name work_item_id {work_item_id!r} "
            f"and stage {stage!r} explicitly"
        )
    if work_item_id not in text:
        raise UserConfirmationRejectedError(
            f"user_confirmation does not name work_item_id {work_item_id!r}: {text!r}"
        )
    if stage not in text:
        raise UserConfirmationRejectedError(
            f"user_confirmation does not name stage {stage!r}: {text!r}"
        )


def resolve_approval_basis(
    *, latest_round_status: str, feedback_bundle_id: str | None, current_bundle_id: str,
    user_confirmation: str | None, work_item_id: str, stage: str,
) -> str:
    """D2's basis decision, run *inside* the approval gate, never as an
    entry precondition (OPUS-R6-004): `EXTERNAL_APPROVE` only when the
    latest reviewed round's status is exactly `APPROVE` and its recorded
    `bundle_id` equals the just-recomputed current bundle_id exactly;
    otherwise `USER_OVERRIDE`. `BLOCK` never reaches either basis (the
    round's findings are, by definition, not resolved). The mechanism-
    independent guard's `user_confirmation` specificity check applies to
    every write regardless of basis (D2's record shape: "every basis") --
    `EXTERNAL_APPROVE` does not additionally prompt for override
    *justification* text (missing-test item 13), but still requires the
    same named-item/stage confirmation already gathered as part of
    invoking `/approve-review` in the current turn."""
    if latest_round_status == "BLOCK":
        raise BlockCannotApproveError(
            f"{work_item_id}/{stage}: latest reviewed round status is BLOCK -- "
            f"neither EXTERNAL_APPROVE nor USER_OVERRIDE is reachable"
        )
    validate_user_confirmation(user_confirmation or "", work_item_id=work_item_id, stage=stage)
    if (
        latest_round_status == "APPROVE"
        and feedback_bundle_id is not None
        and feedback_bundle_id == current_bundle_id
    ):
        return "EXTERNAL_APPROVE"
    return "USER_OVERRIDE"


def build_approval_record(
    *, basis: str, stage: str, user_confirmation: str, now: str,
    reviewed_bundle_id: str | None = None,
    approved_review_content_id: str | None = None,
    review_content_manifest: object | None = None,
    reviewed_content_commit: str | None = None,
    legacy_evidence: object | None = None,
    waived_guarantees: list[str] | None = None,
) -> dict:
    """Builds a D2-shaped `plan_approval`/`technical_approval` record and
    validates it before returning -- `reviewed_content_commit` is the
    caller's concern to omit for the plan stage (permanently null,
    GPT-R9-006) and to supply for the implementation stage."""
    record = {
        "status": "CURRENT",
        "basis": basis,
        "reviewed_bundle_id": reviewed_bundle_id,
        "approved_review_content_id": approved_review_content_id,
        "review_content_manifest": review_content_manifest,
        "reviewed_content_commit": reviewed_content_commit,
        "legacy_evidence": legacy_evidence,
        "user_confirmation": user_confirmation,
        "waived_guarantees": list(waived_guarantees or []),
        "recorded_at": now,
    }
    validate_approval_record(record, stage=stage)
    return record


def validate_approval_record(record: dict, *, stage: str) -> None:
    """D2's shape check: known `status`/`basis`, the plan-stage's
    permanently-null `reviewed_content_commit` rule (GPT-R9-006), a
    non-`LEGACY_V1` basis requiring `reviewed_bundle_id`/
    `approved_review_content_id`/`review_content_manifest`, a non-empty
    `user_confirmation` ("every basis"), and the `waived_guarantees`
    controlled vocabulary (OPUS-R6-025, narrowed OPUS-R10-014)."""
    if stage not in APPROVAL_STAGES:
        raise InvalidApprovalRecordError(f"unknown approval stage: {stage!r}")
    if record.get("status") not in APPROVAL_STATUSES:
        raise InvalidApprovalRecordError(f"unknown approval status: {record.get('status')!r}")
    basis = record.get("basis")
    if basis not in APPROVAL_BASES:
        raise InvalidApprovalRecordError(f"unknown approval basis: {basis!r}")
    if stage == "plan" and record.get("reviewed_content_commit") is not None:
        raise InvalidApprovalRecordError(
            "a plan-stage approval record must never set reviewed_content_commit "
            "-- it stays permanently null (GPT-R9-006)"
        )
    if basis != "LEGACY_V1":
        for field in ("reviewed_bundle_id", "approved_review_content_id", "review_content_manifest"):
            if record.get(field) is None:
                raise InvalidApprovalRecordError(
                    f"a {basis} approval record must set {field} (only LEGACY_V1 may leave it null)"
                )
    if not record.get("user_confirmation"):
        raise InvalidApprovalRecordError("approval record must set a non-empty user_confirmation")
    for guarantee in record.get("waived_guarantees") or []:
        if guarantee not in WAIVED_GUARANTEES:
            raise InvalidApprovalRecordError(f"unknown waived_guarantees entry: {guarantee!r}")


def apply_plan_approval(state: dict, work_item_id: str, record: dict, now: str) -> dict:
    """`/approve-review plan`'s state-write step (D-Approval-Commits): sets
    `plan_approval` and transitions the work item to `IMPLEMENTING`. Commit
    creation itself is `/approve-review`'s own concern (D-Approval-Commits),
    not this function's."""
    validate_approval_record(record, stage="plan")
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    work_item["plan_approval"] = record
    work_item["phase"] = "IMPLEMENTING"
    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    return new_state


def apply_technical_approval(state: dict, work_item_id: str, record: dict, now: str) -> dict:
    """`/approve-review implementation`'s state-write step: sets
    `technical_approval` and transitions the work item to
    `AWAITING_FUNCTIONAL_REVIEW`."""
    validate_approval_record(record, stage="implementation")
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    work_item["technical_approval"] = record
    work_item["phase"] = "AWAITING_FUNCTIONAL_REVIEW"
    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    return new_state


# ---------------------------------------------------------------------------
# WF4a-iv: D-Plan-Review-Stages -- two-stage local-then-manual-external
# plan-review protocol's ledger writers and verdict/state transition table
# ---------------------------------------------------------------------------


def _require_v2_1_plan_review(work_item: dict) -> None:
    if work_item.get("governing_workflow_version") != "2.1":
        raise WrongGoverningVersionForPlanReviewStageError(
            f"{work_item['work_item_id']}: governing_workflow_version is "
            f"{work_item.get('governing_workflow_version')!r}, not \"2.1\" -- "
            f"the two-stage plan-review protocol only applies to \"2.1\" work items"
        )


def validate_local_plan_review_preconditions(work_item: dict) -> None:
    """`/review-plan`'s resolution/phase preconditions (D-Plan-Review-Stages):
    the item must be `"2.1"`-governed and currently at
    `AWAITING_LOCAL_PLAN_REVIEW`. Bundle/manifest staleness is a separate,
    generic check (D-Bundle-Manifest, reused unchanged) run by the command
    itself before this, not duplicated here."""
    _require_v2_1_plan_review(work_item)
    if work_item.get("phase") != "AWAITING_LOCAL_PLAN_REVIEW":
        raise WrongPhaseForPlanReviewStageError(
            f"{work_item['work_item_id']}: phase is {work_item.get('phase')!r}, "
            f"not \"AWAITING_LOCAL_PLAN_REVIEW\" -- /review-plan refuses rather "
            f"than silently re-running (e.g. already completed this round)"
        )


def record_local_plan_review(
    state: dict, work_item_id: str, *, verdict: str, bundle_id: str,
    review_content_id: str, round: int, now: str,
) -> dict:
    """`/review-plan`'s sole state write set (D-Plan-Review-Stages transition
    table, resolves `GPT-R12-001`/`-002`):

    - `APPROVE`: records the completed `local_model_plan_review` stage
      against `review_content_id` (starting a fresh ledger scoped to this
      content id -- any prior `manual_external_plan_review` entry
      necessarily belonged to a different, now-stale content id under the
      correct flow, so it is not carried forward) and transitions to
      `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`.
    - `REVISE`: no ledger write; transitions to `REVISING_PLAN`. Can never
      reach `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` (missing-test item 94).
    - `BLOCK`: no ledger write, no phase transition -- a true no-op
      (missing-test item 95); the returned state is unchanged.
    """
    if verdict not in PLAN_REVIEW_VERDICTS:
        raise UnknownPlanReviewVerdictError(f"unknown plan-review verdict: {verdict!r}")
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    validate_local_plan_review_preconditions(work_item)

    if verdict == "APPROVE":
        work_item["plan_review_stages"] = {
            "review_content_id": review_content_id,
            "local_model_plan_review": {
                "bundle_id": bundle_id, "verdict": "APPROVE",
                "round": round, "completed_at": now,
            },
            "manual_external_plan_review": None,
        }
        work_item["phase"] = "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW"
    elif verdict == "REVISE":
        work_item["phase"] = "REVISING_PLAN"
    else:  # BLOCK
        return state

    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    _validate_plan_review_stages(work_item)
    return new_state


def validate_manual_plan_review_preconditions(
    work_item: dict, *, current_review_content_id: str, feedback_role: str,
    feedback_review_content_id: str,
) -> None:
    """`/record-manual-plan-review`'s resolution/phase/role/staleness/
    invariant preconditions (D-Plan-Review-Stages transition table,
    resolves `GPT-R12-002`/`-003`), checked before writing anything:

    - `"2.1"`-governed and currently at `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`.
    - the feedback's declared role is exactly `manual_external_plan_review`
      (rejects a local-role or unlabeled feedback file).
    - the feedback's `review_content_id` matches the current recomputed
      value -- **hard**, blocks ingestion (distinct from the advisory-only
      `bundle_id` check, `check_manual_stage_bundle_id_advisory`, never
      performed here).
    - a current `local_model_plan_review` `APPROVE` is recorded for the
      same `review_content_id` (restated invariant -- entry to this phase
      already required it; defends against a corrupted/hand-edited state).
    - no `manual_external_plan_review` stage is already recorded against
      the current `review_content_id` (rejects duplicate ingestion).
    """
    _require_v2_1_plan_review(work_item)
    if work_item.get("phase") != "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW":
        raise WrongPhaseForPlanReviewStageError(
            f"{work_item['work_item_id']}: phase is {work_item.get('phase')!r}, "
            f"not \"AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW\""
        )
    if feedback_role != "manual_external_plan_review":
        raise WrongReviewerRoleError(
            f"REVIEW_FEEDBACK.md declares Reviewer role: {feedback_role!r}, "
            f"expected \"manual_external_plan_review\""
        )
    if feedback_review_content_id != current_review_content_id:
        raise StaleReviewContentIdError(
            f"feedback review_content_id {feedback_review_content_id!r} does not "
            f"match the current recomputed value {current_review_content_id!r} -- "
            f"this is a hard block, unlike the manual stage's advisory bundle_id check"
        )
    stages = work_item.get("plan_review_stages") or {}
    local = stages.get("local_model_plan_review")
    if (
        stages.get("review_content_id") != current_review_content_id
        or local is None or local.get("verdict") != "APPROVE"
    ):
        raise MissingLocalApprovalForManualStageError(
            f"{work_item['work_item_id']}: no current local_model_plan_review "
            f"APPROVE recorded for review_content_id {current_review_content_id!r}"
        )
    if stages.get("manual_external_plan_review") is not None:
        raise DuplicateManualStageIngestionError(
            f"{work_item['work_item_id']}: manual_external_plan_review is already "
            f"recorded against review_content_id {current_review_content_id!r}"
        )


def check_manual_stage_bundle_id_advisory(
    feedback_bundle_id: str, current_bundle_id: str,
) -> str | None:
    """The manual stage's `bundle_id` check: advisory only, never blocking
    (resolves `OPUS-R14-005`, corrects the plan-stage-equality symmetry a
    naive reading of `/approve-review`'s own hard `bundle_id` check might
    suggest) -- a wrapper-only bundle regeneration between upload and
    paste (new `bundle_id`, unchanged `review_content_id`) must not
    invalidate the manual stage (`GPT-R11-006`). Returns a warning string
    naming both values on a mismatch, or `None` when they match."""
    if feedback_bundle_id != current_bundle_id:
        return (
            f"bundle_id mismatch (advisory only, does not block ingestion): "
            f"feedback bundle_id={feedback_bundle_id!r}, current recomputed "
            f"bundle_id={current_bundle_id!r}"
        )
    return None


def record_manual_plan_review(
    state: dict, work_item_id: str, *, verdict: str, bundle_id: str, round: int,
    now: str, current_review_content_id: str, feedback_role: str,
    feedback_review_content_id: str,
) -> dict:
    """`/record-manual-plan-review`'s sole state write set (D-Plan-Review-
    Stages transition table, resolves `GPT-R12-002`/`-003`):

    - `APPROVE`: records the completed `manual_external_plan_review` stage
      -- including the feedback's own `bundle_id` **verbatim**, regardless
      of whether it matches the current recomputed one, so the ledger
      records what the reviewer actually saw (`OPUS-R14-005`, missing-test
      item 113) -- and transitions to `AWAITING_PLAN_APPROVAL`.
    - `REVISE`: no ledger write; transitions to `REVISING_PLAN`.
    - `BLOCK`: no ledger write, no phase transition -- a true no-op
      (missing-test item 99); the returned state is unchanged.
    """
    if verdict not in PLAN_REVIEW_VERDICTS:
        raise UnknownPlanReviewVerdictError(f"unknown plan-review verdict: {verdict!r}")
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    validate_manual_plan_review_preconditions(
        work_item, current_review_content_id=current_review_content_id,
        feedback_role=feedback_role, feedback_review_content_id=feedback_review_content_id,
    )

    if verdict == "APPROVE":
        work_item["plan_review_stages"]["manual_external_plan_review"] = {
            "bundle_id": bundle_id, "verdict": "APPROVE",
            "round": round, "completed_at": now,
        }
        work_item["phase"] = "AWAITING_PLAN_APPROVAL"
    elif verdict == "REVISE":
        work_item["phase"] = "REVISING_PLAN"
    else:  # BLOCK
        return state

    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    _validate_plan_review_stages(work_item)
    return new_state


def transition_to_awaiting_local_plan_review(state: dict, work_item_id: str, now: str) -> dict:
    """`/apply-plan-review`'s `"2.1"`-only revised exit step (D-Plan-Review-
    Stages, resolves `GPT-R11-003`/`-007`): after every accepted plan edit,
    the work item transitions to `AWAITING_LOCAL_PLAN_REVIEW` -- never
    self-declaring plan readiness. The stale `plan_review_stages` ledger
    (if any) is left as-is, never explicitly cleared: its own
    `review_content_id` no longer matches the freshly recomputed one the
    moment the edit lands, so both stages already read as absent by the
    recomputation rule (`plan_approval_gate_reachable`). This is the sole
    path back to `AWAITING_LOCAL_PLAN_REVIEW`, whether the edit was driven
    by a local-model `REVISE` or a manual-external `REVISE` (missing-test
    item 89) -- no path re-enters manual-external review without a fresh
    local pass first."""
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    work_item["phase"] = "AWAITING_LOCAL_PLAN_REVIEW"
    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    return new_state


# ---------------------------------------------------------------------------
# WF-M8a: D-Legacy phase 1 -- Milestone 8 legacy import (dormant
# LEGACY_READY, resolves GPT-R9-005/OPUS-R6-006/OPUS-R6-009/OPUS-R10-011).
# Phase 2 (the adoption transition on /prepare-functional-review) is
# WF-M8b's own scope, not built here.
# ---------------------------------------------------------------------------


def verify_legacy_branch_reconciliation(
    repo_root: Path, *, reviewed_content_commit: str,
    required_active_milestone_substring: str, head: str = "HEAD",
    active_milestone_path: str = "docs/ACTIVE_MILESTONE.md",
) -> None:
    """D-Legacy's branch-reconciliation precondition, in full: (1) the
    user integrates the legacy branch into `main` themselves -- this
    function only ever verifies that already happened, it never performs
    the integration; (2) the reviewed legacy commit must be a real,
    reachable ancestor of `head`; (3) `active_milestone_path`'s content at
    `head` must contain the caller-supplied substring naming the expected
    integrated/accepted narrative -- a substring check, not exact-content
    equality, since the branch's own final doc state (e.g. a later
    acceptance/waiver update) can legitimately land in a commit after
    `reviewed_content_commit` itself. Raises `LegacyReconciliationError`,
    naming both values, on either failure -- never proceeds past a failed
    check. Re-checked at both import (WF-M8a) time and adoption (WF-M8b)
    time, never trusted from one to the other (a dormant item can sit for
    a long time between the two)."""
    if not _is_ancestor(repo_root, reviewed_content_commit, head):
        raise LegacyReconciliationError(
            f"reviewed_content_commit {reviewed_content_commit!r} is not a reachable "
            f"ancestor of {head!r} -- integrate the branch into main before "
            f"importing/adopting this legacy work item"
        )
    try:
        actual = _run(["git", "show", f"{head}:{active_milestone_path}"], repo_root)
    except subprocess.CalledProcessError as exc:
        raise LegacyReconciliationError(
            f"{active_milestone_path!r} is not readable at {head!r}: {exc}"
        ) from exc
    if required_active_milestone_substring not in actual:
        raise LegacyReconciliationError(
            f"{active_milestone_path!r} at {head!r} does not contain the expected "
            f"substring {required_active_milestone_substring!r}"
        )


def import_legacy_work_item(
    state: dict, *, work_item_id: str, plan_path: str, registry_path: str | None,
    base_commit: str, reviewed_content_commit: str, approved_review_content_id: str,
    legacy_evidence: object, user_confirmation: str, now: str,
) -> dict:
    """D-Legacy phase 1 (WF-M8a): creates a dormant `LEGACY_READY` entry
    (D3: non-terminal, addressable by id, not `active_work_item_id`) for a
    product milestone fully built and reviewed entirely under Workflow v1.
    `governing_workflow_version` is fixed to `"1"` (factually correct, and
    makes the later adoption transition an ordinary, auditable version
    change rather than a first-time assignment, resolving `OPUS-R10-011`).
    `active_work_item_id` is deliberately left untouched -- import is not
    activation, D-Legacy phase 2 (`WF-M8b`) owns that transition.

    Refuses a second import of the same id outright
    (`LegacyImportAlreadyExistsError`): unlike `route_work_item`'s
    ordinary create-or-resume semantics, importing an already-imported id
    is never a resume.

    The caller is responsible for having already run
    `verify_legacy_branch_reconciliation` and for computing
    `approved_review_content_id` via the implementation-stage projection
    (`approval_review_content_id(..., stage="implementation", ...)`,
    scoped to this work item's own `base_commit`..`reviewed_content_commit`
    and its own artifact-declarations file) -- this function only shapes
    and writes the resulting state, exactly like `apply_technical_approval`
    does for an ordinary approval."""
    validate_work_item_id(work_item_id)
    new_state = copy.deepcopy(state)
    work_items = new_state.setdefault("work_items", {})
    if work_item_id in work_items:
        raise LegacyImportAlreadyExistsError(
            f"{work_item_id!r} already exists -- legacy import is a one-time act, "
            f"never a re-import"
        )
    technical_approval = build_approval_record(
        basis="LEGACY_V1", stage="implementation", user_confirmation=user_confirmation, now=now,
        reviewed_bundle_id=None, approved_review_content_id=approved_review_content_id,
        review_content_manifest=None, reviewed_content_commit=reviewed_content_commit,
        legacy_evidence=legacy_evidence, waived_guarantees=["no_bundle_id", "no_telemetry"],
    )
    work_item = default_work_item(
        work_item_id=work_item_id, work_item_type="product", work_item_kind="product",
        plan_path=plan_path, registry_path=registry_path,
        governing_workflow_version="1", plan_revision=1, last_transition=now,
    )
    work_item["phase"] = "LEGACY_READY"
    work_item["technical_approval"] = technical_approval
    work_item["base_commit"] = base_commit
    work_items[work_item_id] = work_item
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
