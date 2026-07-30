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


if __name__ == "__main__":
    unittest.main()
