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

WF2 adds D-Selection's deterministic four-rule checkpoint-selection
algorithm (`select_next_checkpoint`), the `IN_PROGRESS`/`COMPLETE` state
writers (`transition_checkpoint_in_progress`/`complete_checkpoint`,
implementing checkpoint-complete-vs-all-complete semantics), and D3's
worktree-scoped dirty-resume mechanics: `write_worktree_identity` (the
named writer for `.ai-review/runtime/WORKTREE_IDENTITY.json`, previously
schema-only) and its read-side counterpart `verify_dirty_resume_safety`.

WF4c adds D-Functional-Remediation's general functional-remediation cycle:
`mark_technical_approval_stale` (the bounded-code-change branch's
stale-before-edit write, persisted before the first source/test edit) and
`record_bundle_generation` (`reviewed_implementation_head`'s sole writer,
closing the loop `OPUS-R6-013` found -- nothing wrote this field before,
so `AWAITING_TECHNICAL_APPROVAL`'s entry condition was unreachable for any
real `"2.1"` item; wired into the bundle generator at exactly the
`"implementation"`/`"post-fix"` stage). `create_remediation_child_work_item`
implements the broad/multi-finding branch: a distinct child work item
(`"<parent>-remediation-<n>"`, `n` derived deterministically, never
caller-supplied) with its own registry/mapping/approval lifecycle,
routed through the ordinary commands from there, never mutating the
parent's own registry/mapping/completed-checkpoint history.
`incomplete_children`/`complete_work_item`'s extended check implement
"parent acceptance blocks on an incomplete child" (resolves `GPT-R9-016`).

Stdlib-only, mirroring `scripts/workflow_fingerprint.py`'s own
`docs/TECHNICAL_DECISIONS.md`-recorded constraint.

Run the hermetic suite: python3 scripts/workflow_state_test.py
Run the real-repository demonstration: python3 scripts/workflow_state_demo_test.py
"""

from __future__ import annotations

import copy
import hashlib
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


class NoCheckpointReadyError(Exception):
    """D-Selection rule 4: raised when no checkpoint is `COMPLETE` yet none
    is selectable either -- every remaining checkpoint has an incomplete
    dependency. Names the blocked checkpoint and its unmet dependencies
    rather than ever silently idling."""


class WorktreeIdentityMissingError(Exception):
    """D3's worktree-scoped dirty-resume rule: raised when resuming an
    `IN_PROGRESS` checkpoint finds no local `WORKTREE_IDENTITY.json`, or
    one with no entry for this exact work item (`OPUS-R10-008`'s keyed
    design -- a different work item's entry never satisfies this)."""


class WorktreeIdentityMismatchError(Exception):
    """D3's worktree-scoped dirty-resume rule: raised when a local
    `WORKTREE_IDENTITY.json` exists but its recorded git identity
    (`repo_root`/`git_common_dir`/`worktree_root`) does not match this
    worktree's actual identity -- the file was inherited from, or copied
    from, a different worktree/clone."""


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


class AlreadyActivatedError(Exception):
    """Raised by `build_activated_config` when the config's
    `default_workflow_version` is already `"2.1"` -- `WF-Activate` is a
    sole, one-time boundary (WFR-10), never an idempotent no-op call."""


class NotActivatedError(Exception):
    """Raised by `build_rolled_back_config` when the config's
    `default_workflow_version` is not `"2.1"` -- there is nothing to roll
    back (WFR-10/WFR-12's rollback is a real state reversal, not a bare
    field reset)."""


class UnsupportedGoverningVersionError(Exception):
    """Raised when a work item's `governing_workflow_version` is outside
    the config's `supported_versions` (resolves OPUS-R6-024, optional)."""


class WorkItemTerminalReuseError(Exception):
    """Raised when routing names a `work_item_id` that already exists at a
    terminal phase -- ids are not reused after `MILESTONE_COMPLETE` (D1)."""


class WorkItemDeclarationFactConflictError(Exception):
    """Raised when `route_work_item`'s resume branch is supplied a
    `plan_path`/`registry_path`/`mapping_path`/`base_commit` value that
    disagrees with an already-non-null stored value -- a declaration fact
    is immutable once set (`D-Fingerprint-Generalization`, `OPUS-R28-002`),
    consistent with the existing identity-field immutability rule."""


class PlanRevisionMirrorMismatchError(Exception):
    """Raised when `WORKFLOW_STATE.json`'s own `plan_revision` field for a
    work item disagrees with that item's own registry JSON's
    `plan_revision` -- `WORKFLOW_STATE.json`'s copy is a non-authoritative
    display mirror only (`OPUS-R25-002`), and a divergence between the two
    is a data-integrity defect, not a legitimate state."""


class MissingRegistryForPlanRevisionMirrorCheckError(Exception):
    """Raised by `validate_state`'s `repo_root`-driven whole-state
    plan-revision-mirror check (`GPT-R31-003`) when a work item declares a
    non-null `registry_path` whose file does not exist or is unreadable at
    `repo_root` -- fails closed rather than silently skipping that item's
    own mirror check, which would let "omitted registry coverage" pass as
    if it had been verified. Also raised when `registry_path` fails the
    shared safe-path resolver (`GPT-R32-001`): an absolute path, a `../`
    traversal, a symlink, or any other repository-boundary escape is
    exactly as unusable a "registry" as one that is simply missing."""


class InvalidRegistryPlanRevisionError(Exception):
    """Raised by `validate_state`'s `repo_root`-driven whole-state
    plan-revision-mirror check (`GPT-R32-002`) when a work item's own
    registry resolves but its `plan_revision` field is missing, `null`, not
    an integer (including `bool`, which Python's `int` subclasses), or
    below the schema's minimum of 1. A malformed registry cannot satisfy
    Revision 21's state-mirror invariant, so this fails closed instead of
    silently treating "no revision to compare" as "nothing to check"."""


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


class LegacyAdoptionWrongPhaseError(Exception):
    """Raised when D-Legacy phase 2 (`WF-M8b`) adoption is invoked against
    a work item whose `phase` is not `LEGACY_READY` -- adoption only ever
    promotes a dormant legacy entry; a command targeting a different work
    item, or this same item outside `LEGACY_READY`, never touches it
    (D-Legacy: "adoption never runs implicitly")."""


class LegacyAdoptionStaleApprovalError(Exception):
    """Raised when D-Legacy phase 2 adoption recomputes the dormant
    entry's `technical_approval` freshness and finds it `STALE` --
    protected implementation-stage content changed between import and
    adoption. Adoption stops outright and requires a fresh
    implementation-review round through the normal `technical_approval`
    lifecycle (`/apply-implementation-review` + `/approve-review
    implementation`); it never re-imports (D-Legacy phase 2, resolves
    `WFR-08`'s "no basis branch" rule applied to promotion time, not just
    import time)."""


class InvalidBundleGenerationStageError(Exception):
    """Raised when `record_bundle_generation` is called with a `stage`
    other than `"implementation"`/`"post-fix"` -- `reviewed_implementation_head`
    is written at exactly those two bundle-generation points, never any
    other (D-Approval-Commits, WF4c)."""


class RemediationChildAlreadyExistsError(Exception):
    """Raised when `create_remediation_child_work_item`'s deterministically
    derived `<parent>-remediation-<n>` id already names an existing entry
    -- should not happen given the function's own internal, gap-free
    numbering, so this is a defensive guard against a corrupted/hand-edited
    state file, not a normal control-flow path (D-Functional-Remediation)."""


class IncompleteChildWorkItemError(Exception):
    """Raised by `complete_work_item` when at least one work item names the
    target as its `parent_work_item_id` and has not itself reached
    `MILESTONE_COMPLETE` -- D-Functional-Remediation's "parent acceptance
    blocks on an incomplete child" rule (resolves `GPT-R9-016`, `WFR-35`).
    Names every blocking child, never just the first."""


class DanglingParentWorkItemError(Exception):
    """Raised when a work item's `parent_work_item_id` names an id absent
    from `work_items` -- a corrupted or hand-edited state file, since every
    writer that sets `parent_work_item_id`
    (`create_remediation_child_work_item`) also creates the parent-naming
    entry in the same state dict."""


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
    work_item_id: str, head: str = "HEAD", artifacts_path: Path,
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
    near-inverses, never derived from one another).

    `stage="plan"` no longer accepts a `plan_revision` parameter
    (`OPUS-R25-002`, `D-Fingerprint-Generalization`): the resolved value
    comes from `fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item`'s
    own call into `resolve_plan_stage_metadata`, sourcing
    `work_item_type`/`plan_revision`/the protected-path sets entirely from
    `WORKFLOW_STATE.json`/`<work_item_id>-artifacts.json` -- never a
    caller-supplied literal naming any other work item's identity.
    `artifacts_path` is required (no default -- `DEFAULT_ARTIFACTS_PATH`
    is retired as a live default here) and used only for
    `stage="implementation"`; both actual callers
    (`approval_is_current`/`verify_post_approval_manifest_match`) resolve
    it per work item via `fingerprint.artifacts_path_for_work_item(work_item_id)`
    (`OPUS-R27-002`). `implementation_revision` is never part of either
    projection (D-States: "meaningless before implementation starts and
    mutating during it")."""
    if stage == "plan":
        digest, _ = fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item(
            repo_root, work_item_id, head, base=base_commit,
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
    projection, so this keeps returning `True`. Missing-test item 150: a
    second work item's own `plan_approval`/`technical_approval` record
    gates independently of `workflow-v2-1-core`'s (`D-Fingerprint-
    Generalization`)."""
    record_field = "plan_approval" if stage == "plan" else "technical_approval"
    record = work_item.get(record_field)
    if record is None or record.get("status") != "CURRENT":
        return False
    current_id = approval_review_content_id(
        repo_root, stage=stage, base_commit=base_commit,
        work_item_type=work_item["work_item_type"], work_item_id=work_item["work_item_id"],
        head=head,
        artifacts_path=fingerprint.artifacts_path_for_work_item(work_item["work_item_id"]),
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
        head=commit,
        artifacts_path=fingerprint.artifacts_path_for_work_item(work_item["work_item_id"]),
    )
    if actual != expected:
        raise PostApprovalManifestMismatchError(
            f"{work_item['work_item_id']}/{stage}: commit {commit} recomputes to "
            f"{actual!r}, expected {expected!r} (approved_review_content_id)"
        )


# ---------------------------------------------------------------------------
# WF2: D-Selection's deterministic four-rule checkpoint-selection algorithm,
# the IN_PROGRESS/COMPLETE state writers, and D3's worktree-scoped dirty-
# resume mechanics (WORKTREE_IDENTITY.json writer + resume check).
# ---------------------------------------------------------------------------


def select_next_checkpoint(work_item: dict, registry: dict) -> str | None:
    """D-Selection, rules 1-2-4 in full (rule 3 -- the registry JSON's own
    order being a valid topological order -- is a separate precondition,
    `validate_registry_topological_order`, checked once at registry-write
    time rather than on every selection call).

    Pure: reads only `work_item`/`registry`, touches no filesystem or Git
    state, so two independent fresh sessions given byte-identical inputs
    always select the same checkpoint (missing-test item 28) without any
    tie-break beyond rule 3's own topological-order guarantee. Deliberately
    does *not* perform the worktree-identity check rule 1 defers to --
    that is `verify_dirty_resume_safety`'s job, kept separate so this
    determinism guarantee is testable with no worktree fixture at all.

    Returns:
    - the `current_checkpoint_id`, unchanged, if it is set and its own
      status is `IN_PROGRESS` (rule 1 -- an interrupted checkpoint resumes
      without any human reconciliation of narrative/doc state, missing-
      test item 31);
    - otherwise the first checkpoint in the registry JSON array's own
      order whose status is not `COMPLETE` and whose every `depends_on`
      entry is `COMPLETE` (rule 2);
    - `None` if every registry checkpoint is already `COMPLETE` -- a
      legitimate, distinct outcome from being blocked, signaling the
      caller to drive the `IMPLEMENTING` -> `SELF_REVIEWING_IMPLEMENTATION`
      transition (`complete_checkpoint` already does this the moment the
      last checkpoint completes, so a caller only ever observes `None`
      here on a stale/out-of-band re-check).

    Raises `NoCheckpointReadyError` (rule 4) when at least one checkpoint
    remains incomplete but none is currently selectable -- names the
    specific blocked checkpoint and its unmet dependencies, never silently
    idling.
    """
    checkpoints = work_item.get("checkpoints", {})
    current_id = work_item.get("current_checkpoint_id")
    if current_id is not None and checkpoints.get(current_id, {}).get("status") == "IN_PROGRESS":
        return current_id

    complete = {cid for cid, entry in checkpoints.items() if entry.get("status") == "COMPLETE"}
    for entry in registry["checkpoints"]:
        checkpoint_id = entry["id"]
        if checkpoint_id in complete:
            continue
        depends_on = entry.get("depends_on", [])
        if all(dep in complete for dep in depends_on):
            return checkpoint_id

    incomplete = [entry for entry in registry["checkpoints"] if entry["id"] not in complete]
    if not incomplete:
        return None

    blocked = incomplete[0]
    unmet = [dep for dep in blocked.get("depends_on", []) if dep not in complete]
    raise NoCheckpointReadyError(
        f"no checkpoint is currently selectable: {blocked['id']!r} is the first "
        f"incomplete entry in registry order, blocked on incomplete "
        f"dependencies {unmet}"
    )


def transition_checkpoint_in_progress(
    state: dict, work_item_id: str, checkpoint_id: str, start_commit: str, now: str,
) -> dict:
    """The state half of D3's `IN_PROGRESS` transition: records
    `checkpoints[checkpoint_id] = {status: IN_PROGRESS, start_commit}` and
    sets `current_checkpoint_id`. The filesystem half --
    `write_worktree_identity` -- is a separate call the caller makes
    alongside this one, since it touches a local, gitignored file this
    module's other state writers never touch. Returns a new state dict."""
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    work_item["checkpoints"][checkpoint_id] = {"status": "IN_PROGRESS", "start_commit": start_commit}
    work_item["current_checkpoint_id"] = checkpoint_id
    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    return new_state


def complete_checkpoint(
    state: dict, work_item_id: str, checkpoint_id: str, registry: dict, now: str,
) -> dict:
    """D3's checkpoint-complete-vs-all-complete semantics: marks
    `checkpoint_id` `COMPLETE`, resets `current_checkpoint_id` to `null`
    (phase stays `IMPLEMENTING` between checkpoints), and additionally
    transitions `phase` to `SELF_REVIEWING_IMPLEMENTATION` only when every
    checkpoint named in the registry is now `COMPLETE` -- the two
    conditions are deliberately distinct: completing one checkpoint is
    never itself evidence the whole work item is done. Returns a new
    state dict."""
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    entry = work_item["checkpoints"].setdefault(checkpoint_id, {})
    entry["status"] = "COMPLETE"
    work_item["current_checkpoint_id"] = None
    work_item["last_completed_checkpoint_id"] = checkpoint_id
    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    all_ids = [entry["id"] for entry in registry["checkpoints"]]
    if all(work_item["checkpoints"].get(cid, {}).get("status") == "COMPLETE" for cid in all_ids):
        work_item["phase"] = "SELF_REVIEWING_IMPLEMENTATION"
    return new_state


def _git_identity(repo_root: Path) -> tuple[str, str, str]:
    """`(repo_root, git_common_dir, worktree_root)` as `WORKTREE_IDENTITY.json`
    stores them -- `git_common_dir` resolved to an absolute path since Git
    reports it relative to `cwd` for a normal (non-worktree) checkout."""
    repo_root_out = _run(["git", "rev-parse", "--show-toplevel"], cwd=repo_root).strip()
    raw_common_dir = _run(["git", "rev-parse", "--git-common-dir"], cwd=repo_root).strip()
    git_common_dir = raw_common_dir if Path(raw_common_dir).is_absolute() else str((repo_root / raw_common_dir).resolve())
    return repo_root_out, git_common_dir, repo_root_out


def _hash_dirty_paths(repo_root: Path) -> list[dict]:
    """`{path, sha256}` for every currently dirty path this module's own
    `_dirty_paths` reports, hashing the working-tree file's real bytes
    directly (not `git hash-object`) -- a deleted-but-dirty path is simply
    omitted, since there is no content left to hash."""
    entries = []
    for path in sorted(_dirty_paths(repo_root)):
        full = repo_root / path
        if not full.is_file():
            continue
        entries.append({"path": path, "sha256": hashlib.sha256(full.read_bytes()).hexdigest()})
    return entries


def write_worktree_identity(
    repo_root: Path, work_item_id: str, *, now: str, path: Path = WORKTREE_IDENTITY_PATH,
) -> dict:
    """D3's named writer (resolves `OPUS-R6-026`, optional): the command
    that transitions a checkpoint to `IN_PROGRESS` (WF2's own
    `/milestone-implement`, or the bootstrap command for
    `workflow-v2-1-core`'s own checkpoints) creates or refreshes **only
    its own work item's entry** here -- `expected_dirty_paths_by_work_item`
    is keyed by `work_item_id` precisely so two work items with interleaved
    `IN_PROGRESS` dirty work never disturb each other's entry when this is
    called (`OPUS-R10-008`, missing-test item 71).

    The snapshot recorded per path (`path`, `sha256` of the file's current
    bytes) is diagnostic audit data, not a resume-blocking equality gate:
    `verify_dirty_resume_safety` never compares it against a later dirty
    state. An exact dirty-set match would break resuming an interrupted
    checkpoint (missing-test item 31) -- the whole point of resuming is to
    keep editing, so the dirty set is expected to keep changing between
    the moment this snapshot is taken and the next time it's read.

    Writes (creating parent directories as needed) and returns the full,
    now-validated `WORKTREE_IDENTITY.json` document."""
    full_path = repo_root / path
    existing = _load_json(full_path) or {}
    repo_root_id, git_common_dir, worktree_root = _git_identity(repo_root)
    existing["repo_root"] = repo_root_id
    existing["git_common_dir"] = git_common_dir
    existing["worktree_root"] = worktree_root
    expected = existing.setdefault("expected_dirty_paths_by_work_item", {})
    expected[work_item_id] = _hash_dirty_paths(repo_root)
    existing["generated_at"] = now
    validate_worktree_identity(existing)
    full_path.parent.mkdir(parents=True, exist_ok=True)
    full_path.write_text(json.dumps(existing, indent=2, sort_keys=True) + "\n")
    return existing


def verify_dirty_resume_safety(
    repo_root: Path, work_item_id: str, *, path: Path = WORKTREE_IDENTITY_PATH,
) -> None:
    """D3's worktree-scoped dirty-resume rule, the read half of
    `write_worktree_identity` (missing-test items 31, 43, 71): a clean
    (`COMPLETE`) checkpoint is portable anywhere; an `IN_PROGRESS`
    checkpoint's uncommitted work is worktree-local, so resuming it
    (D-Selection rule 1) requires this worktree's own local
    `WORKTREE_IDENTITY.json` to exist, to record this exact worktree's own
    Git identity, and to carry an entry for this exact `work_item_id`
    (never another item's). Raises `WorktreeIdentityMissingError` for a
    missing file or missing per-work-item entry, `WorktreeIdentityMismatchError`
    for an identity mismatch; returns `None` (no exception) on a clean
    match. Never inspects `expected_dirty_paths_by_work_item`'s per-path
    content -- see `write_worktree_identity`'s docstring for why."""
    full_path = repo_root / path
    data = _load_json(full_path)
    if data is None:
        raise WorktreeIdentityMissingError(
            f"{path} not found -- cannot resume {work_item_id!r}'s IN_PROGRESS "
            f"checkpoint from a worktree with no local identity record"
        )
    validate_worktree_identity(data)
    repo_root_id, git_common_dir, worktree_root = _git_identity(repo_root)
    if (data["repo_root"], data["git_common_dir"], data["worktree_root"]) != (repo_root_id, git_common_dir, worktree_root):
        raise WorktreeIdentityMismatchError(
            f"{path} records a different worktree's identity than this one -- "
            f"resume {work_item_id!r}'s IN_PROGRESS checkpoint from the worktree that started it"
        )
    if work_item_id not in data.get("expected_dirty_paths_by_work_item", {}):
        raise WorktreeIdentityMissingError(
            f"{path} has no expected_dirty_paths_by_work_item entry for {work_item_id!r} -- "
            f"this worktree never started (or lost the record for) this work item's IN_PROGRESS checkpoint"
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


def _changed_paths_between(repo_root: Path, base: str, head: str) -> set[str]:
    out = _run(["git", "diff", "--name-only", "-z", base, head], cwd=repo_root)
    return {p for p in out.split("\x00") if p}


def any_protected_path_changed_since(
    repo_root: Path, base: str, head: str,
    protected_paths: Mapping[str, str],
    protected_prefixes: Mapping[str, str],
    excluded_paths: Mapping[str, str],
    excluded_prefixes: Mapping[str, str],
) -> bool:
    """D-Legacy phase 2's own freshness primitive (`WF-M8b`,
    `promote_legacy_work_item`): a committed-history counterpart of
    `any_protected_path_dirty` -- "did any protected implementation-stage
    path change between `base` and `head`" -- deliberately **not** an
    exact-hash-reproduction check like `approval_is_current`. That
    function embeds the full classification config itself in its hashed
    projection, so widening a work item's own artifact-declarations file
    to correctly exclude paths a *concurrent* work item introduces while
    a legacy entry sits dormant (exactly `milestone-8-artifacts.json`'s
    own situation: workflow-v2-1-core's later `.claude/commands/`,
    `scripts/`, `docs/ai-workflow/` commits were never anticipated by the
    narrow set `WF-M8a` originally authored) would otherwise permanently
    and incorrectly read as `STALE` even with zero real protected-content
    drift -- the classification text itself would no longer hash-match,
    regardless of whether `app/`/`gradle/` content actually changed. This
    function never compares against a stored digest; it only answers
    whether a `protected` path is present in the diff, so widening the
    *excluded* side of a classification is safe here in a way it is not
    for `approval_is_current`. Fails closed (`UnclassifiedPathError`,
    propagated from `classify_path_implementation_stage`) on a changed
    path neither set names, same discipline as `any_protected_path_dirty`."""
    for path in sorted(_changed_paths_between(repo_root, base, head)):
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


def build_activated_config(config: dict) -> dict:
    """`WF-Activate`'s own transform: `default_workflow_version` -> `"2.1"`,
    every other field untouched. The caller writes the returned dict to
    `WORKFLOW_CONFIG.json` and commits it carrying a `Workflow-Activation:
    2.1` trailer (D-Self-Governance) -- this function only computes the
    new content, never touches Git or the filesystem itself, matching
    every other state-transform function in this module. Rejects a config
    already at `"2.1"`: activation is a sole, one-time boundary, not an
    idempotent setter (`AlreadyActivatedError`)."""
    if config.get("default_workflow_version") == "2.1":
        raise AlreadyActivatedError(
            "config.default_workflow_version is already \"2.1\" -- WF-Activate "
            "is a one-time boundary, not an idempotent call"
        )
    return {**config, "default_workflow_version": "2.1"}


def build_rolled_back_config(config: dict) -> dict:
    """The rollback counterpart of `build_activated_config`: `"2.1"` ->
    `"1"`. The caller commits the result carrying a `Workflow-Rollback:
    2.1` trailer. This reverts only the repository-level default -- it
    never touches any work item's own `governing_workflow_version`, fixed
    at creation and immune to this change (D-Self-Governance). Rejects a
    config not currently at `"2.1"`: there is nothing to roll back
    (`NotActivatedError`)."""
    if config.get("default_workflow_version") != "2.1":
        raise NotActivatedError(
            f"config.default_workflow_version is "
            f"{config.get('default_workflow_version')!r}, not \"2.1\" -- "
            f"nothing to roll back"
        )
    return {**config, "default_workflow_version": "1"}


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


def generate_artifacts_declarations(
    work_item_id: str, plan_path: str, registry_path: str, mapping_path: str,
) -> dict:
    """The default `<work_item_id>-artifacts.json` template
    (`D-Fingerprint-Generalization`, `OPUS-R25-004`): the item's own three
    artifact paths as `plan_stage.protected_paths` (satisfying
    `resolve_plan_stage_metadata`'s path-to-role binding check by
    construction at creation time), `workflow-v2-1-core`'s *current*
    `excluded_paths`/`excluded_prefixes` inherited verbatim as a starting
    point (not because they are correct for every future item unmodified,
    but because an inherited, previously-reviewed set fails closed on any
    genuinely novel path exactly as before, and is strictly safer than an
    empty or hand-authored one -- `SELF_REVIEWING_PLAN` must confirm the
    inherited set actually fits the new item's own plan). Also emits the
    file's own concrete, self-referential `implementation_stage.protected_paths`
    entry (`OPUS-R25-006`/`OPUS-R26-004`) so the file protects itself under
    `technical_approval` by construction, the same pattern
    `workflow-v2-1-core-artifacts.json`'s own migrated entry uses."""
    validate_work_item_id(work_item_id)
    own_artifacts_path = str(fingerprint.artifacts_path_for_work_item(work_item_id).as_posix())
    return {
        "schema_version": 2,
        "work_item_id": work_item_id,
        "plan_stage": {
            "protected_paths": sorted({plan_path, registry_path, mapping_path}),
            "excluded_paths": dict(fingerprint.PLAN_STAGE_EXCLUDED_PATHS),
            "excluded_prefixes": dict(fingerprint.PLAN_STAGE_EXCLUDED_PREFIXES),
        },
        "implementation_stage": {
            "protected_paths": {
                own_artifacts_path:
                    "this file's own concrete, spelled-out path -- an editable-under-"
                    "no-approval declarations file would let someone widen an "
                    "exclusion and re-bless the resulting digest in the same "
                    "session, with no gate ever having seen the classification "
                    "change (OPUS-R25-006, OPUS-R26-004)",
            },
            "protected_prefixes": {},
            "excluded_paths": {},
            "excluded_prefixes": {},
        },
    }


# ---------------------------------------------------------------------------
# D1: work-item routing (create-or-resume) and completion/reset
# ---------------------------------------------------------------------------


def default_work_item(
    *, work_item_id: str, work_item_type: str, work_item_kind: str,
    plan_path: str, registry_path: str | None, governing_workflow_version: str,
    plan_revision: int, last_transition: str,
    mapping_path: str | None = None, base_commit: str | None = None,
) -> dict:
    """D3's full per-item field list, defaulted for a freshly created work
    item (D1). `governing_workflow_version` is the caller's concern to fix
    from the then-current repository-level default -- this function never
    reads config itself (D-Self-Governance: fixed at creation, never
    re-read afterward). `mapping_path`/`base_commit` (`D-Fingerprint-
    Generalization`) are the third and fourth declaration facts a process
    work item needs to fingerprint its own plan-stage content -- the same
    mechanism as `plan_path`/`registry_path`, not a new one."""
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
        "mapping_path": mapping_path,
        "governing_workflow_version": governing_workflow_version,
        "phase": "PLANNING",
        "plan_revision": plan_revision,
        "implementation_revision": None,
        "functional_review_round": None,
        "base_commit": base_commit,
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
    mapping_path: str | None = None, base_commit: str | None = None,
) -> dict:
    """D1's routing text, in full: `/milestone-plan` creates or updates the
    item under `work_items[id]`. Returns a new state dict (does not mutate
    the input, so a caller can inspect the pre-route state on failure).

    - A fresh id creates a new `work_items[id]` entry, fixing
      `governing_workflow_version` from the config's current
      `default_workflow_version` (validated against `supported_versions`,
      `OPUS-R6-024`) and never re-read afterward.
    - An id naming an existing *non-terminal* entry resumes it: only
      `plan_revision`/`state_revision`/`last_transition` advance
      unconditionally -- identity fields (`work_item_type`/`kind`/
      `governing_workflow_version`/`parent_work_item_id`) are immutable
      after creation. **`mapping_path`/`base_commit`, and now
      `plan_path`/`registry_path` too, on the resume branch** (new,
      `D-Fingerprint-Generalization`, `OPUS-R28-002`): for each of the
      four declaration facts, independently, if the stored value is
      `null` and a non-null argument is supplied here, it is written; if
      the stored value is non-null and the supplied argument disagrees,
      `WorkItemDeclarationFactConflictError` (a declaration fact is
      immutable once set); if no argument is supplied for a field, that
      field is left untouched. This makes the resume branch usable for a
      pre-declared, non-terminal entry that has some or all of these four
      facts still `null` (e.g. `v2-1-dry-run`, created with only
      `plan_path` set) -- the same call this function's caller makes for
      a fresh id also works, idempotently, for a resumed one.
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
            mapping_path=mapping_path, base_commit=base_commit,
        )
    else:
        for field_name, supplied in (
            ("plan_path", plan_path), ("registry_path", registry_path),
            ("mapping_path", mapping_path), ("base_commit", base_commit),
        ):
            if supplied is None:
                continue
            stored = existing.get(field_name)
            if stored is None:
                existing[field_name] = supplied
            elif stored != supplied:
                raise WorkItemDeclarationFactConflictError(
                    f"{work_item_id}.{field_name} is already {stored!r}, "
                    f"cannot set to {supplied!r}"
                )
        existing["plan_revision"] = plan_revision
        existing["state_revision"] = existing.get("state_revision", 1) + 1
        existing["last_transition"] = now

    active = new_state.get("active_work_item_id")
    if active is None or active == work_item_id:
        new_state["active_work_item_id"] = work_item_id
    # else: a different item is active and non-terminal -- left alone;
    # repointing active_work_item_id is always an explicit, separate act.

    return new_state


def incomplete_children(state: dict, work_item_id: str) -> list[str]:
    """D-Functional-Remediation: every work item naming `work_item_id` as
    its own `parent_work_item_id` and not yet at a terminal phase --
    reverse lookup over `work_items`, no new field needed on the parent
    (the plan's own stated approach). Returns ids in insertion order,
    empty if there are no children or all of them are `MILESTONE_COMPLETE`."""
    return [
        child_id for child_id, child in state.get("work_items", {}).items()
        if child.get("parent_work_item_id") == work_item_id
        and child.get("phase") not in TERMINAL_PHASES
    ]


def complete_work_item(state: dict, work_item_id: str, now: str) -> dict:
    """D1's completion/reset text: on `MILESTONE_COMPLETE` (or
    process-completion archival), the entry's phase becomes terminal and,
    if it was `active_work_item_id`, that pointer resets to `null` so the
    next `/milestone-plan` creates a fresh entry and claims the pointer.
    Returns a new state dict.

    D-Functional-Remediation's "parent acceptance blocks on an incomplete
    child" rule (resolves `GPT-R9-016`, `WFR-35`): refuses outright, naming
    every still-incomplete child, rather than completing a parent whose
    broad remediation work is still open elsewhere."""
    blocking = incomplete_children(state, work_item_id)
    if blocking:
        raise IncompleteChildWorkItemError(
            f"{work_item_id!r} cannot reach MILESTONE_COMPLETE while child work "
            f"item(s) {blocking} have not themselves reached MILESTONE_COMPLETE"
        )
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    work_item["phase"] = "MILESTONE_COMPLETE"
    work_item["current_checkpoint_id"] = None
    work_item["state_revision"] = work_item.get("state_revision", 1) + 1
    work_item["last_transition"] = now
    if new_state.get("active_work_item_id") == work_item_id:
        new_state["active_work_item_id"] = None
    return new_state


def create_remediation_child_work_item(
    state: dict, config: dict, *, parent_work_item_id: str,
    plan_path: str, registry_path: str, base_commit: str, now: str,
) -> tuple[dict, str]:
    """D-Functional-Remediation's broad/multi-finding remediation branch:
    creates a new, separate work item -- `work_item_id:
    "<parent_work_item_id>-remediation-<n>"`, `n` derived deterministically
    as one past however many remediation children this parent already has
    (never caller-supplied, so numbering can never collide or skip) -- with
    the same `work_item_type`/`work_item_kind` as the parent, carrying the
    new `parent_work_item_id` field, its own `base_commit` (the parent's
    implementation head at the moment broad remediation was needed), and
    otherwise `default_work_item`'s ordinary fresh-`PLANNING`-phase shape.
    `governing_workflow_version` is fixed from the config's current
    default, exactly like any other freshly created work item (D1) --
    because a remediation child *is* one, routed through the full normal
    plan/implement/review/accept cycle by the ordinary commands from here.

    Deliberately does not touch `active_work_item_id` -- the parent
    typically stays the operator's focus; the child is addressed by its
    own id until the operator chooses otherwise, exactly D1's "resume-focus
    pointer, not an execution lock" text.

    The parent's own registry/mapping/completed-checkpoint history is
    untouched by construction: this function only adds a new key to
    `work_items`, never reads or writes the parent's `registry_path`/
    `plan_path` contents."""
    parent = state["work_items"][parent_work_item_id]
    existing_children = [
        wid for wid in state.get("work_items", {})
        if state["work_items"][wid].get("parent_work_item_id") == parent_work_item_id
    ]
    remediation_number = len(existing_children) + 1
    child_id = f"{parent_work_item_id}-remediation-{remediation_number}"
    validate_work_item_id(child_id)

    new_state = copy.deepcopy(state)
    work_items = new_state.setdefault("work_items", {})
    if child_id in work_items:
        raise RemediationChildAlreadyExistsError(
            f"{child_id!r} already exists -- remediation-child numbering should "
            f"never collide; check for a hand-edited state file"
        )
    validate_governing_version(config["default_workflow_version"], config)
    child = default_work_item(
        work_item_id=child_id, work_item_type=parent["work_item_type"],
        work_item_kind=parent["work_item_kind"], plan_path=plan_path,
        registry_path=registry_path,
        governing_workflow_version=config["default_workflow_version"],
        plan_revision=1, last_transition=now,
    )
    child["parent_work_item_id"] = parent_work_item_id
    child["base_commit"] = base_commit
    work_items[child_id] = child
    return new_state, child_id


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
# WF4c: D-Functional-Remediation -- the bounded-fix branch's stale-before-
# edit write and the bundle generator's sole reviewed_implementation_head
# writer (closes the loop OPUS-R6-013 found: nothing wrote this field
# before, so AWAITING_TECHNICAL_APPROVAL's entry condition was permanently
# unreachable for any real "2.1" item).
# ---------------------------------------------------------------------------


def mark_technical_approval_stale(state: dict, work_item_id: str, now: str) -> dict:
    """D-Functional-Remediation's bounded-code-change branch, stale-before-
    edit ordering: the caller must persist this write to
    `docs/ai-workflow/WORKFLOW_STATE.json` *before* touching a single
    source/test file, so an interrupted session still shows `STALE` rather
    than a `CURRENT` record whose reviewed content no longer matches the
    working tree. Requires an existing `technical_approval` record (there
    is nothing to stale otherwise) and leaves every other field of it
    untouched -- only `status` flips."""
    work_item = state["work_items"][work_item_id]
    record = work_item.get("technical_approval")
    if record is None:
        raise InvalidApprovalRecordError(
            f"{work_item_id!r} has no technical_approval record to stale -- "
            f"the bounded-fix branch only applies after a prior technical approval"
        )
    new_state = copy.deepcopy(state)
    new_work_item = new_state["work_items"][work_item_id]
    new_work_item["technical_approval"]["status"] = "STALE"
    new_work_item["state_revision"] = new_work_item.get("state_revision", 1) + 1
    new_work_item["last_transition"] = now
    return new_state


def record_bundle_generation(state: dict, work_item_id: str, *, stage: str, head: str, now: str) -> dict:
    """`reviewed_implementation_head`'s sole writer (D-Approval-Commits),
    called by the bundle generator at exactly the `"implementation"` (first
    round) or `"post-fix"` (every remediation round after, whether driven
    by an implementation-review finding or a functional-review bounded
    fix) stage -- never any other. Records live `head` as the new
    `reviewed_implementation_head` and bumps `implementation_revision`
    (`None` -> `1` on the first call, incrementing on every call after --
    `implementation_revision` itself is never part of either fingerprint
    projection, so this bump alone never stales `technical_approval`,
    `approval_is_current`'s own missing-test item 11)."""
    if stage not in ("implementation", "post-fix"):
        raise InvalidBundleGenerationStageError(
            f"reviewed_implementation_head is written only at the "
            f"\"implementation\"/\"post-fix\" bundle-generation stage, got {stage!r}"
        )
    new_state = copy.deepcopy(state)
    work_item = new_state["work_items"][work_item_id]
    work_item["reviewed_implementation_head"] = head
    work_item["implementation_revision"] = (work_item.get("implementation_revision") or 0) + 1
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
# WF-M8b: D-Legacy phase 2 -- Milestone 8 adoption (dormant LEGACY_READY ->
# active, ordinary v2.1 routing). Resolves OPUS-R10-005/-011, GPT-R9-005.
# ---------------------------------------------------------------------------


def promote_legacy_work_item(
    state: dict, repo_root: Path, *, work_item_id: str,
    required_active_milestone_substring: str,
    artifacts_path: Path = fingerprint.DEFAULT_ARTIFACTS_PATH, now: str,
) -> dict:
    """D-Legacy phase 2 (`WF-M8b`): promotes a dormant `LEGACY_READY` entry
    to active, ordinary Workflow v2.1 routing. Called by
    `/prepare-functional-review`'s selector step only when the resolved
    target's `phase` is already `LEGACY_READY` -- adoption never runs
    implicitly against any other item or phase (`LegacyAdoptionWrongPhaseError`
    guards misuse defensively, mirroring `import_legacy_work_item`'s own
    `LegacyImportAlreadyExistsError` guard).

    Re-validates, never trusts from import time:
    1. the branch-reconciliation precondition (`verify_legacy_branch_
       reconciliation`) -- a dormant item can sit for a long time between
       import and adoption;
    2. `technical_approval` freshness, via `any_protected_path_changed_
       since(reviewed_content_commit, "HEAD", ...)` scoped to
       `work_item_id`'s own `artifacts_path` -- deliberately not
       `approval_is_current`'s exact-hash-reproduction, which would
       permanently misfire here (see that function's own docstring): a
       dormant legacy entry's declarations file legitimately needs
       widening, after import, to classify paths a concurrent work item
       introduces while it waits, and that widening alone must never read
       as staleness. A real `protected` path change since
       `reviewed_content_commit` raises `LegacyAdoptionStaleApprovalError`
       and stops outright: adoption never re-imports, a stale legacy
       approval is handled exactly like a stale ordinary one, through the
       normal `technical_approval` lifecycle (D-Approval-Commits).

    On success: `active_work_item_id` is set to `work_item_id`,
    `governing_workflow_version` transitions `"1"` -> `"2.1"` (an
    ordinary, auditable version transition, legal only because adoption
    runs from Workflow v2.1's own completed `/prepare-functional-review`,
    so the repository default is already `"2.1"` -- resolves
    `OPUS-R10-011`), and `phase` transitions to `AWAITING_FUNCTIONAL_REVIEW`
    -- `technical_approval` itself is preserved exactly as imported
    (`basis: LEGACY_V1`, untouched); adoption changes routing, never the
    approval record."""
    work_item = state["work_items"][work_item_id]
    if work_item.get("phase") != "LEGACY_READY":
        raise LegacyAdoptionWrongPhaseError(
            f"{work_item_id!r} is not LEGACY_READY (phase={work_item.get('phase')!r}) -- "
            f"adoption only promotes a dormant legacy entry"
        )
    technical_approval = work_item["technical_approval"]
    verify_legacy_branch_reconciliation(
        repo_root, reviewed_content_commit=technical_approval["reviewed_content_commit"],
        required_active_milestone_substring=required_active_milestone_substring,
    )
    protected_paths, protected_prefixes, excluded_paths, excluded_prefixes = (
        fingerprint.load_implementation_stage_classification(repo_root, artifacts_path)
    )
    if any_protected_path_changed_since(
        repo_root, technical_approval["reviewed_content_commit"], "HEAD",
        protected_paths, protected_prefixes, excluded_paths, excluded_prefixes,
    ):
        raise LegacyAdoptionStaleApprovalError(
            f"{work_item_id!r}'s legacy technical_approval is no longer current -- a "
            f"protected implementation-stage path changed since {technical_approval['reviewed_content_commit']!r}; "
            f"adoption requires a fresh implementation-review round and /approve-review "
            f"implementation, never a re-import"
        )
    new_state = copy.deepcopy(state)
    new_state["active_work_item_id"] = work_item_id
    new_work_item = new_state["work_items"][work_item_id]
    new_work_item["governing_workflow_version"] = "2.1"
    new_work_item["phase"] = "AWAITING_FUNCTIONAL_REVIEW"
    new_work_item["state_revision"] = new_work_item.get("state_revision", 1) + 1
    new_work_item["last_transition"] = now
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


def validate_state(state: dict, *, registry: dict | None = None, repo_root: Path | None = None) -> None:
    """D3's "Validator rejects" list, to the extent checkable from schema
    and (optionally) the registry alone. Corrupt/unparseable JSON is the
    caller's concern (`_load_json`/`CorruptJsonError`) -- this function
    receives already-parsed data.

    `registry`, if given, is a single caller-supplied registry dict used
    for the checkpoint-dependency check below and, if its own
    `work_item_id` names a known item, that one item's plan-revision
    mirror -- unchanged from before `GPT-R31-003`, kept for callers that
    only ever validate one work item's checkpoints against its own
    registry.

    `repo_root`, if given, is the **whole-state** plan-revision-mirror
    check `GPT-R31-003` requires: every work item with a non-null
    `registry_path` has that path resolved and read from disk (relative to
    `repo_root`), independent of whichever single `registry` a caller
    happened to also pass -- so a caller validating `workflow-v2-1-core`'s
    own registry can no longer leave a different work item's stale mirror
    undetected. `registry_path` is resolved through
    `fingerprint._validate_repo_relative_file`, the same safe-path
    resolver plan-stage metadata uses (`GPT-R32-001`) -- an absolute path,
    a `../` traversal, a symlink, or a path that does not exist as a
    regular file all fail closed as
    `MissingRegistryForPlanRevisionMirrorCheckError` rather than silently
    reading whatever `repo_root / registry_path` happens to join to. The
    loaded registry must also declare the exact same `work_item_id` as the
    state-map key that named it (`RegistryWorkItemIdMismatchError`,
    `GPT-R32-003`) and a valid integer `plan_revision >= 1`
    (`InvalidRegistryPlanRevisionError`, `GPT-R32-002`) -- neither
    cross-wired registry ownership nor a malformed revision are silently
    treated as "nothing to check". A `null` `registry_path` remains
    exempt, by design, from any mirror check at all."""
    if state.get("schema_version") != SCHEMA_VERSION:
        raise CorruptJsonError(f"unknown state schema_version: {state.get('schema_version')!r}")

    work_items = state.get("work_items", {})
    for work_item_id, work_item in work_items.items():
        _validate_work_item(work_item_id, work_item)
        parent_id = work_item.get("parent_work_item_id")
        if parent_id is not None and parent_id not in work_items:
            raise DanglingParentWorkItemError(
                f"work_items[{work_item_id!r}].parent_work_item_id names "
                f"{parent_id!r}, which is not a known work item"
            )

    # D-Fingerprint-Generalization (`OPUS-R25-007`): a non-null plan_path/
    # registry_path/mapping_path may not be claimed by more than one work
    # item, checked independently per field, not only as a triple. This is
    # a write-time, whole-map structural belt-and-suspenders check --
    # `resolve_plan_stage_metadata`'s own step 9 is the read path's actual
    # enforcement point, run per lookup; this one catches a hand-edited or
    # half-written state file that never passed through `route_work_item`.
    for field_name in ("plan_path", "registry_path", "mapping_path"):
        seen: dict[str, str] = {}
        for work_item_id, work_item in work_items.items():
            value = work_item.get(field_name)
            if value is None:
                continue
            if value in seen:
                raise fingerprint.DuplicateWorkItemArtifactPathError(
                    f"{field_name} {value!r} is claimed by both {seen[value]!r} and {work_item_id!r}"
                )
            seen[value] = work_item_id

    if repo_root is not None:
        # GPT-R31-003: the single-`registry`-param mirror check above only
        # ever covers the one item that `registry` itself names -- a
        # caller validating with only `workflow-v2-1-core`'s own registry
        # never sees a *different* work item's stale mirror. This is the
        # whole-state counterpart: every non-null `registry_path` in
        # `state` is resolved and read from disk, independent of which
        # single `registry` (if any) the caller also passed.
        for work_item_id, work_item in work_items.items():
            registry_path = work_item.get("registry_path")
            if registry_path is None:
                continue
            # GPT-R32-001: reuse the authoritative repo-relative safe-path
            # resolver (grammar, symlink, existence) instead of a bare
            # `repo_root / registry_path` join, which a `../` traversal or
            # an absolute path could escape.
            try:
                registry_full = fingerprint._validate_repo_relative_file(
                    repo_root, "registry_path", registry_path,
                )
            except fingerprint.InvalidPlanStageMetadataPathError as exc:
                raise MissingRegistryForPlanRevisionMirrorCheckError(
                    f"work_items[{work_item_id!r}].registry_path {registry_path!r} "
                    f"failed safe-path resolution: {exc}"
                ) from exc
            try:
                registry_bytes = registry_full.read_text()
            except OSError as exc:
                raise MissingRegistryForPlanRevisionMirrorCheckError(
                    f"work_items[{work_item_id!r}].registry_path {registry_path!r} "
                    f"does not exist or is unreadable at {registry_full}"
                ) from exc
            registry_data = json.loads(registry_bytes)

            # GPT-R32-003: the loaded registry must declare the exact same
            # work_item_id as the state-map key that named it -- otherwise
            # item A could point at item B's registry and pass whenever the
            # two happened to share a revision number.
            registry_work_item_id = registry_data.get("work_item_id")
            if registry_work_item_id != work_item_id:
                raise fingerprint.RegistryWorkItemIdMismatchError(
                    f"work_items[{work_item_id!r}].registry_path {registry_path!r} "
                    f"declares work_item_id {registry_work_item_id!r}, expected {work_item_id!r}"
                )

            # GPT-R32-002: a registry with no valid `plan_revision` cannot
            # satisfy Revision 21's state-mirror invariant -- fail closed
            # rather than silently `continue`-ing past unverified coverage.
            registry_plan_revision = registry_data.get("plan_revision")
            if (
                not isinstance(registry_plan_revision, int)
                or isinstance(registry_plan_revision, bool)
                or registry_plan_revision < 1
            ):
                raise InvalidRegistryPlanRevisionError(
                    f"work_items[{work_item_id!r}].registry_path {registry_path!r} has no "
                    f"valid 'plan_revision' (an integer >= 1): found {registry_plan_revision!r}"
                )
            state_plan_revision = work_item.get("plan_revision")
            if state_plan_revision != registry_plan_revision:
                raise PlanRevisionMirrorMismatchError(
                    f"work_items[{work_item_id!r}].plan_revision (state mirror) == "
                    f"{state_plan_revision!r}, but registry {registry_path!r} declares "
                    f"plan_revision == {registry_plan_revision!r}"
                )

    active_id = state.get("active_work_item_id")
    if active_id is not None:
        active_item = work_items.get(active_id)
        if active_item is None or active_item.get("phase") in TERMINAL_PHASES:
            raise ActiveWorkItemInvalidError(
                f"active_work_item_id {active_id!r} names a nonexistent or terminal-phase entry"
            )

    if registry is not None:
        # D-Fingerprint-Generalization (`OPUS-R25-002`/`-009`, missing-test
        # item 147): `WORKFLOW_STATE.json`'s own `plan_revision` field is a
        # non-authoritative display mirror of the registry's own
        # `plan_revision` (the authoritative value `load_plan_revision`
        # resolves from). A caller-supplied `registry` names the work item
        # it belongs to via its own `work_item_id` field; if that item is
        # present in `state`, the two must agree before any fingerprint
        # call runs, so a stale/hand-edited mirror fails closed here rather
        # than silently letting a later command act on the wrong revision.
        registry_work_item_id = registry.get("work_item_id")
        registry_plan_revision = registry.get("plan_revision")
        if registry_work_item_id is not None and registry_plan_revision is not None:
            mirrored_work_item = work_items.get(registry_work_item_id)
            if mirrored_work_item is not None:
                state_plan_revision = mirrored_work_item.get("plan_revision")
                if state_plan_revision != registry_plan_revision:
                    raise PlanRevisionMirrorMismatchError(
                        f"work_items[{registry_work_item_id!r}].plan_revision "
                        f"(state mirror) == {state_plan_revision!r}, but registry "
                        f"declares plan_revision == {registry_plan_revision!r}"
                    )
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
# .ai-review/runtime/WORKTREE_IDENTITY.json schema (the writer,
# `write_worktree_identity`, and its resume-side check,
# `verify_dirty_resume_safety`, live in WF2's own section above)
# ---------------------------------------------------------------------------


def validate_worktree_identity(data: dict) -> None:
    """Schema check for D3's local-only, gitignored WORKTREE_IDENTITY.json,
    shared by `write_worktree_identity` (which validates before writing)
    and `verify_dirty_resume_safety` (which validates before trusting a
    file it did not just write)."""
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
