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


if __name__ == "__main__":
    unittest.main()
