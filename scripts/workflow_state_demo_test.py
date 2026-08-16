#!/usr/bin/env python3
"""Explicit integration/demonstration test for workflow_state.py against
this repository's real state, mirroring
workflow_fingerprint_demo_test.py's split from the hermetic suite.

Not part of the hermetic unit suite (workflow_state_test.py); treated as
an opt-in integration check, not wired into CI, for the same reason
workflow_fingerprint_demo_test.py is CI-exempt (GPT-R9-002): it depends on
a specific historical base commit, so an unrelated later product PR would
otherwise fail it purely because the diff from that fixed base grows.

Run: python3 scripts/workflow_state_demo_test.py
"""

from __future__ import annotations

import json
import re
import subprocess
import unittest
from pathlib import Path

import workflow_fingerprint as fingerprint
import workflow_state as ws

BASE_COMMIT = "162154d3e5e10eb65e109833acae4b4fb01fc5d6"
WORK_ITEM_ID = "workflow-v2-1-core"


def _repo_root() -> Path:
    return Path(
        subprocess.run(
            ["git", "rev-parse", "--show-toplevel"], check=True,
            capture_output=True, text=True,
        ).stdout.strip()
    )


def _parse_checkpoint_registry_table(repo_root: Path) -> list[dict]:
    """Parses the '## Checkpoint registry' Markdown table in
    WORKFLOW_V2_PLAN.md into one dict per row, over exactly the five
    columns the table carries a value for besides id/Driven-by: name,
    depends_on_cell (raw, un-mapped), complexity_cell (raw, un-mapped),
    session_target. Used by item 72's conformance test (WF8c clause (r))
    and its own teeth-check below."""
    plan_path = repo_root / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md"
    lines = plan_path.read_text().splitlines()
    section_idx = next(
        (i for i, line in enumerate(lines) if line.startswith("## Checkpoint registry")), None,
    )
    assert section_idx is not None, "WORKFLOW_V2_PLAN.md has no '## Checkpoint registry' section"
    header_idx = next(
        (i for i in range(section_idx, len(lines)) if lines[i].startswith("| ID |")), None,
    )
    assert header_idx is not None, "no '| ID | ...' table header found under 'Checkpoint registry'"
    separator_idx = header_idx + 1
    assert lines[separator_idx].startswith("|---"), (
        f"expected a Markdown table separator row after the header, got: {lines[separator_idx]!r}"
    )
    rows = []
    i = separator_idx + 1
    while i < len(lines) and lines[i].startswith("|"):
        cells = lines[i].split("|")
        assert len(cells) == 8, (
            f"line {i + 1}: expected the 6-column '| ID | Name | Depends on | "
            f"Complexity | Session target | Driven by |' shape (7 pipes), got "
            f"{len(cells) - 1} pipes -- a cell may contain an unescaped '|': {lines[i]!r}"
        )
        rows.append({
            "id": cells[1].strip(),
            "name": cells[2].strip(),
            "depends_on_cell": cells[3].strip(),
            "complexity_cell": cells[4].strip(),
            "session_target": cells[5].strip(),
        })
        i += 1
    assert rows, "no data rows parsed from the Checkpoint registry table"
    return rows


def _registry_row_mismatches(row: dict, entry: dict) -> list[tuple[str, str, object, object]]:
    """Compares one parsed table row against its registry JSON checkpoint
    entry over the five fields WF8c clause (r) names, each under its own
    stated mapping rule. Returns (checkpoint_id, field, table_value,
    json_value) for every field that diverges; completion_obligations is
    deliberately not compared -- the table carries no column for it."""
    mismatches = []

    if row["name"] != entry["name"]:
        mismatches.append((row["id"], "name", row["name"], entry["name"]))

    if row["depends_on_cell"] == "none":
        table_depends_on = []
    else:
        table_depends_on = [d.strip() for d in row["depends_on_cell"].split(",")]
    if table_depends_on != entry["depends_on"]:
        mismatches.append((row["id"], "depends_on", table_depends_on, entry["depends_on"]))

    complexity_match = re.match(r"^(\d+)", row["complexity_cell"])
    assert complexity_match, f"{row['id']}: Complexity cell {row['complexity_cell']!r} has no leading integer"
    table_complexity = int(complexity_match.group(1))
    if table_complexity != entry["complexity"]:
        mismatches.append((row["id"], "complexity", table_complexity, entry["complexity"]))

    if row["session_target"] != entry["session_target"]:
        mismatches.append((row["id"], "session_target", row["session_target"], entry["session_target"]))

    return mismatches


class TestAgainstRealRepository(unittest.TestCase):
    def test_wf0_and_wf1a_are_discovered_via_real_trailer_search(self):
        repo_root = _repo_root()
        discovered = ws.discover_checkpoint_commits(repo_root, WORK_ITEM_ID, BASE_COMMIT)
        self.assertIn("WF0", discovered)
        self.assertEqual(discovered["WF0"], "8f76175348d0f63e61f6c1a9997b5004a27430fe")
        # WF1a's own commit is what this test suite is committed inside of.
        self.assertIn("WF1a", discovered)

    def test_all_seventeen_checkpoints_resolve_with_wf8b_canonical(self):
        """Item 39's real-repository proof: `WF8b` carries the
        `Workflow-Checkpoint: WF8b` trailer on sixteen distinct commits
        (this work item's own continued-scope WF8b round left many
        commits along the way, all first-parent ancestors of `HEAD`), so
        filter (1) alone cannot disambiguate it -- only the role-specific
        verification tie-break (`_checkpoint_commit_claims_complete`) can,
        by finding the one commit whose own committed
        `WORKFLOW_STATE.json` records `WF8b` as `COMPLETE` for
        `workflow-v2-1-core`. All seventeen registered checkpoints resolve
        to exactly one commit each, and `WF8b` resolves to `f37c4e0`, the
        real completion commit (`feat(wf8b): S17 -- restore active
        pointer, remove dry-run entries, complete WF8b`)."""
        repo_root = _repo_root()
        discovered = ws.discover_checkpoint_commits(repo_root, WORK_ITEM_ID, BASE_COMMIT)
        registry = json.loads((repo_root / "docs/ai-workflow/registry/workflow-v2-1-core-registry.json").read_text())
        registered_ids = [entry["id"] for entry in registry["checkpoints"]]
        self.assertEqual(len(registered_ids), 17)
        for checkpoint_id in registered_ids:
            self.assertIn(checkpoint_id, discovered, f"{checkpoint_id} did not resolve")
        self.assertEqual(discovered["WF8b"], "f37c4e04f2358c8d1d5dec333b43538e2f087d15")

    def test_real_config_file_is_schema_valid(self):
        repo_root = _repo_root()
        config = ws.load_config(repo_root)
        ws.validate_config(config)

    def test_real_registry_is_topologically_ordered(self):
        repo_root = _repo_root()
        registry = json.loads((repo_root / "docs/ai-workflow/registry/workflow-v2-1-core-registry.json").read_text())
        ws.validate_registry_topological_order(registry)

    def test_072_generated_markdown_registry_view_row_order_matches_json_array_order(self):
        """Missing-test item 72 (OPUS-R10-009), D-Selection point 3: the
        'Checkpoint registry' Markdown table in WORKFLOW_V2_PLAN.md is a
        generated, human-readable view of the registry JSON and must
        never drift from it -- a hand-edit, a regeneration bug, or a
        readability reordering of the view could otherwise silently break
        rule 2's determinism guarantee. Parses the real table's rows and
        the real registry JSON's checkpoints array, asserting id order
        matches (the original item 72 scope) and, per WF8c clause (r)
        (OPUS-R113-001/-002, restating GPT-R112-001), extends the check
        into a full-field comparison over exactly the five columns the
        table carries: id, name, depends_on, complexity, session_target.
        completion_obligations is deliberately excluded -- the table has
        no column for it, so there is nothing on the view side to compare
        against; it remains registry-only metadata for
        WFO-LEDGER-COVERAGE/WFR-68's obligation-resolution machinery."""
        repo_root = _repo_root()
        rows = _parse_checkpoint_registry_table(repo_root)
        registry = json.loads((repo_root / "docs/ai-workflow/registry/workflow-v2-1-core-registry.json").read_text())
        json_checkpoints = registry["checkpoints"]

        row_ids = [row["id"] for row in rows]
        json_ids = [entry["id"] for entry in json_checkpoints]
        self.assertEqual(
            row_ids, json_ids,
            "the Checkpoint registry Markdown table's row order has drifted from "
            "the registry JSON's checkpoints array order",
        )

        registry_by_id = {entry["id"]: entry for entry in json_checkpoints}
        mismatches = []
        for row in rows:
            mismatches.extend(_registry_row_mismatches(row, registry_by_id[row["id"]]))
        self.assertEqual(
            mismatches, [],
            f"{len(mismatches)} field(s) diverged between the Checkpoint registry "
            f"Markdown table and the registry JSON (checkpoint, field, table value, "
            f"json value): {mismatches}",
        )

    def test_072_full_field_comparison_catches_a_real_divergence_not_vacuously_true(self):
        """The positive test above proves nothing if a table/JSON mismatch
        can never actually be detected. Runs the real
        _registry_row_mismatches comparison used by the positive test
        against the real WF0 table row paired with a deliberately
        tampered copy of the real WF0 registry entry, once per compared
        field, and confirms each tamper is individually flagged."""
        repo_root = _repo_root()
        rows = _parse_checkpoint_registry_table(repo_root)
        wf0_row = next(row for row in rows if row["id"] == "WF0")
        registry = json.loads((repo_root / "docs/ai-workflow/registry/workflow-v2-1-core-registry.json").read_text())
        wf0_entry = next(e for e in registry["checkpoints"] if e["id"] == "WF0")

        self.assertEqual(_registry_row_mismatches(wf0_row, wf0_entry), [])

        tampered = dict(wf0_entry, name=wf0_entry["name"] + " tampered")
        fields = {m[1] for m in _registry_row_mismatches(wf0_row, tampered)}
        self.assertEqual(fields, {"name"})

        tampered = dict(wf0_entry, depends_on=["WF4a-i"])
        fields = {m[1] for m in _registry_row_mismatches(wf0_row, tampered)}
        self.assertEqual(fields, {"depends_on"})

        tampered = dict(wf0_entry, complexity=wf0_entry["complexity"] + 1)
        fields = {m[1] for m in _registry_row_mismatches(wf0_row, tampered)}
        self.assertEqual(fields, {"complexity"})

        tampered = dict(wf0_entry, session_target=wf0_entry["session_target"] + "0")
        fields = {m[1] for m in _registry_row_mismatches(wf0_row, tampered)}
        self.assertEqual(fields, {"session_target"})

    def test_real_registry_and_mapping_have_full_bidirectional_coverage(self):
        repo_root = _repo_root()
        registry = json.loads((repo_root / "docs/ai-workflow/registry/workflow-v2-1-core-registry.json").read_text())
        mapping = json.loads((repo_root / "docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json").read_text())
        ws.validate_registry_mapping_coverage(registry, mapping)

    def test_real_state_file_is_schema_valid_against_the_real_registry(self):
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        registry = json.loads((repo_root / "docs/ai-workflow/registry/workflow-v2-1-core-registry.json").read_text())
        ws.validate_state(state, registry=registry)
        # GPT-R31-003: also exercise the whole-state plan-revision-mirror
        # check against every real work item's own registry_path, not
        # only workflow-v2-1-core's. Also serves as GPT-R33-002's required
        # "all existing repository work items pass the tracked-path check"
        # case: every real work item's registry_path is a Git-tracked
        # file in this actual repository, so this call only stays green
        # under the stricter tracked-file validator if that remains true.
        ws.validate_state(state, registry=registry, repo_root=repo_root)

    def test_real_state_file_checkpoint_completions_are_reachable(self):
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        work_item = state["work_items"][WORK_ITEM_ID]
        ws.verify_checkpoint_completions(work_item, repo_root, work_item["base_commit"])

    def test_real_state_file_current_plan_approval_is_discoverable_and_matches_manifest(self):
        """`plan_approval.approved_review_content_id` was legitimately
        advanced past WF0's original approval by 2da0b66 ("remediate stale
        plan_approval after Milestone 8 merge") -- a later commit that is
        not WF0 and carries no Workflow-Checkpoint trailer of its own. The
        durable invariant is discoverability via the generalized
        approval-trailer search (WF4a-iii), not literal identity with
        WF0's commit: this asserts the current identifier resolves to
        exactly one reachable Workflow-Plan-Approval commit for
        workflow-v2-1-core (discover_plan_approval_commit raises on
        genuine ambiguity rather than returning one), and that commit's
        own trailer and tree content match the state file's approval
        metadata and manifest."""
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        work_item = state["work_items"][WORK_ITEM_ID]
        approval = work_item["plan_approval"]

        discovered = ws.discover_plan_approval_commit(
            repo_root, WORK_ITEM_ID, approval["approved_review_content_id"], BASE_COMMIT,
        )
        self.assertIsNotNone(
            discovered,
            "current approved_review_content_id is not reachable via the "
            "generalized Workflow-Plan-Approval trailer search",
        )

        body = subprocess.run(
            ["git", "log", "-1", "--format=%B", discovered],
            cwd=repo_root, check=True, capture_output=True, text=True,
        ).stdout
        self.assertIn(f"Workflow-Plan-Approval: {approval['approved_review_content_id']}", body)
        self.assertIn(f"Workflow-Work-Item: {WORK_ITEM_ID}", body)

        # Finding B: the full `reviewed_bundle_id` is not a normative
        # commit-trailer/body requirement (Revision 80 specifies neither a
        # bundle-id trailer nor the full id in free-text prose -- prose
        # may legitimately truncate it, as this repository's real approval
        # commit does). The durable source of truth is the approval
        # commit's own *committed* WORKFLOW_STATE.json, not its free-form
        # message -- verify against that instead.
        committed_state = ws._read_json_at_commit_or_empty(
            repo_root, discovered, "docs/ai-workflow/WORKFLOW_STATE.json",
        )
        committed_approval = committed_state["work_items"][WORK_ITEM_ID]["plan_approval"]
        self.assertEqual(committed_approval["reviewed_bundle_id"], approval["reviewed_bundle_id"])

        for entry in approval["review_content_manifest"]:
            blob = subprocess.run(
                ["git", "rev-parse", f"{discovered}:{entry['path']}"],
                cwd=repo_root, check=True, capture_output=True, text=True,
            ).stdout.strip()
            self.assertEqual(
                blob, entry["blob"],
                f"{entry['path']} blob at the discovered approval commit {discovered} "
                "does not match plan_approval.review_content_manifest",
            )

    def test_reviewed_implementation_head_matches_live_head_right_now(self):
        """GPT-R31-001: `record_bundle_generation`'s own state write must
        not itself be a separate git commit -- that would move live HEAD
        past the very commit it records, making `/approve-review`'s exact
        `work_item["reviewed_implementation_head"] == <live HEAD SHA>`
        freshness check (`approve-review.md` step 1) permanently
        unsatisfiable without a further, unrelated commit. Left
        uncommitted (an ordinary, allowed dirty state --
        `WORKFLOW_STATE.json` dirtiness never blocks
        `technical_approval_gate_reachable`, by construction of the
        implementation-stage classification), the two must agree the
        moment this state was written, proven here directly against the
        real repository rather than asserted in prose. This is a
        point-in-time proof, not a permanent invariant: it will correctly
        stop holding once `/approve-review implementation` commits its
        own approval trailer on top -- at that point `reviewed_implementation_head`
        is a fixed historical record and live HEAD has moved past it by
        design (`WFR-22`)."""
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        item = state["work_items"][WORK_ITEM_ID]
        live_head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=repo_root, check=True, capture_output=True, text=True,
        ).stdout.strip()
        self.assertIsNotNone(
            item["reviewed_implementation_head"],
            "no bundle has ever been generated for this work item yet",
        )
        self.assertEqual(
            item["reviewed_implementation_head"], live_head,
            "reviewed_implementation_head must equal live HEAD while the "
            "state write recording it remains uncommitted",
        )

    def test_historical_wf0_plan_approval_remains_independently_discoverable(self):
        """WF0's own original Workflow-Plan-Approval identifier was
        superseded (not invalidated) by the later remediation commit
        above -- it must remain independently discoverable under its own
        identifier, without requiring it to equal the current
        plan_approval.approved_review_content_id (mirrors
        test_wf0_and_wf1a_are_discovered_via_real_trailer_search's
        checkpoint-trailer counterpart, for the approval-trailer
        search)."""
        repo_root = _repo_root()
        all_approvals = ws.discover_approval_commits(
            repo_root, "Workflow-Plan-Approval", WORK_ITEM_ID, BASE_COMMIT,
        )
        original_wf0_review_content_id = (
            "e85533a91d40cd43d6066f0435e90d7b3a94e64e50d58aceec3e4363e60dd030"
        )
        self.assertEqual(
            all_approvals.get(original_wf0_review_content_id),
            "8f76175348d0f63e61f6c1a9997b5004a27430fe",
        )

    def test_real_implementing_entry_is_reachable_at_current_head(self):
        """D-Approval-Commits' full IMPLEMENTING entry condition, exercised
        against this work item's own real history: the plan-approval
        commit (WF0) is a first-parent ancestor of current HEAD, and the
        plan-stage projection is unchanged by every checkpoint commit
        since (missing-test item 9, real-repository half)."""
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        work_item = state["work_items"][WORK_ITEM_ID]
        self.assertTrue(ws.implementing_entry_reachable(repo_root, work_item, work_item["base_commit"]))

    def test_real_plan_approval_is_current(self):
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        work_item = state["work_items"][WORK_ITEM_ID]
        self.assertTrue(
            ws.approval_is_current(repo_root, work_item, stage="plan", base_commit=work_item["base_commit"])
        )

    def test_real_implementation_stage_classification_has_no_unclassified_dirty_path(self):
        """any_protected_path_dirty fails closed on an unclassified dirty
        path -- run here against whatever this working tree's real
        uncommitted state happens to be, proving the real
        artifact-declarations file classifies it either way (missing-test
        item 16, real-repository half; the hermetic half is
        TestProtectedPathDirty in workflow_state_test.py)."""
        repo_root = _repo_root()
        protected_paths, protected_prefixes, excluded_paths, excluded_prefixes = (
            ws.fingerprint.load_implementation_stage_classification(repo_root)
        )
        ws.any_protected_path_dirty(
            repo_root, protected_paths, protected_prefixes, excluded_paths, excluded_prefixes
        )  # must not raise


class TestLegacyImportAgainstRealMilestone8(unittest.TestCase):
    """WF-M8a's own real-repository check: Milestone 8's actual, already-
    integrated history satisfies D-Legacy's branch-reconciliation
    precondition, and its backfilled approved_review_content_id (recorded
    in docs/ai-workflow/WORKFLOW_STATE.json's work_items["milestone-8"])
    reproduces exactly from milestone-8-artifacts.json's declarations *as
    WF-M8a originally authored them* (commit WF_M8A_COMMIT below), scoped
    to Milestone 8's own base_commit..reviewed_content_commit -- never
    workflow-v2-1-core's own base_commit or artifacts file. WF-M8b later
    widens this same file's excluded sets for its own, separate,
    non-hash-based promotion-time freshness check
    (any_protected_path_changed_since) -- that widening deliberately
    changes what recomputing from the file's *current* content would
    produce (the full classification config is embedded in the hash), so
    this test pins the exact historical commit rather than reading the
    live file, to keep proving the stored ID's provenance without being
    broken by that later, legitimate widening."""

    BASE_COMMIT = "2d09ec02252848124d0e1accfbefb57dc8561872"
    REVIEWED_CONTENT_COMMIT = "dc4381a348c114ec4967174c4e6a76ac00b1a537"
    ARTIFACTS_PATH = "docs/ai-workflow/registry/milestone-8-artifacts.json"
    WF_M8A_COMMIT = "2baf7bcfdd12006335e65f7fbae41e5d1fa4a8e3"

    def test_reviewed_commit_is_reachable_and_active_milestone_confirms_acceptance(self):
        repo_root = _repo_root()
        ws.verify_legacy_branch_reconciliation(
            repo_root, reviewed_content_commit=self.REVIEWED_CONTENT_COMMIT,
            required_active_milestone_substring="accepted and closed",
        )  # must not raise

    def test_backfilled_review_content_id_reproduces_from_declarations_as_originally_authored(self):
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        milestone_8 = state["work_items"]["milestone-8"]
        approval = milestone_8["technical_approval"]
        self.assertEqual(approval["basis"], "LEGACY_V1")
        self.assertEqual(approval["reviewed_content_commit"], self.REVIEWED_CONTENT_COMMIT)
        original_declarations = json.loads(
            subprocess.run(
                ["git", "show", f"{self.WF_M8A_COMMIT}:{self.ARTIFACTS_PATH}"],
                cwd=repo_root, check=True, capture_output=True, text=True,
            ).stdout
        )
        recomputed, _ = fingerprint.compute_review_content_id_implementation_stage_at_commit(
            repo_root, milestone_8["base_commit"], self.REVIEWED_CONTENT_COMMIT,
            "product", "milestone-8",
            original_declarations["protected_paths"], original_declarations["protected_prefixes"],
            original_declarations["excluded_paths"], original_declarations["excluded_prefixes"],
        )
        self.assertEqual(recomputed, approval["approved_review_content_id"])

    def test_milestone_8_entry_is_dormant_not_active(self):
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        self.assertEqual(state["work_items"]["milestone-8"]["phase"], "LEGACY_READY")
        self.assertNotEqual(state.get("active_work_item_id"), "milestone-8")


class TestLegacyPromotionAgainstRealMilestone8(unittest.TestCase):
    """WF-M8b's own real-repository check: `promote_legacy_work_item`
    succeeds against the real dormant `milestone-8` entry as of this
    commit -- both the branch-reconciliation re-check and the
    implementation-stage freshness recomputation (against milestone-8's
    own real `milestone-8-artifacts.json`, never `workflow-v2-1-core`'s)
    pass on real repository content. Exercised read-only: the returned
    state is never persisted back to `WORKFLOW_STATE.json`, so this test
    only proves adoption is currently reachable -- it is not itself an
    adoption."""

    ARTIFACTS_PATH = Path("docs/ai-workflow/registry/milestone-8-artifacts.json")

    def test_promotion_succeeds_against_the_real_dormant_entry(self):
        repo_root = _repo_root()
        state_path = repo_root / "docs/ai-workflow/WORKFLOW_STATE.json"
        state = json.loads(state_path.read_text())
        new_state = ws.promote_legacy_work_item(
            state, repo_root, work_item_id="milestone-8",
            required_active_milestone_substring="accepted and closed",
            artifacts_path=self.ARTIFACTS_PATH, now="demo-only, never persisted",
        )
        entry = new_state["work_items"]["milestone-8"]
        self.assertEqual(new_state["active_work_item_id"], "milestone-8")
        self.assertEqual(entry["governing_workflow_version"], "2.1")
        self.assertEqual(entry["phase"], "AWAITING_FUNCTIONAL_REVIEW")
        self.assertEqual(entry["technical_approval"], state["work_items"]["milestone-8"]["technical_approval"])
        # Read-only: the real on-disk file is provably untouched.
        self.assertEqual(json.loads(state_path.read_text()), state)


class TestCheckpointOriginationAgainstRealRepository(unittest.TestCase):
    """WF8b's `D-Checkpoint-Ownership` origination-reference slice
    (`checkpoint_origination_provable`), checked read-only against this
    repository's own real, already-committed defect: the
    `workflow-v2-1-core` Revision-80 plan-approval commit (`8f8d878`)
    swept `v2-1-dry-run`'s dirty `S-CP3` `IN_PROGRESS` delta into `HEAD`
    as a side effect of staging the whole plan-stage protected surface,
    recorded in commit `25246a5`. This was the concrete case the S14/S15
    dry-run scenarios needed an explicit takeover for; S14/S15 have since
    run for real (2026-08-15) and carried `S-CP3` through to `COMPLETE`,
    releasing the claim the takeover published -- the origination defect
    itself remains permanently reproducible (it scans fixed history), but
    the claim- and status-dependent assertions below reflect that
    completion rather than the interrupted `IN_PROGRESS` state."""

    def test_v2_1_dry_run_s_cp3_origination_is_unprovable(self):
        repo_root = _repo_root()
        with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
            ws.checkpoint_origination_provable(repo_root, "v2-1-dry-run", "S-CP3")
        evidence = ctx.exception.evidence
        self.assertEqual(evidence["route"], "observed")
        self.assertEqual(evidence["status"], "IN_PROGRESS")
        self.assertEqual(evidence["commit"], "8f8d878c0985da96d9b462703b6c88ec5b3ab07b")

    def test_a_never_started_checkpoint_id_admits(self):
        repo_root = _repo_root()
        result = ws.checkpoint_origination_provable(
            repo_root, "workflow-v2-1-core", "NO-SUCH-CHECKPOINT-ID-EVER-USED",
        )
        self.assertEqual(result["decision"], "admit")

    def test_checkpoint_origination_provable_still_refuses_for_s_cp3_permanently(self):
        """`checkpoint_origination_provable` scans
        `origination_reference_commits`, a fixed slice of already-committed
        history -- so the real, already-committed defect (`8f8d878`, which
        recorded `v2-1-dry-run`'s `S-CP3` `IN_PROGRESS` as a side effect of
        an unrelated `workflow-v2-1-core` plan-approval commit) refuses
        unconditionally and permanently, regardless of `S-CP3`'s own
        *current* status: this repository's WF8b S15 session (2026-08-15)
        completed `S-CP3` for real (`Workflow-Checkpoint: S-CP3`,
        `Workflow-Work-Item: v2-1-dry-run`) and released the claim the
        prior session's takeover had published, and the refusal is
        unchanged by either fact, since this function only ever looks
        backward. Supersedes this test's prior form, which additionally
        asserted claim-record byte-identity across the call -- that
        assertion no longer applies now that no claim record exists at
        all (S-CP3 is `COMPLETE`, not `IN_PROGRESS`); see
        `test_adopt_claim_refuses_as_not_in_progress_once_s_cp3_is_complete`
        for the claim-side consequence. Read-only: verified by an explicit
        before/after `git status --short` comparison."""
        repo_root = _repo_root()
        before = subprocess.run(
            ["git", "status", "--short"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout
        with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
            ws.checkpoint_origination_provable(repo_root, "v2-1-dry-run", "S-CP3")
        evidence = ctx.exception.evidence
        self.assertEqual(evidence["route"], "observed")
        self.assertEqual(evidence["commit"], "8f8d878c0985da96d9b462703b6c88ec5b3ab07b")
        after = subprocess.run(
            ["git", "status", "--short"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout
        self.assertEqual(before, after)

    def test_adopt_claim_refuses_as_not_in_progress_once_v2_1_dry_run_is_removed(self):
        """`adopt_claim`'s own first precondition -- this worktree's local
        `WORKFLOW_STATE.json` must record `checkpoint_id` `IN_PROGRESS` --
        is checked *before* `checkpoint_origination_provable` ever runs
        (`adopt_claim`'s own docstring, step 1 before step 3). S17
        (`f37c4e0`, 2026-08-15) removed `v2-1-dry-run`'s dry-run entries
        from `WORKFLOW_STATE.json` entirely -- it is no longer a
        `COMPLETE` entry, it is *absent* -- so that precondition now reads
        a `None` status rather than `COMPLETE`, and `adopt_claim` still
        refuses with `CheckpointNotInProgressLocallyError`, just with a
        different reported status. Supersedes this test's S15-era form
        (`... once s_cp3_is_complete`, asserting `"COMPLETE"` in the
        refusal message) -- that assertion is now false, not stale
        evidence to preserve, since S17's cleanup removed the item rather
        than leaving it completed. Read-only: verified by an explicit
        before/after `git status --short` comparison and by confirming no
        claim file exists before or after."""
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        self.assertNotIn("v2-1-dry-run", state["work_items"])
        claim_file = ws.claim_path(repo_root, "v2-1-dry-run")
        self.assertFalse(claim_file.exists())
        before = subprocess.run(
            ["git", "status", "--short"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout
        with self.assertRaises(ws.CheckpointNotInProgressLocallyError) as ctx:
            ws.adopt_claim(repo_root, "v2-1-dry-run", "S-CP3", now="demo")
        self.assertIn("status: None", str(ctx.exception))
        after = subprocess.run(
            ["git", "status", "--short"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout
        self.assertEqual(before, after)
        self.assertFalse(claim_file.exists())
        self.assertIsNone(ws.resolve_claim(repo_root, "v2-1-dry-run"))

    def test_no_stale_claim_survives_v2_1_dry_run_removal(self):
        """S17 removed `v2-1-dry-run` from live `work_items` entirely,
        which also means there is no `work_item` dict left to drive
        `select_next_checkpoint`/`resolve_checkpoint_ownership` against
        for this id anymore -- the claim-side read-only invariant that
        still applies is simply that no claim file was left behind by the
        removal (`release_checkpoint` already released S-CP3's claim
        during WF8b S15, before S17 removed the item itself), so there is
        nothing for a fresh session to adopt or resolve ownership of.
        Supersedes this test's S15-era form
        (`..._reaches_no_checkpoint_once_s_cp3_is_complete`, which read
        `state["work_items"]["v2-1-dry-run"]` and drove
        `resolve_checkpoint_ownership` against it) -- that lookup now
        raises `KeyError` since the entry no longer exists, so the
        checkpoint-selection assertions no longer apply; the claim/
        read-only assertions they were paired with do still apply and are
        preserved here. Read-only: verified by an explicit before/after
        `git status --short` comparison."""
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        self.assertNotIn("v2-1-dry-run", state["work_items"])
        claim_file = ws.claim_path(repo_root, "v2-1-dry-run")
        before = subprocess.run(
            ["git", "status", "--short"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout
        self.assertFalse(claim_file.exists())
        self.assertIsNone(ws.resolve_claim(repo_root, "v2-1-dry-run"))
        after = subprocess.run(
            ["git", "status", "--short"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout
        self.assertEqual(before, after)

    def test_bootstrap_driver_never_calls_this_slice(self):
        """Scope-boundary regression: `/bootstrap-workflow-v2` derives
        completion from commit trailers alone and never enters
        `[2.1 step 1]` (D-Checkpoint-Ownership's own "Scope boundaries"
        bullet), so this slice must stay unreachable from it -- confirmed
        structurally, not merely by convention. `workflow-v2-1-core`'s own
        `WF8b` checkpoint *is* observed `IN_PROGRESS` in committed history
        (five commits, per the plan's "Scope boundaries" text) and would
        itself refuse this check -- proving the point only if the
        bootstrap driver never reaches it."""
        repo_root = _repo_root()
        command_text = (repo_root / ".claude/commands/bootstrap-workflow-v2.md").read_text()
        self.assertNotIn("checkpoint_origination_provable", command_text)
        self.assertNotIn("resolve_checkpoint_ownership", command_text)
        with self.assertRaises(ws.CheckpointOriginationUnprovableError):
            ws.checkpoint_origination_provable(repo_root, "workflow-v2-1-core", "WF8b")

    def test_identity_reference_admits_refuses_this_repositorys_own_reused_work_item_id(self):
        """WFR-66's identity-query enforcement against real history: the
        plan's own "one concrete instance" fact -- `workflow-v2-1-core`
        as a `work_item_id` has, obviously, appeared in every commit that
        ever touched its own `WORKFLOW_STATE.json` entry -- so a fresh
        attempt to *create* a work item under this same id must be
        permanently refused, on the identity-existence query alone,
        before `route_work_item` ever reaches the terminal-phase check.
        Read-only."""
        repo_root = _repo_root()
        before = subprocess.run(
            ["git", "status", "--short"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout
        with self.assertRaises(ws.WorkItemIdReusedError):
            ws.identity_reference_admits(repo_root, WORK_ITEM_ID)
        after = subprocess.run(
            ["git", "status", "--short"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout
        self.assertEqual(before, after)

    def test_identity_reference_admits_refuses_this_repositorys_own_wf8b_pair(self):
        """The same fact `checkpoint_origination_provable` already proves
        for `workflow-v2-1-core`/`WF8b` (observed `IN_PROGRESS` at five
        real commits) also makes it a decidably-observed pair for the
        *identity* query, so a future registry that tried to reintroduce
        a retired `WF8b` id would be refused the identical way."""
        repo_root = _repo_root()
        with self.assertRaises(ws.CheckpointIdReusedError):
            ws.identity_reference_admits(repo_root, WORK_ITEM_ID, "WF8b")

    def test_identity_reference_admits_admits_a_genuinely_unused_id(self):
        repo_root = _repo_root()
        result = ws.identity_reference_admits(repo_root, "an-id-never-used-anywhere-in-this-history")
        self.assertEqual(result["decision"], "admit")


class TestReconciliationTableLedgerStatusAgreement(unittest.TestCase):
    """WF8c (m)'s own "teeth check" against this repository's real
    content -- mirrors item 72's own registry/view agreement test's role
    in this file. Confirms `docs/ai-workflow/registry/workflow-v2-1-core-
    ledger-status.json` (generated via `parse_reconciliation_table`) is a
    faithful, total, duplicate-free transcription of the live plan's own
    '### Reconciliation table', and that `verify_wfo_ledger_coverage`
    (`WFR-68`'s own bound verifier, exercised against synthetic fixtures
    in `workflow_state_completion_obligations_test.py`) is now wired up
    and has real teeth against this repository's own content at `HEAD`."""

    def test_ledger_status_json_matches_the_live_reconciliation_table_exactly(self):
        repo_root = _repo_root()
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()
        table = ws.parse_reconciliation_table(repo_root, head)

        expected_universe = {166} | set(range(167, 377))
        self.assertEqual(set(table.keys()), expected_universe)

        ledger_path = repo_root / ws.ledger_status_path_for_work_item(WORK_ITEM_ID)
        ledger = json.loads(ledger_path.read_text())
        entries = ledger["entries"]

        entry_items = [e["item"] for e in entries]
        self.assertEqual(len(entry_items), len(set(entry_items)), "duplicate item(s) in ledger-status.json")
        self.assertEqual(set(entry_items), expected_universe)

        for entry in entries:
            row = table[entry["item"]]
            self.assertEqual(
                (entry["status"], entry["owner_checkpoint"]), (row["status"], row["owner"]),
                f"item {entry['item']} disagrees between the plan table and ledger-status.json",
            )

    def test_ledger_status_json_is_now_bound_as_a_completion_obligation_conformance(self):
        """WF8c (m)'s remaining scope lands `verify_wfo_ledger_coverage`
        (properties (ii)-(iv), the four adversarial arms) and binds it --
        the standing guard from the part-1 session is updated in place,
        never deleted, to assert the new state rather than the old
        interim `UNKNOWN_OBLIGATION` one."""
        self.assertEqual(
            ws.COMPLETION_OBLIGATION_CONFORMANCE.get("WFO-LEDGER-COVERAGE"), "verify_wfo_ledger_coverage",
        )

    def test_real_ledger_status_json_does_not_yet_pass_the_bound_verifier(self):
        """Binding the verifier this session does not itself populate real
        evidence for any of the 211 reconciliation-table items -- every
        `IMPLEMENTED` entry in the real `workflow-v2-1-core-ledger-
        status.json` still carries `evidence: null` (property (ii)), so
        `verify_wfo_ledger_coverage` against live HEAD correctly still
        derives `FAIL`, naming those items -- confirming the newly-bound
        verifier has real teeth against this repository's own actual
        content, not only against synthetic fixtures. `WFR-69`'s own
        pre-flight above keeps refusing `WF8c`'s checkpoint completion for
        the identical underlying reason."""
        repo_root = _repo_root()
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()
        result = ws.verify_wfo_ledger_coverage(repo_root, head)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("evidence" in a for a in result["failing_assertions"]))


class TestCheckpointReachabilityConformanceLive(unittest.TestCase):
    """Item 355's own conformance test (WF8c clause (m), part 2), run
    against this repository's real, live content -- the "run against the
    live state and registry" claim item 355's own corrected-rule text
    makes for clauses (a)-(d) is exercised here, not merely asserted."""

    def test_holds_against_the_live_repository(self):
        repo_root = _repo_root()
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()
        result = ws.verify_checkpoint_reachability_conformance(repo_root, head, WORK_ITEM_ID)
        self.assertEqual(result["status"], "PASS", result["detail"])


class TestReviewSubjectDeclarationsLive(unittest.TestCase):
    """`WFR-67`'s `review-subject:` header conformance (`WF8c` item (h),
    part 1), run against this repository's real thirteen command files at
    live `HEAD` -- proves the declaration half actually landed on every
    file the plan's own revision-80 text names, not only against synthetic
    fixtures. As documented at `discover_review_subject_declarations`'s own
    docstring, the *value* each file carries here is a recorded,
    known-correct table (the nine-consumer/four-exempt split that text
    states by name), not yet re-derived from each file's own prose against
    the three semantic disjuncts -- that derivation is separate, deferred
    `WF8c` scope. `recover-implementation-provenance.md` (added after
    `WFR-67`'s design was finalized, `WF8c` item (b)) is correctly outside
    the named "all thirteen" and carries no declaration at all."""

    EXPECTED = {
        ".claude/commands/accept-milestone.md": "none",
        ".claude/commands/accept-scoped-remediation.md": "none",
        ".claude/commands/apply-functional-review.md": "bundle",
        ".claude/commands/apply-implementation-review.md": "verdict",
        ".claude/commands/apply-plan-review.md": "verdict",
        ".claude/commands/approve-review.md": "bundle",
        ".claude/commands/bootstrap-workflow-v2.md": "none",
        ".claude/commands/milestone-implement.md": "bundle",
        ".claude/commands/milestone-plan.md": "bundle",
        ".claude/commands/prepare-functional-review.md": "none",
        ".claude/commands/prepare-review.md": "bundle",
        ".claude/commands/record-manual-plan-review.md": "verdict",
        ".claude/commands/review-plan.md": "bundle",
    }

    # The "twice" consumers (existing pre-mutation refusal point + the
    # operation's own mutation guard) vs. the "once" report-only consumers
    # (revision 79: "the single assertion immediately preceding the report
    # IS the mutation-guard assertion").
    EXPECTED_ASSERTION_COUNT = {
        ".claude/commands/apply-implementation-review.md": 2,
        ".claude/commands/apply-plan-review.md": 2,
        ".claude/commands/approve-review.md": 2,
        ".claude/commands/record-manual-plan-review.md": 2,
        ".claude/commands/review-plan.md": 2,
        ".claude/commands/apply-functional-review.md": 1,
        ".claude/commands/milestone-implement.md": 1,
        ".claude/commands/milestone-plan.md": 1,
        ".claude/commands/prepare-review.md": 1,
    }

    def test_all_thirteen_command_files_declare_the_expected_value(self):
        repo_root = _repo_root()
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()
        declarations = ws.discover_review_subject_declarations(repo_root, head)
        self.assertEqual(declarations, self.EXPECTED)

    def test_recover_implementation_provenance_has_no_declaration(self):
        repo_root = _repo_root()
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=repo_root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()
        declarations = ws.discover_review_subject_declarations(repo_root, head)
        self.assertNotIn(".claude/commands/recover-implementation-provenance.md", declarations)

    def test_every_non_exempt_file_calls_the_shared_assertion_the_expected_number_of_times(self):
        repo_root = _repo_root()
        for rel_path, expected_count in self.EXPECTED_ASSERTION_COUNT.items():
            text = (repo_root / rel_path).read_text()
            actual = text.count("assert_bundle_not_rejected")
            self.assertEqual(
                actual, expected_count,
                f"{rel_path}: expected {expected_count} call(s) to "
                f"assert_bundle_not_rejected, found {actual}",
            )

    def test_every_exempt_file_never_calls_the_assertion(self):
        repo_root = _repo_root()
        exempt = [p for p, v in self.EXPECTED.items() if v == "none"]
        self.assertEqual(len(exempt), 4)
        for rel_path in exempt:
            text = (repo_root / rel_path).read_text()
            self.assertNotIn("assert_bundle_not_rejected", text)


if __name__ == "__main__":
    unittest.main()
