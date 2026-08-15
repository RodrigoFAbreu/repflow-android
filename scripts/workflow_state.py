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

WF8b adds the first production slice of `D-Checkpoint-Ownership`
(revisions 69-72 of the plan's "The origination reference" and "The read
fails closed, and the partition is total"): `checkpoint_origination_provable`
and its supporting `origination_reference_commits`/
`_checkpoint_status_at_commit`, scanning every commit
`git rev-list --all --full-history -- <state path>` enumerates for a
decidable observation that a checkpoint's `IN_PROGRESS` was ever supplied
by a checkout, merge, or reset rather than genuinely originated in this
worktree. Wired into `/milestone-implement`'s `[2.1 step 1]` step 1c,
alongside `verify_dirty_resume_safety`, on the resume path only.

A second WF8b slice adds "The record" and "Fencing: the mutation/handoff
guard" (revisions 63-72), plus the explicit takeover and the abandoned-
guard recovery it depends on: the shared, never-committed claim record
under `$(git rev-parse --git-common-dir)/ai-workflow/checkpoint-claims/`
(`claim_checkpoint`/`resolve_claim`/`observe_claim`/`release_checkpoint`,
atomic `os.link`/`os.rename` publication, symlink/directory/EACCES all
failing closed rather than reading as absent), the per-work-item mutation/
handoff guard (`acquire_guard`/`assert_claim_owner`/`owner_mutation`'s one
fixed acquire-assert-mutate-release window, `GPT-R81-001`'s fenced
handoff), the observation-bound, evidence-first explicit takeover
(`takeover_evidence`/`take_over_claim`), and the distinct abandoned-
`"destructive"`-guard recovery for a holder worktree that no longer
exists (`recover_abandoned_destructive_guard`, `OPUS-R82-001`) -- gated
on `git worktree list`, never on a timeout or a liveness guess. Also
fixes `OPUS-R86-002`'s `WORKTREE_IDENTITY.json` lost-update defect
(12/12 threaded trials lost an entry under the prior plain read-modify-
write): `write_worktree_identity` now serializes its whole load-validate-
mutate-publish sequence under `identity_document_lock`
(`.ai-review/runtime/WORKTREE_IDENTITY.lock`, a stable, never-unlinked,
per-worktree `fcntl.flock` leaf) and publishes by a single `os.replace`
(`_publish_worktree_identity`, `OPUS-R86-005`); `repair_worktree_identity`
is the authorized repair-by-overwrite a rotating operation uses when the
taking worktree's own identity document is undecidable
(`OPUS-R86-003`/`-004`).

A third WF8b slice adds `adopt_claim` ("Reconciling the two authorities",
revisions 63-72): the one-time migration path for a checkpoint
interrupted before this mechanism existed, publishing a claim for a
checkpoint this worktree already holds `IN_PROGRESS` -- guarded by the
same origination reference the first slice built
(`checkpoint_origination_provable`, re-evaluated a second time at
publication under `guard_mutation_lock`), never by the dry-run
prototype's superseded `committed_checkpoint_status`-only check.

A fourth WF8b slice adds `/milestone-implement`'s own step 1c/1d/1f
wiring ("Where the check belongs, and the ordering"):
`resolve_checkpoint_ownership` (the full "Reconciling the two
authorities" table -- RESUME/FRESH/CONTINUE_CLAIM/NO_CHECKPOINT, the
foreign-claim refusal, the adopt-then-resume branch, the single
automatic release, and the two defensive mismatch refusals) and
`_attach_ownership_evidence`/`_ownership_escape_hint`, which annotate
every refusal 1c raises with `.ownership_evidence` unconditionally,
including on an absent claim, plus origination-specific evidence
components for `CheckpointOriginationUnprovableError` specifically.

A fifth WF8b slice adds `WFR-66`'s identity-query enforcement: the
identity queries' own read partition (`_identity_query_at_commit`/
`_scan_identity_reference`, distinct from and disjoint with the
origination test's own partition, though both scan the same reference),
the two permanent, unescapable observed-id refusals
(`WorkItemIdReusedError`/`CheckpointIdReusedError`, wired into
`route_work_item`'s fresh-id branch and `write_registry_and_mapping`'s
delta-scoped checkpoint-id check), and the evidence-bound,
non-replayable, repository-serialized escape from the undecidable case
(`authorize_identity_reference_gap`, `recover_abandoned_destructive_
guard`'s own signature) with its durable record under
`$(git rev-parse --git-common-dir)/ai-workflow/identity-gap-authorizations/`.

Deliberately still bounded: no WFR-66 enforcement wired into any
`.claude/commands/*.md` operator flow beyond the two library call sites
above, and executing `v2-1-dry-run`'s `S14`/`S15` scenarios for real
against this design remains future `WF8b` scope.

Stdlib-only, mirroring `scripts/workflow_fingerprint.py`'s own
`docs/TECHNICAL_DECISIONS.md`-recorded constraint.

Run the hermetic suite: python3 scripts/workflow_state_test.py
Run the real-repository demonstration: python3 scripts/workflow_state_demo_test.py
"""

from __future__ import annotations

import contextlib
import copy
import errno
import fcntl
import hashlib
import json
import os
import re
import secrets
import stat
import subprocess
import tempfile
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
# "scoped_remediation" (D-Scoped-Remediation-Acceptance, resolves
# WF8B-002) is a fourth stage keyword, textually non-interchangeable with
# "acceptance" in validate_user_confirmation's existing exact-substring
# check -- this alone makes reuse of terminal milestone acceptance as
# scoped acceptance structurally impossible.
APPROVAL_STAGES = frozenset({"plan", "implementation", "acceptance", "scoped_remediation"})

# The functional-review checklist's fixed location (MILESTONE_WORKFLOW.md's
# AWAITING_FUNCTIONAL_REVIEW section names this path) -- never read from
# any work item's own fields, so a hand-edited or foreign path can never
# substitute for it.
FUNCTIONAL_CHECKLIST_PATH = "docs/ACTIVE_MILESTONE.md"

# D-Scoped-Remediation-Acceptance's eleven documented fields for a
# scoped_remediation_acceptance list entry (revision 27, GPT-R40-002: five
# revision 22 originally defined, plus four revision 23 added, plus
# acceptance_record_version, plus functional_checklist_evidence_commit).
SCOPED_REMEDIATION_ACCEPTANCE_FIELDS = frozenset({
    "outstanding_checkpoint_id",
    "active_work_item_id_at_acceptance",
    "implementation_revision",
    "reviewed_implementation_head",
    "technical_approval_review_content_id",
    "functional_checklist_path",
    "functional_checklist_blob",
    "functional_checklist_evidence_commit",
    "user_confirmation",
    "recorded_at",
    "acceptance_record_version",
})

# resolve_scoped_remediation_round's full canonical comparison set (revision
# 27, GPT-R40-002, widened from six to eight fields): maps each compared
# entry field to the live_fields key it is compared against -- the two
# differ in name for exactly one field, since a committed entry's own
# active_work_item_id_at_acceptance is compared against the *current* live
# active_work_item_id, not a field of the same name.
_SCOPED_REMEDIATION_COMPARISON_FIELD_MAP = {
    "outstanding_checkpoint_id": "outstanding_checkpoint_id",
    "implementation_revision": "implementation_revision",
    "reviewed_implementation_head": "reviewed_implementation_head",
    "technical_approval_review_content_id": "technical_approval_review_content_id",
    "functional_checklist_path": "functional_checklist_path",
    "functional_checklist_blob": "functional_checklist_blob",
    "functional_checklist_evidence_commit": "functional_checklist_evidence_commit",
    "active_work_item_id_at_acceptance": "active_work_item_id",
}

_GIT_OBJECT_ID_RE = re.compile(r"^[0-9a-f]{40}$")

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


class DirtyIndexBeforeStagingError(Exception):
    """`/approve-review`'s Git index-isolation precondition: the index
    already differs from `HEAD` *before* this invocation stages anything
    of its own -- generalizes `D-Approval-Commits`' bootstrap
    transaction's own index-isolation precondition (revision 54,
    `GPT-R71-001`) to the ordinary `/approve-review plan` commit. Checked
    *before* this call's own staging, giving a clearer diagnostic (a
    pre-existing dirty index, distinct from this call's own staging
    picking up something unexpected) for a path whose staged content
    genuinely differs from `HEAD`. Deliberately does **not** claim
    coverage for a re-staged, content-*unchanged* path: re-staging bytes
    already identical to `HEAD` produces an index entry indistinguishable
    from `HEAD`'s own tree, not a separate state this or any diff-based
    check could detect -- confirmed empirically
    (`test_restaging_unrelated_unchanged_content_is_a_true_no_op_not_a_gap`)
    after an earlier draft of this docstring claimed otherwise. Also
    confirmed not to reject this work item's own legitimate pending
    intent-to-add paths (`/milestone-plan`'s own staging step, run before
    `/approve-review`): `git diff --cached` does not report intent-to-add
    entries either, so they pass this precondition exactly like a clean
    index, and this call's own subsequent `git add` then stages their
    real content
    (`test_precondition_does_not_reject_this_work_items_own_pending_intent_to_add_paths`)."""


class UnexpectedStagedPathSetError(Exception):
    """`/approve-review`'s post-staging assertion: the Git index's staged
    diff relative to `HEAD` names a path outside the resolved plan-stage
    approval-commit member set -- generalizes `D-Approval-Commits`'
    bootstrap transaction's own "exact staged-set assertion" (revision
    54, `GPT-R71-001`) to the ordinary `/approve-review plan` commit, so
    an unrelated staged-and-changed path (this work item's own leftover,
    or a concurrent work item's, D1) is caught and refused rather than
    silently absorbed by a pathspec-free `git commit`."""


class StagedBlobMismatchError(Exception):
    """The conditional fifth member's *staged* (Git index) blob does not
    match the sha256 `resolve_plan_stage_approval_commit_paths` pinned
    immediately after resolving it -- defense against a race between
    resolution and staging, mirroring `D-Approval-Commits`' bootstrap's
    own pre-commit declaration pin (revision 53, `GPT-R70-001`),
    generalized here."""


class CommittedBlobMismatchError(Exception):
    """The conditional fifth member's *committed* blob does not match the
    sha256 pinned before staging -- defense in depth beyond
    `verify_post_approval_manifest_match`'s own full projection-digest
    check, isolating exactly which member diverged if the two ever
    disagree."""


class CommittedPathSetMismatchError(Exception):
    """The just-created commit's own changed-path set (relative to its
    sole parent) names a path outside the resolved approval-commit member
    set -- `D-Approval-Commits`' bootstrap transaction's own
    "committed-path-set assertion" (missing-test item 347's own named
    requirement), generalized here as a subset check (an expected member
    byte-identical to the parent commit produces no entry at all, which
    is correct, not an omission). Distinct from, and not redundant with,
    `stage_plan_approval_commit_paths`'s pre-commit staged-diff checks:
    those verify the *index* immediately before `git commit` runs, not
    the commit `git commit` actually produces -- a pre-commit or
    commit-msg hook that itself edits and re-stages files between that
    verification and the commit's own tree write would defeat the
    pre-commit checks alone but not this one (the same class of gap
    `D-Approval-Commits` revision 56 independently found and fixed for
    the bootstrap transaction)."""


class NonTopologicalRegistryOrderError(Exception):
    """Raised when a registry's `checkpoints` array does not list every
    checkpoint after all of its own `depends_on` entries (D-Selection
    point 3; missing-test items 29/73)."""


class NoCheckpointReadyError(Exception):
    """D-Selection rule 4: raised when no checkpoint is `COMPLETE` yet none
    is selectable either -- every remaining checkpoint has an incomplete
    dependency. Names the blocked checkpoint and its unmet dependencies
    rather than ever silently idling.

    Carries a structured `checkpoint_id` attribute (revision 27,
    `D-Scoped-Remediation-Acceptance`) naming the specific blocked
    checkpoint, so `registry_completion_status` never has to parse this
    exception's own message text to recover it."""

    def __init__(self, message: str, checkpoint_id: str) -> None:
        super().__init__(message)
        self.checkpoint_id = checkpoint_id


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
    traversal, a symlink at any path component, or any other
    repository-boundary escape is exactly as unusable a "registry" as one
    that is simply missing. Also raised when `registry_path` resolves to
    an existing regular file that is not Git-tracked (`GPT-R33-002`) -- an
    untracked file is absent from commits, review bundles, and approval
    provenance, so it cannot serve as authoritative mirror-check content
    even though it is readable. Also raised for a resolved registry file
    whose content is malformed JSON or a JSON value that is not an object
    (`GPT-R33-004`), instead of letting a raw `JSONDecodeError`/
    `AttributeError` escape this check."""


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


class AmbiguousBundleGenerationRecordTrailerError(Exception):
    """Raised by `discover_bundle_generation_record_commits` when more than
    one commit reachable in `base_commit..head` carries a
    `Workflow-Bundle-Generation-Record` trailer for the same
    `<work_item_id>/<implementation_revision>` value and the first-parent-
    ancestor tie-break does not resolve to exactly one (D-Commit-
    Provenance; WF8B-003 remediation) -- e.g. a genuine second, unrelated
    generation-record commit for the same round."""


class BundleGenerationRecordNotFoundError(Exception):
    """Raised when no `Workflow-Bundle-Generation-Record` commit is
    reachable for the work item's current `implementation_revision` (or
    when `reviewed_implementation_head`/`implementation_revision` is not
    yet set at all) -- `AWAITING_TECHNICAL_APPROVAL`'s provenance-interval
    check has nothing to validate against."""


class HeadPastBundleGenerationRecordError(Exception):
    """Raised when live HEAD is not exactly the discovered
    `Workflow-Bundle-Generation-Record` commit `T` -- a further commit
    landed after `T` carrying no provenance record of its own (D-Commit-
    Provenance condition 1: HEAD must equal `T` exactly, never merely a
    descendant of it)."""


class ReviewedImplementationHeadNotAncestorError(Exception):
    """Raised when `reviewed_implementation_head` is not an ancestor at
    all of the discovered `Workflow-Bundle-Generation-Record` commit `T`
    -- the provenance interval `reviewed_implementation_head..T` does not
    exist."""


class NonFirstParentProvenanceIntervalError(Exception):
    """Raised when the provenance interval `reviewed_implementation_head..T`
    is not a plain first-parent chain -- `reviewed_implementation_head` is
    reachable from `T` only through a merge or a non-first-parent path, or
    a commit inside the interval itself has more than one parent
    (D-Commit-Provenance condition 2: "no widen-the-search fallback")."""


class ProtectedPathInProvenanceIntervalError(Exception):
    """Raised when a non-terminal commit inside the provenance interval
    `reviewed_implementation_head..T` touches an implementation-stage
    `protected` or unclassified path -- only the terminal
    `Workflow-Bundle-Generation-Record` commit itself may exist between
    `reviewed_implementation_head` and live HEAD; every other commit must
    be excluded-only (D-Commit-Provenance condition 3)."""


class MalformedBundleGenerationRecordCommitError(Exception):
    """Raised when the discovered terminal commit `T` fails its own
    ordinary-role commit contract (D-Commit-Provenance condition 4): it
    must touch only `WORKFLOW_STATE.json`, its own `work_items[work_item_id]`
    field changes must be a non-empty subset of `{phase,
    reviewed_implementation_head, implementation_revision, state_revision,
    last_transition}`, and it must carry exactly the two-trailer ordinary
    set (`Workflow-Bundle-Generation-Record`, `Workflow-Work-Item`). The
    recovered/superseded (`Workflow-Supersedes`) role is not implemented
    -- a commit that isn't a clean ordinary-role match is rejected rather
    than silently treated as recovered."""


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


# ---------------------------------------------------------------------------
# D-Scoped-Remediation-Acceptance (new, resolves `WF8B-002`; hardened,
# resolves `GPT-R36-001`/`-002`/`-003`, `GPT-R37-001`/`-002`/`-003`/`-004`,
# `GPT-R38-001`/`-002`/`-003`, `GPT-R39-001`/`-002`, `GPT-R40-001`/`-002`):
# the non-terminal acceptance path for continued-scope remediation landed
# as extra scope on an already-COMPLETE checkpoint while the work item's
# own last checkpoint is still outstanding.
# ---------------------------------------------------------------------------


class RegistryCoverageError(Exception):
    """`complete_work_item`'s fail-closed own-registry resolution guard
    (revision 24, `GPT-R37-001`, correcting revision 23's `GPT-R36-001`
    fix, which was fail-closed only against a caller-supplied dict, not
    against the trust gap of accepting one at all): the work item's own
    declared `registry_path` must resolve via the shared tracked-metadata
    safe-path validator, exist, be readable, parse as a JSON object, and
    declare this exact work item's own `work_item_id` -- any failure
    refuses outright rather than silently treating an unresolvable or
    foreign registry as "nothing to check.\""""


class StalePlanApprovalRegistryReadError(Exception):
    """`resolve_own_registry_completion_status`'s fail-closed binding of
    registry-derived terminality to the current plan approval (revision
    27, `GPT-R43-002`, correcting `RegistryCoverageError`'s own gap: that
    check proves the registry is safe/well-formed/self-declared, never
    that its *bytes* are the ones `plan_approval` actually covers). Raised
    when `plan_approval` is missing or not `CURRENT`, when `registry_path`
    is not named in `plan_approval.review_content_manifest` at all, or
    when the registry's live `git hash-object` blob disagrees with that
    manifest's recorded blob for the same path -- the last case catches
    both a dirty tracked registry edited after approval and a clean,
    committed-but-unapproved registry mutation alike, since `git
    hash-object` always reads the working tree's current bytes regardless
    of commit status. Registry-derived terminality (`complete_work_item`,
    and the advisory pre-flight `/accept-milestone`/
    `/accept-scoped-remediation` both name) must never be trusted while
    this would raise."""


class IncompleteOwnCheckpointsError(Exception):
    """`complete_work_item`'s own-checkpoint-completion block
    (`D-Scoped-Remediation-Acceptance`, resolves `WF8B-002`): raised when
    the work item's own registry has an incomplete checkpoint --
    `MILESTONE_COMPLETE` is unreachable until every one of the item's own
    checkpoints is `COMPLETE`, independent of, and in addition to, the
    pre-existing `incomplete_children` check. Names the outstanding
    checkpoint."""


class AmbiguousScopedRemediationTrailerError(Exception):
    """Raised when more than one commit in range carries the same
    `Workflow-Scoped-Remediation-Acceptance`/`Workflow-Work-Item` trailer
    pair and the first-parent-ancestor tie-break does not resolve to
    exactly one -- the same genuine-ambiguity recovery every other trailer
    scheme in this module already has."""


class AmbiguousFunctionalChecklistTrailerError(Exception):
    """Raised when more than one commit in range carries the same
    `Workflow-Functional-Checklist`/`Workflow-Work-Item` trailer pair and
    the first-parent-ancestor tie-break does not resolve to exactly one --
    a genuine identical-content duplicate reachable by more than one
    first-parent path (e.g. a rebase/cherry-pick), never a routine outcome
    of an ordinary content revision (revision 26, `GPT-R39-001`: the
    trailer value embeds the checklist's own committed blob, so a real
    content revision is a distinct key outright)."""


class MissingFunctionalChecklistEvidenceError(Exception):
    """The pre-commit evidence guard's discoverability check (`GPT-R38-001`):
    raised when no `Workflow-Functional-Checklist` evidence commit is
    discoverable for the exact live round -- names the missing round key
    and `/prepare-functional-review` as the remedy."""


class NonFirstParentFunctionalChecklistEvidenceError(Exception):
    """Raised by `discover_current_functional_checklist_evidence` (revision
    27 correction, `GPT-R41-002`) when evidence commits for the exact live
    round exist somewhere in `base_commit..head`, but none sits on `head`'s
    first-parent chain -- e.g. a merged side branch whose own evidence
    commit was never carried onto the resulting branch's first-parent line.
    Distinct from `MissingFunctionalChecklistEvidenceError`: evidence was
    genuinely prepared, it just never became a first-parent workflow
    transition, so the remedy is not \"run /prepare-functional-review\" but
    an explicit re-provenance action (re-commit the evidence directly on the
    first-parent line). Never silently resolved by ordinary reachable-history
    order -- that would let side-branch evidence become authoritative
    without ever appearing as a first-parent transition."""


class StaleFunctionalChecklistConfirmationError(Exception):
    """The pre-commit evidence guard's confirmation-evidence-binding check
    (revision 27, `GPT-R40-001`): raised when the user's confirmation names
    an evidence commit/blob that is not the round's current evidence --
    names both the confirmed and current identity, and instructs the user
    to review the newer `/prepare-functional-review` report and
    reconfirm. There is deliberately no automatic migration of an existing
    confirmation onto newer evidence."""


class MalformedFunctionalChecklistEvidenceError(Exception):
    """The pre-commit evidence guard's confirmation-evidence-binding check,
    third clause (revision 27, `GPT-R40-001`): raised when a commit's own
    actually-committed content at `functional_checklist_path` does not
    match the blob its own `Workflow-Functional-Checklist` trailer names --
    a defense against a hand-crafted or corrupted trailer."""


class DirtyFunctionalChecklistPathError(Exception):
    """The pre-commit evidence guard's clean-working-tree check
    (`GPT-R37-004`): raised when `functional_checklist_path` has a staged
    or unstaged working-tree change relative to `HEAD` -- a working-tree
    edit the user may have just read and accepted is never silently
    replaced by an older committed blob."""


class ScopedRemediationLiveValueChangedError(Exception):
    """The pre-commit evidence guard's cross-invocation value-agreement
    check, fourth clause: raised when `reviewed_implementation_head` or
    `functional_checklist_path`'s committed content at `HEAD` has changed
    since this same invocation's own earlier read -- the single-invocation
    window `WFR-21`'s existing plan-approval durability guard already
    treats the same way for a different stage."""


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
# `/approve-review plan`'s generic conditional-fifth-member commit
# mechanics (missing-test item 347's "permanent `/approve-review`"
# obligation, `D-Approval-Commits`' "Conditional fifth commit member"):
# stage exactly the resolved member set, pin-then-verify the conditional
# member's identity across staging and the commit, and provide a
# deterministic rollback for a failure after the state write.
# ---------------------------------------------------------------------------


def _read_committed_bytes(repo_root: Path, commit: str, rel_path: str) -> bytes:
    return subprocess.run(
        ["git", "show", f"{commit}:{rel_path}"], cwd=repo_root, capture_output=True, check=True,
    ).stdout


def stage_plan_approval_commit_paths(repo_root: Path, paths: tuple[str, ...]) -> None:
    """Stages exactly `paths` (`resolve_plan_stage_approval_commit_paths`'
    resolved member set -- four or five, per work item, per round) for a
    plain, pathspec-free `git commit` to pick up next. Two checks, both
    against the same `git diff --name-only --cached HEAD` view (which
    reports a path exactly when its staged content genuinely differs from
    `HEAD` -- never for an unstaged intent-to-add marker, and never for a
    re-staged, content-unchanged path, since neither produces a
    distinguishable index state to report; see
    `DirtyIndexBeforeStagingError`'s docstring):

    1. **Pre-staging index-isolation precondition**
       (`DirtyIndexBeforeStagingError`): that view must already be empty
       before this call stages anything of its own -- an unrelated path
       already staged *with real changed content* (this work item's own
       leftover, or a concurrent work item's write, D1) fails closed here,
       before a single `git add` of this call's own runs, giving a
       clearer diagnostic than discovering it only after staging (mirrors
       `D-Approval-Commits`' bootstrap transaction's own index-isolation
       precondition, revision 54, `GPT-R71-001`, generalized here). Never
       rejects this work item's own legitimate pending intent-to-add
       paths (`/milestone-plan`'s own staging step) -- confirmed
       empirically, not merely asserted.
    2. **Post-staging diff assertion** (`UnexpectedStagedPathSetError`):
       after `git add -- <paths>`, that same view must name no path
       outside `paths` -- a *subset* check, not exact equality, since a
       member whose bytes happen to already be byte-identical at `HEAD`
       legitimately produces no diff entry at all (staging it is then a
       correct no-op, not an omission); requiring every expected path to
       appear would reject a coincidentally-unchanged member for no real
       reason.

    Never `git add -A`/`git add .`."""
    dirty = _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo_root)
    already_staged = {line for line in dirty.splitlines() if line}
    if already_staged:
        raise DirtyIndexBeforeStagingError(
            f"Git index already differs from HEAD before staging began: "
            f"{sorted(already_staged)} -- resolve or unstage these first"
        )
    _run(["git", "add", "--", *paths], cwd=repo_root)
    staged = _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo_root)
    actual = {line for line in staged.splitlines() if line}
    expected = set(paths)
    unexpected = actual - expected
    if unexpected:
        raise UnexpectedStagedPathSetError(
            f"staged diff includes unexpected paths {sorted(unexpected)}, outside "
            f"the resolved approval-commit member set {sorted(expected)}"
        )


def verify_staged_blob_sha256(repo_root: Path, rel_path: str, expected_sha256: str) -> None:
    """Re-verifies the conditional fifth member's *staged* (Git index)
    content against the sha256 `resolve_plan_stage_approval_commit_paths`
    pinned right after resolving it -- call after
    `stage_plan_approval_commit_paths`, before creating the commit, to
    close the race window between resolution and staging. Raises
    `StagedBlobMismatchError` on a mismatch."""
    content = subprocess.run(
        ["git", "show", f":{rel_path}"], cwd=repo_root, capture_output=True, check=True,
    ).stdout
    actual = hashlib.sha256(content).hexdigest()
    if actual != expected_sha256:
        raise StagedBlobMismatchError(
            f"{rel_path}: staged blob sha256 {actual} does not match the sha256 "
            f"{expected_sha256} pinned before staging"
        )


def verify_committed_blob_sha256(
    repo_root: Path, commit: str, rel_path: str, expected_sha256: str,
) -> None:
    """Re-verifies the conditional fifth member's *committed* content
    against the sha256 pinned before staging -- defense in depth beyond
    `verify_post_approval_manifest_match`'s own full projection-digest
    check (which would also catch this), isolating exactly which member
    diverged if the two ever disagree. Raises `CommittedBlobMismatchError`
    on a mismatch."""
    actual = hashlib.sha256(_read_committed_bytes(repo_root, commit, rel_path)).hexdigest()
    if actual != expected_sha256:
        raise CommittedBlobMismatchError(
            f"{rel_path} at {commit}: committed blob sha256 {actual} does not "
            f"match the sha256 {expected_sha256} pinned before staging"
        )


def assert_committed_path_set_matches(
    repo_root: Path, commit: str, expected_paths: tuple[str, ...],
) -> None:
    """The structural half of item 347's committed-path-set assertion:
    `commit`'s own changed-path set relative to its sole parent
    (`git diff-tree --no-commit-id --name-only -r`) must name no path
    outside `expected_paths` -- a *subset* check, not exact equality, for
    the same reason `stage_plan_approval_commit_paths`'s own post-staging
    assertion is a subset check: an expected member whose bytes are
    byte-identical to the parent commit produces no entry in the commit's
    own diff at all (not a distinct, detectable tree state), so requiring
    every expected path to appear would reject a coincidentally-unchanged
    member for no real reason (confirmed empirically, the same way that
    same false assumption was caught and corrected for the pre-commit
    check earlier in this fix's own development --
    `test_declaration_never_committed_resolves_five_member_set` first
    caught this one too). Call immediately after the commit is created,
    alongside `verify_post_approval_manifest_match` (step 6a): this
    checks the commit's own tree shape, a hook-editing scenario the
    pre-commit staging checks alone cannot see (a pre-commit/commit-msg
    hook can still edit and re-stage a file after
    `stage_plan_approval_commit_paths` already verified the index, before
    `git commit` writes the final tree). Raises
    `CommittedPathSetMismatchError` naming the unexpected paths and the
    full expected set on a violation."""
    changed = _run(["git", "diff-tree", "--no-commit-id", "--name-only", "-r", commit], cwd=repo_root)
    actual = {line for line in changed.splitlines() if line}
    expected = set(expected_paths)
    unexpected = actual - expected
    if unexpected:
        raise CommittedPathSetMismatchError(
            f"commit {commit}'s own changed-path set includes unexpected paths "
            f"{sorted(unexpected)}, outside the resolved approval-commit member "
            f"set {sorted(expected)}"
        )


def rollback_plan_approval_write(
    repo_root: Path, state_path: Path, pre_write_bytes: bytes, *, commit_created: bool,
) -> None:
    """Deterministic recovery for a plan-approval attempt that fails after
    `apply_plan_approval`'s state write but before
    `verify_post_approval_manifest_match` succeeds (`/approve-review`
    step 6b): `git reset` back to the commit immediately before this
    invocation's own commit if one was created (undoing exactly that one
    commit, never an earlier one -- mixed reset, so the working tree is
    left untouched, only the branch ref and the index move), or a bare
    `git reset` (unstage back to `HEAD`) if no commit was created yet;
    then restores `state_path`'s working-tree bytes to `pre_write_bytes`
    exactly -- the bytes captured immediately before `apply_plan_approval`
    ran, this invocation's own first durable mutation. Leaves the
    repository byte-identical to its state immediately before that first
    mutation: no partial commit, no partial state write, safe to retry
    from a fresh operator session. Never touches any other file this
    invocation did not itself stage."""
    if commit_created:
        _run(["git", "reset", "HEAD~1"], cwd=repo_root)
    else:
        _run(["git", "reset", "HEAD"], cwd=repo_root)
    (repo_root / state_path).write_bytes(pre_write_bytes)


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
        f"dependencies {unmet}",
        checkpoint_id=blocked["id"],
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


WORKTREE_IDENTITY_LOCK_PATH = Path(".ai-review/runtime/WORKTREE_IDENTITY.lock")


def identity_document_lock_path(repo_root: Path, path: Path = WORKTREE_IDENTITY_LOCK_PATH) -> Path:
    return repo_root / path


@contextlib.contextmanager
def identity_document_lock(repo_root: Path, *, lock_path: Path = WORKTREE_IDENTITY_LOCK_PATH):
    """`D-Checkpoint-Ownership` ("Because it is now load-bearing, its own
    writes must be serialized", `OPUS-R86-002`): `WORKTREE_IDENTITY.json`
    holds every work item's entry in one document, and a plain
    read-modify-write lost an entry in 12/12 threaded trials -- the window
    held open by the `git` subprocesses `_hash_dirty_paths` spawns between
    the read and the write. Every writer of this document (this module's
    own `write_worktree_identity`/`repair_worktree_identity`, and any
    future one) must hold this lock across its whole
    load -> validate -> mutate -> publish sequence.

    A **stable, never-unlinked** object beside the document, per-worktree
    (not per work item, since that is the scope of the document being
    protected), `fcntl.flock`-serialized. Advisory, so it is defense in
    depth layered under the publish primitive's own atomicity, not a
    substitute for it. Released by the kernel on process death, so a crash
    while holding it cannot become a second class of permanent lockout.
    "Never unlinked" is a property of this lock's own writers, not of the
    filesystem: an external wipe of `.ai-review/` (`git clean -xdf`, a
    stale worktree cleanup) removes it like anything else and a
    recreated file gets a new inode, so no serialization claim survives
    such a wipe -- that is an identity-record-destroying event this
    design already treats as the operator's own act.

    It is a **leaf lock**: never held across a `WORKFLOW_STATE.json`
    write, so it can never be held at the same time as `D1`'s state-file
    lock, and no other lock in this design is ever acquired while it is
    held."""
    full = identity_document_lock_path(repo_root, lock_path)
    full.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(full, os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0), 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        yield
    finally:
        os.close(fd)


def _publish_worktree_identity(full_path: Path, document: dict) -> None:
    """The **single** `os.replace` publication (`OPUS-R86-005`): the
    snapshot is built and validated in memory before this is ever called,
    and this is the only statement in the sequence that ever touches the
    final pathname -- a same-directory temp file, written whole, then
    `os.replace`d over the target. A writer that writes the final
    pathname directly and only *then* stages/replaces a temp file (as an
    earlier draft here did) satisfies no atomicity property at all; this
    is the corrected, single-write form."""
    full_path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(
        dir=str(full_path.parent), prefix=f".{full_path.name}-", suffix=".tmp",
    )
    try:
        with os.fdopen(fd, "w") as handle:
            handle.write(json.dumps(document, indent=2, sort_keys=True) + "\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_name, full_path)
    except BaseException:
        Path(tmp_name).unlink(missing_ok=True)
        raise


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

    The whole load -> validate -> mutate -> publish sequence runs inside
    `identity_document_lock` (`OPUS-R86-002`) and publishes by the single
    `os.replace` `_publish_worktree_identity` performs (`OPUS-R86-005`),
    so two work items' interleaved writes -- the routine, expected case --
    can no longer lose an entry to a lost update. Returns the full,
    now-validated `WORKTREE_IDENTITY.json` document."""
    full_path = repo_root / path
    with identity_document_lock(repo_root):
        existing = _load_json(full_path) or {}
        repo_root_id, git_common_dir, worktree_root = _git_identity(repo_root)
        existing["repo_root"] = repo_root_id
        existing["git_common_dir"] = git_common_dir
        existing["worktree_root"] = worktree_root
        expected = existing.setdefault("expected_dirty_paths_by_work_item", {})
        expected[work_item_id] = _hash_dirty_paths(repo_root)
        existing["generated_at"] = now
        validate_worktree_identity(existing)
        _publish_worktree_identity(full_path, existing)
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
# WF8b: D-Checkpoint-Ownership -- the origination reference only (revisions
# 69-72 of docs/ai-workflow/WORKFLOW_V2_PLAN.md's "The origination
# reference" and "The read fails closed, and the partition is total").
#
# This is a bounded first production slice, not the full mechanism: no
# shared claim record, no mutation/handoff guard, no explicit takeover, no
# adoption. It answers exactly one question -- can this worktree prove a
# locally `IN_PROGRESS` checkpoint was never observably supplied by a
# checkout, merge, or reset from committed history? -- and raises rather
# than deciding RESUME/FRESH/adopt itself, since those outcomes depend on
# the still-unimplemented shared claim. See "Where the check belongs, and
# the ordering" in the plan for the full reconciliation table this slice
# is one input to.
# ---------------------------------------------------------------------------


class CheckpointOriginationUnprovableError(Exception):
    """Raised when a checkpoint this worktree records `IN_PROGRESS` cannot
    be proven to have been originated here: either its `IN_PROGRESS` is
    observed at some commit in the origination reference (a checkout,
    merge, or reset could have supplied it) or that reference's read is
    itself undecidable at some commit. Per the plan's precedence rule, an
    observed route always wins over an undecidable one found in the same
    scan. Carries `.evidence` (`route`, the commit and status/reason, the
    state path, and how many commits were examined) for the caller to
    report. D-Checkpoint-Ownership's explicit-takeover escape is not yet
    implemented, so this refusal currently has no automated recovery --
    the caller must stop and report."""

    def __init__(self, message: str, *, evidence: dict):
        super().__init__(message)
        self.evidence = evidence


def origination_reference_commits(repo_root: Path, state_rel_path: str) -> list[str]:
    """`D-Checkpoint-Ownership`'s origination reference, stated exactly:
    every commit reachable from every ref in the repository (`--all`,
    every linked worktree's own `HEAD` included, detached included) that
    is not TREESAME to a parent for the state document (`--full-history`
    retains merges and follows every parent, so a commit reachable only
    through a merge's non-mainline side for this path is never silently
    dropped by History Simplification's default pruning). Raises
    `CheckpointOriginationUnprovableError` if the invocation itself cannot
    be resolved -- an unavailable reference is refused, never read as
    empty."""
    try:
        out = _run(
            ["git", "rev-list", "--all", "--full-history", "--", state_rel_path],
            cwd=repo_root,
        )
    except subprocess.CalledProcessError as exc:
        raise CheckpointOriginationUnprovableError(
            f"origination reference could not be resolved for {state_rel_path!r}: {exc}",
            evidence={"route": "reference_unresolvable", "state_rel_path": state_rel_path},
        ) from exc
    return [line for line in out.splitlines() if line]


def _checkpoint_status_at_commit(
    repo_root: Path, commit: str, work_item_id: str, checkpoint_id: str, state_rel_path: str,
) -> tuple[str, dict]:
    """One commit's contribution to the origination read, partitioned
    exactly per the plan's "The read fails closed, and the partition is
    total" table. Returns `(outcome, detail)`:

    - `"in_progress"` -- the checkpoint's status is decidably `IN_PROGRESS`
      at this commit (the observed refusal route);
    - `"undecidable"` -- this commit's contribution cannot be decided:
      unlistable tree/unresolvable commit, a state path present but not a
      readable regular-file blob, an unparseable or non-object document, a
      present-but-non-object member at any of the four levels
      (`work_items`/the work item/`checkpoints`/the checkpoint entry), or
      a checkpoint entry whose `status` is absent, not a string, or
      outside `CHECKPOINT_STATUSES`;
    - `"decidable"` -- this commit admits: the state path, or one of the
      four keys, is decidably absent from this commit, or the checkpoint's
      status is decidably something other than `IN_PROGRESS`.

    Absence at a level is always a missing **key**, checked with `in`,
    never a falsy or non-object value -- a present `null`/list/string/
    number at any of the four levels is `"undecidable"`, not absence."""
    listing = subprocess.run(
        ["git", "ls-tree", commit, "--", state_rel_path],
        cwd=repo_root, capture_output=True, text=True,
    )
    if listing.returncode != 0:
        return "undecidable", {"commit": commit, "reason": "tree unlistable or commit unresolvable"}
    line = listing.stdout.strip()
    if not line:
        return "decidable", {"commit": commit, "reason": "state path absent from tree"}
    meta, _, _ = line.partition("\t")
    mode = meta.split()[0]
    blob_sha = meta.split()[2]
    if mode not in ("100644", "100755"):
        return "undecidable", {"commit": commit, "reason": f"state path is not a regular-file blob (mode {mode})"}
    blob = subprocess.run(["git", "cat-file", "-p", blob_sha], cwd=repo_root, capture_output=True, text=True)
    if blob.returncode != 0:
        return "undecidable", {"commit": commit, "reason": "blob could not be read"}
    try:
        doc = json.loads(blob.stdout)
    except json.JSONDecodeError:
        return "undecidable", {"commit": commit, "reason": "state document unparseable"}
    if not isinstance(doc, dict):
        return "undecidable", {"commit": commit, "reason": "state document is not an object"}

    if "work_items" not in doc:
        return "decidable", {"commit": commit, "reason": "no work_items key"}
    work_items = doc["work_items"]
    if not isinstance(work_items, dict):
        return "undecidable", {"commit": commit, "reason": "work_items is not an object"}

    if work_item_id not in work_items:
        return "decidable", {"commit": commit, "reason": f"no {work_item_id!r} entry"}
    work_item = work_items[work_item_id]
    if not isinstance(work_item, dict):
        return "undecidable", {"commit": commit, "reason": f"{work_item_id!r} entry is not an object"}

    if "checkpoints" not in work_item:
        return "decidable", {"commit": commit, "reason": "no checkpoints key"}
    checkpoints = work_item["checkpoints"]
    if not isinstance(checkpoints, dict):
        return "undecidable", {"commit": commit, "reason": "checkpoints is not an object"}

    if checkpoint_id not in checkpoints:
        return "decidable", {"commit": commit, "reason": f"no {checkpoint_id!r} entry"}
    entry = checkpoints[checkpoint_id]
    if not isinstance(entry, dict):
        return "undecidable", {"commit": commit, "reason": f"{checkpoint_id!r} entry is not an object"}

    status = entry.get("status")
    if not isinstance(status, str) or status not in CHECKPOINT_STATUSES:
        return "undecidable", {"commit": commit, "reason": f"status is not a readable checkpoint status ({status!r})"}
    if status == "IN_PROGRESS":
        return "in_progress", {"commit": commit, "status": status}
    return "decidable", {"commit": commit, "status": status}


def checkpoint_origination_provable(
    repo_root: Path, work_item_id: str, checkpoint_id: str, *, state_rel_path: str | None = None,
) -> dict:
    """Whether this worktree can prove it originated `checkpoint_id`'s
    local `IN_PROGRESS`, per `D-Checkpoint-Ownership`'s origination
    reference. Scans every commit `origination_reference_commits`
    enumerates; a decidable `IN_PROGRESS` observation anywhere refuses
    unconditionally -- no supersession, no recency, no scoping to a
    lifecycle instance, the reduction rule stated normatively in the plan
    -- and, when both an observed and an undecidable commit exist in the
    same reference, the observed route is reported in preference to the
    undecidable one. Returns an evidence dict on success (admit); raises
    `CheckpointOriginationUnprovableError` (carrying the same evidence
    shape) on refusal.

    This function answers only the origination question. It deliberately
    does not itself decide `RESUME` vs `FRESH` vs adoption -- those
    outcomes, per the plan's reconciliation table, also depend on the
    shared claim record and mutation guard, neither of which this slice
    implements."""
    state_rel_path = state_rel_path or DEFAULT_STATE_PATH.as_posix()
    commits = origination_reference_commits(repo_root, state_rel_path)
    if not commits:
        return {
            "decision": "admit", "route": "no_reference_commits",
            "state_rel_path": state_rel_path, "examined_commits": 0,
        }

    observed = None
    undecidable = None
    for commit in commits:
        outcome, detail = _checkpoint_status_at_commit(repo_root, commit, work_item_id, checkpoint_id, state_rel_path)
        if outcome == "in_progress" and observed is None:
            observed = detail
        elif outcome == "undecidable" and undecidable is None:
            undecidable = detail

    if observed is not None:
        raise CheckpointOriginationUnprovableError(
            f"{work_item_id!r} checkpoint {checkpoint_id!r} is IN_PROGRESS at commit "
            f"{observed['commit']} in the origination reference -- a checkout, merge, or "
            f"reset could have supplied this worktree's own IN_PROGRESS state, so automatic "
            f"resume cannot be trusted (D-Checkpoint-Ownership's explicit-takeover escape is "
            f"not yet implemented -- stop and reconcile manually)",
            evidence={
                "route": "observed", "commit": observed["commit"], "status": "IN_PROGRESS",
                "state_rel_path": state_rel_path, "examined_commits": len(commits),
            },
        )
    if undecidable is not None:
        raise CheckpointOriginationUnprovableError(
            f"{work_item_id!r} checkpoint {checkpoint_id!r}'s origination reference contains "
            f"an undecidable commit ({undecidable['commit']}: {undecidable['reason']}) -- "
            f"origination is unprovable, not proved",
            evidence={
                "route": "undecidable", "commit": undecidable["commit"],
                "reason": undecidable["reason"], "state_rel_path": state_rel_path,
                "examined_commits": len(commits),
            },
        )
    return {
        "decision": "admit", "route": "scanned_all_decidable",
        "state_rel_path": state_rel_path, "examined_commits": len(commits),
    }


# ---------------------------------------------------------------------------
# WF8b: D-Checkpoint-Ownership -- the shared claim record and the
# mutation/handoff guard ("The record" and "Fencing: the mutation/handoff
# guard" in docs/ai-workflow/WORKFLOW_V2_PLAN.md, revisions 63-72),
# plus the explicit takeover and the abandoned-guard recovery it depends
# on. Authored fresh against the approved revision-80 text; the unwired
# dry-run prototype (docs/ai-workflow/dry-run/wf8b-s14-repro/
# checkpoint_ownership.py) is reference/reproduction evidence only.
#
# Scope, deliberately bounded per this session's own direction, continuing
# the prior session's origination-reference slice: no `resolve_ownership`/
# `classify_selection` (the `/milestone-implement` step 1c/1d/1f wiring
# lives under "Where the check belongs, and the ordering"), and no
# `WFR-66` identity-query enforcement (a distinct implementation surface,
# `authorize_identity_reference_gap`). Those remain future WF8b scope.
# `adopt_claim` itself ("Reconciling the two authorities") is defined
# further below, after the claim-publication and mutation-guard
# primitives it composes. This slice makes
# `take_over_claim`/`recover_abandoned_destructive_guard`/`adopt_claim`
# available and independently tested, which is the prerequisite the plan
# names for actually taking `v2-1-dry-run`'s interrupted `S-CP3` over for
# real.
# ---------------------------------------------------------------------------


class CheckpointOwnedByOtherWorktreeError(Exception):
    """Raised when claiming, asserting, or releasing a work item's
    checkpoint that is already claimed by a different worktree of this
    repository."""


class CheckpointOwnershipStateMismatchError(Exception):
    """Raised when this worktree's own claim cannot be reconciled with
    this worktree's own state -- reported, never guessed."""


class CheckpointOwnershipUnavailableError(Exception):
    """Raised when the shared claim/guard record cannot be read or
    written -- fails closed rather than proceeding unprotected."""


class CheckpointClaimTakeoverRefusedError(Exception):
    """Raised when an explicit takeover, guard clearance, or abandoned-
    guard recovery is attempted without the exact authorization its own
    contract requires."""


class CheckpointNotInProgressLocallyError(Exception):
    """Raised by `adopt_claim` when this worktree's own local
    `WORKFLOW_STATE.json` does not record the checkpoint being adopted as
    `IN_PROGRESS` -- adoption never invents an ownership fact where none
    exists."""


CLAIMS_RELDIR = "ai-workflow/checkpoint-claims"
CLAIM_SCHEMA_VERSION = 3

ORDINARY = "ordinary"
DESTRUCTIVE = "destructive"
GUARD_STEP_CLASSES = frozenset({ORDINARY, DESTRUCTIVE})

ABSENT_OBSERVATION = "absent"
# `OPUS-R83-002`: an observation of a record whose *bytes* cannot be read
# is hashed under its own domain tag, so it can never collide with the
# sha256 of some real record's content -- an authorization bound to
# "there is a symlink here" is never satisfiable by a byte-readable
# record, or the reverse.
UNREADABLE_OBSERVATION_DOMAIN = b"unreadable-checkpoint-record-v1\x00"
# Which unreadable kinds a rotation (`os.rename` onto the name) can
# actually replace. A symlink and an unreadable regular file are replaced
# by the rename itself, which never follows the link and never writes
# through it. A directory is not: `rename` refuses, and this design never
# removes a directory it did not create.
REPLACEABLE_UNREADABLE_KINDS = frozenset({"symlink", "unreadable-file", "not-a-regular-file"})


def _worktree_git_dir(repo_root: Path) -> str:
    """`git rev-parse --absolute-git-dir` -- the per-worktree admin
    directory. Recorded as **diagnostic** identity only, never as the
    ownership key: it survives `git worktree move`, which the recorded
    `worktree_root` does not, and that is exactly what lets a takeover
    tell "the holder was deleted" from "the holder was relocated" instead
    of guessing."""
    return _run(["git", "rev-parse", "--absolute-git-dir"], cwd=repo_root).strip()


def claims_dir(repo_root: Path) -> Path:
    _, common_dir, _ = _git_identity(repo_root)
    return Path(common_dir) / CLAIMS_RELDIR


def claim_path(repo_root: Path, work_item_id: str) -> Path:
    """One file per work item, named by digest so no `work_item_id`
    value -- including `../../escape`, `a/b/c`, or an absolute path --
    can address anything outside the claims directory (the same "token,
    not a path" discipline `D-Bundle-Manifest`'s `bundles/<token>`
    already uses)."""
    token = hashlib.sha256(work_item_id.encode()).hexdigest()
    return claims_dir(repo_root) / f"{token}.json"


def guard_path(repo_root: Path, work_item_id: str) -> Path:
    """The mutation/handoff guard (`GPT-R81-001`): one **fixed** pathname
    per work item, deliberately not token-scoped, because its whole
    purpose is to be the single object an owner's mutation and a
    takeover's rotation contend for."""
    token = hashlib.sha256(work_item_id.encode()).hexdigest()
    return claims_dir(repo_root) / f"{token}.lease"


def guard_mutation_lock_path(repo_root: Path, work_item_id: str) -> Path:
    """`OPUS-R83-001`: the stable object every guard **mutation**
    serializes on -- deliberately a different file from the guard itself,
    and deliberately created once and never unlinked. Locking the guard
    file would be useless for exactly the reason the finding exists: the
    guard's whole lifecycle is create-and-remove, and two processes
    holding `flock` on two different inodes that briefly shared one
    pathname are not serialized at all."""
    token = hashlib.sha256(work_item_id.encode()).hexdigest()
    return claims_dir(repo_root) / f"{token}.guardlock"


@contextlib.contextmanager
def guard_mutation_lock(repo_root: Path, work_item_id: str):
    """`D1`'s process-scoped `fcntl.flock` primitive, held across a whole
    guard mutation so **compare-and-remove is one indivisible step**
    rather than two statements a preemption can be scheduled between
    (`OPUS-R83-001`).

    Not the fence and must never be mistaken for one -- held for a
    handful of syscalls entirely inside one guard operation, while the
    *guard* is what spans a mutation window. Never held across a
    `WORKFLOW_STATE.json` write, so it is never held at the same time as
    `D1`'s state-file lock and the existing guard-then-`flock` ordering
    is untouched. Advisory, so `os.link`'s `EEXIST` exclusivity is
    retained underneath it as defense in depth rather than replaced by
    it. Released by the kernel on process death."""
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
    """The claims directory and the claim/guard file must all be real
    objects -- never follow a link out of the claims directory. A claim
    reached through one is not this repository's coordination state,
    whatever it contains."""
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
        # A directory `open`s successfully and fails at `fdopen`/`read`
        # with `EISDIR` -- refuse with the declared error rather than an
        # undeclared `IsADirectoryError` escaping to the caller.
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
            f"{path} has an unsupported shape/schema_version (expected {CLAIM_SCHEMA_VERSION})")
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
    """The one read every ownership decision performs. `None` means
    unclaimed. Every other failure mode -- unreadable, torn, wrong
    schema, wrong work item, reached through a symlink -- raises rather
    than returning `None`, so an undecidable claim can never be mistaken
    for an absent one."""
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


def describe_unreadable_record(path: Path) -> dict | None:
    """What `lstat` alone can say about a record whose bytes cannot be
    read (`OPUS-R83-002`). Returns `None` when the record is absent or
    genuinely byte-readable -- in which case `observation_id` over its
    bytes applies. Derived from durable, re-verifiable facts only, and
    for a symlink from the raw link target rather than anything read
    *through* it: the target is never opened, so an off-tree file is
    never touched."""
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
    """The immutable identity of *the exact record a human reviewed*
    (`GPT-R81-002`). Defined over raw bytes rather than parsed content,
    so an unreadable or torn record has an equally binding observation
    instead of falling back to a reusable generic authorization.
    `"absent"` is itself an observation: authorizing a takeover of "no
    claim" must not survive somebody publishing one in the meantime."""
    if raw is None:
        return ABSENT_OBSERVATION
    return hashlib.sha256(raw).hexdigest()


def observe_claim(repo_root: Path, work_item_id: str) -> tuple[str, dict | None, str | None, dict | None]:
    """Read the claim **and** its observation id in one pass, reporting
    rather than raising on an undecidable record: returns
    `(observation_id, parsed_claim_or_None, error_or_None,
    unreadable_descriptor_or_None)`. `resolve_claim` still fails closed
    for ordinary ownership decisions; this exists for the operations that
    must be able to *describe* a record they refuse to act on
    (`takeover_evidence` and a rotation's own re-read)."""
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


def _build_claim_record(repo_root: Path, work_item_id: str, checkpoint_id: str, now: str,
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
        # `GPT-R81-001`: ownership is durable data a fresh session can
        # present, not a process-lifetime property. Minted here and
        # nowhere else; a takeover rotates it, which is what makes the
        # displaced owner's next assertion fail.
        "owner_token": secrets.token_hex(16),
        "takeover_count": takeover_count,
        "previous_owner_tokens": list(previous_owner_tokens),
    }
    if taken_over_from is not None:
        record["taken_over_from"] = taken_over_from
    return record


def _stage_claim_payload(path: Path, record: dict, *, allow_unreadable_target: bool = False) -> Path:
    """Stage the payload in the claims directory. The claims **directory**
    must always be a real directory -- that assertion is never relaxed.
    `allow_unreadable_target` relaxes the assertion on the final *name*
    only, for the two authorized operations that exist to replace an
    undecidable record (`OPUS-R83-002`): safe because staging happens at
    a fresh temp name and publication is `os.rename` onto the final one,
    which replaces the link, never follows it, never writes through it."""
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


def _publish_claim_replacing(path: Path, record: dict, *, allow_unreadable_target: bool = False) -> None:
    """The takeover's/recovery's publication. `os.rename` over the
    existing name is atomic and never leaves the path absent, so an
    authorized rotation that fails mid-way leaves the *previous* claim in
    force rather than silently unprotecting the work item. Never used for
    ordinary acquisition, which must fail rather than replace."""
    tmp = _stage_claim_payload(path, record, allow_unreadable_target=allow_unreadable_target)
    try:
        os.rename(tmp, path)
    except OSError as exc:
        tmp.unlink(missing_ok=True)
        raise CheckpointOwnershipUnavailableError(
            f"cannot publish the replacement claim at {path} ({exc}) -- the previous claim is "
            f"left in force; nothing was released"
        ) from exc


def _publish_claim_exclusive(path: Path, record: dict) -> None:
    """Atomic in **both** senses -- complete content, and create-if-absent.
    Write the whole payload to a temp file in the same directory, then
    `os.link` it into place: the link either creates the final name with
    fully-written content, or fails `EEXIST` because somebody else won.
    There is no window in which a reader can observe a partially-written
    claim, so a crash can never leave a record that fails closed for
    everybody."""
    tmp = _stage_claim_payload(path, record)
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
        _publish_claim_exclusive(path, record)
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


def claim_checkpoint(repo_root: Path, work_item_id: str, checkpoint_id: str, *, now: str) -> dict:
    """Acquire a fresh-start claim. Intended to be called at step 1d
    **after** this worktree's own identity record is established and
    **before** `transition_checkpoint_in_progress` -- both orderings are
    load-bearing, not stylistic, per "Where the check belongs, and the
    ordering" (future WF8b scope wires this call site; this function is
    the primitive it will call)."""
    record = _build_claim_record(repo_root, work_item_id, checkpoint_id, now)
    return _claim_or_refuse(repo_root, work_item_id, record)


def release_checkpoint(repo_root: Path, work_item_id: str, checkpoint_id: str, *,
                       owner_token: str, now: str = "release") -> None:
    """Release this session's own claim -- intended to be called at step
    1f **after** the checkpoint commit exists.

    A **compare-and-delete**, not a read-then-unlink (`GPT-R81-001`): the
    token the caller holds is asserted *inside* the mutation guard and
    the unlink happens in that same window, so a takeover interleaved
    between the read and the delete can no longer let a displaced owner
    remove the replacement owner's claim."""
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
        raise CheckpointOwnershipUnavailableError(
            f"cannot release {work_item_id!r}'s claim ({exc}) -- the checkpoint's own "
            f"completion is unaffected; the claim is released on the next invocation from "
            f"this worktree once the completion is durable"
        ) from exc


# ---------------------------------------------------------------------------
# Adoption -- "Reconciling the two authorities" (revisions 63-72). The
# one-time migration path for a checkpoint interrupted before this
# mechanism existed, including the real S-CP3 -- and the reason it can be
# protected without recreating it. Authored fresh against the approved
# revision-80 text; the unwired dry-run prototype's revision-68/69 draft
# (checkpoint_ownership.py) checked origination against
# `committed_checkpoint_status` alone, which the plan's own revision-69
# correction superseded before this slice was written -- this function
# calls `checkpoint_origination_provable` instead, never the superseded
# check.
# ---------------------------------------------------------------------------


def adopt_claim(repo_root: Path, work_item_id: str, checkpoint_id: str, *, now: str,
                state_rel_path: str | None = None) -> dict:
    """Publish a claim for a checkpoint this worktree already holds
    `IN_PROGRESS` but never claimed. Guarded so it can only ever run in
    the originating worktree:

    1. this worktree's own local `WORKFLOW_STATE.json` -- the working
       tree, never `HEAD` -- must actually record `checkpoint_id`
       `IN_PROGRESS` for `work_item_id`;
    2. `verify_dirty_resume_safety` must pass -- **first**, so the
       classes a foreign worktree sees here are unchanged;
    3. `checkpoint_origination_provable` must admit: that same
       `IN_PROGRESS` must be absent from the origination reference, and
       that read must be decidable;
    4. the same origination test is **re-evaluated at publication**,
       under `guard_mutation_lock`, not only at evidence time -- no
       observable gap separates the final check from the write;
    5. `_claim_or_refuse`'s own foreign-claim/state-mismatch refusals
       apply unchanged -- no foreign claim may exist.

    Idempotent: an existing self-claim for this exact checkpoint is
    returned unchanged (`_claim_or_refuse`'s own idempotence, unmodified
    here). Writes **no** authoritative state -- not
    `WORKFLOW_STATE.json`, not `WORKTREE_IDENTITY.json`, not the working
    tree -- and never invents an ownership fact where none exists. Runs
    automatically inside the future step-1c resume wiring, and is
    separately invocable as an explicit setup operation for an
    interrupted checkpoint that must be protected *without* being
    resumed -- exactly `S14`'s need."""
    state_rel_path = state_rel_path or DEFAULT_STATE_PATH.as_posix()
    state = _load_json(repo_root / Path(state_rel_path)) or {}
    work_items = state.get("work_items")
    work_item = work_items.get(work_item_id) if isinstance(work_items, dict) else None
    status = None
    if isinstance(work_item, dict):
        checkpoints = work_item.get("checkpoints")
        if isinstance(checkpoints, dict):
            entry = checkpoints.get(checkpoint_id)
            if isinstance(entry, dict):
                status = entry.get("status")
    if status != "IN_PROGRESS":
        raise CheckpointNotInProgressLocallyError(
            f"{work_item_id!r} checkpoint {checkpoint_id!r} is not IN_PROGRESS in this "
            f"worktree's own {state_rel_path} (status: {status!r}) -- there is nothing to "
            f"adopt; adoption never invents an ownership fact where none exists"
        )

    verify_dirty_resume_safety(repo_root, work_item_id)
    checkpoint_origination_provable(repo_root, work_item_id, checkpoint_id, state_rel_path=state_rel_path)

    record = _build_claim_record(repo_root, work_item_id, checkpoint_id, now, adopted=True)
    with guard_mutation_lock(repo_root, work_item_id):
        checkpoint_origination_provable(repo_root, work_item_id, checkpoint_id, state_rel_path=state_rel_path)
        return _claim_or_refuse(repo_root, work_item_id, record)


def committed_checkpoint_status(repo_root: Path, work_item_id: str, checkpoint_id: str,
                                *, state_rel_path: str | None = None) -> str | None:
    """The checkpoint's status in the `WORKFLOW_STATE.json` **committed at
    `HEAD`** -- never the working tree's. The difference between "the
    checkpoint is done" and "somebody typed that it is done"."""
    state_rel_path = state_rel_path or DEFAULT_STATE_PATH.as_posix()
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
# Fencing: the mutation/handoff guard (`GPT-R81-001`)
# ---------------------------------------------------------------------------


def read_guard(repo_root: Path, work_item_id: str) -> dict | None:
    """The guard body, or `None` if the guard is not held. Fails closed
    on a torn or symlinked guard exactly as the claim does -- an
    undecidable guard is never read as an absent one."""
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
    _publish_claim_exclusive(guard_path(repo_root, work_item_id), body)


def _current_owner_token(repo_root: Path, work_item_id: str) -> tuple[str | None, bool]:
    """`(current owner_token, epoch_is_decidable)`. `None` with `True`
    means there is genuinely no claim, so **any** guard is superseded. An
    undecidable claim returns `False`, and a guard whose epoch cannot be
    judged is then never reclaimed -- the fail-closed direction."""
    try:
        claim = resolve_claim(repo_root, work_item_id)
    except CheckpointOwnershipUnavailableError:
        return None, False
    return (claim.get("owner_token") if claim is not None else None), True


def acquire_guard(repo_root: Path, work_item_id: str, *, holder_owner_token: str | None,
                  checkpoint_id: str | None, step: str, step_class: str, now: str,
                  role: str = "owner", authorized_lease_id: str | None = None) -> dict:
    """One acquisition attempt. **No retry loop, no timeout, no wall
    clock** -- every branch decides from durable data alone.

    - **superseded epoch** (`holder_owner_token` is not the claim's
      current token): the guard was provably left by a session whose
      ownership has already been rotated away, since only a rotation
      changes that token and a rotation only ever happens while holding
      this same guard. Reclaimed with no authorization, then acquisition
      is retried exactly **once**. Deliberately not narrowed to a
      worktree, so no post-takeover recovery is stranded.
    - **`role="owner"`, same token *and* same worktree**: the caller's
      own leftover guard from an interrupted earlier step. Reclaimed and
      retried once; the window's first act is `assert_claim_owner`, so a
      stale belief about ownership is caught immediately regardless.
      `holder_worktree_git_dir` is a required conjunct (`OPUS-R82-002`):
      the token is world-readable coordination data any worktree can
      read out of the claim, so token equality alone would let a
      *foreign* worktree reclaim a live `"destructive"` guard.
    - **`role="takeover"`, current epoch**: `"destructive"` refuses
      unconditionally. `"ordinary"` releases only when the observed
      `lease_id` is exactly the one the authorization quoted; a
      *different* `lease_id` proves the owner released and re-acquired,
      i.e. is demonstrably live, and refuses.
    - **`role="recovery"`, current epoch** (`OPUS-R82-001`): the one path
      that may reclaim a `"destructive"` guard from another worktree,
      reachable only from `recover_abandoned_destructive_guard`, which
      has already established the holder worktree is no longer
      registered and bound the authorization to both durable
      observations.
    - anything else refuses, naming the held guard."""
    body = {
        "lease_id": secrets.token_hex(16),
        "holder_owner_token": holder_owner_token,
        "holder_worktree_git_dir": _worktree_git_dir(repo_root),
        "work_item_id": work_item_id,
        "checkpoint_id": checkpoint_id,
        "step": step,
        "step_class": step_class,
        "acquired_at": now,
    }
    if step_class not in GUARD_STEP_CLASSES:
        raise CheckpointOwnershipUnavailableError(f"unknown guard step_class {step_class!r}")
    # `OPUS-R83-001`: the reclaim-and-republish sequence below removes a
    # guard and publishes another, and those two steps must be
    # indivisible with respect to the `lease_id` this session compared
    # against. Everything from the first observation to the publication
    # runs inside the serialization.
    with guard_mutation_lock(repo_root, work_item_id):
        return _acquire_guard_locked(repo_root, work_item_id, body=body,
                                     holder_owner_token=holder_owner_token, role=role,
                                     authorized_lease_id=authorized_lease_id)


def _acquire_guard_locked(repo_root: Path, work_item_id: str, *, body: dict,
                          holder_owner_token: str | None, role: str,
                          authorized_lease_id: str | None) -> dict:
    """`acquire_guard`'s decision and publication, run under
    `guard_mutation_lock`. Split out so the lock is taken exactly once by
    the operation rather than re-entered by each helper (`OPUS-R83-001`)."""
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
        # `OPUS-R82-002`: same token, different worktree. The token
        # proves the epoch, never the holder.
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


def _release_guard_path_locked(repo_root: Path, work_item_id: str, lease_id: str) -> None:
    """Compare-and-delete on `lease_id`: never remove a guard this
    session does not hold. **Caller must hold `guard_mutation_lock`**
    (`OPUS-R83-001`) -- a bare read-then-`unlink` names the *pathname*
    rather than the identity the comparison just established, so a guard
    published between the two statements is removed instead, including a
    live `"destructive"` one belonging to the work item's current owner
    in another worktree.

    An undecidable guard is left in place rather than deleted -- a
    release path that silently drops a record it could not read would be
    the one place fail-closed quietly became fail-open; the documented
    recovery is `clear_malformed_guard`.

    **An absent `lease_id` is a refusal, never a wildcard** (`OPUS-R84`
    non-blocking observation 1, extended per `OPUS-R85` non-blocking
    observation 1 to cover `""` as well as `None`): "every removal of a
    guard, on every path, names the `lease_id` it just observed"."""
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
        return
    if held.get("lease_id") != lease_id:
        return
    path.unlink(missing_ok=True)


def _release_guard_path(repo_root: Path, work_item_id: str, lease_id: str) -> None:
    with guard_mutation_lock(repo_root, work_item_id):
        _release_guard_path_locked(repo_root, work_item_id, lease_id)


def guard_clearance_authorization_literal(evidence: dict) -> str:
    return (f"clear malformed checkpoint guard {evidence.get('work_item_id')} "
            f"observation {evidence.get('guard_observation_id')}")


def clear_malformed_guard(repo_root: Path, work_item_id: str, *, user_authorization: str | None) -> None:
    """The defined recovery for a guard this implementation cannot have
    written -- torn, wrong shape, or otherwise undecidable. Publication
    is a same-directory temp file plus `os.link`, so a partially written
    guard is not producible here; a guard that *is* undecidable was
    corrupted by something else, and it fails closed for every session
    including the legitimate owner. Left there, that is a permanent
    lockout.

    The escape is explicit, user-authorized and observation-bound, never
    automatic and never time-based: the literal must quote the exact
    `guard_observation_id` the evidence reported, and the bytes must
    still hash to it at the moment of removal."""
    path = guard_path(repo_root, work_item_id)
    raw = _read_claim_bytes(path)
    evidence = {"work_item_id": work_item_id, "guard_observation_id": observation_id(raw)}
    if user_authorization != guard_clearance_authorization_literal(evidence):
        raise CheckpointClaimTakeoverRefusedError(
            f"clearing a malformed mutation guard requires the literal authorization "
            f"{guard_clearance_authorization_literal(evidence)!r} -- refusing to remove a "
            f"guard on an inference")
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
    """Re-read the claim at its final pathname and require its
    `owner_token` to equal exactly the token this session holds. On any
    failure -- absent claim, undecidable claim, rotated token -- stop
    immediately and mutate nothing.

    This is the mechanism by which an owner whose claim was taken over
    refuses instead of continuing: after a takeover the claim carries a
    rotated token, so the displaced owner's very next assertion fails.
    Meaningful only **inside** the guard; called outside it, it is a
    check-before-use, which `GPT-R81-001` reproduced as insufficient."""
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
    """The one fixed window shape every ownership-bearing mutation runs
    inside:

        acquire the guard -> assert_claim_owner(T) *under* the guard ->
        perform the mutation -> release the guard

    Every pause, stall or crash between the assertion and the mutation is
    therefore inside a window a takeover cannot enter. Windows are
    strictly non-nested: a session holds at most one guard at a time."""
    lease = acquire_guard(repo_root, work_item_id, holder_owner_token=owner_token,
                          checkpoint_id=checkpoint_id, step=step, step_class=step_class,
                          now=now, role="owner")
    try:
        yield assert_claim_owner(repo_root, work_item_id, owner_token)
    finally:
        release_guard(repo_root, work_item_id, lease)


# ---------------------------------------------------------------------------
# Explicit takeover -- never automatic, always evidence-first
# ---------------------------------------------------------------------------


def registered_worktrees(repo_root: Path) -> list[dict]:
    """`git worktree list --porcelain`, parsed -- the shared registry
    every linked worktree can read, used to tell a deleted holder from a
    live one."""
    out = _run(["git", "worktree", "list", "--porcelain"], cwd=repo_root)
    entries: list[dict] = []
    current: dict = {}
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


def local_identity_observation(repo_root: Path, *, path: Path = WORKTREE_IDENTITY_PATH) -> dict:
    """What the **taking** worktree's own identity document is, as a
    durable, re-verifiable observation (`OPUS-R86-003`). A rotating
    operation must establish this worktree's own identity record, and
    that write owes validate-before-mutate -- so when the document is
    undecidable the operation refuses unless the repair is separately
    authorized. Observed here, never concluded: the id is the sha256 of
    the exact bytes the human is shown, so the authorization it derives
    is bound to that document and to no other, and is non-replayable
    once the repair changes those bytes."""
    full_path = repo_root / path
    try:
        raw = _read_claim_bytes(full_path)
    except CheckpointOwnershipUnavailableError:
        descriptor = describe_unreadable_record(full_path)
        return {"state": "undecidable", "path": str(full_path),
                "identity_observation_id": (unreadable_observation_id(descriptor)
                                            if descriptor is not None else ABSENT_OBSERVATION),
                "unreadable": descriptor, "error": "the document is not a readable regular file"}
    if raw is None:
        return {"state": "absent", "path": str(full_path), "identity_observation_id": ABSENT_OBSERVATION}
    oid = observation_id(raw)
    try:
        data = json.loads(raw)
        validate_worktree_identity(data)
    except Exception as exc:  # noqa: BLE001 -- every decidability failure is one state
        return {"state": "undecidable", "path": str(full_path), "identity_observation_id": oid,
                "error": f"{type(exc).__name__}: {exc}"}
    return {"state": "valid", "path": str(full_path), "identity_observation_id": oid}


def repair_worktree_identity(repo_root: Path, work_item_id: str, *, now: str,
                             path: Path = WORKTREE_IDENTITY_PATH) -> dict:
    """The authorized repair-by-overwrite of this worktree's **own**
    identity document (`OPUS-R86-003`). Deliberately a separate entry
    point from `write_worktree_identity`, which must keep refusing on a
    corrupt existing document rather than silently replacing it
    (`OPUS-R86-004`): this one discards the undecidable document and
    builds a fresh one, and it is reachable only from a rotating
    operation whose authorization literal carried the repair component
    bound to that exact document's observation. Publishes through the
    same lock and the same single `os.replace` as every other write of
    this file."""
    full_path = repo_root / path
    with identity_document_lock(repo_root):
        repo_root_id, git_common_dir, worktree_root = _git_identity(repo_root)
        document = {
            "repo_root": repo_root_id,
            "git_common_dir": git_common_dir,
            "worktree_root": worktree_root,
            "expected_dirty_paths_by_work_item": {work_item_id: _hash_dirty_paths(repo_root)},
            "generated_at": now,
        }
        validate_worktree_identity(document)
        _publish_worktree_identity(full_path, document)
    return document


def _establish_or_repair_identity(repo_root: Path, work_item_id: str, *, now: str,
                                  evidence: dict, operation: str) -> None:
    """The identity half of both rotating operations, run **before** the
    rotation publishes (`OPUS-R86-003`). A decidable document is
    established the ordinary way. An undecidable one is repaired only if
    the authorization the caller already validated carried the repair
    component, and only if the document is still byte-identical to the
    one that component named."""
    observed = evidence.get("local_identity") or local_identity_observation(repo_root)
    if observed.get("state") != "undecidable":
        write_worktree_identity(repo_root, work_item_id, now=now)
        return
    fresh = local_identity_observation(repo_root)
    if fresh.get("identity_observation_id") != observed.get("identity_observation_id"):
        raise CheckpointClaimTakeoverRefusedError(
            f"this worktree's own identity document changed between the evidence the user "
            f"authorized ({observed.get('identity_observation_id')}) and this {operation} "
            f"({fresh.get('identity_observation_id')}) -- refusing, having mutated nothing; "
            f"present fresh evidence and obtain a fresh authorization")
    repair_worktree_identity(repo_root, work_item_id, now=now)


def _identity_repair_clause(evidence: dict) -> str:
    identity = evidence.get("local_identity") or {}
    if identity.get("state") != "undecidable":
        return ""
    return f" repairing identity {identity.get('identity_observation_id')}"


def takeover_evidence(repo_root: Path, work_item_id: str) -> dict:
    """Everything a human needs to decide a takeover, gathered without
    changing anything. Deliberately reports rather than concludes."""
    path = claim_path(repo_root, work_item_id)
    oid, claim, error, unreadable = observe_claim(repo_root, work_item_id)
    gpath = guard_path(repo_root, work_item_id)
    try:
        guard_oid = observation_id(_read_claim_bytes(gpath))
    except CheckpointOwnershipUnavailableError:
        guard_descriptor = describe_unreadable_record(gpath)
        guard_oid = (unreadable_observation_id(guard_descriptor)
                     if guard_descriptor is not None else ABSENT_OBSERVATION)
    evidence = {"claim_path": str(path), "readable": error is None, "claim": None,
                "holder_path_exists": None, "holder_registered": None,
                "holder_current_path": None,
                "claim_observation_id": oid,
                "claim_unreadable": unreadable,
                "claim_replaceable": unreadable is None or (
                    unreadable.get("kind") in REPLACEABLE_UNREADABLE_KINDS),
                "observed_checkpoint_id": claim.get("checkpoint_id") if claim else None,
                "observed_owner_token": claim.get("owner_token") if claim else None,
                "work_item_id": work_item_id,
                "guard": None,
                "guard_observation_id": guard_oid,
                "guard_holder_registered": None,
                "guard_holder_current_path": None,
                "guard_error": None,
                "local_identity": local_identity_observation(repo_root)}
    try:
        evidence["guard"] = read_guard(repo_root, work_item_id)
    except CheckpointOwnershipUnavailableError as exc:
        evidence["guard_error"] = str(exc)
    registered = registered_worktrees(repo_root)

    def _registered_match(recorded_root, recorded_git_dir):
        for entry in registered:
            wt_path = entry.get("worktree")
            if wt_path is None:
                continue
            try:
                admin = _worktree_git_dir(Path(wt_path)) if Path(wt_path).exists() else None
            except subprocess.CalledProcessError:
                admin = None
            if wt_path == recorded_root or (admin and recorded_git_dir and admin == recorded_git_dir):
                return True, wt_path
        return False, None

    guard = evidence["guard"]
    if guard is not None:
        guard_registered, guard_path_now = _registered_match(None, guard.get("holder_worktree_git_dir"))
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
    """The exact literal a user must produce, derived **from the
    evidence they were shown** (`GPT-R81-002`). Names the observed
    record's identity and the checkpoint that record holds, so it cannot
    be written from memory, cannot be replayed against a later record,
    and cannot displace a holder or checkpoint nobody reviewed. Grows a
    repair component when this worktree's own identity document is
    undecidable (`OPUS-R86-003`)."""
    displaced = evidence.get("observed_checkpoint_id") or "none"
    literal = (f"take over {work_item_id} claim {evidence.get('claim_observation_id')} "
               f"holding {displaced} as {checkpoint_id}")
    return literal + _identity_repair_clause(evidence)


def guard_release_authorization_literal(guard: dict) -> str:
    return (f"release checkpoint guard {guard.get('lease_id')} step {guard.get('step')} "
            f"class {guard.get('step_class')}")


def take_over_claim(repo_root: Path, work_item_id: str, checkpoint_id: str, *, now: str,
                    user_authorization: str | None, evidence: dict | None = None,
                    guard_release_authorization: str | None = None) -> dict:
    """The ordinary way a claim this worktree does not own is removed --
    and the only one that ever applies while the holder worktree is
    still registered. (`recover_abandoned_destructive_guard` is the
    single other route, for a destructive window abandoned by a worktree
    that no longer exists; `clear_malformed_guard` removes an
    undecidable guard, never a claim.)

    Never triggered by a timeout, a heartbeat, an age threshold, or an
    inference that the holder "looks gone" -- every one of those is an
    assumption that can discard a live claim, and this design does not
    make any of them.

    1. **Observe** -- `takeover_evidence`, whose `claim_observation_id`
       is the sha256 of the exact record bytes the human is shown.
    2. **Authorize** -- the literal must be exactly
       `takeover_authorization_literal(...)` for *that* observation. When
       a guard was observed, a separate guard-release authorization
       quoting its `lease_id`/`step`/`step_class` is required too, and a
       `"destructive"` guard is refused outright.
    3. **Acquire the same mutation guard** every owner mutation acquires.
    4. **Re-verify under the guard** -- re-read the raw bytes and require
       the observation id to be unchanged. Anything else is stale
       evidence: refuse, having mutated nothing.
    5. **Rotate** by atomic replace, minting a fresh `owner_token`,
       incrementing `takeover_count`, and recording the displaced record
       in `taken_over_from`."""
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
        record = _build_claim_record(
            repo_root, work_item_id, checkpoint_id, now, taken_over_from=taken_over_from,
            takeover_count=(previous or {}).get("takeover_count", 0) + 1,
            previous_owner_tokens=list((previous or {}).get("previous_owner_tokens", []))
            + ([previous["owner_token"]] if previous else []))
        _establish_or_repair_identity(repo_root, work_item_id, now=now,
                                      evidence=evidence, operation="takeover")
        _publish_claim_replacing(path, record,
                                 allow_unreadable_target=evidence.get("claim_unreadable") is not None)
        return record
    finally:
        release_guard(repo_root, work_item_id, lease)


def abandoned_guard_recovery_authorization_literal(work_item_id: str, evidence: dict) -> str:
    """Distinct from the takeover literal in every component, and bound
    to **both** durable observations plus the destructive step being
    abandoned, so it can neither be written from memory nor reused for
    an ordinary takeover (`OPUS-R82-001`)."""
    guard = evidence.get("guard") or {}
    return (f"recover abandoned destructive guard {work_item_id} step {guard.get('step')} "
            f"guard {evidence.get('guard_observation_id')} "
            f"claim {evidence.get('claim_observation_id')}"
            + _identity_repair_clause(evidence))


def recover_abandoned_destructive_guard(repo_root: Path, work_item_id: str, checkpoint_id: str, *,
                                        now: str, user_authorization: str | None,
                                        evidence: dict | None = None) -> dict:
    """The one escape from a `"destructive"` guard left behind by a
    holder worktree that no longer exists (`OPUS-R82-001`). Without it,
    `take_over_claim`'s unconditional destructive refusal and
    `clear_malformed_guard`'s well-formed refusal combine into a work
    item no documented operation can start, resume or hand over.

    Introduces **no** liveness inference: still no timeout, no
    heartbeat, no age threshold, no `claimed_at` comparison, no "the
    holder looks gone". The precondition is a durable, human-made fact
    instead -- the holder worktree is not in `git worktree list`, i.e.
    the operator (or the machine's loss) deregistered it. While it is
    still registered this refuses and names what to do instead, so a
    live destructive window is never broken.

    Mechanically a takeover whose guard reclamation is `role="recovery"`:
    once the claim rotates, the abandoned guard is superseded by
    construction, so the existing epoch rule -- not a second removal
    primitive -- finally clears it."""
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
        current_oid, previous, _error, current_unreadable = observe_claim(repo_root, work_item_id)
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
            "claim_was_undecidable": claim_undecidable,
            "claim_unreadable": current_unreadable,
            "epoch_chain_lost": claim_undecidable,
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
            displaced_tokens.append(guard["holder_owner_token"])
        record = _build_claim_record(
            repo_root, work_item_id, checkpoint_id, now, taken_over_from=taken_over_from,
            takeover_count=previous.get("takeover_count", 0) + 1,
            previous_owner_tokens=displaced_tokens)
        _establish_or_repair_identity(repo_root, work_item_id, now=now,
                                      evidence=evidence, operation="recovery")
        _publish_claim_replacing(path, record, allow_unreadable_target=claim_undecidable)
        return record
    finally:
        release_guard(repo_root, work_item_id, lease)


# ---------------------------------------------------------------------------
# WF8b: D-Checkpoint-Ownership -- "Where the check belongs, and the
# ordering" (docs/ai-workflow/WORKFLOW_V2_PLAN.md, revisions 63-80). This
# is `/milestone-implement`'s step 1c: the resolution step that
# reconciles `WORKFLOW_STATE.json` against the shared claim, composing
# every primitive the three prior WF8b sessions built (the origination
# reference, the claim/guard/takeover machinery, `adopt_claim`) into the
# outcome `step 1d`/`1f` act on.
#
# Authored fresh against the approved revision-80 text; the unwired
# dry-run prototype's own `resolve_ownership`/`_attach_ownership_evidence`
# (docs/ai-workflow/dry-run/wf8b-s14-repro/checkpoint_ownership.py) are
# reference/reproduction evidence only, and predate two corrections this
# implementation does not repeat: `_attach_ownership_evidence` returning
# immediately on an absent claim (corrected, revision 70, `OPUS-R87-003`)
# and the origination-specific evidence components revision 71
# (`OPUS-R88-005`) adds for `CheckpointOriginationUnprovableError`.
#
# Scope, deliberately bounded per this session's own direction: no
# `WFR-66` identity-query enforcement (a distinct implementation surface,
# `authorize_identity_reference_gap`), and not yet wired into
# `.claude/commands/milestone-implement.md`'s step 1d/1f orchestration of
# the guarded state write / guarded commit -- this function is the
# resolution primitive that wiring calls.
# ---------------------------------------------------------------------------

RESUME = "RESUME"
FRESH = "FRESH"
CONTINUE_CLAIM = "CONTINUE_CLAIM"
NO_CHECKPOINT = "NO_CHECKPOINT"
CHECKPOINT_OWNERSHIP_OUTCOMES = frozenset({RESUME, FRESH, CONTINUE_CLAIM, NO_CHECKPOINT})


def _ownership_escape_hint(repo_root: Path, work_item_id: str, claim: dict | None) -> str:
    """The escape named alongside every ownership-evidence attachment
    (`OPUS-R84-001`, generalized to an absent claim by `OPUS-R87-003`):
    what the operator does next, for every claim state -- absent,
    self-owned, or foreign."""
    if claim is None:
        return (
            f"no claim exists for {work_item_id!r} -- the escape for a locally IN_PROGRESS "
            f"checkpoint this worktree cannot prove it originated is the explicit takeover "
            f"(take_over_claim)"
        )
    if claim_is_this_worktree(repo_root, claim):
        return (
            f"{work_item_id!r} checkpoint {claim.get('checkpoint_id')!r} is claimed by this "
            f"worktree ({claim.get('worktree_root')!r}); continue it here -- no takeover "
            f"applies, and nothing needs to be removed by hand"
        )
    return (
        f"{work_item_id!r} checkpoint {claim.get('checkpoint_id')!r} is claimed by worktree "
        f"{claim.get('worktree_root')!r}; resume it there, or take the claim over explicitly "
        f"(take_over_claim) after reviewing the takeover evidence"
    )


def _attach_ownership_evidence(repo_root: Path, exc: Exception, work_item_id: str, claim: dict | None) -> None:
    """Annotate every refusal step 1c raises with the ownership evidence a
    reviewer must be able to read off the exception rather than
    re-deriving by hand: the claim record, the holder, the claimed
    checkpoint, and the escape that applies (`OPUS-R84-001`). Attached
    unconditionally, including when the claim is absent -- the prototype's
    early return on `claim is None` is exactly the defect revision 70
    (`OPUS-R87-003`) withdrew, since an absent claim is a load-bearing
    observation for `CheckpointOriginationUnprovableError` and excluding
    it left the class this repository is actually in (no claim for
    `workflow-v2-1-core`) reporting nothing.

    For `CheckpointOriginationUnprovableError` specifically, also attaches
    the components revision 71 (`OPUS-R88-005`) adds: this worktree's own
    local identity observation, and whether the claim is absent, foreign,
    or self-owned -- so an operator reading the refusal is told the whole
    picture, not just that origination is unprovable.

    Idempotent: a refusal already carrying evidence (raised, then
    re-raised through an outer handler) keeps its first attachment."""
    if getattr(exc, "ownership_evidence", None) is not None:
        return
    exc.ownership_evidence = {
        "claim": claim,
        "holder": claim.get("worktree_root") if claim is not None else None,
        "claimed_checkpoint": claim.get("checkpoint_id") if claim is not None else None,
        "escape": _ownership_escape_hint(repo_root, work_item_id, claim),
    }
    if isinstance(exc, CheckpointOriginationUnprovableError):
        if claim is None:
            claim_state = "absent"
        elif claim_is_this_worktree(repo_root, claim):
            claim_state = "self_owned"
        else:
            claim_state = "foreign"
        exc.ownership_evidence["local_identity"] = local_identity_observation(repo_root)
        exc.ownership_evidence["claim_state"] = claim_state
        exc.ownership_evidence["origination"] = dict(exc.evidence)


def resolve_checkpoint_ownership(
    repo_root: Path, work_item: dict, work_item_id: str, selected_id: str | None, *, now: str,
) -> tuple[str, str | None, str | None]:
    """`/milestone-implement`'s step 1c. `select_next_checkpoint` (1b)
    stays pure and untouched; this is the new resolution step that
    reconciles its result against the shared claim.

    Returns `(outcome, checkpoint_id, owner_token)` where `outcome` is
    `RESUME`, `FRESH`, `CONTINUE_CLAIM`, or the terminal `NO_CHECKPOINT`.
    No mutation-capable outcome ever carries a `None` checkpoint id
    (`GPT-R81-003`): an exhausted registry is `NO_CHECKPOINT`, which
    re-enters the command's existing "nothing to implement this
    invocation" path and never reaches step 1d. `owner_token` is `None`
    only for `NO_CHECKPOINT` and `FRESH`, where step 1d mints it by
    acquiring the claim -- before any state write.

    The internal order is the security property: whenever either
    authority (the local `WORKFLOW_STATE.json` or the shared claim) says
    this work item is contended, `verify_dirty_resume_safety` -- the sole
    proof of worktree *instance* origination (revision 68, `OPUS-R85-001`;
    a self-owned claim is never a substitute, since it proves only a
    *location*) -- runs before any branch that can mutate. Every refusal
    raised from that point on carries the ownership evidence
    (`_attach_ownership_evidence`)."""
    checkpoints = work_item.get("checkpoints", {})
    current_id = work_item.get("current_checkpoint_id")
    local_in_progress = (
        current_id if checkpoints.get(current_id, {}).get("status") == "IN_PROGRESS" else None
    )
    claim = resolve_claim(repo_root, work_item_id)

    if claim is None and local_in_progress is None:
        # The ordinary uncontended case, which must stay exactly as
        # permissive as it is today.
        if selected_id is None:
            return NO_CHECKPOINT, None, None
        return FRESH, selected_id, None

    try:
        verify_dirty_resume_safety(repo_root, work_item_id)

        # A foreign claim is refused here even though `verify_dirty_resume_
        # safety` already passed: a worktree carrying a valid identity
        # record of its own for this work item -- a displaced owner after a
        # takeover, typically -- passes that check and is refused one line
        # later, by `CheckpointOwnedByOtherWorktreeError`, instead.
        if claim is not None and not claim_is_this_worktree(repo_root, claim):
            raise CheckpointOwnedByOtherWorktreeError(
                f"{work_item_id!r} checkpoint {claim.get('checkpoint_id')!r} is claimed by "
                f"worktree {claim.get('worktree_root')!r}"
            )

        if local_in_progress is not None:
            if claim is None:
                # Locally interrupted with no shared claim: a checkpoint
                # started before this mechanism existed (the real
                # `workflow-v2-1-core:WF8b`, and `v2-1-dry-run:S-CP3`), or a
                # claim lost out of band. `adopt_claim` owns the full
                # origination test (and re-evaluates it at publication) --
                # this branch does not duplicate it.
                if selected_id is not None and selected_id != local_in_progress:
                    raise CheckpointOwnershipStateMismatchError(
                        f"{work_item_id!r}'s WORKFLOW_STATE.json records {local_in_progress!r} "
                        f"IN_PROGRESS but selection resolved {selected_id!r} -- reporting rather "
                        f"than guessing which is right"
                    )
                adopted = adopt_claim(repo_root, work_item_id, local_in_progress, now=now)
                return RESUME, local_in_progress, adopted.get("owner_token")

            claimed_id = claim.get("checkpoint_id")
            if local_in_progress != claimed_id or (selected_id is not None and selected_id != claimed_id):
                raise CheckpointOwnershipStateMismatchError(
                    f"this worktree claims {work_item_id!r} checkpoint {claimed_id!r}, its "
                    f"WORKFLOW_STATE.json records {local_in_progress!r} IN_PROGRESS, and "
                    f"selection resolved {selected_id!r} -- reporting rather than guessing "
                    f"which is right"
                )
            return RESUME, claimed_id, claim.get("owner_token")

        # Nothing is locally IN_PROGRESS. Since the uncontended branch above
        # already excluded "claim is None and local_in_progress is None",
        # and the foreign-claim check above already excluded a foreign
        # claim, the claim here is self-owned.
        claimed_id = claim.get("checkpoint_id")
        local_status = checkpoints.get(claimed_id, {}).get("status")

        if local_status is None:
            # Crashed between publishing the claim and writing the state
            # (1d's own ordering exists to keep this window narrow, never
            # to close it). The owner finishes its own acquisition -- this
            # releases nothing and discards nothing, so it is not a
            # stale-claim release.
            if selected_id is not None and selected_id != claimed_id:
                raise CheckpointOwnershipStateMismatchError(
                    f"this worktree claims {work_item_id!r} checkpoint {claimed_id!r} with no "
                    f"local state entry, but selection resolved {selected_id!r} -- reporting "
                    f"rather than guessing"
                )
            return CONTINUE_CLAIM, claimed_id, claim.get("owner_token")

        if local_status == "COMPLETE":
            # The single automatic release in the whole design: a
            # self-owned claim on a checkpoint whose completion is
            # **durable** (committed at HEAD), i.e. a crash between step
            # 1f's commit and its release. The working tree alone saying
            # `COMPLETE` is not enough -- `select_next_checkpoint` already
            # ran against this same local state, so `selected_id` is
            # already the concrete next checkpoint (or `None` because the
            # registry is exhausted), and reselecting here would duplicate
            # rather than trust that.
            durable = committed_checkpoint_status(repo_root, work_item_id, claimed_id)
            if durable == "COMPLETE":
                release_checkpoint(repo_root, work_item_id, claimed_id,
                                   owner_token=claim.get("owner_token"), now=now)
                if selected_id is None:
                    return NO_CHECKPOINT, None, None
                return FRESH, selected_id, None
            raise CheckpointOwnershipStateMismatchError(
                f"this worktree's claim on {work_item_id!r} checkpoint {claimed_id!r} shows "
                f"COMPLETE in the working tree but {durable!r} in the committed state at HEAD "
                f"-- refusing to release a claim whose completion is not durable"
            )

        raise CheckpointOwnershipStateMismatchError(
            f"this worktree holds a claim on {work_item_id!r} checkpoint {claimed_id!r}, but "
            f"its WORKFLOW_STATE.json records status {local_status!r} with nothing IN_PROGRESS "
            f"-- reporting rather than guessing whether to resume or discard"
        )
    except Exception as exc:
        _attach_ownership_evidence(repo_root, exc, work_item_id, claim)
        raise


# ---------------------------------------------------------------------------
# WF8b: D-Checkpoint-Ownership -- WFR-66's identity-query enforcement
# (docs/ai-workflow/WORKFLOW_V2_PLAN.md, "The origination reference"'s
# "The contract covers the identity queries too" / "The identity queries'
# own read partition", plus D1's/D-Registry's own enforcement-point text).
#
# Reuses `origination_reference_commits` -- the identity queries and the
# origination test share one reference, just two different read
# partitions over it (`OPUS-R90-003`): the origination test asks whether
# a *status* was ever IN_PROGRESS; these ask whether a *key* -- a
# work_item_id, or a (work_item_id, checkpoint_id) pair -- was ever
# present at all, which fails closed in the opposite direction (key
# presence is itself the observation, undecidability is scoped to the
# containers strictly *above* the queried key, and the two partitions
# disagree on four of the document shapes explicitly enumerated in the
# plan's "two tables genuinely diverge" paragraph).
#
# Two permanent, unescapable refusals name a decidable observation
# directly (`WorkItemIdReusedError`/`CheckpointIdReusedError`); one
# escapable refusal (`IdentityReferenceUndecidableError`) is cleared only
# by the evidence-bound, non-replayable, repository-serialized
# `authorize_identity_reference_gap` -- `recover_abandoned_destructive_
# guard`'s own signature, deliberately, per `OPUS-R91-003`.
# ---------------------------------------------------------------------------


class IdentityReferenceUndecidableError(Exception):
    """Raised by a WFR-66 identity-enforcement query (work-item creation
    or registry checkpoint-id validation) when D-Checkpoint-Ownership's
    origination reference cannot establish, at one or more commits,
    whether the queried key -- a work_item_id, or a (work_item_id,
    checkpoint_id) pair -- is present or absent. Distinct from a
    decidable observation, which is a permanent, unescapable refusal this
    exception is never raised for (`WorkItemIdReusedError`/
    `CheckpointIdReusedError` instead, since it names a binding rather
    than a repairable repository condition). Carries `.evidence` (the
    undecidable commits and their failure classes, the identity queried,
    and the examined-commit count) for `authorize_identity_reference_gap`'s
    digest and for the operator to inspect. Cleared only by that
    operation's explicit, evidence-bound, non-replayable authorization."""

    def __init__(self, message: str, *, evidence: dict):
        super().__init__(message)
        self.evidence = evidence


class WorkItemIdReusedError(Exception):
    """Raised when work-item creation names a `work_item_id` decidably
    observed at some commit in D-Checkpoint-Ownership's origination
    reference -- ids are permanently non-reusable (D1, revision 71,
    `OPUS-R88-004`), a stronger and historical-evidence-bound check than
    `WorkItemTerminalReuseError`'s narrower, live-state-only one."""


class CheckpointIdReusedError(Exception):
    """Raised when a registry write reintroduces a `checkpoint_id`
    decidably observed for that work item at some commit in
    D-Checkpoint-Ownership's origination reference -- checkpoint ids are
    permanently non-reusable within their work item, but only for ids
    genuinely new to this revision: an id kept live across revisions is
    in-place redefinition, not reuse, and stays legal (D-Registry,
    revision 72, `OPUS-R89-005`)."""


class IdentityGapAuthorizationUnavailableError(Exception):
    """Raised when the durable identity-reference-gap-authorization
    record cannot be read or written -- fails closed rather than
    proceeding unprotected."""


IDENTITY_GAP_AUTHORIZATIONS_RELDIR = "ai-workflow/identity-gap-authorizations"
IDENTITY_GAP_LOCK_RELPATH = "ai-workflow/identity-gap.lock"


def _identity_query_at_commit(
    repo_root: Path, commit: str, state_rel_path: str, work_item_id: str, checkpoint_id: str | None,
) -> tuple[str, dict]:
    """One commit's contribution to an identity-reference query -- "has
    this work_item_id (checkpoint_id=None) or this (work_item_id,
    checkpoint_id) pair ever been observed?" -- per "The identity
    queries' own read partition". Returns `(outcome, detail)`:

    - `"observed"` -- the queried key is decidably **present** at its own
      level, whatever its value -- key presence is the whole of what an
      existence query asks, so this wins unescapably over every other
      row for the same commit, and is checked directly with `in` before
      the value is ever inspected, which is what keeps the rows disjoint
      by construction rather than by a separately-stated precedence rule;
    - `"undecidable"` -- the reader cannot establish presence or absence
      of the queried key itself: an unlistable tree, an unresolvable
      commit, a state path present but not a readable regular-file blob,
      an unparseable or non-object document, or a non-object container
      strictly *above* the queried key (`work_items` for the work-item
      query; `work_items`, the work item entry, or `checkpoints` for the
      pair query);
    - `"decidable"` -- the queried key is decidably **absent**: a missing
      key, established with `in`, at every level examined on the way to
      it."""
    listing = subprocess.run(
        ["git", "ls-tree", commit, "--", state_rel_path],
        cwd=repo_root, capture_output=True, text=True,
    )
    if listing.returncode != 0:
        return "undecidable", {"commit": commit, "failure_class": "tree unlistable or commit unresolvable"}
    line = listing.stdout.strip()
    if not line:
        return "decidable", {"commit": commit, "reason": "state path absent from tree"}
    meta, _, _ = line.partition("\t")
    mode = meta.split()[0]
    blob_sha = meta.split()[2]
    if mode not in ("100644", "100755"):
        return "undecidable", {"commit": commit,
                                "failure_class": f"state path is not a regular-file blob (mode {mode})"}
    blob = subprocess.run(["git", "cat-file", "-p", blob_sha], cwd=repo_root, capture_output=True, text=True)
    if blob.returncode != 0:
        return "undecidable", {"commit": commit, "failure_class": "blob could not be read"}
    try:
        doc = json.loads(blob.stdout)
    except json.JSONDecodeError:
        return "undecidable", {"commit": commit, "failure_class": "state document unparseable"}
    if not isinstance(doc, dict):
        return "undecidable", {"commit": commit, "failure_class": "state document is not an object"}

    if "work_items" not in doc:
        return "decidable", {"commit": commit, "reason": "no work_items key"}
    work_items = doc["work_items"]
    if not isinstance(work_items, dict):
        return "undecidable", {"commit": commit, "failure_class": "work_items is not an object"}

    if checkpoint_id is None:
        if work_item_id in work_items:
            return "observed", {"commit": commit, "reason": f"{work_item_id!r} key present in work_items"}
        return "decidable", {"commit": commit, "reason": f"no {work_item_id!r} entry"}

    if work_item_id not in work_items:
        return "decidable", {"commit": commit, "reason": f"no {work_item_id!r} entry"}
    work_item = work_items[work_item_id]
    if not isinstance(work_item, dict):
        return "undecidable", {"commit": commit, "failure_class": f"{work_item_id!r} entry is not an object"}

    if "checkpoints" not in work_item:
        return "decidable", {"commit": commit, "reason": "no checkpoints key"}
    checkpoints = work_item["checkpoints"]
    if not isinstance(checkpoints, dict):
        return "undecidable", {"commit": commit, "failure_class": "checkpoints is not an object"}

    if checkpoint_id in checkpoints:
        return "observed", {"commit": commit, "reason": f"{checkpoint_id!r} key present in checkpoints"}
    return "decidable", {"commit": commit, "reason": f"no {checkpoint_id!r} entry"}


def _scan_identity_reference(
    repo_root: Path, work_item_id: str, checkpoint_id: str | None = None, *,
    state_rel_path: str | None = None,
) -> dict:
    """Scans every commit `origination_reference_commits` enumerates for
    WFR-66's two enforcement points. A decidable observation anywhere
    binds -- no supersession, no recency, no scoping to a lifecycle
    instance, the same reduction rule the origination test states, shared
    without exception -- and wins over any undecidable commit found in
    the same scan, since the two routes here differ in whether an escape
    exists at all rather than merely in which refusal is reported.

    Returns an evidence dict with `"decision"` `"observed"` or `"admit"`.
    Raises `IdentityReferenceUndecidableError` (never returns
    `"undecidable"`) when the reference itself cannot be resolved or when
    any commit is undecidable for the queried key itself and no commit
    decidably observes it."""
    state_rel_path = state_rel_path or DEFAULT_STATE_PATH.as_posix()
    try:
        commits = origination_reference_commits(repo_root, state_rel_path)
    except CheckpointOriginationUnprovableError as exc:
        raise IdentityReferenceUndecidableError(
            f"the identity reference could not be resolved for {state_rel_path!r}: {exc}",
            evidence={
                "route": "undecidable", "work_item_id": work_item_id, "checkpoint_id": checkpoint_id,
                "state_rel_path": state_rel_path, "examined_commits": 0,
                "reference_unresolvable": True, "undecidable_commits": [],
            },
        ) from exc
    if not commits:
        return {
            "decision": "admit", "route": "no_reference_commits", "work_item_id": work_item_id,
            "checkpoint_id": checkpoint_id, "state_rel_path": state_rel_path, "examined_commits": 0,
        }

    observed = None
    undecidable_commits: list[dict] = []
    for commit in commits:
        outcome, detail = _identity_query_at_commit(repo_root, commit, state_rel_path, work_item_id, checkpoint_id)
        if outcome == "observed" and observed is None:
            observed = detail
        elif outcome == "undecidable":
            undecidable_commits.append({"commit": detail["commit"], "failure_class": detail["failure_class"]})

    if observed is not None:
        return {
            "decision": "observed", "commit": observed["commit"], "reason": observed["reason"],
            "work_item_id": work_item_id, "checkpoint_id": checkpoint_id,
            "state_rel_path": state_rel_path, "examined_commits": len(commits),
        }
    if undecidable_commits:
        identity = f"{work_item_id!r}" + (f"/{checkpoint_id!r}" if checkpoint_id is not None else "")
        raise IdentityReferenceUndecidableError(
            f"the identity reference for {identity} is undecidable at {len(undecidable_commits)} "
            f"commit(s) -- neither observed nor provably never observed; "
            f"authorize_identity_reference_gap is the one explicit, evidence-bound escape",
            evidence={
                "route": "undecidable", "work_item_id": work_item_id, "checkpoint_id": checkpoint_id,
                "state_rel_path": state_rel_path, "examined_commits": len(commits),
                "reference_unresolvable": False, "undecidable_commits": undecidable_commits,
            },
        )
    return {
        "decision": "admit", "route": "scanned_all_decidable", "work_item_id": work_item_id,
        "checkpoint_id": checkpoint_id, "state_rel_path": state_rel_path, "examined_commits": len(commits),
    }


def gap_observation_id(evidence: Mapping) -> str:
    """The digest `authorize_identity_reference_gap`'s literal and
    durable record both bind to: the exact set of undecidable commits and
    the failure class observed at each, **plus the identity being
    authorized** (`work_item_id`, and `checkpoint_id` when the query is
    the pair query) -- so an authorization written against one damaged
    commit can never clear a different one that appeared since, and can
    never be replayed against a different identity (`OPUS-R91-003`)."""
    commits = sorted(
        (
            {"commit": entry["commit"], "failure_class": entry["failure_class"]}
            for entry in evidence.get("undecidable_commits", [])
        ),
        key=lambda entry: entry["commit"],
    )
    payload = {
        "work_item_id": evidence.get("work_item_id"),
        "checkpoint_id": evidence.get("checkpoint_id"),
        "reference_unresolvable": bool(evidence.get("reference_unresolvable", False)),
        "undecidable_commits": commits,
    }
    blob = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(blob).hexdigest()


def identity_reference_gap_authorization_literal(
    work_item_id: str, checkpoint_id: str | None, evidence: Mapping,
) -> str:
    """`authorize identity reference gap <work_item_id> [checkpoint
    <checkpoint_id>] gap <gap_observation_id>` -- distinct in every
    component from the takeover's and the abandoned-guard recovery's own
    literals, unwritable from memory, and validated against the
    re-derived digest rather than merely parsed."""
    oid = gap_observation_id(evidence)
    if checkpoint_id is not None:
        return f"authorize identity reference gap {work_item_id} checkpoint {checkpoint_id} gap {oid}"
    return f"authorize identity reference gap {work_item_id} gap {oid}"


def identity_gap_authorizations_dir(repo_root: Path) -> Path:
    _, common_dir, _ = _git_identity(repo_root)
    return Path(common_dir) / IDENTITY_GAP_AUTHORIZATIONS_RELDIR


def identity_gap_authorization_path(repo_root: Path, gap_oid: str) -> Path:
    """Named by a digest of the digest -- the same "token, not a path"
    discipline the claim record uses -- so no crafted
    `gap_observation_id` value can ever address anything outside this
    directory."""
    token = hashlib.sha256(gap_oid.encode()).hexdigest()
    return identity_gap_authorizations_dir(repo_root) / f"{token}.json"


def identity_gap_lock_path(repo_root: Path) -> Path:
    _, common_dir, _ = _git_identity(repo_root)
    return Path(common_dir) / IDENTITY_GAP_LOCK_RELPATH


@contextlib.contextmanager
def identity_gap_lock(repo_root: Path):
    """The repository-level `fcntl.flock` leaf `authorize_identity_
    reference_gap` runs its re-derive-then-publish sequence inside,
    because at work-item creation there is no work item, no claim and no
    per-work-item mutation guard to serialize on -- the entire
    concurrency apparatus this design otherwise relies on is keyed on an
    entity that does not exist yet at this call site (`OPUS-R91-003`).
    Never acquired while the per-work-item mutation guard, the
    per-worktree identity `flock`, or `D1`'s state-writer `flock` is
    held, and none of those three is acquired while this one is held --
    see `D-Approval-Commits`' single lock-ordering site for the complete
    set. Stable, never unlinked, process-scoped; released by the kernel
    on process death."""
    path = identity_gap_lock_path(repo_root)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_RDWR | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        yield
    finally:
        os.close(fd)


def _stage_identity_gap_authorization_payload(path: Path, record: dict) -> Path:
    payload = json.dumps(record, indent=2, sort_keys=True) + "\n"
    _assert_not_symlink(path.parent, "identity-gap-authorizations directory")
    _assert_not_symlink(path, "identity-gap-authorization record")
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp_name = tempfile.mkstemp(dir=str(path.parent), prefix=".gap-", suffix=".tmp")
        with os.fdopen(fd, "w") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    except OSError as exc:
        raise IdentityGapAuthorizationUnavailableError(
            f"cannot stage an identity-reference-gap authorization next to {path} ({exc})"
        ) from exc
    return Path(tmp_name)


def _publish_identity_gap_authorization_exclusive(path: Path, record: dict) -> None:
    """Atomic in both senses, exactly as the claim record's own exclusive
    publication is: the whole payload is staged at a same-directory temp
    name and then `os.link`ed into place, so `os.link`'s `EEXIST` **is**
    the non-replayability check -- the test and the write are one
    operation, with no read-then-write window between them."""
    tmp = _stage_identity_gap_authorization_payload(path, record)
    try:
        os.link(tmp, path)
    except FileExistsError:
        raise
    except OSError as exc:
        raise IdentityGapAuthorizationUnavailableError(f"cannot publish {path} ({exc})") from exc
    finally:
        tmp.unlink(missing_ok=True)


def _read_identity_gap_authorization(path: Path) -> dict | None:
    _assert_not_symlink(path.parent, "identity-gap-authorizations directory")
    _assert_not_symlink(path, "identity-gap-authorization record")
    try:
        fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    except FileNotFoundError:
        return None
    except OSError as exc:
        raise IdentityGapAuthorizationUnavailableError(f"cannot read {path} ({exc})") from exc
    try:
        with os.fdopen(fd, "rb") as handle:
            raw = handle.read()
    except OSError as exc:
        try:
            os.close(fd)
        except OSError:
            pass
        raise IdentityGapAuthorizationUnavailableError(f"cannot read {path} ({exc})") from exc
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise IdentityGapAuthorizationUnavailableError(f"{path} is not valid JSON ({exc})") from exc
    if not isinstance(data, dict) or data.get("schema_version") != 1:
        raise IdentityGapAuthorizationUnavailableError(
            f"{path} has an unsupported shape/schema_version (expected 1)")
    return data


def authorize_identity_reference_gap(
    repo_root: Path, work_item_id: str, checkpoint_id: str | None = None, *,
    now: str, user_authorization: str | None, evidence: Mapping,
) -> dict:
    """WFR-66's one explicit, evidence-bound, non-replayable escape from
    `IdentityReferenceUndecidableError` (`recover_abandoned_destructive_
    guard`'s own signature, deliberately -- every parameter that sibling
    needs, this one needs for the same reason, `OPUS-R91-003`).

    `evidence` is the `.evidence` a caller's own `IdentityReferenceUndecidableError`
    carried; this function never re-scans the reference to build its own
    evidence from scratch -- the caller already did, and a fresh scan here
    would let a second, cheaper read silently substitute for the one the
    user was actually shown. It re-derives the digest and re-scans only to
    check the evidence is still current, never to source it.

    Never overrides a decidable observation: with the gap authorized, the
    query still refuses if any decidable commit observes the identity --
    this only ever admits the undecidability, never the binding.
    Idempotent rather than consumed on read: a crash between this
    record's publication and the creation it authorizes resumes, on
    retry, by recognising its own completed authorization."""
    if evidence.get("route") != "undecidable":
        raise CheckpointClaimTakeoverRefusedError(
            "authorize_identity_reference_gap requires evidence of an undecidable identity "
            "reference read -- there is nothing to authorize")
    if evidence.get("work_item_id") != work_item_id or evidence.get("checkpoint_id") != checkpoint_id:
        raise CheckpointClaimTakeoverRefusedError(
            f"the supplied evidence was taken for "
            f"({evidence.get('work_item_id')!r}, {evidence.get('checkpoint_id')!r}), not "
            f"({work_item_id!r}, {checkpoint_id!r}) -- refusing to authorize a gap against an "
            f"identity the evidence never observed")

    oid = gap_observation_id(evidence)
    expected = identity_reference_gap_authorization_literal(work_item_id, checkpoint_id, evidence)
    if user_authorization != expected:
        raise CheckpointClaimTakeoverRefusedError(
            f"authorizing an identity-reference gap requires the literal authorization "
            f"{expected!r}, derived from the evidence just presented -- refusing to admit an "
            f"unprovable identity on an inference or on a remembered literal")

    state_rel_path = evidence.get("state_rel_path") or DEFAULT_STATE_PATH.as_posix()

    with identity_gap_lock(repo_root):
        path = identity_gap_authorization_path(repo_root, oid)
        existing = _read_identity_gap_authorization(path)
        if existing is not None:
            if existing.get("work_item_id") != work_item_id or existing.get("checkpoint_id") != checkpoint_id:
                raise CheckpointClaimTakeoverRefusedError(
                    f"a gap authorization already exists at {path} bound to a different "
                    f"identity -- this should be unreachable, since the digest binds the "
                    f"identity")
            return existing

        try:
            fresh = _scan_identity_reference(repo_root, work_item_id, checkpoint_id,
                                             state_rel_path=state_rel_path)
        except IdentityReferenceUndecidableError as exc:
            if gap_observation_id(exc.evidence) != oid:
                raise CheckpointClaimTakeoverRefusedError(
                    f"the identity reference changed between the evidence the user authorized "
                    f"({oid}) and this authorization ({gap_observation_id(exc.evidence)}) -- "
                    f"refusing, having recorded nothing; present fresh evidence and obtain a "
                    f"fresh authorization"
                ) from exc
            # still undecidable, same digest -- proceed to publish below.
        else:
            identity = f"{work_item_id!r}" + (f"/{checkpoint_id!r}" if checkpoint_id is not None else "")
            if fresh["decision"] == "observed":
                raise CheckpointClaimTakeoverRefusedError(
                    f"{identity} is now decidably observed at commit {fresh['commit']} -- "
                    f"refusing to authorize a gap against a binding that now exists")
            raise CheckpointClaimTakeoverRefusedError(
                f"{identity} is now decidably absent from the identity reference -- no gap "
                f"remains to authorize; the query now admits on its own")

        record = {
            "schema_version": 1,
            "gap_observation_id": oid,
            "work_item_id": work_item_id,
            "checkpoint_id": checkpoint_id,
            "undecidable_commits": list(evidence.get("undecidable_commits", [])),
            "authorized_at": now,
            "authorizing_worktree_git_dir": _worktree_git_dir(repo_root),
            "consumed": False,
        }
        _publish_identity_gap_authorization_exclusive(path, record)
        return record


def mark_identity_reference_gap_consumed(repo_root: Path, gap_oid: str) -> None:
    """Diagnostic bookkeeping only, run once the identity/registry
    creation the gap authorized has completed -- the refusal a
    *different* identity receives never depends on this flag, so a crash
    before this runs simply leaves `consumed: false` on an authorization
    whose creation already happened, with no correctness consequence."""
    with identity_gap_lock(repo_root):
        path = identity_gap_authorization_path(repo_root, gap_oid)
        record = _read_identity_gap_authorization(path)
        if record is None or record.get("consumed"):
            return
        record["consumed"] = True
        payload = json.dumps(record, indent=2, sort_keys=True) + "\n"
        fd, tmp_name = tempfile.mkstemp(dir=str(path.parent), prefix=".gap-", suffix=".tmp")
        with os.fdopen(fd, "w") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_name, path)


def identity_reference_admits(
    repo_root: Path, work_item_id: str, checkpoint_id: str | None = None, *,
    state_rel_path: str | None = None,
) -> dict:
    """The one check `D1`'s work-item-creation refusal and `D-Registry`'s
    checkpoint-id-reuse refusal both perform (`OPUS-R89-005`/`OPUS-R90-003`):
    has this `work_item_id` (`checkpoint_id=None`) or this
    `(work_item_id, checkpoint_id)` pair ever been observed in
    D-Checkpoint-Ownership's origination reference? A decidable
    observation is a permanent, unescapable refusal
    (`WorkItemIdReusedError`/`CheckpointIdReusedError`). An undecidable
    reference refuses with `IdentityReferenceUndecidableError` unless a
    matching `authorize_identity_reference_gap` record already exists, in
    which case the undecidability is recognised as authorized and the
    query admits -- never a decidable observation, only ever the gap."""
    try:
        result = _scan_identity_reference(repo_root, work_item_id, checkpoint_id,
                                          state_rel_path=state_rel_path)
    except IdentityReferenceUndecidableError as exc:
        oid = gap_observation_id(exc.evidence)
        record = _read_identity_gap_authorization(identity_gap_authorization_path(repo_root, oid))
        if record is None:
            raise
        if record.get("work_item_id") != work_item_id or record.get("checkpoint_id") != checkpoint_id:
            raise
        return {"decision": "admit", "route": "authorized_gap", "gap_observation_id": oid,
                "work_item_id": work_item_id, "checkpoint_id": checkpoint_id}
    if result["decision"] == "observed":
        if checkpoint_id is None:
            raise WorkItemIdReusedError(
                f"work_item_id {work_item_id!r} has already appeared in D-Checkpoint-Ownership's "
                f"origination reference (commit {result['commit']}) -- work-item ids are "
                f"permanently non-reusable (D1, OPUS-R88-004)")
        raise CheckpointIdReusedError(
            f"checkpoint id {checkpoint_id!r} has already been observed for work item "
            f"{work_item_id!r} in D-Checkpoint-Ownership's origination reference (commit "
            f"{result['commit']}) -- checkpoint ids this work item has ever had observed are "
            f"permanently non-reusable, even after removal or renaming (D-Registry, "
            f"OPUS-R88-004/OPUS-R89-005)")
    return result


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
    their own output.

    **Checkpoint-id reuse** (new, revision 72, `OPUS-R89-005`): before
    writing, every checkpoint id in `registry` that is *not already
    present* in whatever registry currently sits at `registry_path` (none,
    for the first-ever write) is checked against `D-Checkpoint-Ownership`'s
    origination reference via `identity_reference_admits`. This is
    deliberately a **delta** check, scoped to ids genuinely new to this
    revision: an id kept live across revisions -- `workflow-v2-1-core`'s
    own `WF8b`, redefined across dozens of revisions -- is in-place
    redefinition, not reuse, and the plan states this boundary explicitly
    rather than refusing ordinary plan revision. An id that *is* new to
    this revision and decidably observed historically (renamed back,
    reintroduced after removal) refuses with `CheckpointIdReusedError`; an
    undecidable reference read refuses with `IdentityReferenceUndecidableError`,
    cleared only by `authorize_identity_reference_gap`."""
    validate_registry_topological_order(registry)
    validate_registry_mapping_coverage(registry, mapping)

    full_registry_path = repo_root / registry_path
    previous_ids: set[str] = set()
    if full_registry_path.exists():
        previous = json.loads(full_registry_path.read_text())
        previous_ids = {checkpoint["id"] for checkpoint in previous.get("checkpoints", [])}
    new_ids = [
        checkpoint["id"] for checkpoint in registry.get("checkpoints", [])
        if checkpoint["id"] not in previous_ids
    ]
    work_item_id = registry.get("work_item_id")
    for checkpoint_id in new_ids:
        identity_reference_admits(repo_root, work_item_id, checkpoint_id)

    full_registry_path.write_text(json.dumps(registry, indent=2) + "\n")
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
    repo_root: Path | None = None,
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
    - **A fresh id is additionally checked against `D-Checkpoint-Ownership`'s
      origination reference** (new, revision 71, `OPUS-R88-004`): an id
      that has ever appeared there is permanently non-reusable, refused
      with `WorkItemIdReusedError` -- a stronger, historical check than
      the terminal-phase one above, which only ever sees *live* state.
      This check runs only when `repo_root` is supplied; a caller that
      omits it gets the unchanged, repo-independent routing this function
      always had (an in-memory/testing convenience, never the production
      call site's own path). An undecidable reference read raises
      `IdentityReferenceUndecidableError`, cleared only by the explicit
      `authorize_identity_reference_gap`.
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
        if repo_root is not None:
            identity_reference_admits(repo_root, work_item_id, None)
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


def registry_completion_status(work_item: dict, registry: dict) -> tuple[bool, str | None]:
    """`D-Scoped-Remediation-Acceptance`'s new helper: the sole place the
    terminal/non-terminal question is answered -- every other function in
    this section consumes its result rather than re-deriving it. Returns
    `(is_terminal, outstanding_checkpoint_id)`: `(True, None)` when
    `select_next_checkpoint` reports every registry checkpoint already
    `COMPLETE`; `(False, <checkpoint_id>)` otherwise, whether the next
    checkpoint is simply unselected yet (`select_next_checkpoint` returns
    an id) or blocked (`NoCheckpointReadyError`, whose own structured
    `checkpoint_id` attribute is read directly rather than parsed from its
    message text)."""
    try:
        next_checkpoint_id = select_next_checkpoint(work_item, registry)
    except NoCheckpointReadyError as exc:
        return False, exc.checkpoint_id
    if next_checkpoint_id is None:
        return True, None
    return False, next_checkpoint_id


def milestone_complete_gate_reachable(*, phase: str, is_terminal: bool) -> bool:
    """`D-Scoped-Remediation-Acceptance`'s gate function, mirroring
    `approval_gate_reachable`/`technical_approval_gate_reachable`'s
    existing non-circular pattern (D-States): `True` iff `is_terminal` and
    `phase` is `AWAITING_FUNCTIONAL_REVIEW` or `AWAITING_USER_ACCEPTANCE`
    (the latter included for forward compatibility only -- no function in
    this codebase writes it, a pre-existing gap this decision does not
    attempt to close)."""
    return is_terminal and phase in ("AWAITING_FUNCTIONAL_REVIEW", "AWAITING_USER_ACCEPTANCE")


def scoped_remediation_gate_reachable(*, phase: str, is_terminal: bool) -> bool:
    """`D-Scoped-Remediation-Acceptance`'s second gate function: `True` iff
    `phase == "AWAITING_FUNCTIONAL_REVIEW"` and `not is_terminal` -- mutually
    exclusive with `milestone_complete_gate_reachable` by construction,
    since exactly one of `is_terminal`/`not is_terminal` holds at once."""
    return phase == "AWAITING_FUNCTIONAL_REVIEW" and not is_terminal


def resolve_own_registry_completion_status(repo_root: Path, work_item: dict) -> tuple[bool, str | None]:
    """The single place a work item's own registry is resolved and loaded
    to answer `registry_completion_status` (`D-Scoped-Remediation-
    Acceptance`, revision 24, `GPT-R37-001`): `complete_work_item`'s
    authoritative gate and `/accept-milestone`'s/`/accept-scoped-
    remediation`'s own advisory pre-flight reads all call this one
    function rather than each re-deriving the resolution/loading logic.

    `work_item["registry_path"]` is `None` (a registry-less item, e.g. the
    legacy `milestone-8` shape): no load is attempted, vacuously terminal
    -- `(True, None)`.

    Otherwise the path is resolved via the same shared tracked-metadata
    safe-path validator `D3`'s whole-state mirror check already uses
    (`fingerprint._validate_plan_stage_metadata_path`), read, and parsed as
    JSON. A failure at safe-path resolution, existence/readability, or
    JSON-object parsing raises `RegistryCoverageError`, naming the work
    item and its declared `registry_path` and the specific failure. The
    loaded registry's own `work_item_id` field disagreeing with the work
    item actually being resolved is the same `RegistryCoverageError`,
    naming both the expected and the foreign registry's declared id --
    since the registry was loaded from the item's own declared
    `registry_path`, not supplied by any caller, this case can now only
    mean the on-disk file itself is misconfigured or cross-linked, never a
    caller-side substitution (closing the trust gap `GPT-R37-001` found in
    revision 23's caller-supplied-dict design).

    Before the loaded registry's completion status is trusted, its live
    bytes must also be exactly the ones the current `plan_approval`
    covers (revision 27, `GPT-R43-002`) -- `_assert_registry_covered_by_
    current_plan_approval` raises `StalePlanApprovalRegistryReadError`
    otherwise, whether the registry is a dirty tracked edit made after
    approval or a clean, committed-but-unapproved mutation. `RegistryCoverageError`
    alone (the checks above) only proves the file is safe/well-formed/
    self-declaring, never that it is the approved one."""
    work_item_id = work_item["work_item_id"]
    registry_path = work_item.get("registry_path")
    if registry_path is None:
        return True, None

    try:
        fingerprint._validate_plan_stage_metadata_path(
            repo_root, "registry_path", registry_path, at_commit=None,
        )
    except fingerprint.InvalidPlanStageMetadataPathError as exc:
        raise RegistryCoverageError(
            f"work_items[{work_item_id!r}].registry_path {registry_path!r} failed "
            f"safe-path resolution: {exc}"
        ) from exc

    registry_full = repo_root / registry_path
    try:
        registry_bytes = registry_full.read_text()
    except OSError as exc:
        raise RegistryCoverageError(
            f"work_items[{work_item_id!r}].registry_path {registry_path!r} does not "
            f"exist or is unreadable at {registry_full}"
        ) from exc
    try:
        registry_data = json.loads(registry_bytes)
    except json.JSONDecodeError as exc:
        raise RegistryCoverageError(
            f"work_items[{work_item_id!r}].registry_path {registry_path!r} is not "
            f"valid JSON: {exc}"
        ) from exc
    if not isinstance(registry_data, dict):
        raise RegistryCoverageError(
            f"work_items[{work_item_id!r}].registry_path {registry_path!r} does not "
            f"contain a JSON object (found {type(registry_data).__name__})"
        )

    registry_work_item_id = registry_data.get("work_item_id")
    if registry_work_item_id != work_item_id:
        raise RegistryCoverageError(
            f"work_items[{work_item_id!r}].registry_path {registry_path!r} declares "
            f"work_item_id {registry_work_item_id!r}, expected {work_item_id!r}"
        )

    _assert_registry_covered_by_current_plan_approval(repo_root, work_item, registry_path)

    return registry_completion_status(work_item, registry_data)


def _assert_registry_covered_by_current_plan_approval(
    repo_root: Path, work_item: dict, registry_path: str,
) -> None:
    """`GPT-R43-002`'s own fix: the registry bytes
    `resolve_own_registry_completion_status` is about to trust for
    terminality must be exactly the bytes `plan_approval` covers, not
    merely a safely-resolvable, well-formed, self-declaring tracked file.
    Reuses `plan_approval.review_content_manifest`'s own per-path blob
    record (already the durable, approval-time snapshot every plan
    approval writes) rather than recomputing a whole fresh plan-stage
    projection with a guessed `base_commit` -- this work item's own
    continued-scope rounds compute that projection against the
    plan-approval commit, not `work_item["base_commit"]`
    (`REVIEW_REQUEST.md`'s own documented convention), so there is no
    single `base_commit` value this helper could safely assume; a direct
    blob comparison needs none."""
    work_item_id = work_item["work_item_id"]
    plan_approval = work_item.get("plan_approval")
    if plan_approval is None or plan_approval.get("status") != "CURRENT":
        raise StalePlanApprovalRegistryReadError(
            f"work_items[{work_item_id!r}] has no CURRENT plan_approval -- "
            f"registry-derived completion cannot be trusted"
        )
    manifest = plan_approval.get("review_content_manifest") or []
    approved_entry = next(
        (entry for entry in manifest if entry.get("path") == registry_path), None,
    )
    if approved_entry is None:
        raise StalePlanApprovalRegistryReadError(
            f"work_items[{work_item_id!r}].registry_path {registry_path!r} is not "
            f"named in the current plan_approval.review_content_manifest -- cannot "
            f"prove the read registry bytes are plan-approved"
        )
    live_blob = fingerprint._hash_object(repo_root, registry_path)
    approved_blob = approved_entry.get("blob")
    if live_blob != approved_blob:
        raise StalePlanApprovalRegistryReadError(
            f"work_items[{work_item_id!r}].registry_path {registry_path!r} live blob "
            f"{live_blob!r} does not match the current plan_approval's recorded blob "
            f"{approved_blob!r} -- the registry was modified after plan approval"
        )


def complete_work_item(state: dict, work_item_id: str, now: str, *, repo_root: Path) -> dict:
    """D1's completion/reset text: on `MILESTONE_COMPLETE` (or
    process-completion archival), the entry's phase becomes terminal and,
    if it was `active_work_item_id`, that pointer resets to `null` so the
    next `/milestone-plan` creates a fresh entry and claims the pointer.
    Returns a new state dict.

    D-Functional-Remediation's "parent acceptance blocks on an incomplete
    child" rule (resolves `GPT-R9-016`, `WFR-35`): refuses outright, naming
    every still-incomplete child, rather than completing a parent whose
    broad remediation work is still open elsewhere.

    `D-Scoped-Remediation-Acceptance`'s own-checkpoint-completion block
    (resolves `WF8B-002`, hardened `GPT-R37-001`): independent of, and in
    addition to, the child-completion check above, this now also resolves
    and loads the work item's **own** registry authoritatively (never a
    caller-supplied dict -- `resolve_own_registry_completion_status`,
    `repo_root`-driven) and refuses via `IncompleteOwnCheckpointsError`,
    naming the outstanding checkpoint, when the item's own registry has any
    checkpoint that is not `COMPLETE`."""
    blocking = incomplete_children(state, work_item_id)
    if blocking:
        raise IncompleteChildWorkItemError(
            f"{work_item_id!r} cannot reach MILESTONE_COMPLETE while child work "
            f"item(s) {blocking} have not themselves reached MILESTONE_COMPLETE"
        )

    work_item = state["work_items"][work_item_id]
    is_terminal, outstanding_checkpoint_id = resolve_own_registry_completion_status(repo_root, work_item)
    if not is_terminal:
        raise IncompleteOwnCheckpointsError(
            f"{work_item_id!r} cannot reach MILESTONE_COMPLETE -- its own checkpoint "
            f"{outstanding_checkpoint_id!r} is not COMPLETE (use /accept-scoped-remediation "
            f"if this is a continued-scope remediation round, or complete the checkpoint first)"
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
# D-Commit-Provenance / D-Approval-Commits, WF8B-003 remediation (WF8b):
# the `Workflow-Bundle-Generation-Record` trailer and the provenance-
# interval check that replaces a bare `reviewed_implementation_head ==
# HEAD` comparison. `record_bundle_generation` above still writes
# `reviewed_implementation_head`/`implementation_revision` into `state`
# exactly as before; the caller is now responsible for persisting that
# write as its own dedicated commit (touching only `WORKFLOW_STATE.json`,
# carrying the trailers below) *before* generating the bundle, so no
# later commit can ever land between the write and the commit that makes
# it durable (D-Approval-Commits, WF8B-003's own worked contradiction).
# Only the "ordinary" role is implemented here -- the recovered/
# superseded (`Workflow-Supersedes`) role is out of scope for this
# remediation slice; see `MalformedBundleGenerationRecordCommitError`.
# ---------------------------------------------------------------------------


ORDINARY_BUNDLE_GENERATION_RECORD_FIELDS = frozenset({
    "phase", "reviewed_implementation_head", "implementation_revision",
    "state_revision", "last_transition",
})


def discover_bundle_generation_record_commits(
    repo_root: Path, work_item_id: str, base_commit: str, head: str = "HEAD",
) -> dict[str, str]:
    """Every commit reachable in `base_commit..head` carrying an exact
    `Workflow-Bundle-Generation-Record: <work_item_id>/<implementation_revision>`
    + `Workflow-Work-Item: <work_item_id>` trailer pair, requiring exactly
    one match per `<work_item_id>/<implementation_revision>` value after
    the shared first-parent-ancestor tie-break. Returns
    `{"<work_item_id>/<implementation_revision>": commit_sha}`."""
    return _discover_trailer_commits(
        repo_root, "Workflow-Bundle-Generation-Record", work_item_id, base_commit, head,
        ambiguous_error_cls=AmbiguousBundleGenerationRecordTrailerError,
    )


def discover_current_bundle_generation_record_commit(
    repo_root: Path, work_item_id: str, base_commit: str, head: str, implementation_revision: int,
) -> str | None:
    """The specific `Workflow-Bundle-Generation-Record` commit for this
    work item's *current* `implementation_revision`, or `None` if none is
    reachable."""
    matches = discover_bundle_generation_record_commits(repo_root, work_item_id, base_commit, head)
    return matches.get(f"{work_item_id}/{implementation_revision}")


def _commit_own_changed_paths(repo_root: Path, commit: str) -> set[str]:
    """The paths a single commit itself changes, relative to its own
    (first) parent -- distinct from `_changed_paths_between`, which
    diffs two arbitrary endpoints of a range."""
    parent = _run(["git", "rev-parse", f"{commit}^"], cwd=repo_root).strip()
    return _changed_paths_between(repo_root, parent, commit)


def _read_json_at_commit_or_empty(repo_root: Path, commit: str, rel_path: str) -> dict:
    """`git show <commit>:<rel_path>`, parsed as JSON, or `{}` if the path
    does not exist at that commit (e.g. a work item's very first
    Workflow-Bundle-Generation-Record commit, whose parent predates
    WORKFLOW_STATE.json's own creation)."""
    result = subprocess.run(
        ["git", "show", f"{commit}:{rel_path}"], cwd=repo_root, capture_output=True, text=True,
    )
    if result.returncode != 0:
        return {}
    return json.loads(result.stdout)


def _work_item_field_diff(repo_root: Path, commit: str, work_item_id: str) -> set[str]:
    """Field names that differ in `work_items[work_item_id]` between a
    commit's own parent and the commit itself, read from each side's
    actually-committed `WORKFLOW_STATE.json` -- never the working tree."""
    parent = _run(["git", "rev-parse", f"{commit}^"], cwd=repo_root).strip()
    state_rel = DEFAULT_STATE_PATH.as_posix()
    before = _read_json_at_commit_or_empty(repo_root, parent, state_rel)
    after = _read_json_at_commit_or_empty(repo_root, commit, state_rel)
    before_item = before.get("work_items", {}).get(work_item_id, {})
    after_item = after.get("work_items", {}).get(work_item_id, {})
    keys = set(before_item) | set(after_item)
    return {k for k in keys if before_item.get(k) != after_item.get(k)}


def validate_bundle_generation_record_commit(repo_root: Path, commit: str, work_item_id: str) -> None:
    """Validates a discovered `Workflow-Bundle-Generation-Record` commit
    against its ordinary-role contract (D-Approval-Commits/D-Commit-
    Provenance condition 4, revision 28 onward): touches only
    `WORKFLOW_STATE.json`; its own `work_items[work_item_id]` field
    changes are a non-empty subset of
    `ORDINARY_BUNDLE_GENERATION_RECORD_FIELDS`; and it carries exactly
    the two-trailer ordinary set, no other. Raises
    `MalformedBundleGenerationRecordCommitError` naming the concrete
    mismatch otherwise."""
    changed_paths = _commit_own_changed_paths(repo_root, commit)
    state_rel = DEFAULT_STATE_PATH.as_posix()
    if changed_paths != {state_rel}:
        raise MalformedBundleGenerationRecordCommitError(
            f"{commit} carries a Workflow-Bundle-Generation-Record trailer but "
            f"touches {sorted(changed_paths)}, not exactly {{{state_rel!r}}}"
        )
    field_diff = _work_item_field_diff(repo_root, commit, work_item_id)
    if not field_diff or not field_diff <= ORDINARY_BUNDLE_GENERATION_RECORD_FIELDS:
        raise MalformedBundleGenerationRecordCommitError(
            f"{commit}'s own {work_item_id!r} field changes are {sorted(field_diff)}, "
            f"not a non-empty subset of {sorted(ORDINARY_BUNDLE_GENERATION_RECORD_FIELDS)}"
        )
    trailers = _commit_trailers(repo_root, commit)
    if set(trailers) != {"Workflow-Bundle-Generation-Record", "Workflow-Work-Item"}:
        raise MalformedBundleGenerationRecordCommitError(
            f"{commit} carries trailer set {sorted(trailers)}, not exactly the "
            f"ordinary {{'Workflow-Bundle-Generation-Record', 'Workflow-Work-Item'}} set"
        )


def verify_implementation_provenance_interval(
    repo_root: Path, work_item: dict, base_commit: str, head: str = "HEAD",
) -> str:
    """D-Commit-Provenance's "Provenance interval" check (revision 28
    onward, `WF8B-003`) -- the real replacement for a bare
    `reviewed_implementation_head == HEAD` comparison, which the
    contradiction below has been proven to permanently re-break:
    `record_bundle_generation`'s write must become a durable commit for
    fresh-session resumption, and that durability commit is, by
    definition, one commit ahead of whatever `reviewed_implementation_head`
    itself names.

    Given `P = reviewed_implementation_head` and the discovered current
    `Workflow-Bundle-Generation-Record` commit `T` for this work item's
    `implementation_revision`:

    1. Live `head` must equal `T` exactly -- not merely a descendant of it
       (`HeadPastBundleGenerationRecordError`).
    2. `P` must be reachable from `T` via `T`'s own first-parent chain,
       never merely reachable by some other path
       (`ReviewedImplementationHeadNotAncestorError` if not reachable at
       all, `NonFirstParentProvenanceIntervalError` if reachable only off
       the first-parent chain); every commit strictly inside the interval
       must itself have exactly one parent
       (`NonFirstParentProvenanceIntervalError` on a merge).
    3. Every non-terminal commit in the interval (strictly between `P`
       and `T`) must classify implementation-stage excluded-only, in its
       own right -- never merely net-unchanged across the whole interval
       (`ProtectedPathInProvenanceIntervalError`).
    4. `T` itself must pass `validate_bundle_generation_record_commit`.

    Returns `T` on success. Raises `BundleGenerationRecordNotFoundError`
    if `reviewed_implementation_head`/`implementation_revision` is not
    yet set, or no matching commit is discoverable at all."""
    work_item_id = work_item["work_item_id"]
    p = work_item.get("reviewed_implementation_head")
    implementation_revision = work_item.get("implementation_revision")
    if not p or not implementation_revision:
        raise BundleGenerationRecordNotFoundError(
            f"{work_item_id!r} has no reviewed_implementation_head/implementation_revision yet"
        )
    t = discover_current_bundle_generation_record_commit(
        repo_root, work_item_id, base_commit, head, implementation_revision,
    )
    if t is None:
        raise BundleGenerationRecordNotFoundError(
            f"no Workflow-Bundle-Generation-Record commit found for "
            f"{work_item_id}/{implementation_revision} in {base_commit}..{head}"
        )
    live_head = _run(["git", "rev-parse", head], cwd=repo_root).strip()
    if live_head != t:
        raise HeadPastBundleGenerationRecordError(
            f"live HEAD {live_head} is not exactly the discovered "
            f"Workflow-Bundle-Generation-Record commit {t} for "
            f"{work_item_id}/{implementation_revision} -- a further commit landed "
            f"after it carrying no provenance record of its own"
        )
    if not _is_ancestor(repo_root, p, t):
        raise ReviewedImplementationHeadNotAncestorError(
            f"reviewed_implementation_head {p} is not an ancestor of the discovered "
            f"Workflow-Bundle-Generation-Record commit {t}"
        )
    first_parent_chain = _first_parent_commits_ordered(repo_root, t)
    if p not in first_parent_chain:
        raise NonFirstParentProvenanceIntervalError(
            f"reviewed_implementation_head {p} is reachable from {t} but not via "
            f"{t}'s first-parent chain -- the provenance interval crosses a merge "
            f"or a non-first-parent path"
        )
    p_index = first_parent_chain.index(p)
    interval = first_parent_chain[:p_index]  # newest-first: [t, ..., commit-right-after-p]
    non_terminal = interval[1:]  # excludes t itself
    impl_protected_paths, impl_protected_prefixes, impl_excluded_paths, impl_excluded_prefixes = (
        fingerprint.load_implementation_stage_classification(
            repo_root, fingerprint.artifacts_path_for_work_item(work_item_id),
        )
    )
    for commit in non_terminal:
        parents = _run(["git", "rev-parse", f"{commit}^@"], cwd=repo_root).split()
        if len(parents) != 1:
            raise NonFirstParentProvenanceIntervalError(
                f"{commit} in the provenance interval {p}..{t} is a merge commit "
                f"({len(parents)} parents) -- the interval must be a plain first-parent chain"
            )
        for path in sorted(_commit_own_changed_paths(repo_root, commit)):
            classification = fingerprint.classify_path_implementation_stage(
                path, impl_protected_paths, impl_protected_prefixes,
                impl_excluded_paths, impl_excluded_prefixes,
            )
            if classification != "excluded":
                raise ProtectedPathInProvenanceIntervalError(
                    f"{commit} in the provenance interval {p}..{t} touches "
                    f"{path!r}, classified {classification!r}, not excluded -- only "
                    f"the terminal Workflow-Bundle-Generation-Record commit itself "
                    f"may exist between reviewed_implementation_head and live HEAD"
                )
    validate_bundle_generation_record_commit(repo_root, t, work_item_id)
    return t


def implementation_provenance_interval_reachable(
    repo_root: Path, work_item: dict, base_commit: str, head: str = "HEAD",
) -> bool:
    """Boolean wrapper for `technical_approval_gate_reachable`'s
    `head_matches_reviewed_implementation_head` argument: `True` exactly
    when `verify_implementation_provenance_interval` finds a valid
    interval, `False` for any of its named refusal reasons. A caller that
    needs the concrete refusal reason (e.g. `/approve-review implementation`'s
    own step 1 report) should call `verify_implementation_provenance_interval`
    directly instead."""
    try:
        verify_implementation_provenance_interval(repo_root, work_item, base_commit, head)
        return True
    except (
        BundleGenerationRecordNotFoundError,
        HeadPastBundleGenerationRecordError,
        ReviewedImplementationHeadNotAncestorError,
        NonFirstParentProvenanceIntervalError,
        ProtectedPathInProvenanceIntervalError,
        MalformedBundleGenerationRecordCommitError,
        AmbiguousBundleGenerationRecordTrailerError,
    ):
        return False


# ---------------------------------------------------------------------------
# D-Scoped-Remediation-Acceptance continued: functional-checklist evidence
# trailer discovery (`/prepare-functional-review`'s new dedicated commit),
# the pre-commit evidence guard, `/accept-scoped-remediation`'s confirmation
# binding-field parser, round replay/duplicate classification, and the
# acceptance record writer.
# ---------------------------------------------------------------------------


def discover_scoped_remediation_commits(
    repo_root: Path, work_item_id: str, base_commit: str, head: str = "HEAD",
) -> dict[str, str]:
    """Every commit reachable in `base_commit..head` carrying an exact
    `Workflow-Scoped-Remediation-Acceptance: <outstanding_checkpoint_id>/
    <implementation_revision>` + `Workflow-Work-Item: <work_item_id>`
    trailer pair (revision 23, `GPT-R37-002`'s corrected keying: by
    `implementation_revision`, not `work_item_id`, so each round for the
    same still-incomplete checkpoint gets its own distinct key). Returns
    `{"<checkpoint_id>/<implementation_revision>": commit_sha}`."""
    return _discover_trailer_commits(
        repo_root, "Workflow-Scoped-Remediation-Acceptance", work_item_id, base_commit, head,
        ambiguous_error_cls=AmbiguousScopedRemediationTrailerError,
    )


def discover_functional_checklist_commits(
    repo_root: Path, work_item_id: str, base_commit: str, head: str = "HEAD",
) -> dict[str, str]:
    """Every commit reachable in `base_commit..head` carrying an exact
    `Workflow-Functional-Checklist: <work_item_id>/<implementation_revision>/
    <checklist_blob>` + `Workflow-Work-Item: <work_item_id>` trailer pair
    (revision 26, `GPT-R39-001`'s content-scoped trailer value). Returns
    `{"<work_item_id>/<implementation_revision>/<blob>": commit_sha}` --
    raises only for a genuine identical-*content* duplicate reachable by
    more than one first-parent path, never for an ordinary content
    revision, since each distinct checklist content is its own distinct
    key by construction."""
    return _discover_trailer_commits(
        repo_root, "Workflow-Functional-Checklist", work_item_id, base_commit, head,
        ambiguous_error_cls=AmbiguousFunctionalChecklistTrailerError,
    )


def discover_current_functional_checklist_evidence(
    repo_root: Path, work_item_id: str, base_commit: str, head: str, implementation_revision: int,
) -> dict[str, str] | None:
    """Revision 26 (`GPT-R39-001`)'s round-scoped, content-identity-aware
    lookup: enumerates every `Workflow-Functional-Checklist` trailer value
    starting with the round-scoped prefix `<work_item_id>/
    <implementation_revision>/` (not a single exact-key lookup, since the
    trailer value also embeds the checklist's own blob), and returns the
    entry whose commit is nearest `head` -- the round's *current* evidence.
    Multiple historical evidence commits for the same round (an older,
    superseded checklist plus a newer, corrected one) coexist without
    ambiguity by construction, since each has its own distinct trailer
    value. Returns `{"commit_sha": ..., "blob": ...}`, or `None` if no
    evidence commit exists for the round at all. Raises
    `NonFirstParentFunctionalChecklistEvidenceError` (revision 27 correction,
    `GPT-R41-002`) when round-scoped evidence commits exist in
    `base_commit..head` but none sits on `head`'s first-parent chain --
    e.g. evidence prepared on a side branch that was merged without ever
    becoming a first-parent transition. Never falls back to picking one
    such candidate by ordinary reachable-history order."""
    matches = discover_functional_checklist_commits(repo_root, work_item_id, base_commit, head)
    prefix = f"{work_item_id}/{implementation_revision}/"
    candidates = {value: commit for value, commit in matches.items() if value.startswith(prefix)}
    if not candidates:
        return None
    commit_to_blob = {commit: value[len(prefix):] for value, commit in candidates.items()}
    for commit in _first_parent_commits_ordered(repo_root, head):
        if commit in commit_to_blob:
            return {"commit_sha": commit, "blob": commit_to_blob[commit]}
    # Every candidate is reachable in base_commit..head (the discovery call
    # above already proved that) but none sits on head's first-parent
    # chain -- e.g. a merged side branch whose evidence commit never became
    # a first-parent transition. Fail closed with a named, actionable error
    # rather than picking one candidate by ordinary reachable-history order
    # (GPT-R41-002): silently accepting a non-first-parent candidate would
    # let side-branch evidence become authoritative despite the resolver's
    # own first-parent contract.
    raise NonFirstParentFunctionalChecklistEvidenceError(
        f"{len(commit_to_blob)} evidence commit(s) found for {prefix.rstrip('/')} in "
        f"{base_commit}..{head}, but none is on {head}'s first-parent chain: "
        f"{sorted(commit_to_blob)} -- re-commit the checklist evidence directly on "
        f"the first-parent line"
    )


def build_scoped_remediation_live_snapshot(
    repo_root: Path, work_item: dict, *, checklist_path: str = FUNCTIONAL_CHECKLIST_PATH,
) -> dict:
    """The pre-commit evidence guard's cross-invocation reference point:
    captured once, early in `/accept-scoped-remediation`'s own invocation
    (before the entry guard's registry load), and passed unchanged into
    every later call of `verify_functional_checklist_evidence` so its
    fourth check can detect a value that changed mid-invocation."""
    return {
        "reviewed_implementation_head": work_item.get("reviewed_implementation_head"),
        "checklist_blob_at_head": _run(
            ["git", "rev-parse", f"HEAD:{checklist_path}"], cwd=repo_root,
        ).strip(),
    }


def verify_functional_checklist_evidence(
    repo_root: Path, work_item: dict, *, base_commit: str, head: str,
    confirmed_commit: str, confirmed_blob: str, expected_live_snapshot: dict,
    checklist_path: str = FUNCTIONAL_CHECKLIST_PATH,
) -> dict:
    """The pre-commit evidence guard's four ordered checks (`GPT-R36-003`,
    extended `GPT-R37-004`, extended `GPT-R38-001`, discovery corrected
    revision 26 `GPT-R39-001`, confirmation-evidence-binding check added
    revision 27 `GPT-R40-001`). Callers run this once, before building the
    scoped-remediation acceptance entry (with `expected_live_snapshot`
    captured moments earlier via `build_scoped_remediation_live_snapshot`),
    and once more, immediately before the provenance commit, passing the
    *same* `expected_live_snapshot` both times -- a working-tree edit or a
    superseding evidence commit landing in the gap between the two calls is
    exactly as unreviewed as one present from the start.

    1. **Discoverability**: a `Workflow-Functional-Checklist` evidence
       commit must be discoverable for the exact live round --
       `MissingFunctionalChecklistEvidenceError` naming the missing round
       key and `/prepare-functional-review` as the remedy, or
       `NonFirstParentFunctionalChecklistEvidenceError` (`GPT-R41-002`) if
       round-scoped evidence exists only off `head`'s first-parent chain.
    2. **Confirmation-evidence binding**: the confirmed commit/blob must
       equal the round's current evidence exactly --
       `StaleFunctionalChecklistConfirmationError`, naming both identities,
       otherwise. The commit's own actually-committed content at
       `checklist_path` must also equal the confirmed blob --
       `MalformedFunctionalChecklistEvidenceError` otherwise.
    3. **Clean working tree**: `checklist_path` must have no staged or
       unstaged change relative to `HEAD` -- `DirtyFunctionalChecklistPathError`
       otherwise.
    4. **Cross-invocation value agreement**: `reviewed_implementation_head`
       and `checklist_path`'s committed blob at `HEAD` must both still
       equal `expected_live_snapshot` -- `ScopedRemediationLiveValueChangedError`
       otherwise.

    Returns the discoverability check's own `{"commit_sha", "blob"}` result
    on success."""
    work_item_id = work_item["work_item_id"]
    implementation_revision = work_item["implementation_revision"]

    current = discover_current_functional_checklist_evidence(
        repo_root, work_item_id, base_commit, head, implementation_revision,
    )
    if current is None:
        raise MissingFunctionalChecklistEvidenceError(
            f"no Workflow-Functional-Checklist evidence commit found for "
            f"{work_item_id}/{implementation_revision} -- run /prepare-functional-review first"
        )

    if confirmed_commit != current["commit_sha"] or confirmed_blob != current["blob"]:
        raise StaleFunctionalChecklistConfirmationError(
            f"user_confirmation names evidence commit {confirmed_commit!r}/blob "
            f"{confirmed_blob!r}, but the round's current evidence is "
            f"{current['commit_sha']!r}/{current['blob']!r} -- review the newer "
            f"/prepare-functional-review report and reconfirm"
        )

    actual_blob = _run(
        ["git", "rev-parse", f"{confirmed_commit}:{checklist_path}"], cwd=repo_root,
    ).strip()
    if actual_blob != confirmed_blob:
        raise MalformedFunctionalChecklistEvidenceError(
            f"commit {confirmed_commit} actually committed blob {actual_blob!r} at "
            f"{checklist_path!r}, but its Workflow-Functional-Checklist trailer names "
            f"blob {confirmed_blob!r}"
        )

    status = _run(["git", "status", "--porcelain", "--", checklist_path], cwd=repo_root)
    if status.strip():
        raise DirtyFunctionalChecklistPathError(
            f"{checklist_path} has uncommitted changes -- commit or discard them before "
            f"accepting scoped remediation"
        )

    live_now = build_scoped_remediation_live_snapshot(repo_root, work_item, checklist_path=checklist_path)
    if live_now != expected_live_snapshot:
        raise ScopedRemediationLiveValueChangedError(
            f"reviewed_implementation_head or {checklist_path}'s committed content "
            f"changed since this invocation began: expected {expected_live_snapshot}, "
            f"found {live_now}"
        )
    return current


def parse_scoped_remediation_confirmation_binding_fields(text: str) -> dict[str, str]:
    """The `scoped_remediation` stage's own binding-field parser (revision
    27, `GPT-R40-001`), mirroring the pattern `REVIEW_PROTOCOL.md`'s
    `Reviewed bundle ID:`/`Reviewed base commit:`/`Work item:` fields
    already establish for external review feedback
    (`parse_review_feedback_binding_fields`). Requires two explicit fields
    in the confirmation text -- `Functional checklist evidence commit:
    <sha>` and `Functional checklist evidence blob: <blob>` -- each a
    full, well-formed 40-hex Git object id; missing or malformed:
    `UserConfirmationRejectedError`, naming which field is missing or
    malformed."""
    fields: dict[str, str] = {}
    for key, label in (
        ("functional_checklist_evidence_commit", "Functional checklist evidence commit"),
        ("functional_checklist_evidence_blob", "Functional checklist evidence blob"),
    ):
        match = re.search(rf"{re.escape(label)}:\s*(\S*)", text)
        if not match or not match.group(1):
            raise UserConfirmationRejectedError(
                f"user_confirmation is missing the required {label!r} field"
            )
        value = match.group(1)
        if not _GIT_OBJECT_ID_RE.match(value):
            raise UserConfirmationRejectedError(
                f"user_confirmation's {label!r} field {value!r} is not a well-formed "
                f"40-hex Git object id"
            )
        fields[key] = value
    return fields


class NoExistingRound:
    """`resolve_scoped_remediation_round` outcome: no commit carries this
    exact round's `Workflow-Scoped-Remediation-Acceptance` trailer yet --
    a first attempt, or a genuinely new round for a checkpoint scoped-
    accepted before under a different `implementation_revision`."""

    def __repr__(self) -> str:
        return "NoExistingRound()"

    def __eq__(self, other: object) -> bool:
        return isinstance(other, NoExistingRound)


class ExactReplay:
    """`resolve_scoped_remediation_round` outcome: a commit exists for this
    round and its own committed acceptance entry agrees with every live
    field -- a provable replay, safe to report idempotently."""

    def __init__(self, commit_sha: str) -> None:
        self.commit_sha = commit_sha

    def __repr__(self) -> str:
        return f"ExactReplay({self.commit_sha!r})"

    def __eq__(self, other: object) -> bool:
        return isinstance(other, ExactReplay) and other.commit_sha == self.commit_sha


class ConflictingDuplicate:
    """`resolve_scoped_remediation_round` outcome: a commit exists for this
    round but at least one canonical field disagrees with the live values
    -- never silently treated as a replay."""

    def __init__(self, commit_sha: str, differing_fields: list[str]) -> None:
        self.commit_sha = commit_sha
        self.differing_fields = differing_fields

    def __repr__(self) -> str:
        return f"ConflictingDuplicate({self.commit_sha!r}, {self.differing_fields!r})"

    def __eq__(self, other: object) -> bool:
        return (
            isinstance(other, ConflictingDuplicate)
            and other.commit_sha == self.commit_sha
            and other.differing_fields == self.differing_fields
        )


class MalformedAcceptanceRecord:
    """`resolve_scoped_remediation_round` outcome: a commit exists for this
    round but its own committed acceptance entry's schema cannot be
    trusted enough to compare field values from at all."""

    def __init__(self, commit_sha: str, reason: str) -> None:
        self.commit_sha = commit_sha
        self.reason = reason

    def __repr__(self) -> str:
        return f"MalformedAcceptanceRecord({self.commit_sha!r}, {self.reason!r})"

    def __eq__(self, other: object) -> bool:
        return (
            isinstance(other, MalformedAcceptanceRecord)
            and other.commit_sha == self.commit_sha
            and other.reason == self.reason
        )


class AmbiguousHistory:
    """`resolve_scoped_remediation_round` outcome: more than one first-
    parent-reachable commit carries this exact round's trailer -- the same
    genuine-ambiguity recovery every other trailer scheme here already
    has; manual history inspection is required."""

    def __init__(self, round_key: str) -> None:
        self.round_key = round_key

    def __repr__(self) -> str:
        return f"AmbiguousHistory({self.round_key!r})"

    def __eq__(self, other: object) -> bool:
        return isinstance(other, AmbiguousHistory) and other.round_key == self.round_key


def resolve_scoped_remediation_round(
    repo_root: Path, work_item_id: str, base_commit: str, head: str,
    outstanding_checkpoint_id: str, implementation_revision: int, live_fields: dict,
) -> NoExistingRound | ExactReplay | ConflictingDuplicate | MalformedAcceptanceRecord | AmbiguousHistory:
    """The **single** place replay/duplicate classification happens
    (revision 25, `GPT-R38-002`; comparison coverage completed revision 26,
    `GPT-R39-002`; widened revision 27, `GPT-R40-002`) -- called identically
    by `/accept-scoped-remediation`'s entry guard and its provenance-commit
    step, never a second copy of the logic. Always performs the full
    lookup-and-compare in one pass; there is no cheaper "key exists" path
    that skips the comparison.

    `live_fields` must carry the eight keys
    `_SCOPED_REMEDIATION_COMPARISON_FIELD_MAP` names: `outstanding_checkpoint_id`,
    `implementation_revision`, `reviewed_implementation_head`,
    `technical_approval_review_content_id`, `functional_checklist_path`,
    `functional_checklist_blob`, `functional_checklist_evidence_commit`,
    `active_work_item_id`."""
    round_key = f"{outstanding_checkpoint_id}/{implementation_revision}"
    try:
        matches = discover_scoped_remediation_commits(repo_root, work_item_id, base_commit, head)
    except AmbiguousScopedRemediationTrailerError:
        return AmbiguousHistory(round_key)

    commit_sha = matches.get(round_key)
    if commit_sha is None:
        return NoExistingRound()

    state_json = _run(
        ["git", "show", f"{commit_sha}:{DEFAULT_STATE_PATH.as_posix()}"], cwd=repo_root,
    )
    try:
        committed_state = json.loads(state_json)
    except json.JSONDecodeError as exc:
        return MalformedAcceptanceRecord(
            commit_sha, f"committed {DEFAULT_STATE_PATH} is not valid JSON: {exc}",
        )

    entries = (
        committed_state.get("work_items", {}).get(work_item_id, {}).get("scoped_remediation_acceptance") or []
    )
    entry = None
    for candidate in entries:
        if (
            candidate.get("outstanding_checkpoint_id") == outstanding_checkpoint_id
            and candidate.get("implementation_revision") == implementation_revision
        ):
            entry = candidate
            break
    if entry is None:
        return MalformedAcceptanceRecord(
            commit_sha, f"no scoped_remediation_acceptance entry matches round key {round_key!r}",
        )

    # Schema/version check, first (revision 26, GPT-R39-002; version/field
    # count updated revision 27, GPT-R40-002) -- an unsupported or
    # incomplete schema shape cannot be compared against live_fields
    # meaningfully at all.
    if entry.get("acceptance_record_version") != 2:
        return MalformedAcceptanceRecord(
            commit_sha,
            f"unsupported acceptance_record_version: {entry.get('acceptance_record_version')!r}",
        )
    if set(entry.keys()) != SCOPED_REMEDIATION_ACCEPTANCE_FIELDS:
        return MalformedAcceptanceRecord(
            commit_sha,
            f"entry field set does not match the eleven documented fields: {sorted(entry.keys())}",
        )
    recorded_at = entry.get("recorded_at")
    user_confirmation = entry.get("user_confirmation")
    if not isinstance(recorded_at, str) or not recorded_at:
        return MalformedAcceptanceRecord(commit_sha, "recorded_at is missing or not a well-formed non-empty string")
    if not isinstance(user_confirmation, str) or not user_confirmation:
        return MalformedAcceptanceRecord(
            commit_sha, "user_confirmation is missing or not a well-formed non-empty string",
        )

    # Full canonical field comparison (revision 26, GPT-R39-002, widened
    # revision 27, GPT-R40-002): recorded_at/user_confirmation are
    # validated above but never compared to a live value -- recorded_at is
    # a historical timestamp by definition, and user_confirmation's
    # *current*-turn counterpart is already checked separately by
    # validate_user_confirmation.
    differing = [
        entry_field for entry_field, live_key in _SCOPED_REMEDIATION_COMPARISON_FIELD_MAP.items()
        if entry.get(entry_field) != live_fields.get(live_key)
    ]
    if differing:
        return ConflictingDuplicate(commit_sha, differing)
    return ExactReplay(commit_sha)


def build_scoped_remediation_live_fields(
    state: dict, work_item_id: str, *, outstanding_checkpoint_id: str,
    functional_checklist_evidence_commit: str, functional_checklist_blob: str,
) -> dict:
    """Builds the eight-key `live_fields` dict `resolve_scoped_remediation_round`
    compares a committed entry against -- read fresh at classification
    time, never cached across the entry guard's two `resolve_scoped_remediation_round`
    call sites (the entry guard itself, and the provenance-commit step)."""
    work_item = state["work_items"][work_item_id]
    return {
        "outstanding_checkpoint_id": outstanding_checkpoint_id,
        "implementation_revision": work_item["implementation_revision"],
        "reviewed_implementation_head": work_item.get("reviewed_implementation_head"),
        "technical_approval_review_content_id": (
            (work_item.get("technical_approval") or {}).get("approved_review_content_id")
        ),
        "functional_checklist_path": FUNCTIONAL_CHECKLIST_PATH,
        "functional_checklist_blob": functional_checklist_blob,
        "functional_checklist_evidence_commit": functional_checklist_evidence_commit,
        "active_work_item_id": state.get("active_work_item_id"),
    }


def apply_scoped_remediation_acceptance(
    state: dict, work_item_id: str, *, outstanding_checkpoint_id: str,
    functional_checklist_evidence_commit: str, functional_checklist_blob: str,
    user_confirmation: str, now: str,
) -> dict:
    """Appends one entry to `work_item["scoped_remediation_acceptance"]` (a
    list, created empty if absent) -- the eleven documented fields
    (`SCOPED_REMEDIATION_ACCEPTANCE_FIELDS`). Sets `phase = "IMPLEMENTING"`.
    Leaves `checkpoints`/`current_checkpoint_id`/`active_work_item_id`/
    `plan_approval`/`technical_approval`/`functional_acceptance_status`
    completely untouched -- this function's caller (`/accept-scoped-
    remediation`) is responsible for creating the dedicated, metadata-only
    provenance commit this write and the returned state must land in
    together."""
    work_item = state["work_items"][work_item_id]
    entry = {
        "outstanding_checkpoint_id": outstanding_checkpoint_id,
        "active_work_item_id_at_acceptance": state.get("active_work_item_id"),
        "implementation_revision": work_item["implementation_revision"],
        "reviewed_implementation_head": work_item["reviewed_implementation_head"],
        "technical_approval_review_content_id": work_item["technical_approval"]["approved_review_content_id"],
        "functional_checklist_path": FUNCTIONAL_CHECKLIST_PATH,
        "functional_checklist_blob": functional_checklist_blob,
        "functional_checklist_evidence_commit": functional_checklist_evidence_commit,
        "user_confirmation": user_confirmation,
        "recorded_at": now,
        "acceptance_record_version": 2,
    }
    assert set(entry.keys()) == SCOPED_REMEDIATION_ACCEPTANCE_FIELDS  # internal consistency, never user-facing

    new_state = copy.deepcopy(state)
    new_work_item = new_state["work_items"][work_item_id]
    new_work_item.setdefault("scoped_remediation_acceptance", []).append(entry)
    new_work_item["phase"] = "IMPLEMENTING"
    new_work_item["state_revision"] = new_work_item.get("state_revision", 1) + 1
    new_work_item["last_transition"] = now
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
    `fingerprint._validate_plan_stage_metadata_path`, the same *tracked*
    metadata-path validator plan-stage resolution uses (`GPT-R32-001`,
    tightened by `GPT-R33-002`) -- an absolute path, a `../` traversal, a
    symlink at any path component, a path that does not exist as a
    regular file, or a path that exists but is not a Git-tracked file all
    fail closed as `MissingRegistryForPlanRevisionMirrorCheckError` rather
    than silently reading whatever `repo_root / registry_path` happens to
    join to (an untracked file would otherwise become the authoritative
    comparison source for a mirror check meant to police tracked,
    committed metadata). Malformed JSON or a JSON value that is not an
    object fails the same way, by name, instead of a raw
    `JSONDecodeError`/`AttributeError` (`GPT-R33-004`). The loaded
    registry must also declare the exact same `work_item_id` as the
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
            # GPT-R32-001/GPT-R33-002: reuse the authoritative *tracked*
            # metadata-path validator (grammar, symlink, existence, git
            # tracking) instead of the filesystem-only safe-path resolver.
            # The filesystem-only helper alone would let an untracked JSON
            # file dropped anywhere in the worktree become the
            # authoritative comparison source for this state mirror --
            # exactly as unusable a "registry" as a missing one, since it
            # is absent from commits, review bundles, fresh sessions, and
            # approval provenance.
            try:
                fingerprint._validate_plan_stage_metadata_path(
                    repo_root, "registry_path", registry_path, at_commit=None,
                )
            except fingerprint.InvalidPlanStageMetadataPathError as exc:
                raise MissingRegistryForPlanRevisionMirrorCheckError(
                    f"work_items[{work_item_id!r}].registry_path {registry_path!r} "
                    f"failed safe-path resolution: {exc}"
                ) from exc
            registry_full = repo_root / registry_path
            try:
                registry_bytes = registry_full.read_text()
            except OSError as exc:
                raise MissingRegistryForPlanRevisionMirrorCheckError(
                    f"work_items[{work_item_id!r}].registry_path {registry_path!r} "
                    f"does not exist or is unreadable at {registry_full}"
                ) from exc
            # GPT-R33-004: malformed JSON or a well-formed value that is
            # not a JSON object must fail through this check's own named,
            # work-item-specific error, not a raw `JSONDecodeError`/
            # `AttributeError` that bypasses the workflow's fail-closed
            # error model.
            try:
                registry_data = json.loads(registry_bytes)
            except json.JSONDecodeError as exc:
                raise MissingRegistryForPlanRevisionMirrorCheckError(
                    f"work_items[{work_item_id!r}].registry_path {registry_path!r} "
                    f"is not valid JSON: {exc}"
                ) from exc
            if not isinstance(registry_data, dict):
                raise MissingRegistryForPlanRevisionMirrorCheckError(
                    f"work_items[{work_item_id!r}].registry_path {registry_path!r} "
                    f"does not contain a JSON object (found {type(registry_data).__name__})"
                )

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
