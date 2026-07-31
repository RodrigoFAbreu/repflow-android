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


class TestAgainstRealRepository(unittest.TestCase):
    def test_wf0_and_wf1a_are_discovered_via_real_trailer_search(self):
        repo_root = _repo_root()
        discovered = ws.discover_checkpoint_commits(repo_root, WORK_ITEM_ID, BASE_COMMIT)
        self.assertIn("WF0", discovered)
        self.assertEqual(discovered["WF0"], "8f76175348d0f63e61f6c1a9997b5004a27430fe")
        # WF1a's own commit is what this test suite is committed inside of.
        self.assertIn("WF1a", discovered)

    def test_real_config_file_is_schema_valid(self):
        repo_root = _repo_root()
        config = ws.load_config(repo_root)
        ws.validate_config(config)

    def test_real_registry_is_topologically_ordered(self):
        repo_root = _repo_root()
        registry = json.loads((repo_root / "docs/ai-workflow/registry/workflow-v2-1-core-registry.json").read_text())
        ws.validate_registry_topological_order(registry)

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

    def test_real_state_file_checkpoint_completions_are_reachable(self):
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        work_item = state["work_items"][WORK_ITEM_ID]
        ws.verify_checkpoint_completions(work_item, repo_root, work_item["base_commit"])

    def test_real_state_file_plan_approval_matches_wf0_trailer(self):
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        work_item = state["work_items"][WORK_ITEM_ID]
        approval = work_item["plan_approval"]
        body = subprocess.run(
            ["git", "log", "-1", "--format=%B", "8f76175348d0f63e61f6c1a9997b5004a27430fe"],
            cwd=repo_root, check=True, capture_output=True, text=True,
        ).stdout
        self.assertIn(f"Workflow-Plan-Approval: {approval['approved_review_content_id']}", body)

    def test_real_plan_approval_commit_is_discovered_by_the_generalized_trailer_search(self):
        """WF4a-iii's discover_plan_approval_commit against this milestone's
        own real WF0 approval commit (mirrors test_wf0_and_wf1a's checkpoint-
        trailer counterpart above)."""
        repo_root = _repo_root()
        state = json.loads((repo_root / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
        work_item = state["work_items"][WORK_ITEM_ID]
        approval = work_item["plan_approval"]
        discovered = ws.discover_plan_approval_commit(
            repo_root, WORK_ITEM_ID, approval["approved_review_content_id"], BASE_COMMIT,
        )
        self.assertEqual(discovered, "8f76175348d0f63e61f6c1a9997b5004a27430fe")

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


if __name__ == "__main__":
    unittest.main()
