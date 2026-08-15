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

import hashlib
import json
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
                review_content_manifest={"paths": []}, now="t0",
            )
            state = ws.apply_plan_approval(state, "wi", plan_record, now="t0")
            plan_commit = repo.commit_files(
                "plan approved", {state_path: json.dumps(state)},
                trailers={"Workflow-Plan-Approval": "plan-content-1", "Workflow-Work-Item": "wi"},
            )

            # 2. Checkpoint A.
            state = ws.transition_checkpoint_in_progress(state, "wi", "A", plan_commit, now="t1")
            state = ws.complete_checkpoint(state, "wi", "A", registry, now="t2")
            ck_a_commit = repo.commit_files(
                "checkpoint A", {state_path: json.dumps(state), "src/a.txt": "a\n"},
                trailers={"Workflow-Checkpoint": "A", "Workflow-Work-Item": "wi"},
            )

            # 3. Checkpoint B -- the last one, so this also flips phase to
            # SELF_REVIEWING_IMPLEMENTATION as part of the same write.
            state = ws.transition_checkpoint_in_progress(state, "wi", "B", ck_a_commit, now="t3")
            state = ws.complete_checkpoint(state, "wi", "B", registry, now="t4")
            ck_b_commit = repo.commit_files(
                "checkpoint B", {state_path: json.dumps(state), "src/b.txt": "b\n"},
                trailers={"Workflow-Checkpoint": "B", "Workflow-Work-Item": "wi"},
            )
            self.assertEqual(state["work_items"]["wi"]["phase"], "SELF_REVIEWING_IMPLEMENTATION")

            # 4. Technical-approval commit.
            impl_record = ws.build_approval_record(
                basis="USER_OVERRIDE", stage="implementation", user_confirmation="wi implementation",
                reviewed_bundle_id="bundle-impl-1", approved_review_content_id="impl-content-1",
                review_content_manifest={"paths": []}, reviewed_content_commit=ck_b_commit, now="t5",
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
    "milestone-plan.md": "082a444fac6193493af7da5307f3008d6684a19456fb57af297dbfa21d4082a6",
    "milestone-implement.md": "9d8453671756ba7a60e18d280b59d66836f216b21651fe78a5682e58bc1e8add",
    "approve-review.md": "384c3197c34798b72fcc988421db8b64d8c84697c6565658957fd5a76150d08f",
    "accept-milestone.md": "b20355327e560b09518e5305979bc844acb989a0b11fd6a6be00a9d750a9e9f4",
    "prepare-functional-review.md": "37844421e1c63917d7970c8cdd00d6945d06c3a168eb7fb3be51205deb35fee3",
    "apply-plan-review.md": "f79febd0ef769d72bc3ec065a55a536203b533d23db4cc3d9b7854b8d49b8834",
    "apply-implementation-review.md": "96772a51cfe315560983c573483bf66ccf38793e31aedb1b5cbc9003f1367234",
    "review-plan.md": "4cc5a74389c9714cf23fb7ed51bc3da5623bf99268ce118c1c2d59b995519c89",
    "record-manual-plan-review.md": "0ce7893e968c412c41e3aca1afdfb8dca7d0060bbd0f38c2a6ec15c7b32669da",
    "bootstrap-workflow-v2.md": "da4eb9c58ef521402cb84a583403ba1746239192f8d38ebf14896a6bb49622fd",
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
    """

    def test_milestone_plan_v1_steps_equal_pre_v21_base_commit_modulo_known_renames(self):
        current = _command_text("milestone-plan.md")
        steps = _extract_numbered_steps(current)
        # Step 6 excluded: D-Fingerprint-Generalization's required
        # <work_item_id> argument is a genuine v1-visible behavior change,
        # asserted separately below, not folded into this byte-equality
        # comparison (see this class's own docstring).
        normalized = "".join(
            _normalize_v1_path_variables(_strip_bracketed_2_1_bullets(steps[n]))
            for n in "123457"
        )
        self.assertEqual(
            hashlib.sha256(normalized.encode()).hexdigest(),
            "c9d325c869a4395796d855bbf52d73a6c47061ce040593bda3c70c7793704dbf",
        )

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
        # byte-equality comparison.
        current = _command_text("apply-plan-review.md")
        steps = _extract_numbered_steps(current)
        normalized = "".join(_normalize_v1_path_variables(steps[n]) for n in "2346")
        self.assertEqual(
            hashlib.sha256(normalized.encode()).hexdigest(),
            "06f1c04f0593946201dfcab1bad03dd3930e82037767b885a1c38099e6c9483c",
        )

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
        # Sanity: the extraction itself found all 60 rows (WFR-01 through
        # WFR-60, the current count as of revision 27's D-Scoped-
        # Remediation-Acceptance -- WFR-53 through WFR-60 added/amended by
        # revisions 22/23/25 on top of the original 52), not an empty or
        # partial set (which would make the per-row loop below vacuous).
        self.assertEqual(len(table_rows), 60)
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
                feedback_role="manual_external_plan_review",
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


if __name__ == "__main__":
    unittest.main()
