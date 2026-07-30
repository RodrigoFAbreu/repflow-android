#!/usr/bin/env python3
"""Hermetic unit tests for workflow_state.py (WF1a).

Runs entirely against disposable scratch Git repositories this suite
creates and destroys itself, mirroring workflow_fingerprint_test.py's own
pattern. The real-repository demonstration lives separately in
workflow_state_demo_test.py.

Covers D3's "Validator rejects" list (WFR-09, WFR-14, WFR-18;
missing-test items 19, 29/73, 44, 50, 62, 63, 64, 116) to the extent
checkable from schema/registry/Git history alone -- see workflow_state.py's
module docstring for what is deliberately out of WF1a's scope.

Stdlib-only. Run: python3 scripts/workflow_state_test.py
"""

from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import workflow_state as ws


def _run(args, cwd):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


class ScratchRepo:
    """A disposable Git repo. `commit(subject, trailers=...)` writes a
    commit whose body carries the named Git trailers, exactly like a real
    Workflow-Checkpoint/Workflow-Work-Item-bearing commit."""

    def __enter__(self):
        self.root = Path(tempfile.mkdtemp(prefix="wf-state-test-"))
        _run(["git", "init", "-q"], cwd=self.root)
        _run(["git", "config", "user.email", "test@example.com"], cwd=self.root)
        _run(["git", "config", "user.name", "Test"], cwd=self.root)
        (self.root / "README.md").write_text("base\n")
        _run(["git", "add", "README.md"], cwd=self.root)
        _run(["git", "commit", "-q", "-m", "base"], cwd=self.root)
        self.base = self.head()
        return self

    def __exit__(self, *exc):
        import shutil
        shutil.rmtree(self.root, ignore_errors=True)

    def head(self) -> str:
        return subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()

    def commit(self, subject: str, trailers: dict[str, str] | None = None, filename: str | None = None) -> str:
        filename = filename or f"{subject.replace(' ', '_')}.txt"
        (self.root / filename).write_text(subject + "\n")
        _run(["git", "add", filename], cwd=self.root)
        body = subject
        if trailers:
            body += "\n\n" + "\n".join(f"{k}: {v}" for k, v in trailers.items())
        _run(["git", "commit", "-q", "-m", body], cwd=self.root)
        return self.head()


class TestConfigFailSafe(unittest.TestCase):
    def test_missing_config_pre_activation_defaults_to_v1(self):
        with ScratchRepo() as repo:
            config = ws.load_config(repo.root, config_path=Path("nonexistent.json"))
            self.assertEqual(config["default_workflow_version"], "1")

    def test_corrupt_config_pre_activation_defaults_to_v1(self):
        with ScratchRepo() as repo:
            (repo.root / "bad.json").write_text("{not json")
            config = ws.load_config(repo.root, config_path=Path("bad.json"))
            self.assertEqual(config["default_workflow_version"], "1")

    def test_missing_config_after_activation_is_a_hard_stop(self):
        with ScratchRepo() as repo:
            repo.commit("activate", trailers={"Workflow-Activation": "2.1"})
            with self.assertRaises(ws.ConfigMissingAfterActivationError):
                ws.load_config(repo.root, config_path=Path("nonexistent.json"))

    def test_rollback_after_activation_restores_pre_activation_behavior(self):
        with ScratchRepo() as repo:
            repo.commit("activate", trailers={"Workflow-Activation": "2.1"})
            repo.commit("rollback", trailers={"Workflow-Rollback": "2.1"})
            config = ws.load_config(repo.root, config_path=Path("nonexistent.json"))
            self.assertEqual(config["default_workflow_version"], "1")

    def test_latest_event_wins_not_first(self):
        with ScratchRepo() as repo:
            repo.commit("activate", trailers={"Workflow-Activation": "2.1"})
            repo.commit("noop")
            self.assertTrue(ws.is_activated(repo.root))

    def test_present_valid_config_is_returned_as_is(self):
        with ScratchRepo() as repo:
            (repo.root / "config.json").write_text(json.dumps(ws.default_config()))
            config = ws.load_config(repo.root, config_path=Path("config.json"))
            self.assertEqual(config, ws.default_config())

    def test_default_workflow_version_must_be_in_supported_versions(self):
        with self.assertRaises(ws.CorruptJsonError):
            ws.validate_config({
                "schema_version": 1, "default_workflow_version": "3", "supported_versions": ["1", "2.1"],
            })


class TestCheckpointTrailerDiscovery(unittest.TestCase):
    def test_single_match_is_discovered(self):
        with ScratchRepo() as repo:
            sha = repo.commit("wf0", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"})
            discovered = ws.discover_checkpoint_commits(repo.root, "wi", repo.base)
            self.assertEqual(discovered, {"WF0": sha})

    def test_wrong_work_item_is_not_matched(self):
        with ScratchRepo() as repo:
            repo.commit("wf0", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "other"})
            discovered = ws.discover_checkpoint_commits(repo.root, "wi", repo.base)
            self.assertEqual(discovered, {})

    def test_reachable_completion_verifies(self):
        with ScratchRepo() as repo:
            sha = repo.commit("wf0", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"})
            work_item = {
                "work_item_id": "wi",
                "checkpoints": {"WF0": {"status": "COMPLETE", "start_commit": repo.base}},
            }
            ws.verify_checkpoint_completions(work_item, repo.root, repo.base)  # must not raise

    def test_unreachable_completion_raises(self):
        with ScratchRepo() as repo:
            work_item = {
                "work_item_id": "wi",
                "checkpoints": {"WF0": {"status": "COMPLETE", "start_commit": repo.base}},
            }
            with self.assertRaises(ws.CheckpointNotReachableError):
                ws.verify_checkpoint_completions(work_item, repo.root, repo.base)

    def test_duplicate_trailer_resolves_via_first_parent_tie_break(self):
        """A cherry-pick/rebase can copy a trailer onto a new commit while
        the original stays reachable (OPUS-R6-022) -- the first-parent
        ancestor of HEAD wins."""
        with ScratchRepo() as repo:
            # Simulate: an off-first-parent branch commit carries the
            # trailer, then a first-parent commit (e.g. a cherry-pick onto
            # main) carries the same trailer -- the first-parent one wins.
            _run(["git", "checkout", "-q", "-b", "side"], cwd=repo.root)
            side_sha = repo.commit("wf0-side", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"})
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            main_sha = repo.commit("wf0-main", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"})
            _run(["git", "merge", "-q", "--no-ff", "-m", "merge side", "side"], cwd=repo.root)
            discovered = ws.discover_checkpoint_commits(repo.root, "wi", repo.base)
            self.assertEqual(discovered, {"WF0": main_sha})
            self.assertNotEqual(discovered["WF0"], side_sha)

    def test_genuine_ambiguity_raises(self):
        """Two first-parent-ancestor commits both carrying the same
        trailer pair is genuine ambiguity, not silently resolved."""
        with ScratchRepo() as repo:
            repo.commit("wf0-a", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"})
            repo.commit("wf0-b", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"})
            with self.assertRaises(ws.AmbiguousCheckpointTrailerError):
                ws.discover_checkpoint_commits(repo.root, "wi", repo.base)


def _base_work_item(**overrides) -> dict:
    work_item = {
        "work_item_type": "process",
        "work_item_kind": "process",
        "work_item_id": "wi",
        "parent_work_item_id": None,
        "governing_workflow_version": "1",
        "phase": "IMPLEMENTING",
        "checkpoints": {},
        "plan_review_stages": None,
    }
    work_item.update(overrides)
    return work_item


def _base_state(**work_items) -> dict:
    return {"schema_version": 1, "active_work_item_id": None, "work_items": work_items}


class TestStateValidation(unittest.TestCase):
    def test_minimal_valid_state_passes(self):
        ws.validate_state(_base_state(wi=_base_work_item()))

    def test_unknown_phase_rejected(self):
        with self.assertRaises(ws.UnknownPhaseError):
            ws.validate_state(_base_state(wi=_base_work_item(phase="NOT_A_REAL_PHASE")))

    def test_work_item_id_key_mismatch_rejected(self):
        with self.assertRaises(ws.WorkItemIdKeyMismatchError):
            ws.validate_state(_base_state(wi=_base_work_item(work_item_id="different")))

    def test_active_work_item_id_naming_nonexistent_entry_rejected(self):
        state = _base_state(wi=_base_work_item())
        state["active_work_item_id"] = "nonexistent"
        with self.assertRaises(ws.ActiveWorkItemInvalidError):
            ws.validate_state(state)

    def test_active_work_item_id_naming_terminal_phase_rejected(self):
        state = _base_state(wi=_base_work_item(phase="MILESTONE_COMPLETE"))
        state["active_work_item_id"] = "wi"
        with self.assertRaises(ws.ActiveWorkItemInvalidError):
            ws.validate_state(state)

    def test_two_simultaneous_non_terminal_non_active_entries_accepted(self):
        """Missing-test item 19: LEGACY_READY alongside an active
        IMPLEMENTING item -- both non-terminal, only active_work_item_id
        naming a terminal-or-nonexistent entry is rejected."""
        state = _base_state(
            active=_base_work_item(work_item_id="active", phase="IMPLEMENTING"),
            legacy=_base_work_item(work_item_id="legacy", phase="LEGACY_READY"),
        )
        state["active_work_item_id"] = "active"
        ws.validate_state(state)  # must not raise

    def test_multiple_in_progress_checkpoints_rejected(self):
        wi = _base_work_item(checkpoints={
            "A": {"status": "IN_PROGRESS", "start_commit": "x"},
            "B": {"status": "IN_PROGRESS", "start_commit": "y"},
        })
        with self.assertRaises(ws.MultipleInProgressCheckpointsError):
            ws.validate_state(_base_state(wi=wi))

    def test_unknown_checkpoint_status_rejected(self):
        wi = _base_work_item(checkpoints={"A": {"status": "READY", "start_commit": "x"}})
        with self.assertRaises(ws.UnknownCheckpointStatusError):
            ws.validate_state(_base_state(wi=wi))

    def test_completion_while_dependency_incomplete_rejected(self):
        registry = {"checkpoints": [
            {"id": "A", "depends_on": []},
            {"id": "B", "depends_on": ["A"]},
        ]}
        wi = _base_work_item(checkpoints={"B": {"status": "COMPLETE", "start_commit": "x"}})
        with self.assertRaises(ws.CheckpointDependencyNotCompleteError):
            ws.validate_state(_base_state(wi=wi), registry=registry)

    def test_completion_with_dependency_complete_accepted(self):
        registry = {"checkpoints": [
            {"id": "A", "depends_on": []},
            {"id": "B", "depends_on": ["A"]},
        ]}
        wi = _base_work_item(checkpoints={
            "A": {"status": "COMPLETE", "start_commit": "x"},
            "B": {"status": "COMPLETE", "start_commit": "y"},
        })
        ws.validate_state(_base_state(wi=wi), registry=registry)  # must not raise

    def test_corrupt_json_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "state.json"
            path.write_text("{not json")
            with self.assertRaises(ws.CorruptJsonError):
                ws._load_json(path)


class TestPlanReviewStages(unittest.TestCase):
    def test_non_null_on_v1_item_rejected(self):
        wi = _base_work_item(
            governing_workflow_version="1",
            plan_review_stages={"review_content_id": "x", "local_model_plan_review": None, "manual_external_plan_review": None},
        )
        with self.assertRaises(ws.PlanReviewStagesInvalidForVersionError):
            ws.validate_state(_base_state(wi=wi))

    def test_manual_without_local_rejected(self):
        wi = _base_work_item(
            governing_workflow_version="2.1",
            plan_review_stages={
                "review_content_id": "x",
                "local_model_plan_review": None,
                "manual_external_plan_review": {"bundle_id": "b", "verdict": "APPROVE", "round": 1, "completed_at": "t"},
            },
        )
        with self.assertRaises(ws.ManualStageWithoutLocalStageError):
            ws.validate_state(_base_state(wi=wi))

    def test_non_approve_verdict_rejected(self):
        """Missing-test item 116 (GPT-R14-010)."""
        wi = _base_work_item(
            governing_workflow_version="2.1",
            plan_review_stages={
                "review_content_id": "x",
                "local_model_plan_review": {"bundle_id": "b", "verdict": "REVISE", "round": 1, "completed_at": "t"},
                "manual_external_plan_review": None,
            },
        )
        with self.assertRaises(ws.StageVerdictNotApproveError):
            ws.validate_state(_base_state(wi=wi))

    def test_both_stages_approve_accepted(self):
        wi = _base_work_item(
            governing_workflow_version="2.1",
            plan_review_stages={
                "review_content_id": "x",
                "local_model_plan_review": {"bundle_id": "b1", "verdict": "APPROVE", "round": 1, "completed_at": "t1"},
                "manual_external_plan_review": {"bundle_id": "b2", "verdict": "APPROVE", "round": 1, "completed_at": "t2"},
            },
        )
        ws.validate_state(_base_state(wi=wi))  # must not raise


class TestRegistryTopologicalOrder(unittest.TestCase):
    def test_valid_topological_order_accepted(self):
        registry = {"checkpoints": [{"id": "A", "depends_on": []}, {"id": "B", "depends_on": ["A"]}]}
        ws.validate_registry_topological_order(registry)  # must not raise

    def test_non_topological_order_rejected(self):
        """Missing-test items 29/73."""
        registry = {"checkpoints": [{"id": "B", "depends_on": ["A"]}, {"id": "A", "depends_on": []}]}
        with self.assertRaises(ws.NonTopologicalRegistryOrderError):
            ws.validate_registry_topological_order(registry)


class TestRegistryMappingCoverage(unittest.TestCase):
    def test_full_coverage_accepted(self):
        registry = {"checkpoints": [{"id": "A"}, {"id": "B"}]}
        mapping = {"requirements": {
            "R1": {"checkpoint_ids": ["A"]},
            "R2": {"checkpoint_ids": ["B"]},
        }}
        ws.validate_registry_mapping_coverage(registry, mapping)  # must not raise

    def test_unmapped_requirement_rejected(self):
        registry = {"checkpoints": [{"id": "A"}]}
        mapping = {"requirements": {"R1": {"checkpoint_ids": ["NOT_A_CHECKPOINT"]}}}
        with self.assertRaises(ws.UnmappedRequirementError):
            ws.validate_registry_mapping_coverage(registry, mapping)

    def test_unowned_checkpoint_rejected(self):
        registry = {"checkpoints": [{"id": "A"}, {"id": "B"}]}
        mapping = {"requirements": {"R1": {"checkpoint_ids": ["A"]}}}
        with self.assertRaises(ws.UnownedCheckpointError):
            ws.validate_registry_mapping_coverage(registry, mapping)


class TestWorktreeIdentitySchema(unittest.TestCase):
    def test_valid_document_accepted(self):
        ws.validate_worktree_identity({
            "repo_root": "/r", "git_common_dir": "/r/.git", "worktree_root": "/r",
            "expected_dirty_paths_by_work_item": {"wi": [{"path": "a", "sha256": "b"}]},
            "generated_at": "t",
        })  # must not raise

    def test_missing_field_rejected(self):
        with self.assertRaises(ws.CorruptJsonError):
            ws.validate_worktree_identity({"repo_root": "/r"})

    def test_malformed_dirty_path_entry_rejected(self):
        with self.assertRaises(ws.CorruptJsonError):
            ws.validate_worktree_identity({
                "repo_root": "/r", "git_common_dir": "/r/.git", "worktree_root": "/r",
                "expected_dirty_paths_by_work_item": {"wi": [{"path": "a"}]},
                "generated_at": "t",
            })


class TestGoverningVersion(unittest.TestCase):
    def test_supported_version_accepted(self):
        ws.validate_governing_version("2.1", ws.default_config())  # must not raise

    def test_unsupported_version_rejected(self):
        """Resolves OPUS-R6-024 (optional), missing-test item 41."""
        with self.assertRaises(ws.UnsupportedGoverningVersionError):
            ws.validate_governing_version("3", ws.default_config())


if __name__ == "__main__":
    unittest.main()
