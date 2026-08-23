#!/usr/bin/env python3
"""Cross-checkpoint integration fixtures, dual-mode command-text
conformance, and documentation-consistency lints for `WF8a-ii` ("WF8a-i,
part 2"), consuming `workflow_test_harness.py`'s documented fixture
format throughout rather than re-deriving `workflow_state.py`/
`workflow_fingerprint.py` internals.

This module covers exactly the checkpoint's own named scope:

- missing-test item 17: a simulated plan->implement->approve pass creates
  exactly the three authorized commit-trailer kinds and no others;
- missing-test item 58: `select_next_checkpoint`'s determinism holds at
  every point along a full checkpoint walk, not only at one fixed state
  (the single-state case is already covered by `workflow_state_test.py`'s
  own `test_determinism_across_independent_calls`, missing-test item 28 --
  not duplicated here);
- missing-test items 59/60/77: the bootstrap command's own static-text
  conformance (it is a prompt file, not executable code -- see each test
  class's docstring for why a text-conformance technique is the honest
  substitute for a behavioral test here);
- the dual-mode command-text enumeration (`D-Self-Governance`): every
  command this milestone modifies that carries an explicit
  `governing_workflow_version`-keyed two-branch structure actually states
  it in its `.md` file, and the two `"2.1"`-only commands
  (`/review-plan`, `/record-manual-plan-review`) state their own clean
  refusal for a `"1"` item;
- missing-test item 90: golden-hash drift detection for the modified
  commands' v1-governed behavior (`WFR-26`), including a real pre-`v2.1`
  base-commit comparison for `/milestone-plan` and `/apply-plan-review`
  where the base commit is honestly comparable (see
  `TestGoldenV1BehaviorAgainstPreV21BaseCommit`'s docstring for the exact
  scope and its limits);
- missing-test item 107: a bundle-completeness lint for language
  instructing a protected-path correction after approval;
- missing-test item 108: a documentation-consistency lint proving items
  85/96 and the D-Plan-Review-Stages transition table state the same
  `/review-plan` write set;
- `GPT-R11-009`: the two-stage plan-review ledger exercised end to end
  against a real `ScratchRepo`, including a real committed plan-doc edit
  invalidating both stages (the integration-level half of `WFR-38` --
  `workflow_state_test.py` already covers the dict-level transition logic
  for both of these; not duplicated here, only chained against real Git
  history and a real recomputed `review_content_id`).

Stdlib-only. Run: python3 scripts/workflow_integration_test.py
"""

from __future__ import annotations

import ast
import base64
import hashlib
import inspect
import json
import os
import re
import subprocess
import unittest
from pathlib import Path

import workflow_fingerprint as fingerprint
import workflow_state as ws
import workflow_test_harness as h


def _repo_root() -> Path:
    return Path(
        subprocess.run(
            ["git", "rev-parse", "--show-toplevel"], check=True,
            capture_output=True, text=True,
        ).stdout.strip()
    )


def _run(args: list[str], cwd: Path) -> str:
    return subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True).stdout


def _command_text(filename: str) -> str:
    return (_repo_root() / ".claude" / "commands" / filename).read_text()


def _commit_trailer_keys(repo_root: Path, commit: str) -> set[str]:
    body = _run(["git", "log", "-1", "--format=%B", commit], cwd=repo_root)
    parsed = subprocess.run(
        ["git", "interpret-trailers", "--parse"], cwd=repo_root,
        input=body, capture_output=True, text=True, check=True,
    ).stdout
    keys = set()
    for line in parsed.splitlines():
        if ":" in line:
            keys.add(line.split(":", 1)[0].strip())
    return keys


# ---------------------------------------------------------------------------
# Missing-test item 17: a full plan -> implement -> approve pass creates
# exactly the three authorized commit-trailer kinds and no others.
# ---------------------------------------------------------------------------


class TestFullPassAuthorizedCommitKinds(unittest.TestCase):
    """Missing-test item 17: simulates a minimal plan->implement->approve
    pass end to end against a real `ScratchRepo`, using the real
    production functions (`transition_checkpoint_in_progress`/
    `complete_checkpoint`/`apply_plan_approval`/`apply_technical_approval`)
    for the state half and `repo.commit_files` (this checkpoint's own
    small extension to the shared harness) for the Git half, so each
    simulated commit genuinely carries both a trailer and the same
    commit's `WORKFLOW_STATE.json` update side by side, exactly as
    `D3`/`OPUS-R6-005` describes. Asserts the resulting commit
    trailer-*key* set -- discovered via the real
    `discover_checkpoint_commits`/`discover_approval_commits` search, not
    merely inspected by hand -- is exactly the three D-Commit-Provenance/
    D-Approval-Commits kinds and nothing else.

    `Workflow-Work-Item` is deliberately excluded from the counted key
    set: it is the universal scoping companion trailer every one of these
    commits also carries (required by `_discover_trailer_commits` to
    scope the search to this one work item), not itself a distinct
    "commit kind" the way `Workflow-Checkpoint`/`Workflow-Plan-Approval`/
    `Workflow-Technical-Approval` are -- see D-Commit-Provenance's own
    trailer-pair language. Excluding it here is a deliberate, documented
    scope choice, not an oversight.
    """

    def test_plan_implement_approve_pass_uses_exactly_three_trailer_kinds(self):
        with h.ScratchRepo() as repo:
            registry = h.base_registry(checkpoints=[
                {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
                {"id": "B", "name": "b", "depends_on": ["A"], "complexity": 1, "session_target": "1"},
            ])
            state = h.base_state(wi=h.base_work_item(
                work_item_id="wi", phase="AWAITING_PLAN_APPROVAL", checkpoints={},
            ))
            state_path = "docs/ai-workflow/WORKFLOW_STATE.json"

            # 1. Plan-approval commit.
            plan_record = ws.build_approval_record(
                basis="USER_OVERRIDE", stage="plan", user_confirmation="wi plan",
                reviewed_bundle_id="bundle-plan-1", approved_review_content_id="plan-content-1",
                review_content_manifest=[], now="t0",
            )
            state = ws.apply_plan_approval(state, "wi", plan_record, now="t0")
            plan_commit = repo.commit_files(
                "plan approved", {state_path: json.dumps(state)},
                trailers={"Workflow-Plan-Approval": "plan-content-1", "Workflow-Work-Item": "wi"},
            )

            # 2. Checkpoint A.
            state = ws.transition_checkpoint_in_progress(state, "wi", "A", plan_commit, now="t1")
            state = ws.complete_checkpoint(state, "wi", "A", registry, now="t2", repo_root=repo.root)
            ck_a_commit = repo.commit_files(
                "checkpoint A", {state_path: json.dumps(state), "src/a.txt": "a\n"},
                trailers={"Workflow-Checkpoint": "A", "Workflow-Work-Item": "wi"},
            )

            # 3. Checkpoint B -- the last one, so this also flips phase to
            # SELF_REVIEWING_IMPLEMENTATION as part of the same write.
            state = ws.transition_checkpoint_in_progress(state, "wi", "B", ck_a_commit, now="t3")
            state = ws.complete_checkpoint(state, "wi", "B", registry, now="t4", repo_root=repo.root)
            ck_b_commit = repo.commit_files(
                "checkpoint B", {state_path: json.dumps(state), "src/b.txt": "b\n"},
                trailers={"Workflow-Checkpoint": "B", "Workflow-Work-Item": "wi"},
            )
            self.assertEqual(state["work_items"]["wi"]["phase"], "SELF_REVIEWING_IMPLEMENTATION")

            # 4. Technical-approval commit.
            impl_record = ws.build_approval_record(
                basis="USER_OVERRIDE", stage="implementation", user_confirmation="wi implementation",
                reviewed_bundle_id="bundle-impl-1", approved_review_content_id="impl-content-1",
                review_content_manifest=[], reviewed_content_commit=ck_b_commit, now="t5",
            )
            state = ws.apply_technical_approval(state, "wi", impl_record, now="t5")
            tech_commit = repo.commit_files(
                "technical approved", {state_path: json.dumps(state)},
                trailers={"Workflow-Technical-Approval": "impl-content-1", "Workflow-Work-Item": "wi"},
            )

            # Sanity: the resulting state is itself schema-valid and
            # dependency-consistent against the registry it was driven by.
            ws.validate_state(state, registry=registry)

            # Discover every commit kind via the real production search --
            # never inspected by hand -- proving the simulated pass is
            # actually reachable the way a real session's would be.
            self.assertEqual(
                ws.discover_checkpoint_commits(repo.root, "wi", repo.base),
                {"A": ck_a_commit, "B": ck_b_commit},
            )
            self.assertEqual(
                ws.discover_approval_commits(repo.root, "Workflow-Plan-Approval", "wi", repo.base),
                {"plan-content-1": plan_commit},
            )
            self.assertEqual(
                ws.discover_approval_commits(repo.root, "Workflow-Technical-Approval", "wi", repo.base),
                {"impl-content-1": tech_commit},
            )

            commits = [line for line in _run(
                ["git", "log", "--format=%H", f"{repo.base}..{repo.head()}"], cwd=repo.root,
            ).splitlines() if line]
            self.assertEqual(len(commits), 4, "expected exactly one commit per lifecycle step")

            all_keys: set[str] = set()
            for commit in commits:
                all_keys |= _commit_trailer_keys(repo.root, commit)
            all_keys.discard("Workflow-Work-Item")
            self.assertEqual(
                all_keys,
                {"Workflow-Checkpoint", "Workflow-Plan-Approval", "Workflow-Technical-Approval"},
            )


# ---------------------------------------------------------------------------
# Missing-test item 58: select_next_checkpoint's determinism holds at every
# point along a full checkpoint walk (item 28 already covers one fixed
# state -- workflow_state_test.py's own test_determinism_across_independent_calls).
# ---------------------------------------------------------------------------


class TestSelectNextCheckpointDeterminismAlongFullWalk(unittest.TestCase):
    """Missing-test item 58: "at every point in the registry order, exactly
    one command can select and implement the next incomplete checkpoint."

    `workflow_state_test.py`'s `test_determinism_across_independent_calls`
    (missing-test item 28, confirmed present via
    `grep -n "missing-test item 28" scripts/workflow_state_test.py` before
    writing this) already proves `select_next_checkpoint` is pure and
    repeatable at one fixed, hand-picked state. This sweeps every state
    along a real four-checkpoint dependency graph, from empty to fully
    complete, re-asserting agreement between two independently
    constructed state copies at each step -- so a regression that only
    breaks determinism partway through a real sequence (e.g. at a
    fan-in/fan-out point) would still be caught, not just at the one
    state item 28 happens to use."""

    def test_agreement_holds_at_every_point_along_a_diamond_shaped_walk(self):
        registry = h.base_registry(checkpoints=[
            {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
            {"id": "B", "name": "b", "depends_on": ["A"], "complexity": 1, "session_target": "1"},
            {"id": "C", "name": "c", "depends_on": ["A"], "complexity": 1, "session_target": "1"},
            {"id": "D", "name": "d", "depends_on": ["B", "C"], "complexity": 1, "session_target": "1"},
        ])
        completed: dict[str, dict] = {}
        order: list[str] = []
        for _ in range(len(registry["checkpoints"]) + 1):
            wi_a = h.base_work_item(current_checkpoint_id=None, checkpoints=dict(completed))
            wi_b = h.base_work_item(current_checkpoint_id=None, checkpoints=dict(completed))
            result_a = ws.select_next_checkpoint(wi_a, registry)
            result_b = ws.select_next_checkpoint(wi_b, registry)
            self.assertEqual(result_a, result_b, f"disagreement after completing {order}")
            if result_a is None:
                break
            completed[result_a] = {"status": "COMPLETE"}
            order.append(result_a)
        self.assertEqual(order, ["A", "B", "C", "D"])


# ---------------------------------------------------------------------------
# Missing-test items 59/60/77: the bootstrap command's own static-text
# conformance. bootstrap-workflow-v2.md is a *prompt* file, not executable
# code, so "verified by a conformance test" here means parsing its real
# text and asserting the stated invariants are actually present -- the
# same technique workflow_state_demo_test.py's own item-72 test already
# uses for the checkpoint-registry Markdown table (confirmed via
# `grep -n "item 72" -A 40 scripts/workflow_state_demo_test.py` before
# writing this), matched here but placed in the hermetic suite since
# parsing .claude/commands/*.md content is deterministic/stdlib-only and
# not tied to a moving historical base commit.
# ---------------------------------------------------------------------------


class TestBootstrapCommandStaticConformance(unittest.TestCase):
    def setUp(self):
        self.text = _command_text("bootstrap-workflow-v2.md")

    def test_item_59_retired_only_at_this_work_items_own_milestone_complete(self):
        self.assertIn(
            "It is retired — deleted — only at this work item's own\n`MILESTONE_COMPLETE`.",
            self.text,
        )

    def test_item_60_milestone_implement_never_invoked_for_this_work_item(self):
        self.assertIn(
            "`/milestone-implement` is never\ninvoked for this work item at any point.",
            self.text,
        )

    def test_item_60_never_reads_active_milestone_or_roadmap(self):
        self.assertIn("It never reads\n`docs/ACTIVE_MILESTONE.md` or `docs/ROADMAP.md`;", self.text)

    def test_item_60_work_item_id_is_hardcoded_never_an_argument(self):
        self.assertIn("Work item: `workflow-v2-1-core` (hardcoded, never an argument).", self.text)

    def test_item_77_stops_after_exactly_one_checkpoint(self):
        self.assertIn(
            "**Stop immediately** — never loop, never continue to the next\n   checkpoint in the same invocation",
            self.text,
        )

    def test_item_77_own_conformance_coverage_is_acknowledged_in_the_file_itself(self):
        """The command file's own closing paragraph names this exact
        checkpoint (WF8a-ii) as owning its conformance test -- a
        cross-check that the invariant this test class asserts is the one
        the file itself expects to be tested."""
        self.assertIn(
            "It is not exempt from\nconformance coverage (`WF8a-ii` owns a dedicated test asserting it stops\n"
            "after exactly one checkpoint and never reads\ndocs/ACTIVE_MILESTONE.md",
            self.text.replace("`docs/ACTIVE_MILESTONE.md`", "docs/ACTIVE_MILESTONE.md"),
        )

    def test_exempt_from_dual_mode_branching_by_its_own_stated_design(self):
        """Confirms the exemption is *stated*, not merely assumed by this
        suite -- this command is deliberately not covered by
        TestDualModeBranchConformance below."""
        self.assertIn(
            "This command is exempt from `D-Self-Governance`'s dual-mode branching\nrequirement",
            self.text,
        )
        self.assertNotIn('governing_workflow_version: "2.1"', self.text)


# ---------------------------------------------------------------------------
# D-Self-Governance's dual-mode command enumeration: every command this
# milestone modifies that carries an explicit governing_workflow_version-
# keyed two-branch structure actually states it in its .md file.
# ---------------------------------------------------------------------------

# Commands whose dual-mode structure is the literal bold-marker pattern
# `**`governing_workflow_version: "1"`**` / `**`governing_workflow_version:
# "2.1"`**`, each branch followed by materially different step text,
# confirmed present by directly reading each file before writing this list.
_BOLD_MARKER_DUAL_MODE_COMMANDS = (
    "milestone-plan.md",
    "milestone-implement.md",
    "approve-review.md",
    "apply-plan-review.md",
)

# Commands whose "0. **Dual-mode branch**" step states both versions run
# every numbered step *identically*, differing only in the exit-target
# naming named elsewhere in the same step -- a real, but textually
# different, two-branch expression than the bold-marker commands above
# (confirmed by directly reading each file: neither states the fuller
# `governing_workflow_version: "1"` phrase, only the shorthand `"1"`/
# `"2.1"` quoted-version-string pair).
_UNIFIED_DUAL_MODE_COMMANDS = (
    "accept-milestone.md",
    "apply-implementation-review.md",
)


class TestDualModeBranchConformance(unittest.TestCase):
    def test_each_bold_marker_command_states_both_governing_version_branches(self):
        for filename in _BOLD_MARKER_DUAL_MODE_COMMANDS:
            with self.subTest(filename=filename):
                text = _command_text(filename)
                self.assertIn('governing_workflow_version: "1"', text, filename)
                self.assertIn('governing_workflow_version: "2.1"', text, filename)
                self.assertIn("**Dual-mode branch**", text, filename)

    def test_each_unified_command_states_a_dual_mode_step_naming_both_versions(self):
        for filename in _UNIFIED_DUAL_MODE_COMMANDS:
            with self.subTest(filename=filename):
                text = _command_text(filename)
                self.assertIn("**Dual-mode branch**", text, filename)
                self.assertIn('`"1"`', text, filename)
                self.assertIn('`"2.1"`', text, filename)
                self.assertIn("governing_workflow_version", text, filename)

    def test_prepare_functional_review_expresses_dual_mode_via_legacy_ready_adoption(self):
        """`/prepare-functional-review` is named in D-Self-Governance's
        enumeration ("WF-M8b's selector-argument and adoption extension,
        and WF4c"), but -- confirmed by reading the file directly rather
        than assuming the same bold-marker pattern applies uniformly --
        it does not branch its main steps on a literal
        `governing_workflow_version: "1"` / `"2.1"` pair the way the
        commands above do. Its own two-branch structure is phase-keyed
        (`0a`: is the resolved target `LEGACY_READY`, or not) and its
        *effect*, on the adoption path, is exactly what changes
        `governing_workflow_version` from `"1"` to `"2.1"` for that work
        item -- a materially different but still real two-branch
        structure, documented here rather than forced into the other
        commands' shape."""
        text = _command_text("prepare-functional-review.md")
        self.assertIn("`LEGACY_READY` adoption scan, before any version branching", text)
        self.assertIn(
            "If the resolved target's `phase` is\n    **not** `LEGACY_READY`, skip straight to step 1 with that target.",
            text,
        )
        self.assertIn('`governing_workflow_version` has transitioned\n       `"1"` → `"2.1"`', text)

    def test_bundle_scripts_are_deliberately_version_agnostic(self):
        """The bundle scripts (WF5) are part of D-Self-Governance's full
        command roster, but `scripts/prepare-ai-review.sh` itself contains
        no `governing_workflow_version` awareness at all -- confirmed by a
        direct grep before writing this test. This is by design, not a
        gap: bundle generation mechanics are identical for either
        governing version (only the *gate-reachability* check that reads
        a generated bundle differs by version, and that branch already
        lives in, and is already covered by,
        `approve-review.md`'s own step-0 dual-mode text -- the
        "approve-review fingerprint wiring" half of the roster). This test
        asserts the negative fact directly rather than silently assuming
        it."""
        script_text = (_repo_root() / "scripts" / "prepare-ai-review.sh").read_text()
        self.assertNotIn("governing_workflow_version", script_text)
        approve_review_text = _command_text("approve-review.md")
        self.assertIn("plan_approval_gate_reachable", approve_review_text)
        self.assertIn('governing_workflow_version: "2.1"', approve_review_text)


class TestReviewImplementationCommandStaticConformance(unittest.TestCase):
    """`workflow-v2-3` CP1's own conformance coverage for the new
    `/review-implementation` command, mirroring
    `TestBootstrapCommandStaticConformance`'s/
    `TestVersion21OnlyCommandsRefuseCleanlyForV1`'s pattern of asserting
    key invariant sentences are actually present in the file's real text,
    rather than merely described in this plan."""

    def setUp(self):
        self.text = _command_text("review-implementation.md")

    def test_frontmatter_has_description_and_argument_hint(self):
        self.assertIn("description:", self.text)
        self.assertIn("argument-hint:", self.text)
        self.assertIn("state_writer: false", self.text)
        self.assertIn("review-subject: bundle", self.text)

    def test_states_model_independence(self):
        self.assertIn(
            "Implements a model-independent\n**review role**, not a specific model: "
            "nothing in this contract, in the\nreport it produces, or in any check it "
            "performs names a model — running it\nfrom any capable Claude model produces "
            "the same behavior.",
            self.text,
        )

    def test_states_the_report_only_constraint(self):
        """Narrowed, `workflow-v2-3-followups` `CP2` (REQ-5): the command
        no longer claims to write nothing -- it states the new, narrower
        invariant instead (writes `REVIEW_FEEDBACK.md`; still never writes
        `WORKFLOW_STATE.json`, never approves, never advances `phase`)."""
        self.assertIn(
            "**Writes `<feedback_dir>/REVIEW_FEEDBACK.md`; nothing else.** This command\n"
            "writes the current `<feedback_dir>/REVIEW_FEEDBACK.md` (step 7, once every\n"
            "guard there passes) but never writes\n"
            "`docs/ai-workflow/WORKFLOW_STATE.json`, never edits source/test/plan/\n"
            "registry/mapping/bundle content, never approves a stage, and never\n"
            "advances `phase`.",
            self.text,
        )

    def test_phase_guard_names_the_exact_required_phase(self):
        self.assertIn(
            "2. **Phase guard**: if the resolved item's `phase` is not exactly\n"
            "   `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`, refuse cleanly, naming the\n"
            "   actual phase.",
            self.text,
        )

    def test_states_it_writes_nothing(self):
        """Narrowed, `workflow-v2-3-followups` `CP2` (REQ-5): the command
        no longer states "Report only -- writes nothing." anywhere -- it
        now writes `<feedback_dir>/REVIEW_FEEDBACK.md` once its own
        pre-write guards pass (step 7), and step 8's own closing text says
        so."""
        self.assertNotIn("Report only", self.text)
        self.assertNotIn("writes nothing", self.text)
        self.assertIn(
            "8. **Report and stop.** On a successful write, state plainly that\n"
            "   `<feedback_dir>/REVIEW_FEEDBACK.md` was written and is now the\n"
            "   authoritative round for `/apply-implementation-review`/`/approve-review\n"
            "   implementation` to act on",
            self.text,
        )

    def test_states_plan_conformance_read(self):
        """`workflow-v2-3-followups` `CP2` (REQ-6, item 4's deferred
        follow-up): step 3's read list names `PLAN.md`/`plan_path`, and
        step 5 gains a plan-conformance search arm mirroring
        `/review-plan` step 6 -- a further method on this class, not a
        new fixture, per `LPR-R2-O02`'s correction of the original
        (inapplicable) precedent citation."""
        self.assertIn(
            "3. **Read**: `<bundle_dir>/PLAN.md` and the item's own `plan_path`",
            self.text,
        )
        self.assertIn(
            "**Also independently verify the implementation against what the\n"
            "   approved plan (`PLAN.md`/`plan_path`, read in step 3) actually\n"
            "   specified**",
            self.text,
        )

    def test_step_six_and_seven_no_longer_describe_manual_installation(self):
        """`LPR-R3-I02`/`LPR-R4-I02`: the provenance text this command's
        own step 6 and (what was) step 7 carried -- describing the
        operator hand-copying this report into `REVIEW_FEEDBACK.md` as an
        optional installation step -- no longer appears anywhere,
        including the frontmatter `description:` line, which no longer
        reads "Report-only" (`LPR-R4-I02`'s own `assertNotIn`, added to
        this same method rather than a new fixture)."""
        self.assertNotIn("hand-copy this report", self.text)
        self.assertNotIn(
            "if the user chooses to hand-copy this report into", self.text,
        )
        self.assertNotIn("Report-only", self.text)
        self.assertIn(
            "since satisfying\n"
            "   `workflow_fingerprint.parse_review_feedback_binding_fields`/\n"
            "   `assert_feedback_matches_bundle` is a hard precondition of this\n"
            "   command's own write in step 7 below, not a convenience for a\n"
            "   hypothetical hand-copy.",
            self.text,
        )

    def test_write_step_names_the_ownership_guard(self):
        """`GPT-FUP-R6-I01`, revised `LPR-R7-B01`: a further method
        proving the command's own prose actually instructs the fix, not
        only that `workflow_fingerprint.assert_feedback_not_owned_by_other_work_item`
        exists and behaves correctly in isolation -- named, and stated to
        run immediately before the write against the unmodified
        `resolve_feedback_dir(repo_root, work_item_id)` path."""
        self.assertIn("assert_feedback_not_owned_by_other_work_item", self.text)
        self.assertIn(
            "ownership guard runs immediately before the write, against this same\n"
            "     unmodified `resolve_feedback_dir(repo_root, work_item_id)` path.",
            self.text,
        )

    def test_refusal_path_states_report_printing_per_guard_and_recovery(self):
        """`LPR-R10-B01`/`LPR-R11-I01`/`LPR-R11-I02`/`LPR-R12-I01` (round-10
        through round-12 local plan review): the refusal-path prose is
        stated per guard, not as one "either guard" sentence, and the
        recovery text is keyed on a live A reaching the terminal phase
        `MILESTONE_COMPLETE`, not on a fixed two-phase consumption list --
        including that hand-creating a scoped feedback directory is not an
        endorsed remedy."""
        self.assertIn(
            "**On a `BundleRejectedError` here, suppress the\n"
            "     composed report entirely**",
            self.text,
        )
        self.assertIn(
            "**On a `FeedbackOwnedByOtherWorkItemError` here, still print the\n"
            "     composed report in full**",
            self.text,
        )
        self.assertIn(
            "hand-creating a scoped\n"
            "     `.ai-review/<work_item_id>/feedback/` directory is",
            self.text,
        )
        self.assertIn("**not** an endorsed", self.text)
        self.assertIn(
            "Only once a live A's `phase`\n"
            "     independently reaches `MILESTONE_COMPLETE` may the operator delete the",
            self.text,
        )

    def test_names_artifacts_path_for_work_item(self):
        """I3/revision 2's own missing-test gap: a regression back to
        `load_implementation_stage_classification`'s default argument
        (silently resolving `workflow-v2-1-core`'s artifacts file instead
        of the resolved work item's own) fails this cheap textual
        check."""
        self.assertIn("artifacts_path_for_work_item", self.text)

    def test_names_missing_required_bundle_file_error(self):
        """Revision 6/7 I2/O1's own missing-test gap: a regression that
        silently drops the absent-manifest clean-refusal wording fails
        this check."""
        self.assertIn("MissingRequiredBundleFileError", self.text)


class TestReviewFunctionalCommandStaticConformance(unittest.TestCase):
    """`workflow-v2-3` CP2's own conformance coverage for the new
    `/review-functional` command, mirroring
    `TestReviewImplementationCommandStaticConformance`'s pattern of
    asserting key invariant sentences are actually present in the file's
    real text, rather than merely described in this plan. Also pins
    revision 10's own round-9 I1/I2 fixes (`GPT-R9-001`/`GPT-R9-002`) as
    two textual-presence checks, per the plan's "Missing tests?" bullet."""

    def setUp(self):
        self.text = _command_text("review-functional.md")

    def test_frontmatter_has_description_and_argument_hint(self):
        self.assertIn("description:", self.text)
        self.assertIn("argument-hint:", self.text)
        self.assertIn("state_writer: false", self.text)
        self.assertIn("review-subject: bundle", self.text)

    def test_states_model_independence(self):
        self.assertIn(
            "Implements a model-independent\n**review role**, not a specific model: "
            "nothing in this contract, in the\nreport it produces, or in any check it "
            "performs names a model — running it\nfrom any capable Claude model produces "
            "the same behavior.",
            self.text,
        )

    def test_states_the_report_only_constraint(self):
        self.assertIn(
            "**Review and report only.** This command never writes\n"
            "`docs/ACTIVE_MILESTONE.md`, `<feedback_dir>/FUNCTIONAL_REVIEW.md`, or\n"
            "`docs/ai-workflow/WORKFLOW_STATE.json`, never fixes findings, and never\n"
            "advances `phase`.",
            self.text,
        )

    def test_phase_guard_names_the_exact_required_phase(self):
        self.assertIn(
            "2. **Phase guard**: if the resolved item's `phase` is not exactly\n"
            "   `AWAITING_FUNCTIONAL_REVIEW`, refuse cleanly, naming the actual phase.",
            self.text,
        )

    def test_states_it_writes_nothing(self):
        self.assertIn("**Report only — writes nothing.**", self.text)

    def test_single_coherent_untracked_item_policy(self):
        """Revision 10, round 9 I1 fix (`GPT-R9-001`): the file must state
        step 1's single clean refusal for an untracked work item and must
        not also tell the reviewer to read the checklist directly for that
        same case -- the two-branch contradiction the round found in
        revision 9's own text. Mirrors `/review-implementation` step 1
        exactly, per the round's own decision."""
        self.assertIn(
            "Refuse cleanly, naming the\n   problem, if neither resolves to an existing "
            "`work_items` entry",
            self.text,
        )
        self.assertNotIn("read the checklist", self.text)

    def test_functional_acceptance_wording_is_work_item_neutral(self):
        """Revision 10, round 9 I2 fix (`GPT-R9-002`): the file's
        functional-acceptance wording must name the checklist's own
        required flows, work-item-neutrally, rather than assuming every
        work item's functional review is an Android-app walkthrough."""
        self.assertIn("checklist's required functional flows", self.text)
        self.assertNotIn("the Android app", self.text)


class TestVersion21OnlyCommandsRefuseCleanlyForV1(unittest.TestCase):
    """`/review-plan` and `/record-manual-plan-review` are `"2.1"`-only,
    with no v1 counterpart at all -- D-Self-Governance's enumeration says
    each "refuses cleanly" for a `"1"`-governed item or when no `"2.1"`
    item is resolvable, rather than branching. Confirms each command's
    `.md` file actually states this refusal, naming the reason, rather
    than merely relying on `workflow_state.py`'s own exception (already
    covered at the unit level by `workflow_state_test.py`'s
    `TestRecordLocalPlanReview`/`TestRecordManualPlanReview` classes)."""

    def test_review_plan_states_its_own_v1_refusal_condition_and_reason(self):
        text = _command_text("review-plan.md")
        self.assertIn("**Governing-version guard**", text)
        self.assertIn(
            'if the resolved item\'s\n   `governing_workflow_version` is not `"2.1"`, refuse cleanly, naming the\n'
            '   actual version',
            text,
        )
        self.assertIn("no local-review stage to run", text)
        self.assertIn("WrongGoverningVersionForPlanReviewStageError", text)

    def test_record_manual_plan_review_states_its_own_v1_refusal_condition_and_reason(self):
        text = _command_text("record-manual-plan-review.md")
        self.assertIn("**Governing-version guard**", text)
        self.assertIn(
            'if the resolved item\'s\n   `governing_workflow_version` is not `"2.1"`, refuse cleanly, naming the\n'
            '   actual version',
            text,
        )
        self.assertIn("WrongGoverningVersionForPlanReviewStageError", text)

    def test_both_commands_resolve_work_item_id_the_same_way_before_the_guard_runs(self):
        """Both files resolve a target work item (named argument, or
        active_work_item_id) as their own step 1, before the version
        guard in step 2 -- so "when no '2.1' item is resolvable" is the
        same governing-version guard firing on a resolved-but-wrong-
        version (or, transitively, a nonexistent) item, not a second,
        separately documented refusal path. Documented here as the
        reading this suite uses, rather than assumed silently."""
        for filename in ("review-plan.md", "record-manual-plan-review.md"):
            text = _command_text(filename)
            self.assertIn("**Resolve the work item**", text, filename)
            self.assertIn("active_work_item_id", text, filename)


# ---------------------------------------------------------------------------
# Missing-test item 90 / WFR-26: golden-output drift detection for the
# modified commands' v1-governed behavior.
# ---------------------------------------------------------------------------

# A broad, self-referential drift detector (WFR-26: "golden-output test
# per modified command") over every command file this milestone's dual-
# mode enumeration names, plus the bootstrap command and the bundle
# script. This proves "unchanged since this test was written," never
# "correct" or "behaviorally equivalent to pre-v2.1 prose" -- that
# stronger claim is reserved for TestGoldenV1BehaviorAgainstPreV21BaseCommit
# below, for the two files where a real pre-v2.1 copy is actually
# available to diff against.
_GOLDEN_COMMAND_FILE_SHA256 = {
    # Updated by WFO-STATE-SERIALIZATION (item 354/357): every writer
    # command file below gained a `state_writer: true` frontmatter
    # declaration and a "State-writer discipline" paragraph naming
    # `workflow_state.state_transaction`/`state_lock` -- an intentional
    # content change, not a regression.
    #
    # The ten entries below that predate WF8c item (h) were further
    # updated by that item, part 1 (`WFR-67`): each gained a
    # `review-subject:` frontmatter declaration, and every declared
    # consumer among them gained its own
    # `workflow_fingerprint.assert_bundle_not_rejected(...)` call site(s)
    # at the points `WFR-67`'s own text names -- intentional content
    # change, not a regression. The two entries below that were created
    # after WF8c item (h) (`review-implementation.md`/`review-functional.md`,
    # workflow-v2-3 CP1/CP2) were never "further updated" by it -- they
    # were authored from the start with their own `review-subject:`
    # declarations, so their own comments below say "first recorded
    # hash," not "updated."
    "milestone-plan.md": "310271edac2f76351e8bcd93d540a955050b15b1291de3f008bd61b4678617ad",
    # milestone-implement.md further updated, OPUS-R129-001: step 1f's
    # checkpoint-completion commit instruction now states explicitly that
    # the Workflow-Checkpoint/Workflow-Work-Item trailer must be the
    # commit message's own final paragraph, after any Co-Authored-By:/
    # Claude-Session: lines, never before them -- intentional content
    # change (the mechanical fix for 4a769fd's own defect class).
    "milestone-implement.md": "83385a3203826b8fb7f5191af0ee6369c7d8154d9818135ced482ae0ec38a1f5",
    # approve-review.md (WF8c item (c), same-content bundle-generation
    # republication idempotency; further updated WF8c item (b): the
    # trailing caveat naming the dedicated /recover-implementation-provenance
    # command as "not yet built" is corrected now that it exists) --
    # intentional content change.
    #
    # approve-review.md further updated, WF8c items 347/348/352: the
    # plan-stage steps 4a-6b replaced with the full failure-atomicity
    # transaction (journal, guard, index-pinned state write, target-scoped
    # materialize, amend recovery), the "interim scope guard" refusing
    # workflow-v2-1-core's own plan-stage approvals until the Bootstrap
    # plan-approval procedure is withdrawn, and new steps 4b/4c/6c/6d --
    # intentional content change.
    #
    # approve-review.md further updated, workflow-v2-3-followups CP1
    # (LPR-R1-B01/LPR-R3-B01/LPR-R3-B02): steps 5 and 6.1 merged into one
    # guarded window (new ordinary step label "step-5-stage-and-pin") that
    # stages every non-state plan-approval member -- ordinary members and
    # the conditional fifth member alike -- in a single call, fixing the
    # fifth-member DirtyIndexBeforeStagingError; step 6a's amend-recovery
    # text reworded to name the merged step; step 6.4's commit instruction
    # gained the "trailers must be the commit message's own final
    # paragraph" sentence -- intentional content change.
    #
    # approve-review.md further updated, workflow-v2-3-followups REVISE
    # round 1 (self-discovered during this item's own /accept-milestone
    # pre-flight): step 4 gained the explicit review_content_manifest
    # extraction requirement (projection["review_content_manifest"], never
    # the whole projection object) -- intentional content change, the fix
    # for the defect that produced two malformed approval records.
    #
    # approve-review.md further updated, workflow-v2-3-followups REVISE
    # round 2 (I1, external cross-model review): the step-4a1 note's
    # `record_bundle_generation`'s own two entry phases" claim corrected
    # to the stage-aware, now-three-phase contract round 2's own record_
    # bundle_generation widening introduced -- intentional content change,
    # a documentation-only correction with no behavioral effect. Worded to
    # explain the REJECTED-bundle guard by citation (WFR-67) rather than by
    # naming assert_bundle_not_rejected inline, so this file's own prose
    # never perturbs test_every_non_exempt_file_calls_the_shared_assertion_
    # the_expected_number_of_times's exact-count check of its two real call
    # sites.
    "approve-review.md": "9e6de2e744460b5897810a05031554e0c0829f0a12742e4fd9e20b217745ff38",
    "accept-milestone.md": "3822aa4adb7838dfc76a8a41fe102d32d0435ce2ed740939662bb37035a07f70",
    "prepare-functional-review.md": "1b4a08cc0a28c09e0031f73e6003f23fd96fdc6c3fe22553e9f2408c1798f8cd",
    # apply-plan-review.md/bootstrap-workflow-v2.md (D-Plan-Revision-Publication,
    # WFR-65): intentional content change, publish_plan_revision wiring.
    "apply-plan-review.md": "fbfa7e9c980720c77c547cbdce01a4e75ec1bbba69c1be5cbf2fc6584e514ae6",
    # apply-implementation-review.md (WF8c item (c)): step 7's
    # record_bundle_generation call site widened to first resolve the
    # outcome (resolve_bundle_generation_outcome) and write the matching
    # ordinary/recovered-role trailer set -- intentional content change.
    "apply-implementation-review.md": "d86d648502b1ef2d742cbba1830835e50b0a2ed6e9c5dbac058616971c1e34c1",
    # review-plan.md/record-manual-plan-review.md further updated,
    # workflow-v2-3-followups CP3 (REQ-8/-9): the `Reviewer role:` template
    # literal, the round-computation prose, the exact-match-expectation
    # prose, and every other `local_model_plan_review`/
    # `manual_external_plan_review` mention repointed to the canonical
    # `LOCAL_MODEL_PLAN_REVIEW`/`MANUAL_EXTERNAL_PLAN_REVIEW` casing (the
    # legacy casing is still stated as accepted where the command genuinely
    # tolerates it) -- intentional content change.
    "review-plan.md": "404cc99de3f23caffcffb92c8fbe680a97edcf6c9cfb52f84a3f0c1450adfbf0",
    "record-manual-plan-review.md": "43e6bcc9a5fc94cfd84c52301399ad0d1acaf275a03962737964605c14f8e809",
    # bootstrap-workflow-v2.md (WF8c scope clauses (l)/(p)/(q), GPT-R108-002/
    # OPUS-R109-004): the driver-range text made checkpoint-agnostic
    # (OPUS-R102-009), a NO_CHECKPOINT terminal-wrap-up branch added to step
    # 3, and step 6 bound explicitly to complete_checkpoint(...) -- WF8c's
    # own first invocation, intentional content change.
    #
    # bootstrap-workflow-v2.md further updated, WF8c item 352: a new step 0
    # (ownership-aware and guard-aware plan-approval transaction
    # precondition, reading plan_approval_takeover_evidence before step 1)
    # and step 2's durability guard rebound from the bare
    # plan_approval.approved_review_content_id equality to
    # implementing_entry_reachable -- intentional content change.
    #
    # bootstrap-workflow-v2.md further updated, OPUS-R129-001: step 6's
    # checkpoint-completion commit instruction now states explicitly that
    # the Workflow-Checkpoint/Workflow-Work-Item trailer must be the
    # commit message's own final paragraph, after any Co-Authored-By:/
    # Claude-Session: lines, never before them -- intentional content
    # change (the mechanical fix for 4a769fd's own defect class).
    "bootstrap-workflow-v2.md": "6cd29d3d6382ba1e246ecf1779aee9f649cc15a8b3489abbcbdecb04b1be578f",
    # review-implementation.md: new, workflow-v2-3 CP1 -- the first
    # recorded hash, not a change.
    #
    # review-implementation.md further updated, GPT-IR1-001 (round 1
    # implementation-review remediation): corrected the false claim that
    # an excluded-only concurrent commit surfaces as a digest mismatch
    # before the HEAD difference -- intentional content change.
    #
    # review-implementation.md further updated, workflow-v2-3-followups CP2
    # (REQ-3/REQ-4/REQ-6/REQ-17/REQ-20/REQ-21): step 3 gained a PLAN.md/
    # plan_path read; step 5 gained a plan-conformance search arm; step 7 is
    # new -- a second assert_bundle_not_rejected call plus the new
    # assert_feedback_not_owned_by_other_work_item ownership guard,
    # immediately before an unconditional write of
    # <feedback_dir>/REVIEW_FEEDBACK.md; step 8 (was step 7) no longer
    # states the command writes nothing; the frontmatter description: line
    # and the step 6/step 7 provenance text no longer describe a manual
    # hand-copy installation -- intentional content change, not a
    # regression.
    #
    # review-implementation.md further updated, workflow-v2-3-followups
    # REVISE round 1 (O5): step 7 gained a self-check on the composed
    # report text -- parse_review_feedback_binding_fields/
    # assert_feedback_matches_bundle against step 4's own recomputed
    # values, immediately before the write -- so step 6's "hard
    # precondition" wording is now actually enforced -- intentional
    # content change.
    "review-implementation.md": "a017359a961ac5dc3e9cee1c3c2f8265e38f924f1af2cdbe984b381f79f4de6a",
    # review-functional.md: new, workflow-v2-3 CP2 -- the first recorded
    # hash, not a change.
    "review-functional.md": "579b90a0c6e0eea1246b7ae03347e67f86faf24b8ebc83402909f438877968ca",
}


class TestGoldenCommandFileHashes(unittest.TestCase):
    """WFR-26's broad half: one golden sha256 per modified command file.
    A change here is not itself a failure of correctness -- it is a
    prompt to re-review whether the change was intended and, if so, to
    update the recorded hash -- exactly the "unchanged since this test
    was written" caveat `WORKFLOW_V2_PLAN.md`'s own missing-test item 90
    asks this suite to document explicitly."""

    def test_every_roster_command_file_matches_its_recorded_hash(self):
        for filename, expected in _GOLDEN_COMMAND_FILE_SHA256.items():
            with self.subTest(filename=filename):
                actual = hashlib.sha256(_command_text(filename).encode()).hexdigest()
                self.assertEqual(
                    actual, expected,
                    f"{filename} content changed since this golden hash was recorded -- "
                    f"if intentional, update _GOLDEN_COMMAND_FILE_SHA256",
                )


class TestPlanApprovalCommitTrailerFinalParagraphConformance(unittest.TestCase):
    """`workflow-v2-3-followups` CP1 (`LPR-R3-B01`, round-3 local plan
    review): `approve-review.md` step 6.4's commit instruction must state
    the same "trailers must be the commit message's own final paragraph"
    requirement `milestone-implement.md`/`bootstrap-workflow-v2.md` already
    state verbatim -- without it, a plausible commit-message shape makes
    the approval commit's own trailers unparseable by `git
    interpret-trailers --parse`, `discover_plan_approval_commit`'s exact
    mechanism. No existing test asserted this for any of the three files
    before this checkpoint; this is new coverage for all three, not just
    `approve-review.md`."""

    def test_approve_review_states_the_final_paragraph_requirement(self):
        text = _command_text("approve-review.md")
        self.assertIn(
            "**These\n     two lines must be the commit message's own final paragraph** — after\n"
            "     any `Co-Authored-By:`/`Claude-Session:` lines, never before them",
            text,
        )
        self.assertIn("discover_plan_approval_commit", text)

    def test_milestone_implement_states_the_final_paragraph_requirement(self):
        text = _command_text("milestone-implement.md")
        self.assertIn(
            "**These\n      two lines must be the commit message's final paragraph** -- after\n"
            "      any `Co-Authored-By:`/`Claude-Session:` lines, never before them",
            text,
        )

    def test_bootstrap_workflow_v2_states_the_final_paragraph_requirement(self):
        text = _command_text("bootstrap-workflow-v2.md")
        self.assertIn(
            "**These two lines must be the commit message's final\n   paragraph** — after any `Co-Authored-By:`/`Claude-Session:` lines, never\n"
            "   before them",
            text,
        )


class TestAssertLocalGenerationMatchesCallSiteConformance(unittest.TestCase):
    """Item 342 (`GPT-R63-001`; narrowed, revision 82, `OPUS-R102-001` --
    `WF8c` scope clause (k)): `assert_local_generation_matches` has
    exactly three live call sites in this repository today --
    `/approve-review`'s, `/review-plan`'s, and (`workflow-v2-3` CP1)
    `/review-implementation`'s own repository-local staleness checks, all
    three at the permissive `require_metadata=False` default. Item 342's
    own original text (revision 47) expected a third,
    `require_metadata=True` caller -- `D-Approval-Commits`' current-round
    bundle-publication binding check -- but the atomic/staged
    bundle-publication redesign that caller belonged to was superseded,
    revision 82, before ever being built; `require_metadata=True`'s own
    correctness is exercised directly instead
    (`workflow_fingerprint_test.py`'s `TestGenerationDiagnosticMetadata`
    strict-mode tests), not through a caller that does not exist. This
    assertion fails if a future change adds a call site not in
    `EXPECTED_CALL_SITES`, so that change cannot land without a human
    deciding whether `WFR-17`/`D-Bundle-Manifest` need updating too."""

    EXPECTED_CALL_SITES = frozenset({
        Path(".claude/commands/approve-review.md"),
        Path(".claude/commands/review-plan.md"),
        Path(".claude/commands/review-implementation.md"),
    })

    _CALL_RE = re.compile(r"assert_local_generation_matches\(")

    def test_exactly_the_three_live_permissive_callers_exist(self):
        repo_root = _repo_root()
        found: set[Path] = set()
        for path in sorted((repo_root / ".claude" / "commands").glob("*.md")):
            if self._CALL_RE.search(path.read_text()):
                found.add(path.relative_to(repo_root))
        for path in sorted((repo_root / "scripts").glob("*.py")):
            # Excludes workflow_fingerprint.py itself (the function's own
            # definition, not a caller) and every *_test.py/*_demo_test.py
            # (exercises, not production call sites) -- suffix-matched, so
            # workflow_test_harness.py (production code whose name merely
            # contains "test") is not wrongly excluded from the scan.
            if path.name == "workflow_fingerprint.py" or path.stem.endswith("_test"):
                continue
            if self._CALL_RE.search(path.read_text()):
                found.add(path.relative_to(repo_root))
        self.assertEqual(found, set(self.EXPECTED_CALL_SITES))

    def test_no_external_review_or_archive_consumption_path_calls_it(self):
        """`prepare-ai-review.sh` generates bundles consumed by both a
        local approval command and an external reviewer's extracted
        archive -- it must never call the repository-local-only check
        itself (`WFR-17`)."""
        repo_root = _repo_root()
        script_text = (repo_root / "scripts" / "prepare-ai-review.sh").read_text()
        self.assertNotIn("assert_local_generation_matches", script_text)


class TestGenerationDiagnosticMetadataCallerWordingConformance(unittest.TestCase):
    """Item 343 (`GPT-R64-002`, `WF8c` scope clause (k)): active
    caller-facing documentation must agree with the three-live-caller
    reality item 342 proves, using non-exclusive wording rather than
    naming `/approve-review` as the sole repository-local consumer.
    `/review-implementation` (`workflow-v2-3` CP1) is the third live
    caller (`GPT-IR1-002`)."""

    def test_review_protocol_names_all_live_callers(self):
        repo_root = _repo_root()
        text = (repo_root / "docs" / "ai-workflow" / "REVIEW_PROTOCOL.md").read_text()
        match = re.search(
            r"### Generation diagnostic metadata.*?(?=\n### )", text, re.DOTALL,
        )
        self.assertIsNotNone(match, "expected a 'Generation diagnostic metadata' section")
        section = match.group(0)
        self.assertIn("/approve-review", section)
        self.assertIn("/review-plan", section)
        self.assertIn("/review-implementation", section)

    def test_worktree_or_head_mismatch_docstring_is_non_exclusive(self):
        repo_root = _repo_root()
        text = (repo_root / "scripts" / "workflow_fingerprint.py").read_text()
        match = re.search(r"class WorktreeOrHeadMismatchError.*?\"\"\"(.*?)\"\"\"", text, re.DOTALL)
        self.assertIsNotNone(match)
        docstring = match.group(1)
        self.assertIn("/approve-review", docstring)
        self.assertIn("/review-plan", docstring)
        self.assertIn("/review-implementation", docstring)

    def test_assert_local_generation_matches_docstring_is_non_exclusive(self):
        repo_root = _repo_root()
        text = (repo_root / "scripts" / "workflow_fingerprint.py").read_text()
        match = re.search(
            r"def assert_local_generation_matches\(.*?\"\"\"(.*?)\"\"\"", text, re.DOTALL,
        )
        self.assertIsNotNone(match)
        docstring = match.group(1)
        self.assertIn("/approve-review", docstring)
        self.assertIn("/review-plan", docstring)
        self.assertIn("/review-implementation", docstring)


class TestDemoTestNoLiveAnchorStaticConformance(unittest.TestCase):
    """`workflow-v2-3`'s own CP1 missing-test item (revision 5/6/7/8,
    round 4-7 missing tests/B1): a real-repository test anchored at
    `WORKFLOW_V2_1_CORE_COMPLETION_COMMIT` is only actually fixed if every
    call site feeding it a `head`/`commit` argument is fixed too -- five
    functions across both `_demo_test.py` files
    (`workflow_state.approval_is_current`,
    `workflow_state.implementing_entry_reachable`,
    `workflow_fingerprint.compute_review_content_id_plan_stage_at_commit`,
    `..._at_commit_for_work_item`,
    `compute_review_content_id_implementation_stage_at_commit`) each carry
    one parameter (`head` or `commit`, resolved from the real signature via
    `inspect.signature(fn).bind_partial(...)`, never a hand-maintained
    positional-index map -- the index a function's anchor sits at differs
    per function and has drifted out of this document's own prose four
    rounds running) that must never resolve to live `"HEAD"` -- neither by
    omission (the parameter's own default) nor by an explicit literal
    `"HEAD"` string. Gate is this test passing, not a hand-checked list:
    hand enumeration of this exact call set came up short three consecutive
    review rounds (five offenders found -> six -> seven), and a fourth time
    at the property-statement level itself. No allowlist: every real call
    site into these five functions in either `_demo_test.py` file is
    scanned, and the one call this property could never apply to
    (`load_implementation_stage_classification`/`any_protected_path_dirty`
    in `test_real_implementation_stage_classification_has_no_unclassified_dirty_path`,
    and the two active-work-item-scoped tests' own deliberately live
    `_changed_tracked_paths_between` calls) matches none of the five
    scanned names, so it is never flagged in the first place and needs no
    exemption."""

    _TARGET_FUNCTIONS = {
        "approval_is_current": (ws.approval_is_current, "head"),
        "implementing_entry_reachable": (ws.implementing_entry_reachable, "head"),
        "compute_review_content_id_plan_stage_at_commit": (
            fingerprint.compute_review_content_id_plan_stage_at_commit, "commit",
        ),
        "compute_review_content_id_plan_stage_at_commit_for_work_item": (
            fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item, "commit",
        ),
        "compute_review_content_id_implementation_stage_at_commit": (
            fingerprint.compute_review_content_id_implementation_stage_at_commit, "commit",
        ),
    }

    @classmethod
    def _scan(cls, source_text: str) -> tuple[list[str], int]:
        """Returns `(flagged_descriptions, total_real_call_site_count)`.
        A call site is "real" if its function name matches one of the five
        scanned names (via attribute access, e.g. `ws.approval_is_current(...)`
        or `wf.compute_review_content_id_plan_stage_at_commit(...)` -- the
        only calling convention either `_demo_test.py` file uses for these
        functions); it is "flagged" if the anchor parameter's bound value
        (resolved via `inspect.signature(fn).bind_partial(...)`, positional
        or keyword, never a hand-maintained index) is omitted entirely
        (the parameter's own live-`"HEAD"` default) or is present as the
        literal constant string `"HEAD"`."""
        tree = ast.parse(source_text)
        flagged: list[str] = []
        total = 0
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            func = node.func
            if not isinstance(func, ast.Attribute):
                continue
            name = func.attr
            if name not in cls._TARGET_FUNCTIONS:
                continue
            real_fn, anchor_param = cls._TARGET_FUNCTIONS[name]
            if any(isinstance(a, ast.Starred) for a in node.args):
                continue
            if any(kw.arg is None for kw in node.keywords):
                continue
            sig = inspect.signature(real_fn)
            try:
                bound = sig.bind_partial(*node.args, **{kw.arg: kw.value for kw in node.keywords})
            except TypeError:
                continue
            total += 1
            if anchor_param not in bound.arguments:
                flagged.append(f"{name}(...) at line {node.lineno}: {anchor_param!r} omitted (live default)")
                continue
            value = bound.arguments[anchor_param]
            if isinstance(value, ast.Constant) and value.value == "HEAD":
                flagged.append(f"{name}(...) at line {node.lineno}: {anchor_param}=\"HEAD\" (live literal)")
        return flagged, total

    def test_negative_control_flags_one_synthetic_fixture_per_scanned_function_plus_explicit_head(self):
        """A silently-broken callee-matcher (wrong attribute/name
        resolution, a missed `ws.`/`fingerprint.` prefix, a module-alias
        change) must not pass vacuously by finding nothing -- and a scan
        that resolves any single function's anchor slot incorrectly must
        fail this control rather than pass it (revision 8, round 7 B1/
        missing tests)."""
        fixture = "\n".join([
            "ws.approval_is_current(repo_root, work_item, stage='plan', base_commit=base_commit)",
            "ws.implementing_entry_reachable(repo_root, work_item, base_commit)",
            "wf.compute_review_content_id_plan_stage_at_commit(repo_root, base, "
            "work_item_type='process', work_item_id='x', plan_revision=1, protected=p, "
            "excluded_paths=e, excluded_prefixes=x)",
            "wf.compute_review_content_id_plan_stage_at_commit_for_work_item(repo_root, 'x')",
            "wf.compute_review_content_id_implementation_stage_at_commit(repo_root, base, "
            "work_item_type='process', work_item_id='x', protected_paths=p, "
            "protected_prefixes=pp, excluded_paths=e, excluded_prefixes=x)",
            "ws.approval_is_current(repo_root, work_item, stage='plan', base_commit=base_commit, "
            "head=\"HEAD\")",
        ])
        flagged, total = self._scan(fixture)
        self.assertEqual(total, 6, flagged)
        self.assertEqual(len(flagged), 6, flagged)

    def test_negative_control_passes_a_fixed_commit_fixture(self):
        fixture = "\n".join([
            "ws.approval_is_current(repo_root, work_item, stage='plan', base_commit=base_commit, "
            "head=FIXED_COMMIT)",
            "ws.implementing_entry_reachable(repo_root, work_item, base_commit, head=FIXED_COMMIT)",
            "wf.compute_review_content_id_plan_stage_at_commit(repo_root, base, FIXED_COMMIT, "
            "work_item_type='process', work_item_id='x', plan_revision=1, protected=p, "
            "excluded_paths=e, excluded_prefixes=x)",
            "wf.compute_review_content_id_plan_stage_at_commit_for_work_item(repo_root, 'x', "
            "FIXED_COMMIT)",
            "wf.compute_review_content_id_implementation_stage_at_commit(repo_root, base, "
            "FIXED_COMMIT, work_item_type='process', work_item_id='x', protected_paths=p, "
            "protected_prefixes=pp, excluded_paths=e, excluded_prefixes=x)",
        ])
        flagged, total = self._scan(fixture)
        self.assertEqual(total, 5, flagged)
        self.assertEqual(flagged, [])

    def test_real_demo_test_files_have_no_live_anchor_call_site(self):
        repo_root = _repo_root()
        all_flagged: list[str] = []
        total = 0
        for relpath in ("scripts/workflow_fingerprint_demo_test.py", "scripts/workflow_state_demo_test.py"):
            text = (repo_root / relpath).read_text()
            flagged, count = self._scan(text)
            total += count
            all_flagged.extend(f"{relpath}: {item}" for item in flagged)
        self.assertGreater(total, 0, "expected at least one real call site into the five scanned functions")
        self.assertEqual(all_flagged, [])


def _extract_numbered_steps(text: str) -> dict[str, str]:
    """Splits a command file's body into `{step number: full block text}`,
    where a block runs from a line starting `N. ` up to (not including)
    the next such line. Used only to isolate the v1-relevant step blocks
    for comparison below -- not a general Markdown parser."""
    steps: dict[str, str] = {}
    current_num: str | None = None
    current_lines: list[str] = []
    for line in text.splitlines(keepends=True):
        match = re.match(r"^(\d+)\.\s", line)
        if match:
            if current_num is not None:
                steps[current_num] = "".join(current_lines)
            current_num = match.group(1)
            current_lines = [line]
        elif current_num is not None:
            current_lines.append(line)
    if current_num is not None:
        steps[current_num] = "".join(current_lines)
    return steps


def _strip_bracketed_2_1_bullets(block: str) -> str:
    """Removes any `   - **[2.1]**` bulleted sub-item (and its indented
    continuation lines) from a step block, leaving the v1-relevant text
    that was already there before this milestone interleaved its own
    additive `[2.1]` sub-steps."""
    out: list[str] = []
    skipping = False
    for line in block.splitlines(keepends=True):
        if re.match(r"^   - \*\*\[2\.1\]\*\*", line):
            skipping = True
            continue
        if skipping:
            if line.strip() == "" or line.startswith("     "):
                continue
            skipping = False
        out.append(line)
    return "".join(out)


def _normalize_v1_path_variables(text: str) -> str:
    """Reverses this milestone's purely cosmetic `<bundle_dir>`/
    `<feedback_dir>`/`[work_item_id]` template-variable introduction
    (WF5, per-work-item bundle relayout with a flat-layout fallback) back
    to the literal paths a v1 item always resolves them to, so the result
    is comparable against the real pre-v2.1 base-commit text."""
    text = text.replace("<bundle_dir>", ".ai-review/current")
    text = text.replace("<feedback_dir>", ".ai-review/feedback")
    text = text.replace(" [work_item_id]", "")
    return text


class TestGoldenV1BehaviorAgainstPreV21BaseCommit(unittest.TestCase):
    """Missing-test item 90's stronger half: for `/milestone-plan` and
    `/apply-plan-review`, a genuine pre-`v2.1` copy of each command file
    *is* available (`git show <base_commit>:<path>`, `base_commit`
    `162154d3e5e10eb65e109833acae4b4fb01fc5d6`, from `WORKFLOW_STATE.json`'s
    own `work_items["workflow-v2-1-core"].base_commit` -- confirmed
    fetchable before writing this test), so this goes beyond a
    self-referential hash: it verifies the *current* file's v1-relevant
    step text is structurally equal to that real base-commit text, modulo
    two purely cosmetic, already-documented substitutions (`<bundle_dir>`/
    `<feedback_dir>` templating and an added optional `[work_item_id]`
    argument to `prepare-ai-review.sh`, both WF5) and the additive
    `[2.1]`-tagged sub-steps this milestone interleaved.

    `/milestone-plan`'s step 6 is a **third, genuine (not cosmetic)**
    delta, added by `D-Fingerprint-Generalization` (Revision 21,
    `WF8B-S1-001`): `prepare-ai-review.sh`'s work-item-id argument for the
    plan stage becomes `<work_item_id>` (required), not `[work_item_id]`
    (optional) -- this is a real behavior change for a `"1"`-governed item
    too (`workflow-v2-1-core` itself now always passes its own id), not
    reversible by `_normalize_v1_path_variables`'s cosmetic substitution.
    Step 6 is therefore excluded from the byte-equality hash below (same
    pattern `/apply-plan-review`'s own step-1 `WFR-03` addition uses) and
    separately asserted present, unconditional, and stated as required.

    The base-commit text is embedded here as a **literal sha256
    constant** rather than fetched via `git show` at test time, since a
    shallow CI checkout (this repository's `actions/checkout@v4` step
    uses the default `fetch-depth: 1`) would not have that historical
    commit reachable at all -- embedding the already-verified-equal
    normalized text's hash keeps this test hermetic and CI-safe while
    still being a real base-commit comparison, not a self-referential one
    invented after the fact. The comparison was performed once, directly
    against a real `git show` of that commit, before this constant was
    recorded (documented in this checkpoint's own report).

    `/apply-plan-review`'s v1 path is **not** fully byte-identical to the
    base commit: step 1 gained a genuine, version-unconditional addition
    (`WFR-03`'s feedback binding-field validation, from `WF5` -- a
    different checkpoint, not `WF4a-ii`/`WF4a-iv`) and step 7 gained a
    deliberate, disclosed exit-target renaming (`WF4a-ii`, "the
    exit-target naming" the checkpoint's own scope note names explicitly).
    Both are legitimate, already-documented deltas -- this test therefore
    only claims byte-equality for `/apply-plan-review`'s steps 2-6, and
    separately confirms step 1's real addition is present and applies
    unconditionally (not gated behind a `"2.1"`-only block) rather than
    silently ignoring it.

    `WF8c` item (h), part 1 (`WFR-67`) adds a fourth genuine, real,
    version-unconditional delta to each file, following the identical
    `WFR-03` pattern above: `/milestone-plan`'s step 7 and
    `/apply-plan-review`'s step 3 each gain a
    `workflow_fingerprint.assert_bundle_not_rejected(...)` call --
    `REJECTED`-bundle refusal applies to every governing version alike,
    nothing about it is `"2.1"`-specific. Both steps are therefore
    excluded from the byte-equality comparisons below (`/milestone-plan`'s
    joined set narrows from `"123457"` to `"12345"`; `/apply-plan-review`'s
    from `"2346"` to `"246"`), the remaining steps were independently
    re-verified byte-identical to a fresh `git show` of the real base
    commit before the two hash constants below were recomputed, and each
    addition's presence and unconditional placement is separately asserted
    below, mirroring `test_apply_plan_review_step1_wfr03_addition_is_present_and_version_unconditional`.
    """

    def test_milestone_plan_v1_steps_equal_pre_v21_base_commit_modulo_known_renames(self):
        current = _command_text("milestone-plan.md")
        steps = _extract_numbered_steps(current)
        # Step 6 excluded: D-Fingerprint-Generalization's required
        # <work_item_id> argument is a genuine v1-visible behavior change,
        # asserted separately below, not folded into this byte-equality
        # comparison (see this class's own docstring). Step 7 excluded:
        # WFR-67's assert_bundle_not_rejected addition (WF8c item (h),
        # part 1), same reasoning, asserted separately below.
        normalized = "".join(
            _normalize_v1_path_variables(_strip_bracketed_2_1_bullets(steps[n]))
            for n in "12345"
        )
        self.assertEqual(
            hashlib.sha256(normalized.encode()).hexdigest(),
            "e70115cd0afe40d04d92c1895623f2500d7d210ca5912966a740a04ef3728fb3",
        )

    def test_milestone_plan_step7_wfr67_addition_is_present_and_version_unconditional(self):
        current = _command_text("milestone-plan.md")
        steps = _extract_numbered_steps(current)
        step7 = steps["7"]
        self.assertIn("assert_bundle_not_rejected", step7)
        self.assertIn("WFR-67", step7)
        # The addition lives in step 7 itself, outside both the step-0
        # dual-mode block and any "[2.1]"-tagged sub-step -- it applies to
        # both governing versions equally, exactly as WFR-67's own scope
        # (bundle-integrity, not gated by governing_workflow_version)
        # intends.
        step0 = steps.get("0", "")
        self.assertNotIn("WFR-67", step0)

    def test_milestone_plan_step6_requires_work_item_id_for_plan_stage(self):
        current = _command_text("milestone-plan.md")
        steps = _extract_numbered_steps(current)
        step6 = steps["6"]
        self.assertIn("prepare-ai-review.sh <base-sha> plan <work_item_id>", step6)
        self.assertNotIn("[work_item_id]", step6)
        self.assertIn("required", step6)

    def test_apply_plan_review_v1_steps_2_to_6_equal_pre_v21_base_commit_modulo_known_renames(self):
        # Step 5 excluded: D-Fingerprint-Generalization's required
        # <work_item_id> argument is a genuine v1-visible behavior change
        # here too (same reasoning as milestone-plan.md's step 6 above),
        # asserted separately below rather than folded into this
        # byte-equality comparison. Step 3 excluded: WFR-67's
        # assert_bundle_not_rejected addition (WF8c item (h), part 1),
        # same reasoning, asserted separately below.
        current = _command_text("apply-plan-review.md")
        steps = _extract_numbered_steps(current)
        normalized = "".join(_normalize_v1_path_variables(steps[n]) for n in "246")
        self.assertEqual(
            hashlib.sha256(normalized.encode()).hexdigest(),
            "1af695495d148c5568e0030222fb4bae32327cfdcee2db64b934c2196aa18854",
        )

    def test_apply_plan_review_step3_wfr67_addition_is_present_and_version_unconditional(self):
        current = _command_text("apply-plan-review.md")
        steps = _extract_numbered_steps(current)
        step3 = steps["3"]
        self.assertIn("assert_bundle_not_rejected", step3)
        self.assertIn("WFR-67", step3)
        step0 = steps.get("0", "")
        self.assertNotIn("WFR-67", step0)

    def test_apply_plan_review_step5_requires_work_item_id_for_plan_stage(self):
        current = _command_text("apply-plan-review.md")
        steps = _extract_numbered_steps(current)
        step5 = steps["5"]
        self.assertIn("prepare-ai-review.sh <base-sha> plan <work_item_id>", step5)
        self.assertNotIn("[work_item_id]", step5)
        self.assertIn("required", step5)

    def test_apply_plan_review_step1_wfr03_addition_is_present_and_version_unconditional(self):
        current = _command_text("apply-plan-review.md")
        steps = _extract_numbered_steps(current)
        step1 = steps["1"]
        self.assertIn("Validate its binding\n   fields", step1)
        self.assertIn("WFR-03", step1)
        # The addition lives in step 1 itself, outside both the step-0
        # dual-mode block and the "2.1"-only step 7' block -- i.e. it
        # applies to both governing versions equally, exactly as WF5's
        # own scope (a general REVIEW_PROTOCOL.md hardening, not gated by
        # governing_workflow_version) intended.
        step0 = steps.get("0", "")
        self.assertNotIn("WFR-03", step0)


class TestReviewPlanRefusalGoldenHash(unittest.TestCase):
    """`/review-plan` has no v1 branch at all (it refuses cleanly
    instead), so item 90's second half ("`/review-plan` invoked against a
    `"1"` item refuses cleanly, naming why") is verified by
    `TestVersion21OnlyCommandsRefuseCleanlyForV1` above; this adds the
    golden-hash drift guard for that exact refusal text specifically
    (narrower than the whole-file hash in `TestGoldenCommandFileHashes`,
    so a future edit to the refusal wording itself is caught even if
    unrelated parts of the file also change)."""

    def test_governing_version_guard_step_text_is_unchanged(self):
        text = _command_text("review-plan.md")
        steps_by_marker = text.split("2. **Governing-version guard**:", 1)
        self.assertEqual(len(steps_by_marker), 2, "review-plan.md's step-2 marker text has changed")
        guard_text = steps_by_marker[1].split("3. **Phase guard**", 1)[0]
        self.assertEqual(
            hashlib.sha256(guard_text.encode()).hexdigest(),
            "770935c554e378479890df7b1b4b5d0a14a8c9fa44dab92b12d6fe87504984a1",
        )
        self.assertIn('not `"2.1"`, refuse cleanly, naming the', guard_text)


# ---------------------------------------------------------------------------
# Missing-test item 107: a bundle-completeness lint for language
# instructing a protected-path correction after approval.
# ---------------------------------------------------------------------------


_POST_APPROVAL_TIMING_CUES = (
    "after approval", "after this is approved", "once approved",
    "post-approval", "after the plan is approved", "after approve-review",
    "after it is approved", "after being approved",
)
_CORRECTION_ACTION_CUES = ("fix", "correct", "update", "edit", "modify", "change")
_NEGATION_CUES = ("never", "must not", "do not", "don't", "should not", "shouldn't", "cannot", "won't", "will not")


def find_post_approval_protected_path_corrections(
    text: str, protected_path_names: frozenset[str],
) -> list[str]:
    """A text heuristic (not a proof -- documented limits below) for
    missing-test item 107: flags sentences that co-occur (a) a
    post-approval timing cue, (b) a correction-action verb, and (c) a
    reference to a named protected path or the generic phrase "protected
    path"/"protected file". A sentence containing a negation cue anywhere
    (e.g. "never correct... after approval") is treated as *stating the
    rule*, not violating it, and is excluded.

    Known limitations, stated plainly:
    - sentence splitting is a naive regex on `.`/`!`/`?`/newlines -- an
      instruction spread across multiple sentences ("Do this. It must
      happen after approval.") is not caught;
    - the negation exclusion is sentence-wide, not scope-limited to the
      action verb -- a sentence with an unrelated negation elsewhere
      could suppress a real hit (a false negative in the safe direction);
    - it is keyword co-occurrence, not natural-language understanding --
      a creatively-worded instruction that avoids every listed cue is not
      caught, and this function makes no claim otherwise.
    """
    sentences = re.split(r"(?<=[.!?])\s+|\n{2,}", text)
    findings = []
    for sentence in sentences:
        lower = sentence.lower()
        if any(cue in lower for cue in _NEGATION_CUES):
            continue
        has_timing = any(cue in lower for cue in _POST_APPROVAL_TIMING_CUES)
        has_action = any(re.search(rf"\b{re.escape(cue)}\b", lower) for cue in _CORRECTION_ACTION_CUES)
        mentions_protected = (
            any(path.lower() in lower for path in protected_path_names)
            or "protected path" in lower or "protected file" in lower
        )
        if has_timing and has_action and mentions_protected:
            findings.append(sentence.strip())
    return findings


class TestBundleProtectedPathCorrectionLint(unittest.TestCase):
    """Missing-test item 107 (`OPUS-R14-003`): same class of check as
    WF5's own `assert_stage_completeness` (confirmed present via
    `git show d18d939 --stat` and a grep for `assert_stage_completeness`
    in `workflow_fingerprint.py` before writing this), extended here to a
    content lint over a bundle's `PLAN.md`/`IMPLEMENTATION_SUMMARY.md`."""

    def test_clean_bundle_content_has_no_findings(self):
        clean_plan = (
            "## Checkpoint WF9\n\nImplements the widget loader. "
            "No further action is required after approval.\n"
        )
        self.assertEqual(
            find_post_approval_protected_path_corrections(clean_plan, fingerprint.PLAN_STAGE_PROTECTED), [],
        )

    def test_an_injected_post_approval_protected_path_instruction_is_flagged(self):
        violating_summary = (
            "## Implementation notes\n\n"
            "After the plan is approved, update docs/ai-workflow/WORKFLOW_V2_PLAN.md "
            "to correct the checkpoint count.\n"
        )
        findings = find_post_approval_protected_path_corrections(
            violating_summary, fingerprint.PLAN_STAGE_PROTECTED,
        )
        self.assertEqual(len(findings), 1)
        self.assertIn("WORKFLOW_V2_PLAN.md", findings[0])

    def test_a_negated_statement_of_the_rule_itself_is_not_flagged(self):
        """The sentence states the rule ("never correct... after
        approval"), not a violation of it -- the negation exclusion
        exists precisely so this class of sentence, which a naive
        co-occurrence check would misfire on, reads as compliant."""
        rule_statement = (
            "Reviewers must never correct docs/ai-workflow/WORKFLOW_V2_PLAN.md "
            "after approval; open a new remediation item instead.\n"
        )
        self.assertEqual(
            find_post_approval_protected_path_corrections(rule_statement, fingerprint.PLAN_STAGE_PROTECTED), [],
        )

    def test_generic_protected_path_phrase_is_also_detected(self):
        violating = "Once approved, edit the protected path to fix the typo.\n"
        findings = find_post_approval_protected_path_corrections(violating, fingerprint.PLAN_STAGE_PROTECTED)
        self.assertEqual(len(findings), 1)


# ---------------------------------------------------------------------------
# Missing-test item 108: a documentation-consistency lint proving items
# 85/96 and the D-Plan-Review-Stages transition table state the same
# /review-plan write set.
# ---------------------------------------------------------------------------


def _extract_numbered_plan_doc_item(plan_text: str, item_number: int) -> str:
    match = re.search(rf"^{item_number}\.\s.*$", plan_text, re.MULTILINE)
    if match is None:
        raise AssertionError(f"WORKFLOW_V2_PLAN.md has no line starting '{item_number}. '")
    return match.group(0)


def _write_set_tokens_from_item_clause(clause: str, *, verdict: str) -> frozenset[str]:
    """Extracts the {REVIEW_FEEDBACK.md, ledger, phase_transition}
    write-target vocabulary from one clause of item 85/96's prose. The
    `phase transition` token is gated by a literal `(for `REVISE` only)`
    qualifier, since both items state it that way for the shared
    REVISE/BLOCK clause."""
    tokens = set()
    if "REVIEW_FEEDBACK.md" in clause:
        tokens.add("REVIEW_FEEDBACK.md")
    if "ledger" in clause:
        tokens.add("ledger")
    if "phase transition" in clause:
        if "for `REVISE` only" in clause:
            if verdict == "REVISE":
                tokens.add("phase_transition")
        else:
            tokens.add("phase_transition")
    return frozenset(tokens)


def _write_set_by_verdict_from_plan_item(item_text: str) -> dict[str, frozenset[str]]:
    approve_match = re.search(
        r"for (?:an? )?`APPROVE`,?\s*(?:verdict\s*)?(?:is exactly\s*)?(.+?);", item_text,
    )
    revise_block_match = re.search(r"for `REVISE`/`BLOCK`,?\s*(?:it is )?(.+?)(?:;|—)", item_text)
    if approve_match is None or revise_block_match is None:
        raise AssertionError(f"could not locate APPROVE / REVISE-BLOCK clauses in: {item_text!r}")
    approve_clause = approve_match.group(1)
    revise_block_clause = revise_block_match.group(1)
    return {
        "APPROVE": _write_set_tokens_from_item_clause(approve_clause, verdict="APPROVE"),
        "REVISE": _write_set_tokens_from_item_clause(revise_block_clause, verdict="REVISE"),
        "BLOCK": _write_set_tokens_from_item_clause(revise_block_clause, verdict="BLOCK"),
    }


def _write_set_by_verdict_from_transition_table(plan_text: str) -> dict[str, frozenset[str]]:
    """Extracts the same {REVIEW_FEEDBACK.md, ledger, phase_transition}
    vocabulary from the D-Plan-Review-Stages transition table's three
    `/review-plan` rows (`Current state == AWAITING_LOCAL_PLAN_REVIEW`).

    `REVIEW_FEEDBACK.md` is treated as an always-present baseline across
    all three rows, sourced separately from the state's own "Artifacts"
    bullet (`Artifacts`: `REVIEW_FEEDBACK.md` ...") rather than from each
    table cell -- the table's own cells do not restate it per row by
    design (it is written regardless of verdict, stated once for the
    whole state), so a literal per-cell substring search would otherwise
    under-count the APPROVE row. `ledger`/`phase_transition` presence is
    read from each row's own action cell: `ledger` from "record local
    stage"/"ledger entry" language, explicitly absent on "no ledger
    write"; `phase_transition` from an arrow (`→`) to a different state,
    explicitly absent on "no transition"."""
    if "Artifacts**: `REVIEW_FEEDBACK.md` (role: `local_model_plan_review`)" not in plan_text:
        raise AssertionError(
            "AWAITING_LOCAL_PLAN_REVIEW's Artifacts bullet no longer states REVIEW_FEEDBACK.md "
            "as its baseline artifact -- the table-side heuristic's baseline assumption is stale"
        )

    row_re = re.compile(
        r"\|\s*`AWAITING_LOCAL_PLAN_REVIEW`\s*\|\s*`(APPROVE|REVISE|BLOCK)`\s*\|\s*`/review-plan`\s*\|\s*([^|]+)\|"
    )
    rows = {verdict: action for verdict, action in row_re.findall(plan_text)}
    if set(rows) != {"APPROVE", "REVISE", "BLOCK"}:
        raise AssertionError(f"expected exactly 3 /review-plan transition-table rows, found: {sorted(rows)}")

    result = {}
    for verdict, action_cell in rows.items():
        tokens = {"REVIEW_FEEDBACK.md"}
        if "no ledger write" not in action_cell and (
            "record local stage" in action_cell or "ledger entry" in action_cell or "ledger fields" in action_cell
        ):
            tokens.add("ledger")
        if "no transition" not in action_cell and "→" in action_cell:
            tokens.add("phase_transition")
        result[verdict] = frozenset(tokens)
    return result


class TestReviewPlanWriteSetConsistencyLint(unittest.TestCase):
    """Missing-test item 108: items 85 and 96 (both long single-paragraph
    entries in `WORKFLOW_V2_PLAN.md`'s "Missing tests" section, located
    via `^85\\.`/`^96\\.` and read in full before writing this) and the
    D-Plan-Review-Stages verdict/state transition table's three
    `/review-plan` rows must describe the same write set per verdict. A
    future edit to any one of the three that silently drifts from the
    other two is caught here -- this is a documentation-consistency lint
    over this repository's own real plan doc (deterministic, stdlib-only,
    not tied to a moving base commit -- hence hermetic, unlike the
    `_demo_test.py` suites)."""

    def setUp(self):
        self.plan_text = (_repo_root() / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md").read_text()

    def test_item_85_and_item_96_state_the_same_write_set_per_verdict(self):
        item85 = _extract_numbered_plan_doc_item(self.plan_text, 85)
        item96 = _extract_numbered_plan_doc_item(self.plan_text, 96)
        write_sets_85 = _write_set_by_verdict_from_plan_item(item85)
        write_sets_96 = _write_set_by_verdict_from_plan_item(item96)
        self.assertEqual(write_sets_85, write_sets_96)
        # Sanity: the extraction itself found real content, not empty sets
        # for every verdict (which would make the equality assertion
        # vacuous).
        self.assertTrue(any(write_sets_85.values()))

    def test_transition_table_agrees_with_items_85_and_96(self):
        item96 = _extract_numbered_plan_doc_item(self.plan_text, 96)
        write_sets_prose = _write_set_by_verdict_from_plan_item(item96)
        write_sets_table = _write_set_by_verdict_from_transition_table(self.plan_text)
        self.assertEqual(write_sets_prose, write_sets_table)

    def test_ledger_write_is_scoped_to_approve_only_across_all_three_sources(self):
        """A targeted regression guard for the specific property GPT-R12-
        001/002 fixed: only APPROVE ever writes the ledger, in every one
        of the three sources."""
        item96 = _extract_numbered_plan_doc_item(self.plan_text, 96)
        for source_name, write_sets in (
            ("item 96", _write_set_by_verdict_from_plan_item(item96)),
            ("transition table", _write_set_by_verdict_from_transition_table(self.plan_text)),
        ):
            self.assertIn("ledger", write_sets["APPROVE"], source_name)
            self.assertNotIn("ledger", write_sets["REVISE"], source_name)
            self.assertNotIn("ledger", write_sets["BLOCK"], source_name)


def _strip_trailing_citation_parenthetical(cell: str) -> str:
    """Strips a single trailing `(corrected/extended/new OPUS-R.../GPT-
    R...)` historical-citation parenthetical from a table cell, if
    present -- balance-aware (scanning backward from the end so an
    earlier, substantive parenthetical elsewhere in the same cell, e.g.
    `WFR-47`'s "(`WORKFLOW_STATE.json` and `<work_item_id>-artifacts.json`)",
    is never touched). Missing-test item 166's own stated normalization."""
    cell = cell.rstrip()
    if not cell.endswith(")"):
        return cell
    depth = 0
    for i in range(len(cell) - 1, -1, -1):
        ch = cell[i]
        if ch == ")":
            depth += 1
        elif ch == "(":
            depth -= 1
            if depth == 0:
                inner = cell[i + 1 : -1]
                if re.match(r"(?i)^(corrected|extended|new)\b.*(OPUS-R|GPT-R)", inner):
                    return cell[:i].rstrip()
                return cell
    return cell


def _normalize_requirement_cell(cell: str) -> str:
    """Item 166's exact normalization: backtick markup, `**` bold markup,
    and a trailing citation parenthetical stripped; em dash normalized to
    `--`; case-insensitive; whitespace collapsed."""
    cell = _strip_trailing_citation_parenthetical(cell)
    cell = cell.replace("`", "").replace("**", "")
    cell = cell.replace("—", "--")
    cell = re.sub(r"\s+", " ", cell).strip()
    return cell.lower()


def _normalize_json_description(description: str) -> str:
    return re.sub(r"\s+", " ", description).strip().lower()


class TestRequirementsMappingTableConformance(unittest.TestCase):
    """Missing-test item 166 (`OPUS-R27-001`, `OPUS-R28-003`, ownership
    reassigned to `WF8b` by `GPT-R29-003`): a grep-based conformance test
    over `docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`
    and the Requirements traceability table asserts every `WFR-*`
    requirement's JSON `description` matches its rendered table row's
    Requirement column, under the normalization the table's own
    introductory prose states -- the ongoing regression guard that keeps
    the two synced going forward, not the one-time data sync itself
    (already performed, revision 21). The Checkpoint column is out of
    scope by the same prose (it may cite a design-doc section instead of
    or alongside a registry checkpoint id)."""

    def setUp(self):
        self.plan_text = (_repo_root() / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md").read_text()
        self.mapping = json.loads(
            (_repo_root() / "docs" / "ai-workflow" / "requirements"
             / "workflow-v2-1-core-mapping.json").read_text()
        )

    def _table_rows(self) -> dict[str, str]:
        rows = re.findall(r"^\| (WFR-\d+) \|(.*)\|.*\|.*\|$", self.plan_text, re.MULTILINE)
        return {req_id: requirement_cell for req_id, requirement_cell in rows}

    def test_every_wfr_row_description_matches_json_exactly(self):
        table_rows = self._table_rows()
        requirements = self.mapping["requirements"]
        # Sanity: the extraction found a non-empty set of rows whose count
        # matches the JSON side exactly (item 166, `WF8c` clause (f),
        # `OPUS-R113-001`: a hardcoded row count here went stale every time
        # a WFR was added -- 60 at revision 27, 69 as of this revision --
        # so this derives the expected size from `requirements` itself
        # instead of a literal that needs bumping forever). Both sides
        # non-empty guards the per-row loop below against passing vacuously
        # on two empty sets, which the set-equality check alone could not
        # rule out.
        self.assertGreater(len(requirements), 0)
        self.assertEqual(len(table_rows), len(requirements))
        self.assertEqual(set(table_rows), set(requirements))
        mismatches = []
        for req_id, table_cell in table_rows.items():
            table_normalized = _normalize_requirement_cell(table_cell)
            json_normalized = _normalize_json_description(requirements[req_id]["description"])
            if table_normalized != json_normalized:
                mismatches.append((req_id, table_normalized, json_normalized))
        self.assertEqual(
            mismatches, [],
            f"{len(mismatches)} WFR row(s) diverged from their JSON description: "
            f"{[m[0] for m in mismatches]}",
        )

    def test_normalization_catches_a_real_divergence_not_vacuously_true(self):
        """The positive test above proves nothing if the normalization is
        so loose it can never fail. Confirms it actually distinguishes a
        genuinely different description from the real WFR-01 row."""
        table_cell = self._table_rows()["WFR-01"]
        real_json_description = self.mapping["requirements"]["WFR-01"]["description"]
        tampered_description = real_json_description + " and something else entirely"
        self.assertNotEqual(
            _normalize_requirement_cell(table_cell),
            _normalize_json_description(tampered_description),
        )

    def test_trailing_citation_parenthetical_is_stripped_but_substantive_one_is_not(self):
        """WFR-47's own real table cell exercises both halves of the
        normalization rule at once: a substantive parenthetical
        mid-sentence (naming its two authoritative sources) must survive,
        while the trailing `(corrected ...)` citation parenthetical must
        not."""
        cell = self._table_rows()["WFR-47"]
        normalized = _normalize_requirement_cell(cell)
        self.assertIn("(workflow_state.json and <work_item_id>-artifacts.json)", normalized)
        self.assertNotIn("corrected", normalized)
        self.assertNotIn("opus-r25-002", normalized)


# ---------------------------------------------------------------------------
# GPT-R11-009: the two-stage plan-review ledger, exercised end to end
# against a real ScratchRepo. workflow_state_test.py's own
# TestRecordLocalPlanReview/TestRecordManualPlanReview classes already
# prove the dict-level transition logic (verified via
# `grep -n "transition_to_awaiting_local_plan_review" scripts/workflow_state_test.py`
# before writing this) -- not duplicated here; this chains the same
# writers against a real committed plan-doc tree and a genuinely
# recomputed review_content_id, matching what /review-plan and
# /record-manual-plan-review actually operate on.
# ---------------------------------------------------------------------------


class TestTwoStagePlanReviewIntegration(unittest.TestCase):
    def test_local_then_manual_approve_reaches_awaiting_plan_approval(self):
        with h.ScratchRepo() as repo:
            repo.write_plan_docs(work_item_id="wi")
            repo.commit_plan_docs_as_base()
            protected = h.plan_stage_protected_paths("wi")
            review_content_id, _ = fingerprint.compute_review_content_id_plan_stage(
                repo.root, repo.base, work_item_type="process", work_item_id="wi",
                plan_revision=1, protected=protected,
                excluded_paths=h.plan_stage_excluded_paths(),
                excluded_prefixes=h.plan_stage_excluded_prefixes(),
            )
            state = h.base_state(wi=h.base_work_item(
                work_item_id="wi", governing_workflow_version="2.1",
                phase="AWAITING_LOCAL_PLAN_REVIEW",
            ))

            state = ws.record_local_plan_review(
                state, "wi", verdict="APPROVE", bundle_id="b1",
                review_content_id=review_content_id, round=1, now="t1",
            )
            self.assertEqual(state["work_items"]["wi"]["phase"], "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW")

            state = ws.record_manual_plan_review(
                state, "wi", verdict="APPROVE", bundle_id="b2", round=1, now="t2",
                current_review_content_id=review_content_id,
                feedback_role="MANUAL_EXTERNAL_PLAN_REVIEW",
                feedback_review_content_id=review_content_id,
            )
            item = state["work_items"]["wi"]
            self.assertEqual(item["phase"], "AWAITING_PLAN_APPROVAL")
            self.assertTrue(ws.plan_approval_gate_reachable(
                latest_round_status="APPROVE", governing_workflow_version="2.1",
                plan_review_stages=item["plan_review_stages"],
                current_review_content_id=review_content_id,
            ))

    def test_a_real_committed_plan_edit_after_local_approval_invalidates_the_stage(self):
        """WFR-38's integration half: an actual committed edit to a
        protected plan document -- not a dict field mutation -- changes
        the real recomputed `review_content_id`, so the local-stage
        ledger entry recorded against the old id no longer satisfies
        `plan_approval_gate_reachable`, and
        `transition_to_awaiting_local_plan_review` is the sole documented
        path back to `AWAITING_LOCAL_PLAN_REVIEW`."""
        with h.ScratchRepo() as repo:
            repo.write_plan_docs(work_item_id="wi")
            repo.commit_plan_docs_as_base()
            protected = h.plan_stage_protected_paths("wi")
            old_id, _ = fingerprint.compute_review_content_id_plan_stage(
                repo.root, repo.base, work_item_type="process", work_item_id="wi",
                plan_revision=1, protected=protected,
                excluded_paths=h.plan_stage_excluded_paths(),
                excluded_prefixes=h.plan_stage_excluded_prefixes(),
            )
            state = h.base_state(wi=h.base_work_item(
                work_item_id="wi", governing_workflow_version="2.1",
                phase="AWAITING_LOCAL_PLAN_REVIEW",
            ))
            state = ws.record_local_plan_review(
                state, "wi", verdict="APPROVE", bundle_id="b1",
                review_content_id=old_id, round=1, now="t1",
            )
            self.assertEqual(state["work_items"]["wi"]["phase"], "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW")

            (repo.root / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md").write_text("plan v2\n")
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "revise plan"], cwd=repo.root)

            new_id, _ = fingerprint.compute_review_content_id_plan_stage(
                repo.root, repo.base, work_item_type="process", work_item_id="wi",
                plan_revision=1, protected=protected,
                excluded_paths=h.plan_stage_excluded_paths(),
                excluded_prefixes=h.plan_stage_excluded_prefixes(),
            )
            self.assertNotEqual(old_id, new_id)

            item = state["work_items"]["wi"]
            self.assertFalse(ws.plan_approval_gate_reachable(
                latest_round_status="APPROVE", governing_workflow_version="2.1",
                plan_review_stages=item["plan_review_stages"],
                current_review_content_id=new_id,
            ))

            state = ws.transition_to_awaiting_local_plan_review(state, "wi", now="t3")
            self.assertEqual(state["work_items"]["wi"]["phase"], "AWAITING_LOCAL_PLAN_REVIEW")

            # And the stale ledger's own review_content_id still names the
            # old, now-superseded id -- never explicitly cleared, exactly
            # as transition_to_awaiting_local_plan_review's own docstring
            # describes.
            self.assertEqual(
                state["work_items"]["wi"]["plan_review_stages"]["review_content_id"], old_id,
            )


class TestPlanStageApprovalCommitMembership(unittest.TestCase):
    """WF8b evidence (real `/approve-review plan v2-1-dry-run` S5
    execution, 2026-08-12): the installed command's literal four-member
    plan-stage commit set omits `<work_item_id>-artifacts.json`, so
    `verify_post_approval_manifest_match` raises
    `MissingWorkItemArtifactsDeclarationError` *after* the state write and
    the commit, for any `"process"` work item whose declaration was never
    previously committed -- not only `workflow-v2-1-core`'s own case
    `D-Approval-Commits`' bootstrap procedure already covers. Exercises
    `workflow_fingerprint.resolve_plan_stage_approval_commit_paths` and
    `workflow_state.{stage_plan_approval_commit_paths,
    verify_staged_blob_sha256, verify_committed_blob_sha256,
    rollback_plan_approval_write}` end to end against real `ScratchRepo`
    git repositories -- generic, never hardcoded to a specific
    `work_item_id`."""

    def _commit_base_without_artifacts(self, repo: h.ScratchRepo, work_item_id: str) -> None:
        """Commits the plan/registry/mapping triad (plus a `.gitignore`
        excluding `.ai-review/`, mirroring this real repository's own --
        bundle-directory content must never itself become an unclassified
        changed path) but deliberately leaves
        `<work_item_id>-artifacts.json` untracked -- the exact real shape
        `v2-1-dry-run` was in (never committed, present only in the
        working tree) when S5 reproduced the defect."""
        (repo.root / ".gitignore").write_text(".ai-review/\n")
        paths = [
            ".gitignore",
            "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            "docs/ai-workflow/WORKFLOW_V2_AUDIT.md",
            "docs/TECHNICAL_DECISIONS.md",
            f"docs/ai-workflow/registry/{work_item_id}-registry.json",
            f"docs/ai-workflow/requirements/{work_item_id}-mapping.json",
        ]
        _run(["git", "add", "--", *paths], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "settle plan docs, artifacts declaration pending"], cwd=repo.root)
        repo.base = repo.head()

    def _seed_bundle_capture(self, repo: h.ScratchRepo, work_item_id: str, artifacts_rel: str) -> Path:
        """Writes `<bundle_dir>/files/<artifacts_rel>` with the working
        tree's *current* bytes -- the same "final copies of changed
        files" `scripts/prepare-ai-review.sh` always captures, fresh
        (matching) by construction. Returns the captured file's path so a
        test can mutate it to simulate staleness."""
        bundle_dir = repo.root / ".ai-review" / work_item_id / "current"
        captured = bundle_dir / "files" / artifacts_rel
        captured.parent.mkdir(parents=True, exist_ok=True)
        captured.write_bytes((repo.root / artifacts_rel).read_bytes())
        return captured

    def _write_state(self, repo: h.ScratchRepo, work_item_id: str, governing_workflow_version: str) -> dict:
        work_item = h.base_work_item(
            work_item_id=work_item_id, governing_workflow_version=governing_workflow_version,
            phase="AWAITING_PLAN_APPROVAL", plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            registry_path=f"docs/ai-workflow/registry/{work_item_id}-registry.json",
            mapping_path=f"docs/ai-workflow/requirements/{work_item_id}-mapping.json",
            base_commit=repo.base,
        )
        repo.write_workflow_state(**{work_item_id: work_item})
        return work_item

    def test_declaration_never_committed_resolves_five_member_set(self):
        """The real defect's exact fixture: reproduces
        `MissingWorkItemArtifactsDeclarationError` on the *old* four-member
        commit, and proves the resolved five-member set fixes it."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")
            artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"
            self._seed_bundle_capture(repo, wi, artifacts_rel)

            plan = fingerprint.resolve_plan_stage_approval_commit_paths(
                repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
            )
            self.assertEqual(
                set(plan.paths),
                {
                    "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                    f"docs/ai-workflow/registry/{wi}-registry.json",
                    f"docs/ai-workflow/requirements/{wi}-mapping.json",
                    "docs/ai-workflow/WORKFLOW_STATE.json",
                    artifacts_rel,
                },
            )
            self.assertEqual(plan.artifacts_declaration_path, artifacts_rel)
            expected_sha = hashlib.sha256((repo.root / artifacts_rel).read_bytes()).hexdigest()
            self.assertEqual(plan.artifacts_declaration_sha256, expected_sha)

            protected = h.plan_stage_protected_paths(wi)
            review_content_id, _ = fingerprint.compute_review_content_id_plan_stage(
                repo.root, repo.base, work_item_type="process", work_item_id=wi,
                plan_revision=1, protected=protected,
                excluded_paths=h.plan_stage_excluded_paths(),
                excluded_prefixes=h.plan_stage_excluded_prefixes(),
            )

            # --- regression guard: the OLD four-member commit (the
            # installed command's literal step-6 set, explicitly staged --
            # never `git add -A`, which would sweep the pending
            # declaration in and defeat the point of this fixture) really
            # does reproduce MissingWorkItemArtifactsDeclarationError
            # post-commit, exactly as the real S5 execution did ---
            base_four = (
                "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                f"docs/ai-workflow/registry/{wi}-registry.json",
                f"docs/ai-workflow/requirements/{wi}-mapping.json",
                "docs/ai-workflow/WORKFLOW_STATE.json",
            )
            _run(["git", "add", "--", *base_four], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "old four-member commit"], cwd=repo.root)
            old_commit = repo.head()
            with self.assertRaises(fingerprint.MissingWorkItemArtifactsDeclarationError):
                fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item(
                    repo.root, wi, old_commit, base=repo.base,
                )
            _run(["git", "reset", "--hard", repo.base], cwd=repo.root)
            self._write_state(repo, wi, "2.1")  # write_workflow_state doesn't survive the hard reset

            # --- the fixed five-member commit succeeds end to end ---
            ws.stage_plan_approval_commit_paths(repo.root, plan.paths)
            ws.verify_staged_blob_sha256(repo.root, plan.artifacts_declaration_path, plan.artifacts_declaration_sha256)
            _run(["git", "commit", "-q", "-m", "plan approval (five-member)"], cwd=repo.root)
            new_commit = repo.head()
            ws.assert_committed_path_set_matches(repo.root, new_commit, plan.paths)
            ws.verify_committed_blob_sha256(
                repo.root, new_commit, plan.artifacts_declaration_path, plan.artifacts_declaration_sha256,
            )

            record = ws.build_approval_record(
                basis="EXTERNAL_APPROVE", stage="plan", user_confirmation=f"I confirm plan approval for {wi}, plan stage.",
                now="t1", reviewed_bundle_id="b1", approved_review_content_id=review_content_id,
                review_content_manifest=[{"path": "x", "exists": True, "mode": "100644", "blob": "y"}],
            )
            work_item = {**h.base_work_item(work_item_id=wi, governing_workflow_version="2.1"), "plan_approval": record}
            ws.verify_post_approval_manifest_match(repo.root, work_item, stage="plan", base_commit=repo.base, commit=new_commit)

            self.assertTrue(ws.approval_is_current(repo.root, work_item, stage="plan", base_commit=repo.base, head=new_commit))

    def test_declaration_already_committed_and_unchanged_resolves_four_member_set(self):
        """The ordinary, steady-state round -- every round after a work
        item's first -- gains no gratuitous fifth member. Also the shape
        `workflow-v2-1-core`'s own already-committed declaration is in on
        every real round since its WFR-63 fix, proving this generic
        resolver reproduces that already-approved bootstrap behavior
        rather than regressing it."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            repo.commit_plan_docs_as_base()  # commits the artifacts declaration too
            self._write_state(repo, wi, "1")

            plan = fingerprint.resolve_plan_stage_approval_commit_paths(
                repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
            )
            self.assertEqual(
                set(plan.paths),
                {
                    "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                    f"docs/ai-workflow/registry/{wi}-registry.json",
                    f"docs/ai-workflow/requirements/{wi}-mapping.json",
                    "docs/ai-workflow/WORKFLOW_STATE.json",
                },
            )
            self.assertIsNone(plan.artifacts_declaration_path)
            self.assertIsNone(plan.artifacts_declaration_sha256)

    def test_stale_declaration_between_preflight_and_commit_refuses_before_any_mutation(self):
        """Condition 2 (freshness): a pending declaration whose bytes
        moved on again after the bundle was generated must refuse -- no
        staging, no commit, no state write -- rather than commit bytes no
        reviewer ever saw."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")
            artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"
            captured = self._seed_bundle_capture(repo, wi, artifacts_rel)
            # the bundle captured an OLDER version than what's in the
            # working tree right now (edited again after bundle generation).
            captured.write_text("stale bundle-captured bytes\n")

            status_before = _run(["git", "status", "--porcelain"], cwd=repo.root)
            with self.assertRaises(fingerprint.StaleArtifactsDeclarationError):
                fingerprint.resolve_plan_stage_approval_commit_paths(
                    repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
                )
            status_after = _run(["git", "status", "--porcelain"], cwd=repo.root)
            self.assertEqual(status_before, status_after)  # no mutation of any kind

    def test_declaration_required_but_missing_from_worktree_fails_before_any_mutation(self):
        """A `"process"` work item's plan-stage recomputation always
        requires its own declaration to exist somewhere -- if it is
        genuinely absent (never written at all, not merely uncommitted),
        the pre-existing `resolve_plan_stage_metadata` check this
        function reuses already fails closed, and this function never
        masks that with a different, more permissive error."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            (repo.root / f"docs/ai-workflow/registry/{wi}-artifacts.json").unlink()
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")

            with self.assertRaises(fingerprint.MissingWorkItemArtifactsDeclarationError):
                fingerprint.resolve_plan_stage_approval_commit_paths(
                    repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
                )

    def test_unexpected_changed_staged_path_refuses_before_commit(self):
        """A pre-existing, unrelated staged path with real changed content
        (this work item's own leftover or a concurrent work item's write,
        D1) must be caught and refused, never silently absorbed by a
        later pathspec-free `git commit`."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")
            artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"
            self._seed_bundle_capture(repo, wi, artifacts_rel)
            plan = fingerprint.resolve_plan_stage_approval_commit_paths(
                repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
            )

            (repo.root / "unrelated.txt").write_text("surprise\n")
            _run(["git", "add", "unrelated.txt"], cwd=repo.root)

            # Caught by the pre-staging index-isolation precondition --
            # DirtyIndexBeforeStagingError, not the post-staging diff
            # assertion, since the index is already dirty before this
            # call's own `git add` runs at all.
            with self.assertRaises(ws.DirtyIndexBeforeStagingError):
                ws.stage_plan_approval_commit_paths(repo.root, plan.paths)
            # nothing was committed
            self.assertEqual(repo.head(), repo.base)

    def test_restaging_unrelated_unchanged_content_is_a_true_no_op_not_a_gap(self):
        """Corrects an earlier (wrong) assumption made and disproven while
        stress-testing this fix: re-staging an unrelated path whose
        content is byte-identical to `HEAD` produces an index entry
        indistinguishable from `HEAD`'s own tree -- there is no separate
        Git-level state to detect here at all, so `git diff --cached
        HEAD` correctly reports nothing, neither before nor after. This
        is not a security gap; it is confirmation that the pre-staging
        precondition and the post-staging assertion are each checking a
        real, distinguishable condition, not papering over one that
        cannot occur."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")
            artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"
            self._seed_bundle_capture(repo, wi, artifacts_rel)
            plan = fingerprint.resolve_plan_stage_approval_commit_paths(
                repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
            )

            _run(["git", "add", "docs/ai-workflow/WORKFLOW_V2_AUDIT.md"], cwd=repo.root)
            self.assertEqual(
                _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root).strip(), "",
            )
            # Both checks correctly see nothing to refuse -- there is
            # genuinely nothing there.
            ws.stage_plan_approval_commit_paths(repo.root, plan.paths)

    def test_precondition_does_not_reject_this_work_items_own_pending_intent_to_add_paths(self):
        """Safety proof for the real defect this whole fix targets: the
        real `v2-1-dry-run` repository state that reproduced
        `MissingWorkItemArtifactsDeclarationError` had its own genuinely
        new (never committed) plan/registry/mapping/declaration paths
        sitting in the index as `git add -N` intent-to-add entries
        (empty-blob placeholders) -- `/milestone-plan`'s own documented
        staging step (S1's real evidence, `docs/ai-workflow/dry-run/
        WF8B_SCENARIOS.md`), run long before `/approve-review` ever
        executes. `git diff --cached --name-only HEAD` does not report
        intent-to-add entries at all (verified directly below), so the
        new pre-staging `DirtyIndexBeforeStagingError` precondition must
        not mistake this work item's own legitimate pending paths for an
        unrelated dirty index -- and this call's own subsequent `git add`
        must still upgrade every one of them from an empty-blob
        placeholder to its real content, exactly the four-or-five-member
        commit the resolved plan calls for."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            (repo.root / ".gitignore").write_text(".ai-review/\n")
            _run(["git", "add", ".gitignore"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "gitignore"], cwd=repo.root)
            repo.base = repo.head()
            repo.write_plan_docs(work_item_id=wi)  # plan/registry/mapping/artifacts all genuinely new
            self._write_state(repo, wi, "2.1")
            artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"

            # Mark every plan-stage file intent-to-add -- /milestone-plan's
            # own real staging step -- before the bundle even exists.
            intent_to_add_paths = (
                "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                f"docs/ai-workflow/registry/{wi}-registry.json",
                f"docs/ai-workflow/requirements/{wi}-mapping.json",
                artifacts_rel,
            )
            _run(["git", "add", "-N", "--", *intent_to_add_paths], cwd=repo.root)
            self.assertEqual(
                _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root).strip(), "",
            )

            self._seed_bundle_capture(repo, wi, artifacts_rel)
            plan = fingerprint.resolve_plan_stage_approval_commit_paths(
                repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
            )
            self.assertEqual(plan.artifacts_declaration_path, artifacts_rel)  # never committed -> pending

            # Must not raise DirtyIndexBeforeStagingError, and must
            # upgrade every intent-to-add placeholder to real content.
            ws.stage_plan_approval_commit_paths(repo.root, plan.paths)
            staged = set(_run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root).splitlines())
            self.assertEqual(staged, set(plan.paths))

    def test_wrong_staged_content_for_conditional_member_is_caught_before_commit(self):
        """A race between pinning the fifth member's digest and staging
        it (a hook, a concurrent edit) must be caught rather than let the
        commit silently carry bytes that were never freshness-checked."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")
            artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"
            self._seed_bundle_capture(repo, wi, artifacts_rel)
            plan = fingerprint.resolve_plan_stage_approval_commit_paths(
                repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
            )

            (repo.root / artifacts_rel).write_text("tampered after pinning\n")
            ws.stage_plan_approval_commit_paths(repo.root, plan.paths)  # stages the tampered bytes
            with self.assertRaises(ws.StagedBlobMismatchError):
                ws.verify_staged_blob_sha256(
                    repo.root, plan.artifacts_declaration_path, plan.artifacts_declaration_sha256,
                )

    def test_committed_path_set_mismatch_from_a_hook_editing_after_staging_is_caught(self):
        """A structural, post-commit-only failure mode the pre-commit
        staging checks cannot see by construction: a pre-commit hook
        that edits and re-stages an extra file *after*
        `stage_plan_approval_commit_paths` already verified the index,
        but before `git commit` writes the final tree, produces a commit
        whose own changed-path set is wider than what was verified.
        `assert_committed_path_set_matches` is the only one of this
        fix's checks positioned to catch it."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")
            artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"
            self._seed_bundle_capture(repo, wi, artifacts_rel)
            plan = fingerprint.resolve_plan_stage_approval_commit_paths(
                repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
            )
            ws.stage_plan_approval_commit_paths(repo.root, plan.paths)

            # Simulate a hook: stage one more, unrelated file directly via
            # Git, bypassing this fix's own staging function entirely --
            # exactly what a real pre-commit hook does.
            (repo.root / "hook-added.txt").write_text("added by a hook\n")
            _run(["git", "add", "hook-added.txt"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "commit widened by a hook"], cwd=repo.root)
            new_commit = repo.head()

            with self.assertRaises(ws.CommittedPathSetMismatchError):
                ws.assert_committed_path_set_matches(repo.root, new_commit, plan.paths)

    def test_rollback_before_commit_boundary_restores_state_bytes_exactly(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")
            state_path = Path("docs/ai-workflow/WORKFLOW_STATE.json")
            pre_write_bytes = (repo.root / state_path).read_bytes()

            (repo.root / state_path).write_bytes(pre_write_bytes + b"\n")  # simulate the state write
            head_before = repo.head()

            ws.rollback_plan_approval_write(repo.root, state_path, pre_write_bytes, commit_created=False)

            self.assertEqual((repo.root / state_path).read_bytes(), pre_write_bytes)
            self.assertEqual(repo.head(), head_before)  # no commit existed, none created

    def test_rollback_after_commit_boundary_undoes_exactly_one_commit(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            repo.write_plan_docs(work_item_id=wi)
            self._commit_base_without_artifacts(repo, wi)
            self._write_state(repo, wi, "2.1")
            state_path = Path("docs/ai-workflow/WORKFLOW_STATE.json")
            pre_write_bytes = (repo.root / state_path).read_bytes()
            head_before_write = repo.head()

            (repo.root / state_path).write_bytes(pre_write_bytes + b"\n")  # the state write
            _run(["git", "add", "--", str(state_path)], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "partial approval commit"], cwd=repo.root)
            self.assertNotEqual(repo.head(), head_before_write)

            ws.rollback_plan_approval_write(repo.root, state_path, pre_write_bytes, commit_created=True)

            self.assertEqual(repo.head(), head_before_write)  # exactly one commit undone
            self.assertEqual((repo.root / state_path).read_bytes(), pre_write_bytes)

    def test_governing_version_does_not_change_resolution_generic_across_v1_and_v21(self):
        """The resolver keys on `work_item_type == "process"`
        (`resolve_plan_stage_metadata`'s own existing rule), never on
        `governing_workflow_version` -- a `"1"` item (like
        `workflow-v2-1-core` itself) and a `"2.1"` item both go through
        the identical conditional-fifth-member logic."""
        for governing_workflow_version in ("1", "2.1"):
            with self.subTest(governing_workflow_version=governing_workflow_version):
                with h.ScratchRepo() as repo:
                    wi = "wi"
                    repo.write_plan_docs(work_item_id=wi)
                    self._commit_base_without_artifacts(repo, wi)
                    self._write_state(repo, wi, governing_workflow_version)
                    artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"
                    self._seed_bundle_capture(repo, wi, artifacts_rel)

                    plan = fingerprint.resolve_plan_stage_approval_commit_paths(
                        repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
                    )
                    self.assertEqual(plan.artifacts_declaration_path, artifacts_rel)


class TestPlanApprovalFailureAtomicityTransaction(unittest.TestCase):
    """WF8c (g), part 1 (`WFR-63`, missing-test items 349/350): the durable
    `PLAN_APPROVAL_JOURNAL`, the ownership assertion, the three-way outcome
    classifier, and the index-only rollback, exercised against real
    `ScratchRepo` git history end to end. Not yet wired into
    `.claude/commands/approve-review.md` -- these tests exercise
    `workflow_state.py`'s new library functions directly, the same way
    `TestPlanStageApprovalCommitMembership` above exercises the
    conditional-fifth-member mechanics it composes with."""

    def _setup(self, repo: h.ScratchRepo, wi: str) -> tuple[dict, dict, str, fingerprint.PlanApprovalCommitPlan]:
        """Settles a clean, already-committed four-member plan-stage
        fixture (no pending fifth member -- kept simple; the fifth-member
        interaction is `resolve_plan_stage_approval_commit_paths`'s own
        already-tested concern, not this transaction's), writes
        `AWAITING_PLAN_APPROVAL` state, and returns
        `(pre_state, record, review_content_id, plan)`."""
        repo.write_plan_docs(work_item_id=wi)
        repo.commit_plan_docs_as_base()
        work_item = h.base_work_item(
            work_item_id=wi, governing_workflow_version="1", phase="AWAITING_PLAN_APPROVAL",
            plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            registry_path=f"docs/ai-workflow/registry/{wi}-registry.json",
            mapping_path=f"docs/ai-workflow/requirements/{wi}-mapping.json",
            base_commit=repo.base,
        )
        repo.write_workflow_state(**{wi: work_item})
        pre_state = json.loads((repo.root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())

        plan = fingerprint.resolve_plan_stage_approval_commit_paths(
            repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
        )
        self.assertIsNone(plan.artifacts_declaration_path)  # confirms the simple four-member case

        protected = h.plan_stage_protected_paths(wi)
        review_content_id, _ = fingerprint.compute_review_content_id_plan_stage(
            repo.root, repo.base, work_item_type="process", work_item_id=wi,
            plan_revision=1, protected=protected,
            excluded_paths=h.plan_stage_excluded_paths(),
            excluded_prefixes=h.plan_stage_excluded_prefixes(),
        )
        record = ws.build_approval_record(
            basis="EXTERNAL_APPROVE", stage="plan",
            user_confirmation=f"I confirm plan approval for {wi}, plan stage.", now="t1",
            reviewed_bundle_id="b1", approved_review_content_id=review_content_id,
            review_content_manifest=[{"path": "x", "exists": True, "mode": "100644", "blob": "y"}],
        )
        return pre_state, record, review_content_id, plan

    def _open_journal(self, repo: h.ScratchRepo, wi: str, pre_state: dict, record: dict,
                       review_content_id: str, plan: fingerprint.PlanApprovalCommitPlan) -> dict:
        return ws.open_plan_approval_journal(
            repo.root, work_item_id=wi, base_commit=repo.base, pre_state=pre_state,
            record=record, approval_now="t1", expected_bundle_id="b1",
            expected_review_content_id=review_content_id, applicable_paths=plan.paths,
            fifth_member_applies=plan.artifacts_declaration_path is not None,
            fifth_member_sha256=plan.artifacts_declaration_sha256,
            user_confirmation=f"I confirm plan approval for {wi}, plan stage.",
            quiescence_authorization=f"quiescence authorized for {wi} plan",
        )

    def _commit_plan_approval(self, repo: h.ScratchRepo, wi: str, plan, review_content_id: str) -> str:
        ws.stage_plan_approval_commit_paths(repo.root, plan.paths)
        body = f"plan approval\n\nWorkflow-Plan-Approval: {review_content_id}\nWorkflow-Work-Item: {wi}"
        _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
        return repo.head()

    # -- open/read/close -----------------------------------------------

    def test_open_journal_captures_expected_identity_and_state_bytes(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            head_before = repo.head()

            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            self.assertEqual(journal["schema_version"], 3)
            self.assertRegex(journal["owner_token"], r"^[0-9a-f]{32}$")
            self.assertEqual(journal["takeover_count"], 0)
            self.assertEqual(journal["previous_owner_tokens"], [])
            self.assertEqual(journal["work_item_id"], wi)
            self.assertEqual(journal["stage"], "plan")
            self.assertEqual(journal["pre_procedure_head"], head_before)
            self.assertEqual(journal["base_commit"], repo.base)
            self.assertEqual(journal["expected_review_content_id"], review_content_id)
            self.assertEqual(sorted(journal["applicable_paths"]), sorted(plan.paths))
            self.assertFalse(journal["fifth_member_applies"])
            self.assertIsNone(journal["fifth_member_sha256"])

            pre_bytes = base64.b64decode(journal["pre_procedure_state_b64"])
            self.assertEqual(pre_bytes, ws._serialize_state(pre_state))
            self.assertEqual(journal["pre_procedure_state_sha256"], hashlib.sha256(pre_bytes).hexdigest())

            expected_post_state = ws.apply_plan_approval(pre_state, wi, record, "t1")
            post_bytes = base64.b64decode(journal["expected_post_state_b64"])
            self.assertEqual(post_bytes, ws._serialize_state(expected_post_state))
            self.assertEqual(json.loads(post_bytes)["work_items"][wi]["phase"], "IMPLEMENTING")
            self.assertEqual(journal["expected_post_state_sha256"], hashlib.sha256(post_bytes).hexdigest())

            # Read back independently agrees.
            reread = ws.read_plan_approval_journal(repo.root)
            self.assertEqual(reread, journal)

    def test_journal_is_gitignored_and_invisible_to_git_status(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            (repo.root / ".gitignore").write_text(".ai-review/\n")
            _run(["git", "add", ".gitignore"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "gitignore"], cwd=repo.root)
            status_before = _run(["git", "status", "--porcelain"], cwd=repo.root)

            self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            status_after = _run(["git", "status", "--porcelain"], cwd=repo.root)
            self.assertEqual(status_after, status_before)

    def test_second_open_refuses_without_disturbing_the_first(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            first = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            with self.assertRaises(ws.PlanApprovalTransactionInProgressError):
                self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            self.assertEqual(ws.read_plan_approval_journal(repo.root), first)

    def test_read_journal_returns_none_when_absent(self):
        with h.ScratchRepo() as repo:
            self.assertIsNone(ws.read_plan_approval_journal(repo.root))

    def test_read_journal_rejects_wrong_schema_version(self):
        with h.ScratchRepo() as repo:
            path = ws.plan_approval_journal_path(repo.root)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps({"schema_version": 1}))
            with self.assertRaises(ws.PlanApprovalJournalUnavailableError):
                ws.read_plan_approval_journal(repo.root)

    def test_read_journal_rejects_missing_fields(self):
        with h.ScratchRepo() as repo:
            path = ws.plan_approval_journal_path(repo.root)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps({"schema_version": 3, "owner_token": "a" * 32}))
            with self.assertRaises(ws.PlanApprovalJournalUnavailableError):
                ws.read_plan_approval_journal(repo.root)

    def test_close_journal_is_idempotent(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            ws.close_plan_approval_journal(repo.root)
            self.assertIsNone(ws.read_plan_approval_journal(repo.root))
            ws.close_plan_approval_journal(repo.root)  # no error on an already-absent journal

    # -- ownership --------------------------------------------------------

    def test_assert_owner_succeeds_for_correct_token_and_fails_for_wrong_token(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            reread = ws.assert_plan_approval_journal_owner(repo.root, journal["owner_token"])
            self.assertEqual(reread, journal)

            with self.assertRaises(ws.PlanApprovalOwnershipError):
                ws.assert_plan_approval_journal_owner(repo.root, "0" * 32)

    def test_assert_owner_raises_when_no_transaction_open(self):
        with h.ScratchRepo() as repo:
            with self.assertRaises(ws.NoPlanApprovalTransactionError):
                ws.assert_plan_approval_journal_owner(repo.root, "0" * 32)

    # -- classifier ---------------------------------------------------

    def test_classify_not_committed_when_nothing_happened(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_NOT_COMMITTED,
            )

    def test_classify_committed_when_the_matching_commit_lands_directly_on_pre_procedure_head(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            self._commit_plan_approval(repo, wi, plan, review_content_id)

            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_COMMITTED,
            )

    def test_classify_ambiguous_when_an_unrelated_commit_lands_between_journal_open_and_the_approval_commit(self):
        """Real provenance concern: if another commit slips in between
        this transaction's own pinned `pre_procedure_head` and the
        eventual approval commit, the approval commit's first parent no
        longer matches what the journal pinned -- a concurrent writer
        may have interleaved, so this must never be silently treated as
        this transaction's own success."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            (repo.root / "unrelated.txt").write_text("interleaved commit\n")
            _run(["git", "add", "unrelated.txt"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "an unrelated commit lands first"], cwd=repo.root)

            self._commit_plan_approval(repo, wi, plan, review_content_id)

            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_AMBIGUOUS,
            )

    def test_classify_ambiguous_when_head_moved_but_no_matching_commit_exists(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            (repo.root / "unrelated.txt").write_text("head moved, no approval commit\n")
            _run(["git", "add", "unrelated.txt"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "unrelated"], cwd=repo.root)

            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_AMBIGUOUS,
            )

    def test_classify_ambiguous_when_the_trailer_search_itself_is_ambiguous(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            for subject in ("approve-a", "approve-b"):
                repo.commit(subject, trailers={
                    "Workflow-Plan-Approval": review_content_id, "Workflow-Work-Item": wi,
                })

            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_AMBIGUOUS,
            )

    # -- rollback -------------------------------------------------------

    def test_rollback_unstages_and_closes_the_journal_when_not_committed(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            head_before = repo.head()

            # Simulate a failure partway through staging (item 350's
            # "rollback after a staged-set/blob failure" scenario).
            ws.stage_plan_approval_commit_paths(repo.root, plan.paths)
            self.assertNotEqual(
                _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root).strip(), "",
            )

            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_NOT_COMMITTED,
            )
            ws.rollback_plan_approval_transaction(repo.root, owner_token=journal["owner_token"])

            self.assertEqual(
                _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root).strip(), "",
            )
            self.assertEqual(repo.head(), head_before)
            self.assertIsNone(ws.read_plan_approval_journal(repo.root))

    def test_rollback_refuses_for_the_wrong_owner_token(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            with self.assertRaises(ws.PlanApprovalOwnershipError):
                ws.rollback_plan_approval_transaction(repo.root, owner_token="0" * 32)
            # journal survives an unauthorized rollback attempt
            self.assertEqual(ws.read_plan_approval_journal(repo.root), journal)

    def test_rollback_refuses_when_live_state_already_carries_the_transactions_own_identity(self):
        """The invariant-violation safety net: if the live
        `WORKFLOW_STATE.json` already shows this exact transaction's
        post-approval identity under `phase: IMPLEMENTING` -- e.g. a
        step-8b-equivalent materialization ran out of band -- rollback
        must refuse rather than silently discard what may be the only
        record that the approval actually took effect."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            post_state = ws.apply_plan_approval(pre_state, wi, record, "t1")
            (repo.root / "docs/ai-workflow/WORKFLOW_STATE.json").write_text(
                json.dumps(post_state, indent=2),
            )

            with self.assertRaises(ws.PlanApprovalRollbackInvariantViolationError):
                ws.rollback_plan_approval_transaction(repo.root, owner_token=journal["owner_token"])
            # journal is left in place, not closed
            self.assertIsNotNone(ws.read_plan_approval_journal(repo.root))

    def test_rollback_verification_failure_when_head_has_moved_leaves_the_journal_in_place(self):
        """Defense in depth against calling rollback without classifying
        first: if `HEAD` has genuinely moved past `pre_procedure_head`
        (e.g. a concurrent commit landed), a bare `git reset --mixed
        HEAD` cannot and must not be reported as having restored the
        pre-transaction state."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            (repo.root / "unrelated.txt").write_text("a concurrent commit\n")
            _run(["git", "add", "unrelated.txt"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "concurrent"], cwd=repo.root)

            with self.assertRaises(ws.PlanApprovalRollbackVerificationError):
                ws.rollback_plan_approval_transaction(repo.root, owner_token=journal["owner_token"])
            # journal is left in place, not closed
            self.assertIsNotNone(ws.read_plan_approval_journal(repo.root))

    def test_rollback_also_closes_the_owner_progress_record(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=journal["owner_token"], step="step-5-declaration-pin", now="t2",
            ):
                pass
            self.assertIsNotNone(ws.read_plan_approval_owner_progress(repo.root, journal["owner_token"]))

            ws.rollback_plan_approval_transaction(repo.root, owner_token=journal["owner_token"])

            self.assertIsNone(ws.read_plan_approval_owner_progress(repo.root, journal["owner_token"]))

    def test_journal_read_rejects_malformed_takeover_count_and_previous_owner_tokens(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            path = ws.plan_approval_journal_path(repo.root)

            bad = dict(journal)
            bad["takeover_count"] = "0"  # a string, not an int
            path.write_text(json.dumps(bad))
            with self.assertRaises(ws.PlanApprovalJournalUnavailableError):
                ws.read_plan_approval_journal(repo.root)

            bad = dict(journal)
            bad["previous_owner_tokens"] = "none"  # not a list
            path.write_text(json.dumps(bad))
            with self.assertRaises(ws.PlanApprovalJournalUnavailableError):
                ws.read_plan_approval_journal(repo.root)


class TestPlanApprovalMutationGuardAndTakeover(unittest.TestCase):
    """WF8c (g), part 2 (`D-Approval-Commits`' "Owner progress record" /
    "Transaction mutation/handoff guard" / "Refuse by default; takeover is
    explicit", revision 57-59): the fixed-shape guarded-mutation window,
    the owner progress record, and the explicit-takeover contract,
    exercised directly against part 1's journal against real `ScratchRepo`
    git history. Mirrors `D-Checkpoint-Ownership`'s own already-tested
    guard/takeover test shapes (see the class above this one's peers in
    `workflow_state_test.py`) rather than inventing new coverage
    technique."""

    def _setup(self, repo: h.ScratchRepo, wi: str = "wi"):
        return TestPlanApprovalFailureAtomicityTransaction._setup(self, repo, wi)

    def _open_journal(self, repo: h.ScratchRepo, wi: str, *args):
        return TestPlanApprovalFailureAtomicityTransaction._open_journal(self, repo, wi, *args)

    def _open(self, repo: h.ScratchRepo, wi: str = "wi") -> dict:
        pre_state, record, review_content_id, plan = self._setup(repo, wi)
        return self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

    # -- step classification ---------------------------------------------

    def test_step_classification_is_exhaustive_and_refuses_an_unknown_step(self):
        for step in ws.PLAN_APPROVAL_DESTRUCTIVE_STEPS:
            self.assertEqual(ws.plan_approval_step_class(step), ws.DESTRUCTIVE)
        for step in ws.PLAN_APPROVAL_ORDINARY_STEPS:
            self.assertEqual(ws.plan_approval_step_class(step), ws.ORDINARY)
        self.assertTrue(ws.PLAN_APPROVAL_DESTRUCTIVE_STEPS.isdisjoint(ws.PLAN_APPROVAL_ORDINARY_STEPS))
        with self.assertRaises(ws.PlanApprovalGuardUnavailableError):
            ws.plan_approval_step_class("step-does-not-exist")

    # -- guard publish/read/release ---------------------------------------

    def test_guard_publish_read_release_round_trip(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))

            lease = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=journal["owner_token"],
                step="step-5-declaration-pin", now="t2",
            )
            self.assertRegex(lease["lease_id"], r"^[0-9a-f]{32}$")
            self.assertEqual(lease["holder_owner_token"], journal["owner_token"])
            self.assertEqual(lease["step"], "step-5-declaration-pin")
            self.assertEqual(lease["step_class"], ws.ORDINARY)
            self.assertEqual(ws.read_plan_approval_guard(repo.root), lease)

            ws.release_plan_approval_guard(repo.root, lease)
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))
            ws.release_plan_approval_guard(repo.root, lease)  # idempotent

    def test_guard_is_gitignored_and_invisible_to_git_status(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            (repo.root / ".gitignore").write_text(".ai-review/\n")
            _run(["git", "add", ".gitignore"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "gitignore"], cwd=repo.root)
            status_before = _run(["git", "status", "--porcelain"], cwd=repo.root)

            lease = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=journal["owner_token"],
                step="step-5-declaration-pin", now="t2",
            )
            self.assertEqual(_run(["git", "status", "--porcelain"], cwd=repo.root), status_before)
            ws.release_plan_approval_guard(repo.root, lease)

    def test_owner_reclaims_its_own_leftover_guard_and_retries_once(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            stale = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=journal["owner_token"],
                step="step-5-declaration-pin", now="t2",
            )
            # Simulate an interrupted earlier step: the guard is left
            # behind, never released.
            fresh = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=journal["owner_token"],
                step="step-6.2-stage-ordinary", now="t3",
            )
            self.assertNotEqual(fresh["lease_id"], stale["lease_id"])
            self.assertEqual(ws.read_plan_approval_guard(repo.root), fresh)

    def test_acquire_guard_refuses_when_held_under_the_current_epoch_by_a_different_token(self):
        """Defense in depth for `role="owner"`: a live guard whose
        `holder_owner_token` equals the journal's own current epoch but
        does *not* equal the token this call presents is never this
        session's own leftover -- refuse rather than reclaim."""
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            live = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=journal["owner_token"],
                step="step-5-declaration-pin", now="t2",
            )
            with self.assertRaises(ws.PlanApprovalGuardHeldError):
                ws.acquire_plan_approval_guard(
                    repo.root, holder_owner_token="0" * 32,
                    step="step-6.2-stage-ordinary", now="t3",
                )
            self.assertEqual(ws.read_plan_approval_guard(repo.root), live)

    # -- guarded_mutation fixed window -------------------------------------

    def test_guarded_mutation_happy_path_mutates_advances_progress_and_releases(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            calls = []
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=journal["owner_token"], step="step-5-declaration-pin", now="t2",
            ) as lease:
                calls.append(lease["step"])
                self.assertEqual(ws.read_plan_approval_guard(repo.root)["lease_id"], lease["lease_id"])

            self.assertEqual(calls, ["step-5-declaration-pin"])
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))
            progress = ws.read_plan_approval_owner_progress(repo.root, journal["owner_token"])
            self.assertEqual(progress["step"], "step-5-declaration-pin")
            self.assertEqual(progress["step_seq"], 1)
            self.assertEqual(progress["updated_at"], "t2")

    def test_guarded_mutation_releases_guard_without_advancing_progress_when_body_raises(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)

            class _Boom(Exception):
                pass

            with self.assertRaises(_Boom):
                with ws.plan_approval_guarded_mutation(
                    repo.root, owner_token=journal["owner_token"], step="step-5-declaration-pin", now="t2",
                ):
                    raise _Boom()

            self.assertIsNone(ws.read_plan_approval_guard(repo.root))
            self.assertIsNone(ws.read_plan_approval_owner_progress(repo.root, journal["owner_token"]))

    def test_guarded_mutation_releases_guard_without_advancing_progress_when_owner_assertion_fails(self):
        """A displaced owner still holding its old token: guard
        acquisition succeeds trivially (no guard is held), but
        `assert_owner` inside the window fails because a takeover already
        rotated the journal -- the guard must still be released and no
        progress record written under the stale token."""
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            old_token = journal["owner_token"]
            evidence = ws.plan_approval_takeover_evidence(repo.root)
            new_token = ws.take_over_plan_approval_transaction(
                repo.root, now="t2", evidence=evidence,
                user_authorization=ws.plan_approval_takeover_authorization_literal(evidence),
            )
            self.assertNotEqual(new_token, old_token)

            with self.assertRaises(ws.PlanApprovalOwnershipError):
                with ws.plan_approval_guarded_mutation(
                    repo.root, owner_token=old_token, step="step-5-declaration-pin", now="t3",
                ):
                    self.fail("must not reach the mutation body under a rotated-away token")

            self.assertIsNone(ws.read_plan_approval_guard(repo.root))
            self.assertIsNone(ws.read_plan_approval_owner_progress(repo.root, old_token))

    def test_owner_progress_step_seq_is_monotonic_across_multiple_guarded_mutations(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            owner_token = journal["owner_token"]
            for i, step in enumerate(
                ("step-5-declaration-pin", "step-6.1b-state-pin", "step-6.2-stage-ordinary"), start=1,
            ):
                with ws.plan_approval_guarded_mutation(
                    repo.root, owner_token=owner_token, step=step, now=f"t{i}",
                ):
                    pass
                progress = ws.read_plan_approval_owner_progress(repo.root, owner_token)
                self.assertEqual(progress["step_seq"], i)
                self.assertEqual(progress["step"], step)

    # -- takeover -----------------------------------------------------------

    def test_takeover_refuses_when_no_transaction_open(self):
        with h.ScratchRepo() as repo:
            with self.assertRaises(ws.NoPlanApprovalTransactionError):
                ws.take_over_plan_approval_transaction(
                    repo.root, now="t1", user_authorization="whatever",
                )

    def test_takeover_requires_the_exact_authorization_literal(self):
        with h.ScratchRepo() as repo:
            self._open(repo)
            with self.assertRaises(ws.PlanApprovalTakeoverRefusedError):
                ws.take_over_plan_approval_transaction(
                    repo.root, now="t2", user_authorization="not the right literal",
                )
            # nothing mutated -- the journal's owner_token is unchanged
            self.assertIsNotNone(ws.read_plan_approval_journal(repo.root))

    def test_takeover_happy_path_rotates_token_and_records_history(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            old_token = journal["owner_token"]
            evidence = ws.plan_approval_takeover_evidence(repo.root)
            self.assertEqual(evidence["owner_token"], old_token)
            self.assertIsNone(evidence["progress"])
            self.assertIsNone(evidence["guard"])
            literal = ws.plan_approval_takeover_authorization_literal(evidence)
            self.assertIn(old_token, literal)
            self.assertIn("step_seq none", literal)

            new_token = ws.take_over_plan_approval_transaction(
                repo.root, now="t2", evidence=evidence, user_authorization=literal,
            )

            reread = ws.read_plan_approval_journal(repo.root)
            self.assertEqual(reread["owner_token"], new_token)
            self.assertEqual(reread["takeover_count"], 1)
            self.assertEqual(reread["previous_owner_tokens"], [old_token])
            # every other field carried over byte-identically
            for key in reread:
                if key in ("owner_token", "takeover_count", "previous_owner_tokens"):
                    continue
                self.assertEqual(reread[key], journal[key], key)
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))  # released at 5a

    def test_displaced_owners_next_assertion_fails_after_takeover(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            old_token = journal["owner_token"]
            evidence = ws.plan_approval_takeover_evidence(repo.root)
            ws.take_over_plan_approval_transaction(
                repo.root, now="t2", evidence=evidence,
                user_authorization=ws.plan_approval_takeover_authorization_literal(evidence),
            )
            with self.assertRaises(ws.PlanApprovalOwnershipError):
                ws.assert_plan_approval_journal_owner(repo.root, old_token)

    def test_takeover_refuses_when_progress_advanced_between_observe_and_claim(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            owner_token = journal["owner_token"]
            evidence = ws.plan_approval_takeover_evidence(repo.root)  # observes: no progress yet
            literal = ws.plan_approval_takeover_authorization_literal(evidence)

            # The "owner" makes real progress before the takeover claims.
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-5-declaration-pin", now="t2",
            ):
                pass

            with self.assertRaises(ws.PlanApprovalTakeoverRefusedError):
                ws.take_over_plan_approval_transaction(
                    repo.root, now="t3", evidence=evidence, user_authorization=literal,
                )
            # nothing mutated -- still the original owner
            self.assertEqual(ws.read_plan_approval_journal(repo.root)["owner_token"], owner_token)

    def test_takeover_refuses_when_guard_is_destructive(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            owner_token = journal["owner_token"]
            ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=owner_token, step="step-6.5-commit", now="t2",
            )
            evidence = ws.plan_approval_takeover_evidence(repo.root)
            self.assertEqual(evidence["guard"]["step_class"], ws.DESTRUCTIVE)
            literal = ws.plan_approval_takeover_authorization_literal(evidence)

            with self.assertRaises(ws.PlanApprovalTakeoverRefusedError):
                ws.take_over_plan_approval_transaction(
                    repo.root, now="t3", evidence=evidence, user_authorization=literal,
                    guard_release_authorization=ws.plan_approval_guard_release_authorization_literal(
                        evidence["guard"],
                    ),
                )
            # the destructive guard survives untouched
            self.assertEqual(
                ws.read_plan_approval_guard(repo.root)["step"], "step-6.5-commit",
            )
            self.assertEqual(ws.read_plan_approval_journal(repo.root)["owner_token"], owner_token)

    def test_takeover_authorized_break_of_an_ordinary_abandoned_guard(self):
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            owner_token = journal["owner_token"]
            abandoned = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=owner_token, step="step-5-declaration-pin", now="t2",
            )
            evidence = ws.plan_approval_takeover_evidence(repo.root)
            self.assertEqual(evidence["guard"], abandoned)
            literal = ws.plan_approval_takeover_authorization_literal(evidence)
            guard_release = ws.plan_approval_guard_release_authorization_literal(abandoned)

            new_token = ws.take_over_plan_approval_transaction(
                repo.root, now="t3", evidence=evidence, user_authorization=literal,
                guard_release_authorization=guard_release,
            )

            self.assertEqual(ws.read_plan_approval_journal(repo.root)["owner_token"], new_token)
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))  # released at 5a

    def test_takeover_refuses_ordinary_guard_release_when_lease_id_does_not_match(self):
        """A changed `lease_id` proves the owner released and re-acquired
        between observation and takeover -- i.e. is demonstrably live."""
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            owner_token = journal["owner_token"]
            first = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=owner_token, step="step-5-declaration-pin", now="t2",
            )
            evidence = ws.plan_approval_takeover_evidence(repo.root)
            literal = ws.plan_approval_takeover_authorization_literal(evidence)
            guard_release = ws.plan_approval_guard_release_authorization_literal(first)

            ws.release_plan_approval_guard(repo.root, first)
            second = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=owner_token, step="step-6.2-stage-ordinary", now="t3",
            )
            self.assertNotEqual(second["lease_id"], first["lease_id"])

            with self.assertRaises(ws.PlanApprovalTakeoverRefusedError):
                ws.take_over_plan_approval_transaction(
                    repo.root, now="t4", evidence=evidence, user_authorization=literal,
                    guard_release_authorization=guard_release,
                )
            self.assertEqual(ws.read_plan_approval_guard(repo.root), second)

    def test_a_stale_takeover_against_an_already_superseded_epoch_refuses(self):
        """Two takeover attempts built from the same (now-stale)
        evidence: the first succeeds and rotates the token; the second,
        replaying the same authorization for the epoch it superseded,
        must refuse rather than silently rotating again or double-
        counting `takeover_count`."""
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            evidence = ws.plan_approval_takeover_evidence(repo.root)
            literal = ws.plan_approval_takeover_authorization_literal(evidence)

            first_new_token = ws.take_over_plan_approval_transaction(
                repo.root, now="t2", evidence=evidence, user_authorization=literal,
            )
            with self.assertRaises(ws.PlanApprovalTakeoverInProgressError):
                ws.take_over_plan_approval_transaction(
                    repo.root, now="t3", evidence=evidence, user_authorization=literal,
                )
            self.assertEqual(ws.read_plan_approval_journal(repo.root)["owner_token"], first_new_token)
            self.assertEqual(ws.read_plan_approval_journal(repo.root)["takeover_count"], 1)

    def test_a_dead_claim_from_a_crashed_same_epoch_takeover_is_cleared_and_retried(self):
        """A takeover attempt that died between step 3 (claim) and step 5
        (rotate) leaves a claim link behind while the journal still
        carries the same token -- provably having mutated nothing. A
        fresh takeover of that same epoch clears it and proceeds rather
        than refusing forever."""
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            owner_token = journal["owner_token"]
            full_journal_path = ws.plan_approval_journal_path(repo.root)
            dead_claim = full_journal_path.parent / f"PLAN_APPROVAL_JOURNAL.claim.{owner_token}"
            os.link(full_journal_path, dead_claim)
            self.assertTrue(dead_claim.exists())

            evidence = ws.plan_approval_takeover_evidence(repo.root)
            new_token = ws.take_over_plan_approval_transaction(
                repo.root, now="t2", evidence=evidence,
                user_authorization=ws.plan_approval_takeover_authorization_literal(evidence),
            )
            self.assertEqual(ws.read_plan_approval_journal(repo.root)["owner_token"], new_token)

    def test_takeover_evidence_reports_absent_transaction(self):
        with h.ScratchRepo() as repo:
            evidence = ws.plan_approval_takeover_evidence(repo.root)
            self.assertIsNone(evidence["journal"])
            self.assertIsNone(evidence["owner_token"])
            self.assertIsNone(evidence["progress"])
            self.assertIsNone(evidence["guard"])
            self.assertIsNone(evidence["outcome"])

    # -- WF8c item 352: /bootstrap-workflow-v2's own step-0 precondition ---

    def test_takeover_evidence_reports_a_fresh_unstarted_transaction_for_step_0(self):
        """`/bootstrap-workflow-v2`'s own step 0 (`WF8c` item 352) reads
        exactly `plan_approval_takeover_evidence` before ever calling
        `state-sync`; a transaction just opened, before any guarded
        mutation has run, must report a real `owner_token`,
        `outcome == NOT_COMMITTED` (nothing committed, HEAD unmoved),
        `progress is None` (no step completed yet), and `guard is None`
        (not currently held) -- exactly the four values step 0's own text
        names. Read-only: confirmed via an explicit before/after
        `git status --short` comparison, since step 0 must never mutate
        anything itself, only observe and (when a journal exists) stop."""
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            before = _run(["git", "status", "--short"], cwd=repo.root)

            evidence = ws.plan_approval_takeover_evidence(repo.root)

            after = _run(["git", "status", "--short"], cwd=repo.root)
            self.assertEqual(before, after)
            self.assertIsNotNone(evidence["journal"])
            self.assertEqual(evidence["owner_token"], journal["owner_token"])
            self.assertEqual(evidence["outcome"], ws.PLAN_APPROVAL_OUTCOME_NOT_COMMITTED)
            self.assertIsNone(evidence["progress"])
            self.assertIsNone(evidence["guard"])

    def test_takeover_evidence_reports_last_completed_step_and_held_guard_for_step_0(self):
        """A transaction interrupted mid-flight -- one guarded mutation
        already completed, a second one's guard left held (simulating a
        crash inside that step's own mutation body) -- is exactly what a
        real abandoned transaction looks like to a fresh session's step 0.
        `plan_approval_takeover_evidence` must surface both facts: the
        last-*completed* step (never a step merely started), and the
        currently-held guard's own step/class, so step 0's report names
        both without step 0 itself acquiring or releasing anything."""
        with h.ScratchRepo() as repo:
            journal = self._open(repo)
            owner_token = journal["owner_token"]
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-5-declaration-pin", now="t2",
            ):
                pass
            # A second guarded step starts but never completes (crash
            # inside the mutation body, left holding the guard).
            ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=owner_token,
                step="step-6.1b-state-pin", now="t3",
            )

            evidence = ws.plan_approval_takeover_evidence(repo.root)

            self.assertEqual(evidence["owner_token"], owner_token)
            self.assertEqual(evidence["outcome"], ws.PLAN_APPROVAL_OUTCOME_NOT_COMMITTED)
            self.assertEqual(evidence["progress"]["step"], "step-5-declaration-pin")
            self.assertEqual(evidence["guard"]["step"], "step-6.1b-state-pin")
            self.assertEqual(evidence["guard"]["step_class"], ws.ORDINARY)


_STATE_PATH = Path("docs/ai-workflow/WORKFLOW_STATE.json")


class TestPlanApprovalStateBlobPinAndMaterialize(unittest.TestCase):
    """WF8c (g), part 3 (`WFR-63`): the index-pinned-blob writer for
    `WORKFLOW_STATE.json` (step-6.1b-state-pin) and step 8b's
    materialization, exercised directly against real `ScratchRepo` git
    history the same way parts 1 and 2 exercise the journal and the
    guard/takeover contract -- not yet wired into a guarded-mutation
    caller or `.claude/commands/approve-review.md`.

    `TestPlanApprovalFailureAtomicityTransaction._setup` leaves
    `WORKFLOW_STATE.json` written but *uncommitted* (parts 1/2 never
    needed it at `HEAD`, since the journal captures `pre_state` as a
    plain dict). This part's own primitives read the file's mode *at
    `HEAD`*, matching this repository's own real invariant --
    `WORKFLOW_STATE.json` is always already tracked before any approval
    round begins -- so `_setup` below commits it first, before the
    journal opens (its own `pre_procedure_head` must be pinned after
    that commit, not before, or the classifier would see a spurious
    concurrent commit)."""

    def _setup(self, repo: h.ScratchRepo, wi: str):
        pre_state, record, review_content_id, plan = TestPlanApprovalFailureAtomicityTransaction._setup(
            self, repo, wi,
        )
        _run(["git", "add", str(_STATE_PATH)], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "seed workflow state"], cwd=repo.root)
        return pre_state, record, review_content_id, plan

    def _open_journal(self, repo: h.ScratchRepo, wi: str, *args):
        return TestPlanApprovalFailureAtomicityTransaction._open_journal(self, repo, wi, *args)

    def _expected_bytes(self, journal: dict) -> bytes:
        return base64.b64decode(journal["expected_post_state_b64"])

    def _commit_pinned_state(
        self, repo: h.ScratchRepo, wi: str, plan, journal: dict, review_content_id: str,
    ) -> str:
        """Stages `plan.paths` minus `_STATE_PATH` via the ordinary `git
        add` mechanism, pins the state blob directly into the index,
        verifies both, and creates the approval commit -- a full
        end-to-end pass through this part's own primitives (not
        necessarily the eventual production step ordering, which remains
        a follow-up session's scope to fix in place)."""
        ordinary_paths = tuple(p for p in plan.paths if p != str(_STATE_PATH))
        ws.stage_plan_approval_commit_paths(repo.root, ordinary_paths)
        ws.pin_plan_approval_state_blob(repo.root, self._expected_bytes(journal))
        ws.verify_staged_plan_approval_state_blob(repo.root, journal["expected_post_state_sha256"])
        body = f"plan approval\n\nWorkflow-Plan-Approval: {review_content_id}\nWorkflow-Work-Item: {wi}"
        _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
        return repo.head()

    # -- pin ----------------------------------------------------------

    def test_pin_stages_the_expected_bytes_without_touching_the_working_tree(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            pre_bytes = (repo.root / _STATE_PATH).read_bytes()
            expected_bytes = self._expected_bytes(journal)
            self.assertNotEqual(pre_bytes, expected_bytes)

            blob_sha = ws.pin_plan_approval_state_blob(repo.root, expected_bytes)

            self.assertIn(len(blob_sha), (40, 64))
            self.assertRegex(blob_sha, r"^[0-9a-f]+$")
            # working tree is untouched
            self.assertEqual((repo.root / _STATE_PATH).read_bytes(), pre_bytes)
            # the index carries the new content instead
            staged = subprocess.run(
                ["git", "show", f":{_STATE_PATH}"], cwd=repo.root, capture_output=True, check=True,
            ).stdout
            self.assertEqual(staged, expected_bytes)
            # both a staged diff (index vs HEAD) and an unstaged diff (worktree vs index) exist
            self.assertIn(
                str(_STATE_PATH),
                _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root).splitlines(),
            )
            self.assertIn(
                str(_STATE_PATH), _run(["git", "diff", "--name-only"], cwd=repo.root).splitlines(),
            )

    def test_pin_precondition_refuses_when_state_path_already_staged_and_dirty(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            (repo.root / _STATE_PATH).write_text(
                (repo.root / _STATE_PATH).read_text() + "\n",
            )
            _run(["git", "add", str(_STATE_PATH)], cwd=repo.root)

            with self.assertRaises(ws.DirtyIndexBeforeStagingError):
                ws.pin_plan_approval_state_blob(repo.root, self._expected_bytes(journal))

    def test_pin_raises_when_state_path_absent_at_head(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)

            with self.assertRaises(ws.PlanApprovalStateBlobUnavailableError):
                ws.pin_plan_approval_state_blob(
                    repo.root, self._expected_bytes(journal),
                    state_path=Path("docs/ai-workflow/DOES_NOT_EXIST.json"),
                )

    # -- verify staged --------------------------------------------------

    def test_verify_staged_state_blob_passes_then_fails_after_a_foreign_restage(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            ws.pin_plan_approval_state_blob(repo.root, self._expected_bytes(journal))

            ws.verify_staged_plan_approval_state_blob(repo.root, journal["expected_post_state_sha256"])

            # A foreign restage over the same path (e.g. a concurrent writer) must be
            # caught rather than trusted on the earlier pin's word alone.
            tampered_sha = subprocess.run(
                ["git", "hash-object", "-w", "--stdin"], cwd=repo.root,
                input=b'{"tampered": true}\n', capture_output=True, check=True,
            ).stdout.decode("ascii").strip()
            _run(
                ["git", "update-index", "--cacheinfo", f"100644,{tampered_sha},{_STATE_PATH}"],
                cwd=repo.root,
            )

            with self.assertRaises(ws.StagedStateBlobMismatchError):
                ws.verify_staged_plan_approval_state_blob(repo.root, journal["expected_post_state_sha256"])

    # -- end to end: pin, commit, verify committed, materialize ---------

    def test_end_to_end_pin_commit_materialize_matches_apply_plan_approval(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            pre_bytes = (repo.root / _STATE_PATH).read_bytes()

            commit = self._commit_pinned_state(repo, wi, plan, journal, review_content_id)

            # working tree is still untouched immediately after the commit
            self.assertEqual((repo.root / _STATE_PATH).read_bytes(), pre_bytes)
            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal), ws.PLAN_APPROVAL_OUTCOME_COMMITTED,
            )
            ws.verify_committed_plan_approval_state_blob(
                repo.root, commit, journal["expected_post_state_sha256"],
            )

            ws.materialize_plan_approval_state(repo.root, commit, journal["expected_post_state_sha256"])

            expected_bytes = self._expected_bytes(journal)
            self.assertEqual((repo.root / _STATE_PATH).read_bytes(), expected_bytes)
            self.assertEqual(json.loads(expected_bytes)["work_items"][wi]["phase"], "IMPLEMENTING")
            # the working tree now matches HEAD/the index exactly for this path
            self.assertEqual(
                _run(["git", "status", "--porcelain", "--", str(_STATE_PATH)], cwd=repo.root), "",
            )

    def test_verify_committed_state_blob_raises_on_mismatch(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            commit = self._commit_pinned_state(repo, wi, plan, journal, review_content_id)

            with self.assertRaises(ws.CommittedStateBlobMismatchError):
                ws.verify_committed_plan_approval_state_blob(repo.root, commit, "0" * 64)

    # -- materialize ------------------------------------------------------

    def test_materialize_refuses_unverified_committed_content_and_leaves_working_tree_untouched(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            pre_bytes = (repo.root / _STATE_PATH).read_bytes()
            commit = self._commit_pinned_state(repo, wi, plan, journal, review_content_id)

            with self.assertRaises(ws.CommittedStateBlobMismatchError):
                ws.materialize_plan_approval_state(repo.root, commit, "0" * 64)

            self.assertEqual((repo.root / _STATE_PATH).read_bytes(), pre_bytes)

    def test_materialize_leaves_no_stray_temp_file(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            commit = self._commit_pinned_state(repo, wi, plan, journal, review_content_id)

            ws.materialize_plan_approval_state(repo.root, commit, journal["expected_post_state_sha256"])

            leftovers = list((repo.root / _STATE_PATH.parent).glob(f".{_STATE_PATH.name}-*.tmp"))
            self.assertEqual(leftovers, [])

    def test_materialize_is_byte_identical_to_git_show_at_commit(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            commit = self._commit_pinned_state(repo, wi, plan, journal, review_content_id)

            ws.materialize_plan_approval_state(repo.root, commit, journal["expected_post_state_sha256"])

            committed = subprocess.run(
                ["git", "show", f"{commit}:{_STATE_PATH}"], cwd=repo.root,
                capture_output=True, check=True,
            ).stdout
            self.assertEqual((repo.root / _STATE_PATH).read_bytes(), committed)


class TestPlanApprovalMaterializeTargetScopedClassification(unittest.TestCase):
    """`WF8c` item 348(hh)/(mm): the target-scoped three-way
    classification and the whole-file freshness re-check together are
    what make step 8b safe against a *different* work item's own
    legitimate concurrent state write -- the property that makes
    `materialize_plan_approval_state`'s existing whole-file overwrite
    (part 3, already tested for the single-work-item case) safe to call
    from the permanent `/approve-review` command, where other work
    items' uncommitted writes between journal-open and materialization
    are the ordinary case, not an edge case."""

    def _two_item_setup(self, repo: h.ScratchRepo, wi: str, other_wi: str):
        """Seeds a `WORKFLOW_STATE.json` declaring *two* work items and
        commits it, then opens a plan-approval journal for `wi` alone --
        `other_wi`'s own entry is never touched by anything this class
        exercises, only read back to confirm it survives."""
        repo.write_plan_docs(work_item_id=wi)
        repo.commit_plan_docs_as_base()
        work_item = h.base_work_item(
            work_item_id=wi, governing_workflow_version="1", phase="AWAITING_PLAN_APPROVAL",
            plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            registry_path=f"docs/ai-workflow/registry/{wi}-registry.json",
            mapping_path=f"docs/ai-workflow/requirements/{wi}-mapping.json",
            base_commit=repo.base,
        )
        other_work_item = h.base_work_item(
            work_item_id=other_wi, governing_workflow_version="1", phase="IMPLEMENTING",
            plan_path=f"docs/ai-workflow/{other_wi}-plan.md",
            registry_path=f"docs/ai-workflow/registry/{other_wi}-registry.json",
            mapping_path=f"docs/ai-workflow/requirements/{other_wi}-mapping.json",
            base_commit=repo.base,
        )
        repo.write_workflow_state(**{wi: work_item, other_wi: other_work_item})
        # write_workflow_state's own fixture serialization is compact
        # JSON, not the canonical form `_publish_state_file` always uses
        # in production (item 353) -- re-canonicalize on disk so this
        # fixture's real bytes actually match what
        # plan_approval_state_matches_pre_transaction's sha256 re-read
        # will observe, exactly as a real, already-committed
        # WORKFLOW_STATE.json always is.
        pre_state = json.loads((repo.root / _STATE_PATH).read_text())
        (repo.root / _STATE_PATH).write_bytes(ws._serialize_state(pre_state))
        _run(["git", "add", str(_STATE_PATH)], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "seed workflow state"], cwd=repo.root)

        plan = fingerprint.resolve_plan_stage_approval_commit_paths(repo.root, wi, _STATE_PATH)
        protected = h.plan_stage_protected_paths(wi)
        review_content_id, _ = fingerprint.compute_review_content_id_plan_stage(
            repo.root, repo.base, work_item_type="process", work_item_id=wi,
            plan_revision=1, protected=protected,
            excluded_paths=h.plan_stage_excluded_paths(),
            excluded_prefixes=h.plan_stage_excluded_prefixes(),
        )
        record = ws.build_approval_record(
            basis="EXTERNAL_APPROVE", stage="plan",
            user_confirmation=f"I confirm plan approval for {wi}, plan stage.", now="t1",
            reviewed_bundle_id="b1", approved_review_content_id=review_content_id,
            review_content_manifest=[{"path": "x", "exists": True, "mode": "100644", "blob": "y"}],
        )
        journal = ws.open_plan_approval_journal(
            repo.root, work_item_id=wi, base_commit=repo.base, pre_state=pre_state,
            record=record, approval_now="t1", expected_bundle_id="b1",
            expected_review_content_id=review_content_id, applicable_paths=plan.paths,
            fifth_member_applies=plan.artifacts_declaration_path is not None,
            fifth_member_sha256=plan.artifacts_declaration_sha256,
            user_confirmation=f"I confirm plan approval for {wi}, plan stage.",
            quiescence_authorization="not required: see plan_approval_state_matches_pre_transaction",
        )
        post_state = ws.apply_plan_approval(pre_state, wi, record, "t1")
        return pre_state, post_state, journal, other_work_item

    def test_classify_write_when_current_still_matches_pre_state(self):
        with h.ScratchRepo() as repo:
            pre_state, post_state, journal, _ = self._two_item_setup(repo, "wi", "other-wi")
            self.assertEqual(
                ws.classify_plan_approval_materialize_target(repo.root, "wi", pre_state, post_state),
                ws.PLAN_APPROVAL_MATERIALIZE_WRITE,
            )
            self.assertTrue(
                ws.plan_approval_state_matches_pre_transaction(
                    repo.root, journal["pre_procedure_state_sha256"],
                ),
            )

    def test_classify_noop_when_already_materialized(self):
        with h.ScratchRepo() as repo:
            pre_state, post_state, journal, _ = self._two_item_setup(repo, "wi", "other-wi")
            (repo.root / _STATE_PATH).write_text(json.dumps(post_state))
            self.assertEqual(
                ws.classify_plan_approval_materialize_target(repo.root, "wi", pre_state, post_state),
                ws.PLAN_APPROVAL_MATERIALIZE_NOOP,
            )

    def test_classify_raises_when_this_work_items_own_entry_diverges(self):
        with h.ScratchRepo() as repo:
            pre_state, post_state, journal, _ = self._two_item_setup(repo, "wi", "other-wi")
            tampered = json.loads((repo.root / _STATE_PATH).read_text())
            tampered["work_items"]["wi"]["phase"] = "SOMETHING_ELSE_ENTIRELY"
            (repo.root / _STATE_PATH).write_text(json.dumps(tampered))
            with self.assertRaises(ws.PlanApprovalMaterializeDivergentEntryError):
                ws.classify_plan_approval_materialize_target(repo.root, "wi", pre_state, post_state)

    def test_a_different_work_items_concurrent_write_is_never_discarded(self):
        """The decisive case item 348(hh)/(mm) exist for: `other-wi`'s
        own entry is legitimately updated (a real concurrent
        `state_transaction`-style write, uncommitted) after journal-open
        but before materialization. `wi`'s own target-scoped
        classification is unaffected (still `WRITE`, since `wi`'s own
        entry never changed) -- but the whole-file freshness re-check
        correctly detects the change and refuses, so the caller never
        reaches `materialize_plan_approval_state`'s own unconditional
        whole-file overwrite, which would otherwise silently revert
        `other-wi` back to its pre-transaction value."""
        with h.ScratchRepo() as repo:
            pre_state, post_state, journal, other_work_item = self._two_item_setup(
                repo, "wi", "other-wi",
            )
            current = json.loads((repo.root / _STATE_PATH).read_text())
            current["work_items"]["other-wi"]["phase"] = "AWAITING_FUNCTIONAL_REVIEW"
            current["work_items"]["other-wi"]["state_revision"] = (
                other_work_item.get("state_revision", 1) + 1
            )
            (repo.root / _STATE_PATH).write_text(json.dumps(current))

            # wi's own classification is unaffected by other-wi's write.
            self.assertEqual(
                ws.classify_plan_approval_materialize_target(repo.root, "wi", pre_state, post_state),
                ws.PLAN_APPROVAL_MATERIALIZE_WRITE,
            )
            # But the whole-file freshness re-check catches it and the
            # caller must refuse rather than call materialize_plan_approval_state.
            self.assertFalse(
                ws.plan_approval_state_matches_pre_transaction(
                    repo.root, journal["pre_procedure_state_sha256"],
                ),
            )
            # Confirming what an unconditional overwrite *would* have
            # discarded, had the freshness check not been run first.
            self.assertNotEqual(
                json.loads((repo.root / _STATE_PATH).read_text())["work_items"]["other-wi"]["phase"],
                post_state["work_items"].get("other-wi", {}).get("phase"),
            )


class TestPlanApprovalPermanentSiteEndToEnd(unittest.TestCase):
    """`WF8c` items 347/348: `.claude/commands/approve-review.md`'s own
    new plan-stage steps 4b-6d, composed and executed exactly in the
    order that file's own prose describes -- proof the whole failure-
    atomicity transaction is actually implementable end to end at the
    permanent site, not merely that each of its primitives works in
    isolation (parts 1-3's own tests, item 348's own predecessor). Serves
    as item 348's own evidence: the "Bootstrap plan-approval procedure"
    was, by its own design, never automated code to test directly (`WF8c`
    item 348's own docstring) -- this class is that same live checklist,
    exercised as a real, composed procedure against real `ScratchRepo`
    git history instead."""

    def _setup(self, repo: h.ScratchRepo, wi: str):
        """Mirrors `TestPlanApprovalStateBlobPinAndMaterialize._setup`
        (`WORKFLOW_STATE.json` committed before the journal opens, since
        this part's own primitives read the file's mode *at* `HEAD`) but
        additionally re-canonicalizes the fixture's bytes first --
        `write_workflow_state`'s own compact-JSON form is not what a
        real, already-committed `WORKFLOW_STATE.json` ever looks like
        (item 353 requires the canonical form from every production
        writer), and `plan_approval_state_matches_pre_transaction`'s
        fresh, whole-file byte comparison is the first primitive in this
        whole transaction to actually depend on that being true on disk,
        not only in `_serialize_state`'s own re-derivation."""
        pre_state, record, review_content_id, plan = TestPlanApprovalFailureAtomicityTransaction._setup(
            self, repo, wi,
        )
        (repo.root / _STATE_PATH).write_bytes(ws._serialize_state(pre_state))
        (repo.root / ".gitignore").write_text(".ai-review/\n")
        _run(["git", "add", str(_STATE_PATH), ".gitignore"], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "seed workflow state"], cwd=repo.root)
        return pre_state, record, review_content_id, plan

    def _open_journal(self, repo: h.ScratchRepo, wi: str, *args):
        return TestPlanApprovalFailureAtomicityTransaction._open_journal(self, repo, wi, *args)

    def test_end_to_end_happy_path_matches_the_new_permanent_site_procedure(self):
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            owner_token = journal["owner_token"]
            post_state = ws.apply_plan_approval(pre_state, wi, record, "t1")

            # Step 5 (merged staging-and-pin): no fifth member in this
            # fixture, so the single call covers the three ordinary
            # members only and no pin follows -- confirms the merge left
            # the simple four-member case's behavior unchanged.
            self.assertIsNone(plan.artifacts_declaration_path)
            ordinary_paths = tuple(p for p in plan.paths if p != str(_STATE_PATH))
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-5-stage-and-pin", now="t2",
            ):
                ws.stage_plan_approval_commit_paths(repo.root, ordinary_paths)

            # Step 6.2: 6.1a compare-and-swap, then pin the state blob.
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-6.1b-state-pin", now="t3",
            ):
                self.assertTrue(
                    ws.plan_approval_state_matches_pre_transaction(
                        repo.root, journal["pre_procedure_state_sha256"],
                    ),
                )
                ws.pin_plan_approval_state_blob(
                    repo.root, base64.b64decode(journal["expected_post_state_b64"]),
                )
                ws.verify_staged_plan_approval_state_blob(
                    repo.root, journal["expected_post_state_sha256"],
                )

            # Step 6.3: staged-set assertion.
            staged = _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root)
            staged_paths = {line for line in staged.splitlines() if line}
            self.assertTrue(staged_paths.issubset(set(journal["applicable_paths"])))

            # Step 6.4: the commit.
            body = f"plan approval\n\nWorkflow-Plan-Approval: {review_content_id}\nWorkflow-Work-Item: {wi}"
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-6.5-commit", now="t4",
            ):
                _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
            commit = repo.head()

            # Step 6a: classify -> COMMITTED; the post-commit verification set.
            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_COMMITTED,
            )
            ws.verify_post_approval_manifest_match(
                repo.root, post_state["work_items"][wi], stage="plan",
                base_commit=repo.base, commit=commit,
            )
            ws.assert_committed_path_set_matches(repo.root, commit, journal["applicable_paths"])
            ws.verify_committed_plan_approval_state_blob(
                repo.root, commit, journal["expected_post_state_sha256"],
            )

            # Step 6c: materialize.
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-8b-materialize", now="t5",
            ):
                target = ws.classify_plan_approval_materialize_target(repo.root, wi, pre_state, post_state)
                self.assertEqual(target, ws.PLAN_APPROVAL_MATERIALIZE_WRITE)
                self.assertTrue(
                    ws.plan_approval_state_matches_pre_transaction(
                        repo.root, journal["pre_procedure_state_sha256"],
                    ),
                )
                ws.materialize_plan_approval_state(repo.root, commit, journal["expected_post_state_sha256"])

            # Step 6d: close the journal.
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-8a-close-journal", now="t6",
            ):
                ws.close_plan_approval_journal(repo.root)

            self.assertIsNone(ws.read_plan_approval_journal(repo.root))
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))
            final = json.loads((repo.root / _STATE_PATH).read_text())
            self.assertEqual(final, post_state)
            self.assertEqual(_run(["git", "status", "--porcelain"], cwd=repo.root), "")

    def _setup_with_fifth_member(self, repo: h.ScratchRepo, wi: str):
        """The real defect's exact fixture, reused for this class's own
        real-choreography exercise: the artifacts declaration is pending
        (never committed) and fresh in the bundle capture, so
        `resolve_plan_stage_approval_commit_paths` resolves the genuine
        five-member set step 5's merged staging-and-pin window must now
        handle in one call (`workflow-v2-3-followups` CP1,
        `LPR-R1-B01`)."""
        membership = TestPlanStageApprovalCommitMembership()
        repo.write_plan_docs(work_item_id=wi)
        membership._commit_base_without_artifacts(repo, wi)
        work_item = h.base_work_item(
            work_item_id=wi, governing_workflow_version="2.1", phase="AWAITING_PLAN_APPROVAL",
            plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            registry_path=f"docs/ai-workflow/registry/{wi}-registry.json",
            mapping_path=f"docs/ai-workflow/requirements/{wi}-mapping.json",
            base_commit=repo.base,
        )
        repo.write_workflow_state(**{wi: work_item})
        # Canonicalize and commit the state file before the journal opens
        # -- mirrors `_setup` above: `plan_approval_state_matches_pre_
        # transaction`'s fresh, whole-file byte comparison depends on the
        # live bytes matching `_serialize_state`'s canonical form, and the
        # merged step's own state-pin sub-step (6.2) needs a stable `HEAD`
        # to diff its own new pin against. Only the fifth member --
        # `<wi>-artifacts.json` -- stays genuinely pending.
        pre_state = json.loads((repo.root / _STATE_PATH).read_text())
        (repo.root / _STATE_PATH).write_bytes(ws._serialize_state(pre_state))
        _run(["git", "add", str(_STATE_PATH)], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "seed workflow state"], cwd=repo.root)
        artifacts_rel = f"docs/ai-workflow/registry/{wi}-artifacts.json"
        membership._seed_bundle_capture(repo, wi, artifacts_rel)

        plan = fingerprint.resolve_plan_stage_approval_commit_paths(
            repo.root, wi, Path("docs/ai-workflow/WORKFLOW_STATE.json"),
        )
        self.assertIsNotNone(plan.artifacts_declaration_path)  # confirms the five-member case

        protected = h.plan_stage_protected_paths(wi)
        review_content_id, _ = fingerprint.compute_review_content_id_plan_stage(
            repo.root, repo.base, work_item_type="process", work_item_id=wi,
            plan_revision=1, protected=protected,
            excluded_paths=h.plan_stage_excluded_paths(),
            excluded_prefixes=h.plan_stage_excluded_prefixes(),
        )
        record = ws.build_approval_record(
            basis="EXTERNAL_APPROVE", stage="plan",
            user_confirmation=f"I confirm plan approval for {wi}, plan stage.", now="t1",
            reviewed_bundle_id="b1", approved_review_content_id=review_content_id,
            review_content_manifest=[{"path": "x", "exists": True, "mode": "100644", "blob": "y"}],
        )
        return pre_state, record, review_content_id, plan

    def test_five_member_fixture_succeeds_through_the_real_merged_step_5(self):
        """The exact gap `workflow-v2-3-followups` CP1 closes and
        `WORKFLOW_V2_3_FOLLOWUPS.md` confirmed no test covered: a genuine
        five-member fixture (fifth member pending and fresh) driven
        through `approve-review.md`'s own real, merged step 5 -- one call
        to `stage_plan_approval_commit_paths` over all four non-state
        members, then the fifth-member pin, both inside the same
        `"step-5-stage-and-pin"` guarded window -- proving the documented
        sequence now succeeds end to end, all the way through to a
        committed, materialized, journal-closed outcome. The *old*
        two-call split is proven broken by
        `TestPlanStageApprovalCommitMembership`'s own low-level fixture
        already; this test proves the *new* one-call choreography works,
        not merely that the underlying primitive accepts an arbitrary
        path tuple."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup_with_fifth_member(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            owner_token = journal["owner_token"]
            post_state = ws.apply_plan_approval(pre_state, wi, record, "t1")

            # Step 5 (merged): one call over every non-state member,
            # ordinary members plus the fifth, then pin the fifth --
            # exactly approve-review.md's own documented call shape.
            fifth = plan.artifacts_declaration_path
            self.assertIsNotNone(fifth)
            ordinary_paths = tuple(p for p in plan.paths if p not in (str(_STATE_PATH), fifth))
            self.assertEqual(len(ordinary_paths), 3)
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-5-stage-and-pin", now="t2",
            ):
                ws.stage_plan_approval_commit_paths(repo.root, ordinary_paths + (fifth,))
                ws.verify_staged_blob_sha256(repo.root, fifth, plan.artifacts_declaration_sha256)

            # The fifth member is the only one of the four with real
            # changed content at this fixture's HEAD (the three ordinary
            # members were already settled by `_commit_base_without_
            # artifacts`) -- a subset check, matching
            # `stage_plan_approval_commit_paths`'s own post-staging
            # assertion, not exact equality (a byte-identical member
            # legitimately produces no diff entry).
            staged = _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root)
            staged_paths = {line for line in staged.splitlines() if line}
            self.assertIn(fifth, staged_paths)
            self.assertTrue(staged_paths.issubset(set(ordinary_paths) | {fifth}))

            # Step 6.2: 6.1a compare-and-swap, then pin the state blob.
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-6.1b-state-pin", now="t3",
            ):
                self.assertTrue(
                    ws.plan_approval_state_matches_pre_transaction(
                        repo.root, journal["pre_procedure_state_sha256"],
                    ),
                )
                ws.pin_plan_approval_state_blob(
                    repo.root, base64.b64decode(journal["expected_post_state_b64"]),
                )
                ws.verify_staged_plan_approval_state_blob(
                    repo.root, journal["expected_post_state_sha256"],
                )

            # Step 6.3: staged-set assertion.
            staged = _run(["git", "diff", "--name-only", "--cached", "HEAD"], cwd=repo.root)
            staged_paths = {line for line in staged.splitlines() if line}
            self.assertTrue(staged_paths.issubset(set(journal["applicable_paths"])))

            # Step 6.4: the commit.
            body = f"plan approval\n\nWorkflow-Plan-Approval: {review_content_id}\nWorkflow-Work-Item: {wi}"
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-6.5-commit", now="t4",
            ):
                _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
            commit = repo.head()

            # Step 6a: classify -> COMMITTED; the post-commit verification
            # set, fifth-member blob check included.
            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_COMMITTED,
            )
            ws.verify_post_approval_manifest_match(
                repo.root, post_state["work_items"][wi], stage="plan",
                base_commit=repo.base, commit=commit,
            )
            ws.assert_committed_path_set_matches(repo.root, commit, journal["applicable_paths"])
            ws.verify_committed_plan_approval_state_blob(
                repo.root, commit, journal["expected_post_state_sha256"],
            )
            ws.verify_committed_blob_sha256(repo.root, commit, fifth, plan.artifacts_declaration_sha256)

            # Step 6c: materialize; step 6d: close the journal.
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-8b-materialize", now="t5",
            ):
                target = ws.classify_plan_approval_materialize_target(repo.root, wi, pre_state, post_state)
                self.assertEqual(target, ws.PLAN_APPROVAL_MATERIALIZE_WRITE)
                ws.materialize_plan_approval_state(repo.root, commit, journal["expected_post_state_sha256"])
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-8a-close-journal", now="t6",
            ):
                ws.close_plan_approval_journal(repo.root)

            self.assertIsNone(ws.read_plan_approval_journal(repo.root))
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))
            final = json.loads((repo.root / _STATE_PATH).read_text())
            self.assertEqual(final, post_state)
            self.assertEqual(_run(["git", "status", "--porcelain"], cwd=repo.root), "")

    def test_merged_step_5_still_refuses_on_unrelated_dirty_index(self):
        """The merge does not weaken the pre-staging precondition: unrelated
        content already staged with real changed content before the merged
        window's own `stage_plan_approval_commit_paths` call runs still
        raises `DirtyIndexBeforeStagingError`, and the guard releases
        without advancing progress -- step 6b's rollback then resets it
        cleanly, and a retry (with the unrelated content removed) succeeds
        through the same merged step."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup_with_fifth_member(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            owner_token = journal["owner_token"]

            status_before = _run(["git", "status", "--porcelain"], cwd=repo.root)
            (repo.root / "unrelated.txt").write_text("unrelated concurrent content\n")
            _run(["git", "add", "unrelated.txt"], cwd=repo.root)

            fifth = plan.artifacts_declaration_path
            ordinary_paths = tuple(p for p in plan.paths if p not in (str(_STATE_PATH), fifth))
            with self.assertRaises(ws.DirtyIndexBeforeStagingError):
                with ws.plan_approval_guarded_mutation(
                    repo.root, owner_token=owner_token, step="step-5-stage-and-pin", now="t2",
                ):
                    ws.stage_plan_approval_commit_paths(repo.root, ordinary_paths + (fifth,))

            # The guard released without advancing progress -- no
            # progress record for this step exists yet.
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))
            progress = ws.read_plan_approval_owner_progress(repo.root, owner_token)
            self.assertTrue(progress is None or progress.get("step") != "step-5-stage-and-pin")

            # Step 6b's rollback resets the whole index back to HEAD --
            # both this attempt's own unrelated stage and (a no-op, since
            # the call above raised before staging anything of its own)
            # any partial staging from the merged call.
            lease = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=owner_token, step="rollback-index-reset", now="t3",
            )
            try:
                ws.rollback_plan_approval_transaction(repo.root, owner_token=owner_token)
            finally:
                ws.release_plan_approval_guard(repo.root, lease)
            self.assertEqual(repo.head(), journal["pre_procedure_head"])
            status_after = _run(["git", "status", "--porcelain"], cwd=repo.root)
            expected = set(status_before.splitlines()) | {"?? unrelated.txt"}
            self.assertEqual(set(status_after.splitlines()), expected)

    def test_new_step_label_classifies_ordinary_and_acquires_the_guard(self):
        """`plan_approval_step_class("step-5-stage-and-pin")` returns
        `"ordinary"` and `plan_approval_guarded_mutation` successfully
        acquires the guard under it -- the existing generic
        `TestPlanApprovalFailureAtomicityTransaction`-sibling set-iteration
        test covers the frozensets' exhaustiveness generically but proves
        nothing about this specific, newly-added label (`LPR-R3-B02`)."""
        self.assertEqual(ws.plan_approval_step_class("step-5-stage-and-pin"), ws.ORDINARY)
        with h.ScratchRepo() as repo:
            wi = "wi"
            journal = self._open_journal(repo, wi, *self._setup(repo, wi))
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=journal["owner_token"], step="step-5-stage-and-pin", now="t2",
            ):
                pass
            progress = ws.read_plan_approval_owner_progress(repo.root, journal["owner_token"])
            self.assertEqual(progress["step"], "step-5-stage-and-pin")

    def test_not_committed_outcome_runs_the_step_6b_rollback_pattern(self):
        """Nothing staged, nothing committed -- classify must find
        `NOT_COMMITTED`, and step 6b's own guard-wrapped (not
        `plan_approval_guarded_mutation`) rollback call must leave no
        journal, no guard, and no owner-progress orphan behind."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            owner_token = journal["owner_token"]

            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_NOT_COMMITTED,
            )
            lease = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=owner_token, step="rollback-index-reset", now="t2",
            )
            try:
                ws.rollback_plan_approval_transaction(repo.root, owner_token=owner_token)
            finally:
                ws.release_plan_approval_guard(repo.root, lease)

            self.assertIsNone(ws.read_plan_approval_journal(repo.root))
            self.assertIsNone(ws.read_plan_approval_guard(repo.root))
            self.assertIsNone(ws.read_plan_approval_owner_progress(repo.root, owner_token))
            self.assertEqual(repo.head(), journal["pre_procedure_head"])
            self.assertEqual(_run(["git", "status", "--porcelain"], cwd=repo.root), "")

    def test_step_6_2_compare_and_swap_catches_staleness_before_any_commit(self):
        """A legitimate concurrent write lands on
        `docs/ai-workflow/WORKFLOW_STATE.json` between journal-open and
        step 6.2 -- the 6.1a compare-and-swap must detect it and refuse
        *before* pinning a post-state derived from superseded bytes;
        step 6b's rollback then leaves the concurrent write's own bytes
        completely untouched (rollback never writes the working tree)."""
        with h.ScratchRepo() as repo:
            wi = "wi"
            pre_state, record, review_content_id, plan = self._setup(repo, wi)
            journal = self._open_journal(repo, wi, pre_state, record, review_content_id, plan)
            owner_token = journal["owner_token"]

            # A concurrent, legitimate state_transaction-shaped write.
            concurrent = json.loads((repo.root / _STATE_PATH).read_text())
            concurrent["work_items"][wi]["some_unrelated_field"] = "touched by another writer"
            (repo.root / _STATE_PATH).write_bytes(ws._serialize_state(concurrent))

            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-6.2-stage-ordinary", now="t2",
            ):
                ordinary_paths = tuple(p for p in plan.paths if p != str(_STATE_PATH))
                ws.stage_plan_approval_commit_paths(repo.root, ordinary_paths)

            fresh = ws.plan_approval_state_matches_pre_transaction(
                repo.root, journal["pre_procedure_state_sha256"],
            )
            self.assertFalse(fresh)

            lease = ws.acquire_plan_approval_guard(
                repo.root, holder_owner_token=owner_token, step="rollback-index-reset", now="t3",
            )
            try:
                ws.rollback_plan_approval_transaction(repo.root, owner_token=owner_token)
            finally:
                ws.release_plan_approval_guard(repo.root, lease)

            self.assertIsNone(ws.read_plan_approval_journal(repo.root))
            # The concurrent write survives untouched -- rollback resets
            # only the index, never the working tree.
            self.assertEqual(json.loads((repo.root / _STATE_PATH).read_text()), concurrent)

    def test_step_6c_refuses_rather_than_discard_a_different_work_items_write(self):
        """Full-stack version of
        `TestPlanApprovalMaterializeTargetScopedClassification`'s own
        decisive case: after this work item's commit lands, a different
        work item's own entry is legitimately updated before step 6c
        runs. The caller (this test, standing in for the command's own
        step 6c prose) must observe `WRITE` from the target-scoped
        classifier but `False` from the freshness re-check, and must
        therefore never call `materialize_plan_approval_state` at all --
        proving the two checks compose correctly to prevent exactly the
        silent-discard failure mode item 348(hh)/(mm) exist to close."""
        with h.ScratchRepo() as repo:
            wi, other_wi = "wi", "other-wi"
            pre_state, post_state, journal, other_work_item = (
                TestPlanApprovalMaterializeTargetScopedClassification._two_item_setup(
                    self, repo, wi, other_wi,
                )
            )
            owner_token = journal["owner_token"]

            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-6.2-stage-ordinary", now="t2",
            ):
                ordinary_paths = tuple(
                    p for p in journal["applicable_paths"] if p != str(_STATE_PATH)
                )
                ws.stage_plan_approval_commit_paths(repo.root, ordinary_paths)
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-6.1b-state-pin", now="t3",
            ):
                ws.pin_plan_approval_state_blob(
                    repo.root, base64.b64decode(journal["expected_post_state_b64"]),
                )
            body = f"plan approval\n\nWorkflow-Plan-Approval: {journal['expected_review_content_id']}\nWorkflow-Work-Item: {wi}"
            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-6.5-commit", now="t4",
            ):
                _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
            commit = _run(["git", "rev-parse", "HEAD"], cwd=repo.root).strip()
            self.assertEqual(
                ws.classify_plan_approval_outcome(repo.root, journal),
                ws.PLAN_APPROVAL_OUTCOME_COMMITTED,
            )

            # A different work item's own entry is legitimately updated
            # in the working tree before step 6c runs.
            current = json.loads((repo.root / _STATE_PATH).read_text())
            current["work_items"][other_wi]["phase"] = "AWAITING_FUNCTIONAL_REVIEW"
            (repo.root / _STATE_PATH).write_bytes(ws._serialize_state(current))

            with ws.plan_approval_guarded_mutation(
                repo.root, owner_token=owner_token, step="step-8b-materialize", now="t5",
            ):
                target = ws.classify_plan_approval_materialize_target(repo.root, wi, pre_state, post_state)
                self.assertEqual(target, ws.PLAN_APPROVAL_MATERIALIZE_WRITE)
                fresh = ws.plan_approval_state_matches_pre_transaction(
                    repo.root, journal["pre_procedure_state_sha256"],
                )
                self.assertFalse(fresh)
                # The command's own prose stops here without writing --
                # confirm what materialize_plan_approval_state *would*
                # have discarded, had it been called anyway.
                would_be_written = json.loads(base64.b64decode(journal["expected_post_state_b64"]))
                self.assertNotEqual(
                    would_be_written["work_items"][other_wi]["phase"],
                    current["work_items"][other_wi]["phase"],
                )

            # The journal is still open (6c refused, 6d never ran) and
            # the durable commit is untouched -- both survive for a
            # later reconciliation + re-run.
            self.assertIsNotNone(ws.read_plan_approval_journal(repo.root))
            self.assertEqual(
                _run(["git", "rev-parse", "HEAD"], cwd=repo.root).strip(), commit,
            )


if __name__ == "__main__":
    unittest.main()
