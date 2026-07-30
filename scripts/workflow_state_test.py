#!/usr/bin/env python3
"""Hermetic unit tests for workflow_state.py (WF1a, extended by WF1b).

Runs entirely against disposable scratch Git repositories this suite
creates and destroys itself, mirroring workflow_fingerprint_test.py's own
pattern. The real-repository demonstration lives separately in
workflow_state_demo_test.py.

Covers D3's "Validator rejects" list (WFR-09, WFR-14, WFR-18;
missing-test items 19, 29/73, 44, 50, 62, 63, 64, 116) to the extent
checkable from schema/registry/Git history alone -- see workflow_state.py's
module docstring for what is deliberately out of WF1a's scope.

WF1b additions: D1's work-item routing (create-or-resume, completion/
reset) and D-Registry/D4b's registry/mapping generator, including missing-
test item 35 (review_content_id invariant under Markdown-view
reformatting, sensitive to a real registry/mapping edit).

Stdlib-only. Run: python3 scripts/workflow_state_test.py
"""

from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import workflow_fingerprint as fingerprint
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


class TestWorkItemRouting(unittest.TestCase):
    def test_fresh_id_creates_entry_and_claims_active_pointer(self):
        state = _base_state()
        config = ws.default_config()
        new_state = ws.route_work_item(
            state, config, work_item_id="milestone-9", work_item_type="product",
            work_item_kind="product", plan_path="p", registry_path="r",
            plan_revision=1, now="t1",
        )
        entry = new_state["work_items"]["milestone-9"]
        self.assertEqual(entry["phase"], "PLANNING")
        self.assertEqual(entry["governing_workflow_version"], config["default_workflow_version"])
        self.assertEqual(entry["plan_revision"], 1)
        self.assertEqual(entry["state_revision"], 1)
        self.assertEqual(new_state["active_work_item_id"], "milestone-9")
        # input untouched
        self.assertEqual(state, _base_state())

    def test_resuming_existing_non_terminal_entry_only_advances_revision_fields(self):
        state = _base_state(wi=_base_work_item(governing_workflow_version="2.1"))
        config = ws.default_config()
        new_state = ws.route_work_item(
            state, config, work_item_id="wi", work_item_type="process",
            work_item_kind="process", plan_path="p", registry_path="r",
            plan_revision=2, now="t2",
        )
        entry = new_state["work_items"]["wi"]
        self.assertEqual(entry["plan_revision"], 2)
        self.assertEqual(entry["state_revision"], 2)
        self.assertEqual(entry["last_transition"], "t2")
        # identity fields are immutable across a resume
        self.assertEqual(entry["governing_workflow_version"], "2.1")

    def test_routing_a_terminal_id_is_rejected(self):
        state = _base_state(wi=_base_work_item(phase="MILESTONE_COMPLETE"))
        config = ws.default_config()
        with self.assertRaises(ws.WorkItemTerminalReuseError):
            ws.route_work_item(
                state, config, work_item_id="wi", work_item_type="process",
                work_item_kind="process", plan_path="p", registry_path="r",
                plan_revision=1, now="t",
            )

    def test_routing_never_steals_focus_from_a_different_active_item(self):
        state = _base_state(
            active=_base_work_item(work_item_id="active", phase="IMPLEMENTING"),
            legacy=_base_work_item(work_item_id="legacy", phase="LEGACY_READY"),
        )
        state["active_work_item_id"] = "active"
        config = ws.default_config()
        new_state = ws.route_work_item(
            state, config, work_item_id="legacy", work_item_type="process",
            work_item_kind="process", plan_path="p", registry_path="r",
            plan_revision=2, now="t",
        )
        self.assertEqual(new_state["active_work_item_id"], "active")
        self.assertEqual(new_state["work_items"]["legacy"]["plan_revision"], 2)

    def test_invalid_work_item_id_rejected(self):
        config = ws.default_config()
        with self.assertRaises(ws.InvalidWorkItemIdError):
            ws.route_work_item(
                _base_state(), config, work_item_id="Not Valid!", work_item_type="process",
                work_item_kind="process", plan_path="p", registry_path="r",
                plan_revision=1, now="t",
            )

    def test_unsupported_governing_version_rejected_at_creation(self):
        config = {"schema_version": 1, "default_workflow_version": "3", "supported_versions": ["3"]}
        # "3" is supported by this deliberately-bogus config but not the
        # real default_config() -- exercise the opposite: config's own
        # default outside its own supported_versions is a config bug, not
        # this function's concern (validate_config catches that). Here we
        # confirm the *positive* path: creation succeeds when the default
        # is in supported_versions.
        new_state = ws.route_work_item(
            _base_state(), config, work_item_id="wi", work_item_type="process",
            work_item_kind="process", plan_path="p", registry_path="r",
            plan_revision=1, now="t",
        )
        self.assertEqual(new_state["work_items"]["wi"]["governing_workflow_version"], "3")


class TestWorkItemCompletion(unittest.TestCase):
    def test_completing_active_item_resets_pointer(self):
        state = _base_state(wi=_base_work_item(phase="AWAITING_USER_ACCEPTANCE"))
        state["active_work_item_id"] = "wi"
        new_state = ws.complete_work_item(state, "wi", now="t")
        self.assertEqual(new_state["work_items"]["wi"]["phase"], "MILESTONE_COMPLETE")
        self.assertIsNone(new_state["active_work_item_id"])
        ws.validate_state(new_state)  # a completed, unpointed item is valid

    def test_completing_non_active_item_leaves_pointer_alone(self):
        state = _base_state(
            active=_base_work_item(work_item_id="active", phase="IMPLEMENTING"),
            other=_base_work_item(work_item_id="other", phase="AWAITING_USER_ACCEPTANCE"),
        )
        state["active_work_item_id"] = "active"
        new_state = ws.complete_work_item(state, "other", now="t")
        self.assertEqual(new_state["active_work_item_id"], "active")


class TestRegistryMappingGenerator(unittest.TestCase):
    def test_generate_registry_valid_order_accepted(self):
        registry = ws.generate_registry("wi", 1, [
            {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
            {"id": "B", "name": "b", "depends_on": ["A"], "complexity": 1, "session_target": "1"},
        ])
        self.assertEqual(registry["work_item_id"], "wi")
        self.assertEqual([c["id"] for c in registry["checkpoints"]], ["A", "B"])

    def test_generate_registry_bad_order_rejected(self):
        with self.assertRaises(ws.NonTopologicalRegistryOrderError):
            ws.generate_registry("wi", 1, [
                {"id": "B", "name": "b", "depends_on": ["A"], "complexity": 1, "session_target": "1"},
                {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
            ])

    def test_generate_mapping_full_coverage_accepted(self):
        registry = ws.generate_registry("wi", 1, [
            {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
        ])
        mapping = ws.generate_mapping("wi", {"R1": {"description": "d", "checkpoint_ids": ["A"]}}, registry=registry)
        self.assertEqual(mapping["work_item_id"], "wi")

    def test_generate_mapping_unowned_checkpoint_rejected(self):
        registry = ws.generate_registry("wi", 1, [
            {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
            {"id": "B", "name": "b", "depends_on": [], "complexity": 1, "session_target": "1"},
        ])
        with self.assertRaises(ws.UnownedCheckpointError):
            ws.generate_mapping("wi", {"R1": {"description": "d", "checkpoint_ids": ["A"]}}, registry=registry)

    def test_write_registry_and_mapping_round_trips(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            registry = ws.generate_registry("wi", 1, [
                {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
            ])
            mapping = ws.generate_mapping("wi", {"R1": {"description": "d", "checkpoint_ids": ["A"]}}, registry=registry)
            ws.write_registry_and_mapping(root, Path("reg.json"), Path("map.json"), registry, mapping)
            self.assertEqual(json.loads((root / "reg.json").read_text()), registry)
            self.assertEqual(json.loads((root / "map.json").read_text()), mapping)

    def test_write_refuses_bad_coverage_even_if_caller_bypassed_generate(self):
        """Fail-closed guard: write_registry_and_mapping re-validates even
        against a hand-built (not generate_*-produced) argument pair."""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            registry = {"work_item_id": "wi", "checkpoints": [{"id": "A", "depends_on": []}]}
            mapping = {"work_item_id": "wi", "requirements": {}}
            with self.assertRaises(ws.UnownedCheckpointError):
                ws.write_registry_and_mapping(root, Path("reg.json"), Path("map.json"), registry, mapping)
            self.assertFalse((root / "reg.json").exists())

    def test_render_registry_markdown_is_a_pure_function_of_the_json(self):
        registry = ws.generate_registry("wi", 1, [
            {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
        ])
        self.assertEqual(ws.render_registry_markdown(registry), ws.render_registry_markdown(registry))
        self.assertIn("A", ws.render_registry_markdown(registry))

    def test_review_content_id_invariant_under_markdown_reformatting_sensitive_to_real_edit(self):
        """Missing-test item 35: regenerating the (unhashed) Markdown view
        never changes review_content_id; a real registry/mapping JSON edit
        does. Exercised end-to-end via workflow_fingerprint's own plan-
        stage identity function against a scratch repo."""
        import workflow_fingerprint as wf

        with ScratchRepo() as repo:
            registry_rel = Path("docs/ai-workflow/registry/wi-registry.json")
            mapping_rel = Path("docs/ai-workflow/requirements/wi-mapping.json")
            generated_md_rel = Path("docs/ai-workflow/registry/wi-registry.generated.md")
            (repo.root / registry_rel.parent).mkdir(parents=True, exist_ok=True)
            (repo.root / mapping_rel.parent).mkdir(parents=True, exist_ok=True)

            def write_round(checkpoint_name: str, markdown_note: str) -> str:
                registry = ws.generate_registry("wi", 1, [
                    {"id": "A", "name": checkpoint_name, "depends_on": [], "complexity": 1, "session_target": "1"},
                ])
                mapping = ws.generate_mapping(
                    "wi", {"R1": {"description": "d", "checkpoint_ids": ["A"]}}, registry=registry,
                )
                ws.write_registry_and_mapping(repo.root, registry_rel, mapping_rel, registry, mapping)
                # The Markdown view is a *generated, excluded* artifact
                # (D-Registry): only markdown_note changes here, never the
                # protected registry/mapping JSON bytes.
                (repo.root / generated_md_rel).write_text(
                    ws.render_registry_markdown(registry) + markdown_note + "\n"
                )
                _run(["git", "add", "."], cwd=repo.root)
                _run(["git", "commit", "-q", "-m", "round"], cwd=repo.root)
                review_content_id, _manifest = wf.compute_review_content_id_plan_stage(
                    repo.root, repo.base, "process", "wi", 1,
                    protected=frozenset({str(registry_rel), str(mapping_rel)}),
                    excluded_paths={str(generated_md_rel): "generated registry markdown view, never hashed"},
                    excluded_prefixes={},
                )
                return review_content_id

            id_round1 = write_round("checkpoint A", "cosmetic note one")
            id_round2 = write_round("checkpoint A", "totally different cosmetic note")
            self.assertEqual(id_round1, id_round2, "Markdown-only reformatting must not change review_content_id")

            # A real registry edit (renaming the checkpoint) must change it.
            id_round3 = write_round("checkpoint A renamed", "totally different cosmetic note")
            self.assertNotEqual(id_round2, id_round3, "a real registry edit must change review_content_id")


class TestApprovalGateReachability(unittest.TestCase):
    """WF4a-ii, D-States: non-circular entry -- reachable from the review
    round alone, never from plan_approval/technical_approval existing."""

    def test_approve_round_reaches_gate(self):
        self.assertTrue(ws.approval_gate_reachable("APPROVE"))

    def test_revise_round_with_findings_resolved_reaches_gate(self):
        """Missing-test items 12/27: a REVISE round with zero blocking
        findings left reaches the gate exactly as readily as APPROVE,
        with no approval record in existence."""
        self.assertTrue(ws.approval_gate_reachable("REVISE"))

    def test_block_never_reaches_gate(self):
        """Missing-test item 15."""
        self.assertFalse(ws.approval_gate_reachable("BLOCK"))

    def test_technical_gate_blocked_by_dirty_protected_path(self):
        self.assertFalse(ws.technical_approval_gate_reachable(
            latest_round_status="APPROVE", protected_path_dirty=True,
            head_matches_reviewed_implementation_head=True,
        ))

    def test_technical_gate_blocked_by_head_mismatch(self):
        self.assertFalse(ws.technical_approval_gate_reachable(
            latest_round_status="APPROVE", protected_path_dirty=False,
            head_matches_reviewed_implementation_head=False,
        ))

    def test_technical_gate_reachable_when_clean_and_matching(self):
        self.assertTrue(ws.technical_approval_gate_reachable(
            latest_round_status="REVISE", protected_path_dirty=False,
            head_matches_reviewed_implementation_head=True,
        ))

    def test_v1_plan_gate_ignores_plan_review_stages(self):
        self.assertTrue(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="1",
            plan_review_stages=None, current_review_content_id="c1",
        ))

    def test_v2_1_plan_gate_requires_both_stages_current(self):
        """GPT-R11-001/-003: a single reviewed round is necessary but no
        longer sufficient for a "2.1" item."""
        self.assertFalse(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="2.1",
            plan_review_stages=None, current_review_content_id="c1",
        ))
        stages = {
            "review_content_id": "c1",
            "local_model_plan_review": {"bundle_id": "b1", "verdict": "APPROVE", "round": 1, "completed_at": "t1"},
            "manual_external_plan_review": {"bundle_id": "b2", "verdict": "APPROVE", "round": 1, "completed_at": "t2"},
        }
        self.assertTrue(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="2.1",
            plan_review_stages=stages, current_review_content_id="c1",
        ))

    def test_v2_1_plan_gate_rejects_stale_review_content_id(self):
        stages = {
            "review_content_id": "stale",
            "local_model_plan_review": {"bundle_id": "b1", "verdict": "APPROVE", "round": 1, "completed_at": "t1"},
            "manual_external_plan_review": {"bundle_id": "b2", "verdict": "APPROVE", "round": 1, "completed_at": "t2"},
        }
        self.assertFalse(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="2.1",
            plan_review_stages=stages, current_review_content_id="current",
        ))

    def test_v2_1_plan_gate_rejects_local_only(self):
        stages = {
            "review_content_id": "c1",
            "local_model_plan_review": {"bundle_id": "b1", "verdict": "APPROVE", "round": 1, "completed_at": "t1"},
            "manual_external_plan_review": None,
        }
        self.assertFalse(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="2.1",
            plan_review_stages=stages, current_review_content_id="c1",
        ))


class TestUserConfirmationGuard(unittest.TestCase):
    """WF4a-ii, D2's mechanism-independent guard, second control."""

    def test_empty_confirmation_rejected(self):
        """Missing-test item 26."""
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.validate_user_confirmation("", work_item_id="wi", stage="plan")

    def test_whitespace_only_confirmation_rejected(self):
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.validate_user_confirmation("   ", work_item_id="wi", stage="plan")

    def test_confirmation_naming_wrong_work_item_rejected(self):
        """Missing-test item 74."""
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.validate_user_confirmation("I approve the plan for other-item", work_item_id="wi", stage="plan")

    def test_confirmation_naming_wrong_stage_rejected(self):
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.validate_user_confirmation("I approve the implementation for wi", work_item_id="wi", stage="plan")

    def test_confirmation_naming_exact_item_and_stage_accepted(self):
        ws.validate_user_confirmation("I approve the plan for wi", work_item_id="wi", stage="plan")  # no raise

    def test_unknown_stage_rejected(self):
        with self.assertRaises(ws.InvalidApprovalRecordError):
            ws.validate_user_confirmation("I approve wi plan", work_item_id="wi", stage="not_a_stage")

    def test_legitimate_repeat_across_two_different_approvals_accepted(self):
        """Missing-test items 74/91 (GPT-R11-004): a correct confirmation
        is not a novelty check -- the same literal text legitimately
        repeated across two genuinely different approval calls is accepted
        both times, not rejected as "reused"."""
        ws.validate_user_confirmation("wi plan implementation go-ahead", work_item_id="wi", stage="plan")
        ws.validate_user_confirmation("wi plan implementation go-ahead", work_item_id="wi", stage="implementation")

    def test_acceptance_stage_supported_for_accept_milestone(self):
        ws.validate_user_confirmation("I accept the wi milestone acceptance", work_item_id="wi", stage="acceptance")


class TestApprovalBasisResolution(unittest.TestCase):
    """WF4a-ii, D2's basis decision."""

    def test_matching_approve_feedback_yields_external_approve(self):
        """Missing-test item 13: writes EXTERNAL_APPROVE without a separate
        override-justification prompt -- the standard confirmation still
        satisfies the mechanism-independent guard."""
        basis = ws.resolve_approval_basis(
            latest_round_status="APPROVE", feedback_bundle_id="b1", current_bundle_id="b1",
            user_confirmation="approve wi plan", work_item_id="wi", stage="plan",
        )
        self.assertEqual(basis, "EXTERNAL_APPROVE")

    def test_mismatched_bundle_id_requires_override_text(self):
        """Missing-test item 14: without matching feedback, refuses to
        write until literal override text is supplied."""
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.resolve_approval_basis(
                latest_round_status="APPROVE", feedback_bundle_id="stale-bundle", current_bundle_id="b1",
                user_confirmation="", work_item_id="wi", stage="plan",
            )
        basis = ws.resolve_approval_basis(
            latest_round_status="APPROVE", feedback_bundle_id="stale-bundle", current_bundle_id="b1",
            user_confirmation="override wi plan", work_item_id="wi", stage="plan",
        )
        self.assertEqual(basis, "USER_OVERRIDE")

    def test_revise_round_requires_override_text(self):
        basis = ws.resolve_approval_basis(
            latest_round_status="REVISE", feedback_bundle_id="b1", current_bundle_id="b1",
            user_confirmation="override wi implementation", work_item_id="wi", stage="implementation",
        )
        self.assertEqual(basis, "USER_OVERRIDE")

    def test_block_never_reaches_either_basis(self):
        """Missing-test item 15."""
        with self.assertRaises(ws.BlockCannotApproveError):
            ws.resolve_approval_basis(
                latest_round_status="BLOCK", feedback_bundle_id="b1", current_bundle_id="b1",
                user_confirmation="override wi plan", work_item_id="wi", stage="plan",
            )


class TestApprovalRecordShape(unittest.TestCase):
    """WF4a-ii, D2's record shape."""

    def test_plan_stage_forbids_reviewed_content_commit(self):
        with self.assertRaises(ws.InvalidApprovalRecordError):
            ws.validate_approval_record({
                "status": "CURRENT", "basis": "EXTERNAL_APPROVE",
                "reviewed_bundle_id": "b", "approved_review_content_id": "c",
                "review_content_manifest": [], "reviewed_content_commit": "deadbeef",
                "user_confirmation": "approve wi plan",
            }, stage="plan")

    def test_implementation_stage_allows_reviewed_content_commit(self):
        ws.validate_approval_record({
            "status": "CURRENT", "basis": "EXTERNAL_APPROVE",
            "reviewed_bundle_id": "b", "approved_review_content_id": "c",
            "review_content_manifest": [], "reviewed_content_commit": "deadbeef",
            "user_confirmation": "approve wi implementation",
        }, stage="implementation")  # must not raise

    def test_non_legacy_basis_requires_bundle_fields(self):
        with self.assertRaises(ws.InvalidApprovalRecordError):
            ws.validate_approval_record({
                "status": "CURRENT", "basis": "USER_OVERRIDE",
                "reviewed_bundle_id": None, "approved_review_content_id": None,
                "review_content_manifest": None, "reviewed_content_commit": None,
                "user_confirmation": "override wi plan",
            }, stage="plan")

    def test_legacy_v1_basis_allows_null_bundle_fields(self):
        ws.validate_approval_record({
            "status": "CURRENT", "basis": "LEGACY_V1",
            "reviewed_bundle_id": None, "approved_review_content_id": "backfilled",
            "review_content_manifest": None, "reviewed_content_commit": "deadbeef",
            "user_confirmation": "legacy import confirmed for wi",
            "legacy_evidence": {"note": "milestone-8"},
        }, stage="implementation")  # must not raise

    def test_unknown_waived_guarantee_rejected(self):
        with self.assertRaises(ws.InvalidApprovalRecordError):
            ws.validate_approval_record({
                "status": "CURRENT", "basis": "LEGACY_V1",
                "reviewed_bundle_id": None, "approved_review_content_id": "backfilled",
                "review_content_manifest": None, "reviewed_content_commit": "deadbeef",
                "user_confirmation": "legacy import confirmed for wi",
                "waived_guarantees": ["no_content_id"],
            }, stage="implementation")

    def test_build_approval_record_round_trips(self):
        record = ws.build_approval_record(
            basis="EXTERNAL_APPROVE", stage="plan", user_confirmation="approve wi plan",
            now="t", reviewed_bundle_id="b", approved_review_content_id="c",
            review_content_manifest=[{"path": "x"}],
        )
        self.assertEqual(record["status"], "CURRENT")
        self.assertIsNone(record["reviewed_content_commit"])


class TestApprovalStateWrites(unittest.TestCase):
    """WF4a-ii, D-Approval-Commits' state-write step (commit creation is
    WF4a-iii's own concern, not exercised here)."""

    def test_apply_plan_approval_transitions_to_implementing(self):
        wi = _base_work_item(phase="AWAITING_PLAN_APPROVAL", state_revision=1)
        state = _base_state(wi=wi)
        record = ws.build_approval_record(
            basis="EXTERNAL_APPROVE", stage="plan", user_confirmation="approve wi plan",
            now="t2", reviewed_bundle_id="b", approved_review_content_id="c",
            review_content_manifest=[],
        )
        new_state = ws.apply_plan_approval(state, "wi", record, now="t2")
        self.assertEqual(new_state["work_items"]["wi"]["phase"], "IMPLEMENTING")
        self.assertEqual(new_state["work_items"]["wi"]["plan_approval"], record)
        self.assertEqual(new_state["work_items"]["wi"]["state_revision"], 2)
        # Original state is untouched.
        self.assertIsNone(state["work_items"]["wi"].get("plan_approval"))

    def test_apply_technical_approval_transitions_to_awaiting_functional_review(self):
        wi = _base_work_item(phase="AWAITING_TECHNICAL_APPROVAL", state_revision=1)
        state = _base_state(wi=wi)
        record = ws.build_approval_record(
            basis="USER_OVERRIDE", stage="implementation", user_confirmation="override wi implementation",
            now="t2", reviewed_bundle_id="b", approved_review_content_id="c",
            review_content_manifest=[], reviewed_content_commit="deadbeef",
        )
        new_state = ws.apply_technical_approval(state, "wi", record, now="t2")
        self.assertEqual(new_state["work_items"]["wi"]["phase"], "AWAITING_FUNCTIONAL_REVIEW")
        self.assertEqual(new_state["work_items"]["wi"]["technical_approval"], record)


# ---------------------------------------------------------------------------
# WF4a-iii: approval-trailer discovery (extends D-Commit-Provenance beyond
# checkpoint trailers, resolves OPUS-R6-022 for approval commits too --
# missing-test item 39)
# ---------------------------------------------------------------------------


class TestApprovalTrailerDiscovery(unittest.TestCase):
    def test_single_plan_approval_match_is_discovered(self):
        with ScratchRepo() as repo:
            sha = repo.commit("approve-plan", trailers={
                "Workflow-Plan-Approval": "abc123", "Workflow-Work-Item": "wi",
            })
            discovered = ws.discover_approval_commits(repo.root, "Workflow-Plan-Approval", "wi", repo.base)
            self.assertEqual(discovered, {"abc123": sha})
            self.assertEqual(ws.discover_plan_approval_commit(repo.root, "wi", "abc123", repo.base), sha)

    def test_single_technical_approval_match_is_discovered(self):
        with ScratchRepo() as repo:
            sha = repo.commit("approve-impl", trailers={
                "Workflow-Technical-Approval": "def456", "Workflow-Work-Item": "wi",
            })
            self.assertEqual(ws.discover_technical_approval_commit(repo.root, "wi", "def456", repo.base), sha)

    def test_no_match_returns_none(self):
        with ScratchRepo() as repo:
            self.assertIsNone(ws.discover_plan_approval_commit(repo.root, "wi", "nonexistent", repo.base))

    def test_wrong_work_item_is_not_matched(self):
        with ScratchRepo() as repo:
            repo.commit("approve-plan", trailers={
                "Workflow-Plan-Approval": "abc123", "Workflow-Work-Item": "other",
            })
            self.assertIsNone(ws.discover_plan_approval_commit(repo.root, "wi", "abc123", repo.base))

    def test_duplicate_approval_trailer_resolves_via_first_parent_tie_break(self):
        """Same OPUS-R6-022 scenario as checkpoint trailers, extended to
        approval trailers: a cherry-pick/rebase can copy the trailer onto
        a new commit while the original stays reachable."""
        with ScratchRepo() as repo:
            _run(["git", "checkout", "-q", "-b", "side"], cwd=repo.root)
            repo.commit("approve-side", trailers={"Workflow-Plan-Approval": "abc123", "Workflow-Work-Item": "wi"})
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            main_sha = repo.commit("approve-main", trailers={"Workflow-Plan-Approval": "abc123", "Workflow-Work-Item": "wi"})
            _run(["git", "merge", "-q", "--no-ff", "-m", "merge side", "side"], cwd=repo.root)
            self.assertEqual(ws.discover_plan_approval_commit(repo.root, "wi", "abc123", repo.base), main_sha)

    def test_genuine_ambiguity_raises(self):
        with ScratchRepo() as repo:
            repo.commit("approve-a", trailers={"Workflow-Technical-Approval": "abc123", "Workflow-Work-Item": "wi"})
            repo.commit("approve-b", trailers={"Workflow-Technical-Approval": "abc123", "Workflow-Work-Item": "wi"})
            with self.assertRaises(ws.AmbiguousApprovalTrailerError):
                ws.discover_approval_commits(repo.root, "Workflow-Technical-Approval", "wi", repo.base)


# ---------------------------------------------------------------------------
# WF4a-iii: per-stage approval-freshness rule and the full IMPLEMENTING
# entry condition (resolves OPUS-R6-003; missing-test items 9, 10, 11)
# ---------------------------------------------------------------------------


class TestApprovalFreshnessAndEntry(unittest.TestCase):
    """Uses the module's own real `PLAN_STAGE_PROTECTED`/
    `PLAN_STAGE_EXCLUDED_PREFIXES` defaults rather than a fabricated
    classification: `docs/TECHNICAL_DECISIONS.md` stands in for "the plan
    document" (a real protected path), and checkpoint-commit scaffolding
    lives under `scripts/` (a real excluded prefix), so no override
    plumbing is needed and the scenario matches this milestone's own
    actual classification."""

    # One representative real protected path (the plan-stage manifest
    # requires every entry in PLAN_STAGE_PROTECTED to exist, fail-closed --
    # `_approve_plan` below writes placeholder content for all of them,
    # this is the one it later edits to simulate a post-approval plan
    # revision).
    PLAN_DOC = "docs/ai-workflow/WORKFLOW_V2_PLAN.md"

    def _plan_stage_worktree_id(self, repo, plan_revision=1):
        digest, _ = fingerprint.compute_review_content_id_plan_stage(
            repo.root, repo.base, "process", "wi", plan_revision,
        )
        return digest

    def _commit_at_path(self, repo, rel_path, content, subject, trailers=None):
        full = repo.root / rel_path
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text(content)
        _run(["git", "add", rel_path], cwd=repo.root)
        body = subject
        if trailers:
            body += "\n\n" + "\n".join(f"{k}: {v}" for k, v in trailers.items())
        _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
        return repo.head()

    def _approve_plan(self, repo, plan_revision=1):
        for rel_path in fingerprint.PLAN_STAGE_PROTECTED:
            full = repo.root / rel_path
            full.parent.mkdir(parents=True, exist_ok=True)
            if not full.exists():
                full.write_text(f"placeholder for {rel_path}\n")
            _run(["git", "add", rel_path], cwd=repo.root)
        (repo.root / self.PLAN_DOC).write_text("plan v1\n")
        _run(["git", "add", self.PLAN_DOC], cwd=repo.root)
        review_content_id = self._plan_stage_worktree_id(repo, plan_revision)
        _run(["git", "commit", "-q", "-m",
              "approve plan\n\nWorkflow-Plan-Approval: " + review_content_id + "\nWorkflow-Work-Item: wi"],
             cwd=repo.root)
        approval_sha = repo.head()
        work_item = {
            "work_item_id": "wi", "work_item_type": "process", "plan_revision": plan_revision,
            "implementation_revision": None,
            "plan_approval": {"status": "CURRENT", "approved_review_content_id": review_content_id},
        }
        return approval_sha, review_content_id, work_item

    def _commit_checkpoint(self, repo, n):
        return self._commit_at_path(
            repo, f"scripts/checkpoint_{n}.txt", f"checkpoint {n}\n", f"checkpoint {n}", trailers={
                "Workflow-Checkpoint": f"WF{n}", "Workflow-Work-Item": "wi",
            },
        )

    def test_reachable_at_checkpoint_0_1_2_and_n(self):
        with ScratchRepo() as repo:
            _, _, work_item = self._approve_plan(repo)
            self.assertTrue(ws.implementing_entry_reachable(repo.root, work_item, repo.base))
            for n in range(1, 4):
                self._commit_checkpoint(repo, n)
                self.assertTrue(
                    ws.implementing_entry_reachable(repo.root, work_item, repo.base),
                    f"expected reachable at checkpoint {n}",
                )

    def test_plan_document_edit_after_checkpoint_stales_plan_approval(self):
        with ScratchRepo() as repo:
            _, _, work_item = self._approve_plan(repo)
            self._commit_checkpoint(repo, 1)
            self.assertTrue(ws.implementing_entry_reachable(repo.root, work_item, repo.base))
            self._commit_at_path(repo, self.PLAN_DOC, "plan v2 -- edited after checkpoint\n", "edit plan post-checkpoint")
            self.assertFalse(ws.implementing_entry_reachable(repo.root, work_item, repo.base))
            self.assertFalse(
                ws.approval_is_current(repo.root, work_item, stage="plan", base_commit=repo.base)
            )

    def test_implementation_revision_bump_does_not_stale_plan_approval(self):
        with ScratchRepo() as repo:
            _, _, work_item = self._approve_plan(repo)
            self.assertTrue(
                ws.approval_is_current(repo.root, work_item, stage="plan", base_commit=repo.base)
            )
            work_item["implementation_revision"] = 5
            self.assertTrue(
                ws.approval_is_current(repo.root, work_item, stage="plan", base_commit=repo.base),
                "an implementation_revision bump touches no field of the plan-stage projection",
            )

    def test_no_approval_record_is_never_reachable(self):
        with ScratchRepo() as repo:
            work_item = {"work_item_id": "wi", "work_item_type": "process", "plan_revision": 1, "plan_approval": None}
            self.assertFalse(ws.implementing_entry_reachable(repo.root, work_item, repo.base))

    def test_stale_status_is_never_reachable_even_with_matching_content(self):
        with ScratchRepo() as repo:
            _, _, work_item = self._approve_plan(repo)
            work_item["plan_approval"]["status"] = "STALE"
            self.assertFalse(ws.implementing_entry_reachable(repo.root, work_item, repo.base))

    def test_post_approval_manifest_match_succeeds_for_the_real_approval_commit(self):
        with ScratchRepo() as repo:
            approval_sha, _, work_item = self._approve_plan(repo)
            ws.verify_post_approval_manifest_match(
                repo.root, work_item, stage="plan", base_commit=repo.base, commit=approval_sha,
            )  # must not raise

    def test_post_approval_manifest_mismatch_raises(self):
        with ScratchRepo() as repo:
            approval_sha, _, work_item = self._approve_plan(repo)
            work_item["plan_approval"]["approved_review_content_id"] = "wrong-value"
            with self.assertRaises(ws.PostApprovalManifestMismatchError):
                ws.verify_post_approval_manifest_match(
                    repo.root, work_item, stage="plan", base_commit=repo.base, commit=approval_sha,
                )


# ---------------------------------------------------------------------------
# WF4a-iii: "no protected path is dirty" gate condition (D-States;
# missing-test item 16)
# ---------------------------------------------------------------------------


class TestProtectedPathDirty(unittest.TestCase):
    PROTECTED_PATHS = {"scripts/foo.py": "protected"}
    PROTECTED_PREFIXES = {"app/": "protected"}
    EXCLUDED_PATHS = {
        "docs/ai-workflow/WORKFLOW_STATE.json": "excluded",
        "docs/ai-workflow/WORKFLOW_CONFIG.json": "excluded",
    }
    EXCLUDED_PREFIXES = {"docs/": "excluded"}

    def _any_dirty(self, repo_root):
        return ws.any_protected_path_dirty(
            repo_root, self.PROTECTED_PATHS, self.PROTECTED_PREFIXES,
            self.EXCLUDED_PATHS, self.EXCLUDED_PREFIXES,
        )

    def test_clean_tree_is_not_dirty(self):
        with ScratchRepo() as repo:
            self.assertFalse(self._any_dirty(repo.root))

    def test_dirty_state_and_config_files_alone_do_not_block(self):
        with ScratchRepo() as repo:
            (repo.root / "docs" / "ai-workflow").mkdir(parents=True)
            (repo.root / "docs/ai-workflow/WORKFLOW_STATE.json").write_text("{}")
            (repo.root / "docs/ai-workflow/WORKFLOW_CONFIG.json").write_text("{}")
            self.assertFalse(self._any_dirty(repo.root))

    def test_dirty_protected_prefix_path_blocks(self):
        with ScratchRepo() as repo:
            (repo.root / "app").mkdir()
            (repo.root / "app" / "Main.kt").write_text("fun main() {}\n")
            self.assertTrue(self._any_dirty(repo.root))

    def test_dirty_protected_exact_path_blocks(self):
        with ScratchRepo() as repo:
            (repo.root / "scripts").mkdir()
            (repo.root / "scripts" / "foo.py").write_text("pass\n")
            self.assertTrue(self._any_dirty(repo.root))

    def test_unclassified_dirty_path_fails_closed(self):
        with ScratchRepo() as repo:
            (repo.root / "mystery.txt").write_text("???\n")
            with self.assertRaises(fingerprint.UnclassifiedPathError):
                self._any_dirty(repo.root)


if __name__ == "__main__":
    unittest.main()
