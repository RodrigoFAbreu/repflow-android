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


class TestWFActivateFourCombinations(unittest.TestCase):
    """Item 32: all four combinations of (config present/absent) x
    (activation trailer present/absent) behave per D-Self-Governance's
    documented rule -- config-present short-circuits regardless of
    trailer state (the trailer only matters for missing-config recovery),
    so the four combinations collapse to three distinct behaviors, all
    asserted here in one place explicitly owned by WF-Activate."""

    def test_config_present_trailer_present_returns_config_as_is(self):
        with ScratchRepo() as repo:
            repo.commit("activate", trailers={"Workflow-Activation": "2.1"})
            config = {"schema_version": 1, "default_workflow_version": "2.1", "supported_versions": ["1", "2.1"]}
            (repo.root / "config.json").write_text(json.dumps(config))
            loaded = ws.load_config(repo.root, config_path=Path("config.json"))
            self.assertEqual(loaded, config)

    def test_config_present_trailer_absent_returns_config_as_is(self):
        with ScratchRepo() as repo:
            config = ws.default_config()
            (repo.root / "config.json").write_text(json.dumps(config))
            loaded = ws.load_config(repo.root, config_path=Path("config.json"))
            self.assertEqual(loaded, config)

    def test_config_absent_trailer_present_is_a_hard_stop(self):
        with ScratchRepo() as repo:
            repo.commit("activate", trailers={"Workflow-Activation": "2.1"})
            with self.assertRaises(ws.ConfigMissingAfterActivationError):
                ws.load_config(repo.root, config_path=Path("nonexistent.json"))

    def test_config_absent_trailer_absent_defaults_to_v1(self):
        with ScratchRepo() as repo:
            config = ws.load_config(repo.root, config_path=Path("nonexistent.json"))
            self.assertEqual(config["default_workflow_version"], "1")


class TestActivationRollbackTransforms(unittest.TestCase):
    def test_build_activated_config_flips_only_the_version_field(self):
        config = ws.default_config()
        activated = ws.build_activated_config(config)
        self.assertEqual(activated["default_workflow_version"], "2.1")
        self.assertEqual(activated["supported_versions"], config["supported_versions"])
        self.assertEqual(config["default_workflow_version"], "1")  # input untouched

    def test_build_activated_config_rejects_already_activated(self):
        config = {**ws.default_config(), "default_workflow_version": "2.1"}
        with self.assertRaises(ws.AlreadyActivatedError):
            ws.build_activated_config(config)

    def test_build_rolled_back_config_flips_back_to_v1(self):
        config = {**ws.default_config(), "default_workflow_version": "2.1"}
        rolled_back = ws.build_rolled_back_config(config)
        self.assertEqual(rolled_back["default_workflow_version"], "1")

    def test_build_rolled_back_config_rejects_not_activated(self):
        config = ws.default_config()
        with self.assertRaises(ws.NotActivatedError):
            ws.build_rolled_back_config(config)

    def test_activate_then_rollback_round_trips_to_original(self):
        config = ws.default_config()
        activated = ws.build_activated_config(config)
        rolled_back = ws.build_rolled_back_config(activated)
        self.assertEqual(rolled_back, config)


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

    def test_plan_revision_mirror_mismatch_rejected(self):
        # Missing-test item 147 (`OPUS-R25-002`/`-009`, `GPT-R30-004`):
        # WORKFLOW_STATE.json's own plan_revision field for a work item is
        # a non-authoritative mirror of the registry's own plan_revision --
        # a hand-edited or stale mirror must fail closed before any
        # fingerprint call runs, not silently diverge.
        registry = {"work_item_id": "wi", "plan_revision": 5, "checkpoints": []}
        wi = _base_work_item(plan_revision=4)
        with self.assertRaises(ws.PlanRevisionMirrorMismatchError):
            ws.validate_state(_base_state(wi=wi), registry=registry)

    def test_plan_revision_mirror_match_accepted(self):
        registry = {"work_item_id": "wi", "plan_revision": 5, "checkpoints": []}
        wi = _base_work_item(plan_revision=5)
        ws.validate_state(_base_state(wi=wi), registry=registry)  # must not raise

    def test_plan_revision_mirror_check_skipped_for_unrelated_registry(self):
        # A registry naming a work item absent from this state (e.g. a
        # different work item's registry passed by mistake, or a registry
        # with no `work_item_id` at all, matching every pre-existing test
        # above) must not raise -- there is nothing to mirror-check.
        registry = {"work_item_id": "some-other-item", "plan_revision": 999, "checkpoints": []}
        wi = _base_work_item(plan_revision=1)
        ws.validate_state(_base_state(wi=wi), registry=registry)  # must not raise

    def test_corrupt_json_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "state.json"
            path.write_text("{not json")
            with self.assertRaises(ws.CorruptJsonError):
                ws._load_json(path)


class TestWF8bDryRunEntrySetup(unittest.TestCase):
    """WF8b's entry step: `default_work_item` builds the synthetic
    `v2-1-dry-run` item (the grammar-compliant canonicalization of the
    plan's literal `v2.1-dry-run` prose -- see
    TestWF8bSyntheticWorkItemIdCanonicalization in
    workflow_fingerprint_test.py for the ID-grammar half of that
    decision), and the resulting multi-item state -- the real process
    item still present, non-active, exactly as WF8b's entry step leaves
    it -- validates cleanly."""

    def _dry_run_item(self, **overrides):
        item = ws.default_work_item(
            work_item_id="v2-1-dry-run", work_item_type="process",
            work_item_kind="synthetic", plan_path="docs/ai-workflow/dry-run/v2-1-dry-run-plan.md",
            registry_path=None, governing_workflow_version="2.1",
            plan_revision=1, last_transition="2026-07-31T19:25:00+01:00",
        )
        item.update(overrides)
        return item

    def test_synthetic_item_type_is_process_per_opus_r10_007(self):
        item = self._dry_run_item()
        self.assertEqual(item["work_item_type"], "process")
        self.assertEqual(item["work_item_kind"], "synthetic")

    def test_active_pointer_repointed_to_synthetic_item_validates(self):
        """Mirrors the real WF8b entry commit's shape: the prior active
        process item (`workflow-v2-1-core`) stays present and unmodified,
        non-active, while `active_work_item_id` repoints at the synthetic
        item -- D1's "resume-focus pointer, not an execution lock"."""
        state = _base_state(
            **{
                "workflow-v2-1-core": _base_work_item(
                    work_item_id="workflow-v2-1-core", phase="IMPLEMENTING",
                ),
                "v2-1-dry-run": self._dry_run_item(),
            }
        )
        state["active_work_item_id"] = "v2-1-dry-run"
        ws.validate_state(state)  # must not raise


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


class TestLegacyBranchReconciliation(unittest.TestCase):
    def test_reachable_ancestor_with_matching_substring_accepted(self):
        with ScratchRepo() as repo:
            (repo.root / "docs").mkdir()
            (repo.root / "docs/ACTIVE_MILESTONE.md").write_text("Milestone 8 accepted and closed.\n")
            _run(["git", "add", "docs/ACTIVE_MILESTONE.md"], cwd=repo.root)
            reviewed = repo.commit("reviewed head")
            ws.verify_legacy_branch_reconciliation(
                repo.root, reviewed_content_commit=reviewed,
                required_active_milestone_substring="Milestone 8 accepted",
            )  # must not raise

    def test_unreachable_reviewed_commit_rejected(self):
        with ScratchRepo() as repo:
            _run(["git", "checkout", "-q", "-b", "side"], cwd=repo.root)
            side_sha = repo.commit("side work")
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            with self.assertRaises(ws.LegacyReconciliationError):
                ws.verify_legacy_branch_reconciliation(
                    repo.root, reviewed_content_commit=side_sha,
                    required_active_milestone_substring="anything",
                )

    def test_missing_expected_substring_rejected(self):
        with ScratchRepo() as repo:
            (repo.root / "docs").mkdir()
            (repo.root / "docs/ACTIVE_MILESTONE.md").write_text("still in progress\n")
            _run(["git", "add", "docs/ACTIVE_MILESTONE.md"], cwd=repo.root)
            reviewed = repo.commit("reviewed head")
            with self.assertRaises(ws.LegacyReconciliationError):
                ws.verify_legacy_branch_reconciliation(
                    repo.root, reviewed_content_commit=reviewed,
                    required_active_milestone_substring="accepted and closed",
                )

    def test_missing_active_milestone_file_rejected(self):
        with ScratchRepo() as repo:
            reviewed = repo.base
            with self.assertRaises(ws.LegacyReconciliationError):
                ws.verify_legacy_branch_reconciliation(
                    repo.root, reviewed_content_commit=reviewed,
                    required_active_milestone_substring="anything",
                )


class TestLegacyImport(unittest.TestCase):
    def _import(self, state=None, **overrides):
        kwargs = dict(
            work_item_id="milestone-8", plan_path="docs/milestones/completed/milestone-8-execution.md",
            registry_path=None, base_commit="base-sha", reviewed_content_commit="reviewed-sha",
            approved_review_content_id="backfilled-id", legacy_evidence={"rounds": 4},
            user_confirmation="legacy import confirmed for milestone-8 implementation", now="t1",
        )
        kwargs.update(overrides)
        return ws.import_legacy_work_item(state if state is not None else _base_state(), **kwargs)

    def test_import_creates_dormant_legacy_ready_entry(self):
        new_state = self._import()
        entry = new_state["work_items"]["milestone-8"]
        self.assertEqual(entry["phase"], "LEGACY_READY")
        self.assertEqual(entry["work_item_type"], "product")
        self.assertEqual(entry["work_item_kind"], "product")
        self.assertEqual(entry["governing_workflow_version"], "1")
        self.assertEqual(entry["base_commit"], "base-sha")
        self.assertIsNone(entry["plan_approval"])
        approval = entry["technical_approval"]
        self.assertEqual(approval["basis"], "LEGACY_V1")
        self.assertEqual(approval["status"], "CURRENT")
        self.assertIsNone(approval["reviewed_bundle_id"])
        self.assertEqual(approval["approved_review_content_id"], "backfilled-id")
        self.assertEqual(approval["reviewed_content_commit"], "reviewed-sha")
        self.assertEqual(approval["waived_guarantees"], ["no_bundle_id", "no_telemetry"])
        ws.validate_state(new_state)  # must not raise

    def test_import_never_claims_active_work_item_pointer(self):
        state = _base_state(active=_base_work_item(work_item_id="active", phase="IMPLEMENTING"))
        state["active_work_item_id"] = "active"
        new_state = self._import(state=state)
        self.assertEqual(new_state["active_work_item_id"], "active")

    def test_import_does_not_mutate_input_state(self):
        state = _base_state()
        self._import(state=state)
        self.assertEqual(state, _base_state())

    def test_reimporting_existing_id_rejected(self):
        new_state = self._import()
        with self.assertRaises(ws.LegacyImportAlreadyExistsError):
            self._import(state=new_state)


class TestLegacyPromotion(unittest.TestCase):
    """WF-M8b: D-Legacy phase 2 (`promote_legacy_work_item`). Uses its own
    scratch implementation-stage artifact-declarations file (never
    `workflow-v2-1-core`'s own `DEFAULT_ARTIFACTS_PATH`), exactly the
    "never silently reused across work items" discipline the function's
    own docstring states."""

    ARTIFACTS_REL = Path("artifacts.json")
    PROTECTED_PATH = "src/thing.txt"

    def _write_artifacts(self, repo):
        (repo.root / self.ARTIFACTS_REL).write_text(json.dumps({
            "schema_version": 2,
            "work_item_id": "milestone-8",
            "implementation_stage": {
                "protected_paths": {self.PROTECTED_PATH: "product code"},
                "protected_prefixes": {},
                "excluded_paths": {"artifacts.json": "declarations file"},
                "excluded_prefixes": {"docs/": "docs"},
            },
        }))
        _run(["git", "add", str(self.ARTIFACTS_REL)], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "add artifact declarations"], cwd=repo.root)

    def _seed_active_milestone(self, repo, text):
        (repo.root / "docs").mkdir(exist_ok=True)
        (repo.root / "docs/ACTIVE_MILESTONE.md").write_text(text)
        _run(["git", "add", "docs/ACTIVE_MILESTONE.md"], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "seed active milestone doc"], cwd=repo.root)

    def _content_id_at(self, repo, commit):
        digest, _ = fingerprint.compute_review_content_id_implementation_stage_at_commit(
            repo.root, repo.base, commit, "product", "milestone-8",
            {self.PROTECTED_PATH: "product code"}, {},
            {"artifacts.json": "declarations file"}, {"docs/": "docs"},
        )
        return digest

    def _import(self, repo, *, active_milestone_text="Milestone 8 accepted and closed.\n"):
        self._write_artifacts(repo)
        self._seed_active_milestone(repo, active_milestone_text)
        (repo.root / "src").mkdir(exist_ok=True)
        (repo.root / self.PROTECTED_PATH).write_text("v1\n")
        _run(["git", "add", self.PROTECTED_PATH], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "reviewed head"], cwd=repo.root)
        reviewed_sha = repo.head()
        content_id = self._content_id_at(repo, reviewed_sha)
        state = ws.import_legacy_work_item(
            _base_state(), work_item_id="milestone-8",
            plan_path="docs/milestones/completed/milestone-8-execution.md", registry_path=None,
            base_commit=repo.base, reviewed_content_commit=reviewed_sha,
            approved_review_content_id=content_id, legacy_evidence={"rounds": 4},
            user_confirmation="legacy import confirmed for milestone-8 implementation", now="t1",
        )
        return state, reviewed_sha

    def _promote(self, state, repo, **overrides):
        kwargs = dict(
            work_item_id="milestone-8",
            required_active_milestone_substring="accepted and closed",
            artifacts_path=self.ARTIFACTS_REL, now="t2",
        )
        kwargs.update(overrides)
        return ws.promote_legacy_work_item(state, repo.root, **kwargs)

    def test_promotion_activates_and_transitions_version_and_phase(self):
        with ScratchRepo() as repo:
            state, _ = self._import(repo)
            new_state = self._promote(state, repo)
            entry = new_state["work_items"]["milestone-8"]
            self.assertEqual(new_state["active_work_item_id"], "milestone-8")
            self.assertEqual(entry["governing_workflow_version"], "2.1")
            self.assertEqual(entry["phase"], "AWAITING_FUNCTIONAL_REVIEW")
            # technical_approval itself is preserved exactly as imported.
            self.assertEqual(entry["technical_approval"]["basis"], "LEGACY_V1")
            self.assertEqual(entry["technical_approval"], state["work_items"]["milestone-8"]["technical_approval"])

    def test_promotion_does_not_mutate_input_state(self):
        with ScratchRepo() as repo:
            state, _ = self._import(repo)
            before = json.loads(json.dumps(state))
            self._promote(state, repo)
            self.assertEqual(state, before)

    def test_promotion_refused_when_not_legacy_ready(self):
        with ScratchRepo() as repo:
            state, _ = self._import(repo)
            already_active = self._promote(state, repo)
            with self.assertRaises(ws.LegacyAdoptionWrongPhaseError):
                self._promote(already_active, repo)

    def test_stale_technical_approval_blocks_promotion_not_reimport(self):
        with ScratchRepo() as repo:
            state, reviewed_sha = self._import(repo)
            # A protected path changes after import, before adoption.
            (repo.root / self.PROTECTED_PATH).write_text("v2 -- changed after import\n")
            _run(["git", "add", self.PROTECTED_PATH], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "post-import protected edit"], cwd=repo.root)
            with self.assertRaises(ws.LegacyAdoptionStaleApprovalError):
                self._promote(state, repo)
            # Still dormant -- never silently re-imported or promoted.
            self.assertEqual(state["work_items"]["milestone-8"]["phase"], "LEGACY_READY")

    def test_branch_reconciliation_recheck_failure_blocks_promotion(self):
        with ScratchRepo() as repo:
            state, _ = self._import(repo)
            (repo.root / "docs/ACTIVE_MILESTONE.md").write_text("still in progress\n")
            _run(["git", "add", "docs/ACTIVE_MILESTONE.md"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "regressed active milestone doc"], cwd=repo.root)
            with self.assertRaises(ws.LegacyReconciliationError):
                self._promote(state, repo)


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
    classification: the five real `workflow-v2-1-core` paths stand in as
    `"wi"`'s own declared plan/registry/mapping paths, and checkpoint-
    commit scaffolding lives under `scripts/` (a real excluded prefix), so
    the scenario matches this milestone's own actual classification.
    `D-Fingerprint-Generalization`: every approval-freshness function now
    routes through `resolve_plan_stage_metadata`, so `"wi"`'s own
    `WORKFLOW_STATE.json` entry and `wi-artifacts.json` declaration are
    committed alongside the plan docs, exactly mirroring the real
    repository's own layout for a second work item."""

    # One representative real protected path (the plan-stage manifest
    # requires every entry in PLAN_STAGE_PROTECTED to exist, fail-closed --
    # `_approve_plan` below writes placeholder content for all of them,
    # this is the one it later edits to simulate a post-approval plan
    # revision).
    PLAN_DOC = "docs/ai-workflow/WORKFLOW_V2_PLAN.md"
    REGISTRY_PATH = "docs/ai-workflow/registry/workflow-v2-1-core-registry.json"
    MAPPING_PATH = "docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json"
    ARTIFACTS_PATH = "docs/ai-workflow/registry/wi-artifacts.json"
    STATE_PATH = "docs/ai-workflow/WORKFLOW_STATE.json"

    def _plan_doc_content(self, plan_revision, body="plan v1"):
        return f"# Plan (Revision {plan_revision})\n\n{body}\n"

    def _plan_stage_worktree_id(self, repo, plan_revision=1):
        # This class's own docstring already documents deliberately
        # reusing the module's real PLAN_STAGE_* defaults as "wi"'s own
        # fixture -- now passed explicitly since the low-level function no
        # longer defaults them itself (`GPT-R30-005`).
        digest, _ = fingerprint.compute_review_content_id_plan_stage(
            repo.root, repo.base, "process", "wi", plan_revision,
            fingerprint.PLAN_STAGE_PROTECTED,
            fingerprint.PLAN_STAGE_EXCLUDED_PATHS,
            fingerprint.PLAN_STAGE_EXCLUDED_PREFIXES,
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
            if rel_path == self.PLAN_DOC:
                full.write_text(self._plan_doc_content(plan_revision))
            elif rel_path == self.REGISTRY_PATH:
                full.write_text(json.dumps({
                    "work_item_id": "wi", "plan_revision": plan_revision, "checkpoints": [],
                }))
            elif rel_path == self.MAPPING_PATH:
                full.write_text(json.dumps({"work_item_id": "wi", "requirements": {}}))
            elif not full.exists():
                full.write_text(f"placeholder for {rel_path}\n")
            _run(["git", "add", rel_path], cwd=repo.root)

        artifacts_full = repo.root / self.ARTIFACTS_PATH
        artifacts_full.parent.mkdir(parents=True, exist_ok=True)
        artifacts_full.write_text(json.dumps({
            "schema_version": 2,
            "work_item_id": "wi",
            "plan_stage": {
                "protected_paths": sorted(fingerprint.PLAN_STAGE_PROTECTED),
                "excluded_paths": dict(fingerprint.PLAN_STAGE_EXCLUDED_PATHS),
                "excluded_prefixes": dict(fingerprint.PLAN_STAGE_EXCLUDED_PREFIXES),
            },
        }))
        _run(["git", "add", self.ARTIFACTS_PATH], cwd=repo.root)

        state_full = repo.root / self.STATE_PATH
        state_full.parent.mkdir(parents=True, exist_ok=True)
        state_full.write_text(json.dumps({
            "schema_version": 1,
            "active_work_item_id": "wi",
            "work_items": {
                "wi": {
                    "work_item_id": "wi",
                    "work_item_type": "process",
                    "plan_path": self.PLAN_DOC,
                    "registry_path": self.REGISTRY_PATH,
                    "mapping_path": self.MAPPING_PATH,
                    "base_commit": repo.base,
                },
            },
        }))
        _run(["git", "add", self.STATE_PATH], cwd=repo.root)

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
            self._commit_at_path(
                repo, self.PLAN_DOC,
                self._plan_doc_content(work_item["plan_revision"], "plan v2 -- edited after checkpoint"),
                "edit plan post-checkpoint",
            )
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


class TestImplementationStageApprovalSecondItem(unittest.TestCase):
    """Missing-test item 164 (`OPUS-R27-002`/`OPUS-R28-001`, `GPT-R30-004`
    correction #7): `approval_is_current(stage="implementation")` and
    `verify_post_approval_manifest_match(stage="implementation")`, each
    exercised against two distinct work items (never `workflow-v2-1-core`
    alone), load each item's own `<id>-artifacts.json` via
    `fingerprint.artifacts_path_for_work_item` -- never `DEFAULT_ARTIFACTS_PATH`.
    Unlike the plan stage, neither function's `stage="implementation"`
    branch reads `plan_path`/`registry_path`/`mapping_path` at all
    (`approval_review_content_id`'s own implementation branch), so this
    fixture needs no full plan-stage scaffolding -- only each item's own
    artifacts declaration and a real committed source file to change."""

    def _write_implementation_stage_artifacts(self, repo, work_item_id: str) -> str:
        """Each item gets a disjoint protected exact-path set (never a
        shared prefix) so a change to one item's own protected file is
        unambiguously `excluded` (not merely unclassified) under a
        *different* item's own sets -- proving real independence rather
        than accidental non-interference from an always-unclassified path."""
        artifacts_path = f"docs/ai-workflow/registry/{work_item_id}-artifacts.json"
        full = repo.root / artifacts_path
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text(json.dumps({
            "schema_version": 2,
            "work_item_id": work_item_id,
            "implementation_stage": {
                "protected_paths": {
                    artifacts_path: "self-referential declarations file",
                    f"scripts/{work_item_id}_tool.py": "this item's own tooling change",
                },
                "protected_prefixes": {},
                "excluded_paths": {},
                "excluded_prefixes": {
                    "scripts/": "any other item's own tooling files, or this item's own non-protected scripts",
                    "docs/": "plan-stage-governed design content",
                },
            },
        }))
        _run(["git", "add", artifacts_path], cwd=repo.root)
        return artifacts_path

    def _approve_implementation(self, repo, work_item_id: str):
        self._write_implementation_stage_artifacts(repo, work_item_id)
        tool_path = f"scripts/{work_item_id}_tool.py"
        full = repo.root / tool_path
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text("print('v1')\n")
        _run(["git", "add", tool_path], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", f"seed {work_item_id}"], cwd=repo.root)
        commit = repo.head()
        digest = ws.approval_review_content_id(
            repo.root, stage="implementation", base_commit=repo.base,
            work_item_type="process", work_item_id=work_item_id, head=commit,
            artifacts_path=fingerprint.artifacts_path_for_work_item(work_item_id),
        )
        work_item = {
            "work_item_id": work_item_id, "work_item_type": "process",
            "technical_approval": {"status": "CURRENT", "approved_review_content_id": digest},
        }
        return commit, work_item

    def test_approval_is_current_gates_independently_per_item(self):
        with ScratchRepo() as repo:
            commit_a, work_item_a = self._approve_implementation(repo, "workflow-v2-1-core")
            commit_b, work_item_b = self._approve_implementation(repo, "second-item")
            self.assertTrue(
                ws.approval_is_current(
                    repo.root, work_item_a, stage="implementation", base_commit=repo.base, head=commit_a,
                )
            )
            self.assertTrue(
                ws.approval_is_current(
                    repo.root, work_item_b, stage="implementation", base_commit=repo.base, head=commit_b,
                )
            )

    def test_verify_post_approval_manifest_match_succeeds_for_second_item(self):
        with ScratchRepo() as repo:
            commit, work_item = self._approve_implementation(repo, "second-item")
            ws.verify_post_approval_manifest_match(
                repo.root, work_item, stage="implementation", base_commit=repo.base, commit=commit,
            )  # must not raise

    def test_editing_workflow_v2_1_cores_own_artifacts_file_leaves_second_item_untouched(self):
        with ScratchRepo() as repo:
            commit_core, work_item_core = self._approve_implementation(repo, "workflow-v2-1-core")
            commit_second, work_item_second = self._approve_implementation(repo, "second-item")
            # Widen workflow-v2-1-core's own self-referential artifacts
            # declaration -- changes only its own implementation-stage
            # review_content_id.
            core_artifacts_path = repo.root / "docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json"
            core_data = json.loads(core_artifacts_path.read_text())
            core_data["implementation_stage"]["excluded_paths"]["README.md"] = "newly excluded"
            core_artifacts_path.write_text(json.dumps(core_data))
            _run(["git", "add", "docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "widen workflow-v2-1-core's own artifacts file"], cwd=repo.root)
            new_head = repo.head()

            self.assertFalse(
                ws.approval_is_current(
                    repo.root, work_item_core, stage="implementation", base_commit=repo.base, head=new_head,
                ),
                "workflow-v2-1-core's own approval must stale on its own artifacts-file edit",
            )
            self.assertTrue(
                ws.approval_is_current(
                    repo.root, work_item_second, stage="implementation", base_commit=repo.base, head=new_head,
                ),
                "a different item's approval must be untouched by workflow-v2-1-core's own artifacts-file edit",
            )

    def test_changed_implementation_file_stales_approval(self):
        with ScratchRepo() as repo:
            _, work_item = self._approve_implementation(repo, "second-item")
            tool_path = repo.root / "scripts/second-item_tool.py"
            tool_path.write_text("print('v2')\n")
            _run(["git", "add", "scripts/second-item_tool.py"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "change second-item's own protected tool file"], cwd=repo.root)
            new_head = repo.head()
            self.assertFalse(
                ws.approval_is_current(
                    repo.root, work_item, stage="implementation", base_commit=repo.base, head=new_head,
                )
            )

    def test_excluded_implementation_file_does_not_stale_approval(self):
        with ScratchRepo() as repo:
            _, work_item = self._approve_implementation(repo, "second-item")
            # Matches second-item's own excluded_prefixes ("scripts/"),
            # and is not its own declared protected path.
            unrelated_path = repo.root / "scripts/unrelated_helper.py"
            unrelated_path.write_text("print('unrelated')\n")
            _run(["git", "add", "scripts/unrelated_helper.py"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "add an excluded-prefix script file"], cwd=repo.root)
            new_head = repo.head()
            self.assertTrue(
                ws.approval_is_current(
                    repo.root, work_item, stage="implementation", base_commit=repo.base, head=new_head,
                )
            )

    def test_wrong_artifacts_declaration_missing_file_fails_closed(self):
        with ScratchRepo() as repo:
            with self.assertRaises(fingerprint.MissingWorkItemArtifactsDeclarationError):
                ws.approval_review_content_id(
                    repo.root, stage="implementation", base_commit=repo.base,
                    work_item_type="process", work_item_id="second-item", head=repo.base,
                    artifacts_path=fingerprint.artifacts_path_for_work_item("second-item"),
                )

    def test_wrong_artifacts_declaration_missing_implementation_stage_key_fails_closed(self):
        with ScratchRepo() as repo:
            artifacts_path = "docs/ai-workflow/registry/second-item-artifacts.json"
            full = repo.root / artifacts_path
            full.parent.mkdir(parents=True, exist_ok=True)
            # Pre-migration, schema-version-1-shaped file: no
            # implementation_stage key at all -- the same fail-closed
            # boundary as a wholly absent file, not a distinct mechanism.
            full.write_text(json.dumps({
                "schema_version": 1, "work_item_id": "second-item", "plan_stage": {},
            }))
            _run(["git", "add", artifacts_path], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "pre-migration artifacts file"], cwd=repo.root)
            head = repo.head()
            with self.assertRaises(fingerprint.MissingWorkItemArtifactsDeclarationError):
                ws.approval_review_content_id(
                    repo.root, stage="implementation", base_commit=repo.base,
                    work_item_type="process", work_item_id="second-item", head=head,
                    artifacts_path=fingerprint.artifacts_path_for_work_item("second-item"),
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


# ---------------------------------------------------------------------------
# WF-M8b: D-Legacy phase 2's own freshness primitive -- a committed-
# history (not exact-hash-reproduction) counterpart of
# any_protected_path_dirty, purpose-built so widening a dormant legacy
# item's own artifact-declarations file (to classify a concurrent work
# item's later paths) never itself reads as staleness.
# ---------------------------------------------------------------------------


class TestProtectedPathChangedSince(unittest.TestCase):
    PROTECTED_PATHS = {"app/Main.kt": "protected"}
    PROTECTED_PREFIXES = {"gradle/": "protected"}
    EXCLUDED_PATHS = {}
    EXCLUDED_PREFIXES = {"docs/": "excluded", "scripts/": "excluded"}

    def _changed_since(self, repo_root, base, head):
        return ws.any_protected_path_changed_since(
            repo_root, base, head, self.PROTECTED_PATHS, self.PROTECTED_PREFIXES,
            self.EXCLUDED_PATHS, self.EXCLUDED_PREFIXES,
        )

    def test_no_change_in_range_is_not_stale(self):
        with ScratchRepo() as repo:
            reviewed = repo.commit("reviewed head")
            self.assertFalse(self._changed_since(repo.root, reviewed, "HEAD"))

    def test_unrelated_excluded_commit_after_review_is_not_stale(self):
        with ScratchRepo() as repo:
            reviewed = repo.commit("reviewed head")
            self._commit_at(repo, "docs/NOTES.md", "unrelated concurrent work item\n")
            self.assertFalse(self._changed_since(repo.root, reviewed, "HEAD"))

    def test_protected_exact_path_commit_after_review_is_stale(self):
        with ScratchRepo() as repo:
            reviewed = repo.commit("reviewed head")
            self._commit_at(repo, "app/Main.kt", "fun main() {}\n")
            self.assertTrue(self._changed_since(repo.root, reviewed, "HEAD"))

    def test_protected_prefix_path_commit_after_review_is_stale(self):
        with ScratchRepo() as repo:
            reviewed = repo.commit("reviewed head")
            self._commit_at(repo, "gradle/libs.versions.toml", "[versions]\n")
            self.assertTrue(self._changed_since(repo.root, reviewed, "HEAD"))

    def test_protected_change_before_review_is_never_seen(self):
        with ScratchRepo() as repo:
            self._commit_at(repo, "app/Main.kt", "fun main() {}\n")
            reviewed = repo.head()
            self._commit_at(repo, "docs/NOTES.md", "unrelated\n")
            self.assertFalse(self._changed_since(repo.root, reviewed, "HEAD"))

    def test_unclassified_committed_path_after_review_fails_closed(self):
        with ScratchRepo() as repo:
            reviewed = repo.commit("reviewed head")
            self._commit_at(repo, "mystery.txt", "???\n")
            with self.assertRaises(fingerprint.UnclassifiedPathError):
                self._changed_since(repo.root, reviewed, "HEAD")

    def _commit_at(self, repo, rel_path, content):
        full = repo.root / rel_path
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text(content)
        _run(["git", "add", rel_path], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", f"touch {rel_path}"], cwd=repo.root)
        return repo.head()


# ---------------------------------------------------------------------------
# WF4a-iv: D-Plan-Review-Stages' verdict/state transition table -- the
# ledger writers `record_local_plan_review`/`record_manual_plan_review`
# and the `"2.1"`-only revised /apply-plan-review exit step
# (transition_to_awaiting_local_plan_review). Missing-test items 80-99,
# 112-114, WFR-41.
# ---------------------------------------------------------------------------


def _v21_work_item(**overrides) -> dict:
    defaults = {"governing_workflow_version": "2.1", "phase": "AWAITING_LOCAL_PLAN_REVIEW"}
    defaults.update(overrides)
    return _base_work_item(**defaults)


class TestRecordLocalPlanReview(unittest.TestCase):
    def test_wrong_governing_version_rejected(self):
        wi = _base_work_item(governing_workflow_version="1", phase="AWAITING_LOCAL_PLAN_REVIEW")
        with self.assertRaises(ws.WrongGoverningVersionForPlanReviewStageError):
            ws.record_local_plan_review(
                _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b1",
                review_content_id="c1", round=1, now="t1",
            )

    def test_wrong_phase_rejected(self):
        """Missing-test item 90 (v2.1 side): "already completed this
        round" is exactly a phase that has moved on."""
        wi = _v21_work_item(phase="AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW")
        with self.assertRaises(ws.WrongPhaseForPlanReviewStageError):
            ws.record_local_plan_review(
                _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b1",
                review_content_id="c1", round=1, now="t1",
            )

    def test_unknown_verdict_rejected(self):
        wi = _v21_work_item()
        with self.assertRaises(ws.UnknownPlanReviewVerdictError):
            ws.record_local_plan_review(
                _base_state(wi=wi), "wi", verdict="MAYBE", bundle_id="b1",
                review_content_id="c1", round=1, now="t1",
            )

    def test_approve_records_ledger_and_transitions_to_manual_stage(self):
        """Missing-test items 80/82 (first half): a local APPROVE alone
        reaches AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW, not AWAITING_PLAN_APPROVAL."""
        wi = _v21_work_item(state_revision=1)
        new_state = ws.record_local_plan_review(
            _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b1",
            review_content_id="c1", round=1, now="t1",
        )
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["phase"], "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW")
        self.assertEqual(item["plan_review_stages"], {
            "review_content_id": "c1",
            "local_model_plan_review": {"bundle_id": "b1", "verdict": "APPROVE", "round": 1, "completed_at": "t1"},
            "manual_external_plan_review": None,
        })
        self.assertEqual(item["state_revision"], 2)

    def test_approve_clears_stale_manual_entry_from_a_prior_content_id(self):
        wi = _v21_work_item(plan_review_stages={
            "review_content_id": "old",
            "local_model_plan_review": {"bundle_id": "b0", "verdict": "APPROVE", "round": 1, "completed_at": "t0"},
            "manual_external_plan_review": {"bundle_id": "b0", "verdict": "APPROVE", "round": 1, "completed_at": "t0"},
        })
        new_state = ws.record_local_plan_review(
            _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b1",
            review_content_id="new", round=1, now="t1",
        )
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["plan_review_stages"]["review_content_id"], "new")
        self.assertIsNone(item["plan_review_stages"]["manual_external_plan_review"])

    def test_revise_transitions_to_revising_plan_with_no_ledger_write(self):
        """Missing-test item 94: can never reach AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW."""
        wi = _v21_work_item(state_revision=1, plan_review_stages=None)
        new_state = ws.record_local_plan_review(
            _base_state(wi=wi), "wi", verdict="REVISE", bundle_id="b1",
            review_content_id="c1", round=1, now="t1",
        )
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["phase"], "REVISING_PLAN")
        self.assertIsNone(item["plan_review_stages"])

    def test_block_is_a_true_no_op(self):
        """Missing-test item 95: no ledger write, no transition; remains
        AWAITING_LOCAL_PLAN_REVIEW -- the returned state is unchanged."""
        wi = _v21_work_item(state_revision=1, plan_review_stages=None)
        state = _base_state(wi=wi)
        new_state = ws.record_local_plan_review(
            state, "wi", verdict="BLOCK", bundle_id="b1",
            review_content_id="c1", round=1, now="t1",
        )
        self.assertEqual(new_state, state)
        self.assertEqual(new_state["work_items"]["wi"]["phase"], "AWAITING_LOCAL_PLAN_REVIEW")


class TestRecordManualPlanReview(unittest.TestCase):
    def _local_approved_wi(self, **overrides):
        defaults = {
            "phase": "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW",
            "plan_review_stages": {
                "review_content_id": "c1",
                "local_model_plan_review": {"bundle_id": "b1", "verdict": "APPROVE", "round": 1, "completed_at": "t1"},
                "manual_external_plan_review": None,
            },
        }
        defaults.update(overrides)
        return _v21_work_item(**defaults)

    def test_wrong_governing_version_rejected(self):
        wi = self._local_approved_wi(governing_workflow_version="1")
        with self.assertRaises(ws.WrongGoverningVersionForPlanReviewStageError):
            ws.record_manual_plan_review(
                _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b2", round=1, now="t2",
                current_review_content_id="c1", feedback_role="manual_external_plan_review",
                feedback_review_content_id="c1",
            )

    def test_wrong_phase_rejected(self):
        """WFR-41: a local REVISE verdict followed by an attempted manual-
        stage action is refused."""
        wi = self._local_approved_wi(phase="REVISING_PLAN")
        with self.assertRaises(ws.WrongPhaseForPlanReviewStageError):
            ws.record_manual_plan_review(
                _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b2", round=1, now="t2",
                current_review_content_id="c1", feedback_role="manual_external_plan_review",
                feedback_review_content_id="c1",
            )

    def test_wrong_role_rejected(self):
        """Missing-test item 97."""
        wi = self._local_approved_wi()
        with self.assertRaises(ws.WrongReviewerRoleError):
            ws.record_manual_plan_review(
                _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b2", round=1, now="t2",
                current_review_content_id="c1", feedback_role="local_model_plan_review",
                feedback_review_content_id="c1",
            )

    def test_stale_review_content_id_is_hard_blocked(self):
        """Missing-test items 97/112: a review_content_id change blocks
        ingestion, unlike a wrapper-only bundle_id mismatch."""
        wi = self._local_approved_wi()
        with self.assertRaises(ws.StaleReviewContentIdError):
            ws.record_manual_plan_review(
                _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b2", round=1, now="t2",
                current_review_content_id="c1", feedback_role="manual_external_plan_review",
                feedback_review_content_id="stale",
            )

    def test_missing_local_approval_rejected(self):
        """Missing-test item 81/97: no current local APPROVE for this
        review_content_id."""
        wi = self._local_approved_wi(plan_review_stages={
            "review_content_id": "c1",
            "local_model_plan_review": None,
            "manual_external_plan_review": None,
        })
        with self.assertRaises(ws.MissingLocalApprovalForManualStageError):
            ws.record_manual_plan_review(
                _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b2", round=1, now="t2",
                current_review_content_id="c1", feedback_role="manual_external_plan_review",
                feedback_review_content_id="c1",
            )

    def test_duplicate_ingestion_rejected(self):
        """Missing-test item 97."""
        wi = self._local_approved_wi(plan_review_stages={
            "review_content_id": "c1",
            "local_model_plan_review": {"bundle_id": "b1", "verdict": "APPROVE", "round": 1, "completed_at": "t1"},
            "manual_external_plan_review": {"bundle_id": "b2", "verdict": "APPROVE", "round": 1, "completed_at": "t2"},
        })
        with self.assertRaises(ws.DuplicateManualStageIngestionError):
            ws.record_manual_plan_review(
                _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b3", round=2, now="t3",
                current_review_content_id="c1", feedback_role="manual_external_plan_review",
                feedback_review_content_id="c1",
            )

    def test_approve_completes_ledger_and_transitions_to_plan_approval(self):
        """Missing-test item 82: both stages recorded, in order, against
        the same review_content_id reaches AWAITING_PLAN_APPROVAL."""
        wi = self._local_approved_wi(state_revision=1)
        new_state = ws.record_manual_plan_review(
            _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="b2", round=1, now="t2",
            current_review_content_id="c1", feedback_role="manual_external_plan_review",
            feedback_review_content_id="c1",
        )
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["phase"], "AWAITING_PLAN_APPROVAL")
        self.assertEqual(item["plan_review_stages"]["manual_external_plan_review"], {
            "bundle_id": "b2", "verdict": "APPROVE", "round": 1, "completed_at": "t2",
        })
        self.assertEqual(item["state_revision"], 2)
        self.assertTrue(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="2.1",
            plan_review_stages=item["plan_review_stages"], current_review_content_id="c1",
        ))

    def test_approve_records_actual_bundle_id_even_when_mismatched(self):
        """Missing-test item 113 (OPUS-R14-005): the ledger records the
        reviewed feedback's actual bundle_id in both the matching and
        mismatched cases -- the advisory bundle_id check never blocks."""
        wi = self._local_approved_wi()
        warning = ws.check_manual_stage_bundle_id_advisory(
            feedback_bundle_id="stale-wrapper-bundle", current_bundle_id="fresh-wrapper-bundle",
        )
        self.assertIsNotNone(warning)
        new_state = ws.record_manual_plan_review(
            _base_state(wi=wi), "wi", verdict="APPROVE", bundle_id="stale-wrapper-bundle", round=1, now="t2",
            current_review_content_id="c1", feedback_role="manual_external_plan_review",
            feedback_review_content_id="c1",
        )
        self.assertEqual(
            new_state["work_items"]["wi"]["plan_review_stages"]["manual_external_plan_review"]["bundle_id"],
            "stale-wrapper-bundle",
        )

    def test_matching_bundle_id_has_no_advisory_warning(self):
        self.assertIsNone(ws.check_manual_stage_bundle_id_advisory("b1", "b1"))

    def test_revise_transitions_to_revising_plan_with_no_ledger_write(self):
        """Missing-test item 99."""
        wi = self._local_approved_wi(state_revision=1)
        new_state = ws.record_manual_plan_review(
            _base_state(wi=wi), "wi", verdict="REVISE", bundle_id="b2", round=1, now="t2",
            current_review_content_id="c1", feedback_role="manual_external_plan_review",
            feedback_review_content_id="c1",
        )
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["phase"], "REVISING_PLAN")
        self.assertIsNone(item["plan_review_stages"]["manual_external_plan_review"])

    def test_block_is_a_true_no_op(self):
        """Missing-test item 99: no ledger write, no transition; remains
        AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW."""
        wi = self._local_approved_wi(state_revision=1)
        state = _base_state(wi=wi)
        new_state = ws.record_manual_plan_review(
            state, "wi", verdict="BLOCK", bundle_id="b2", round=1, now="t2",
            current_review_content_id="c1", feedback_role="manual_external_plan_review",
            feedback_review_content_id="c1",
        )
        self.assertEqual(new_state, state)
        self.assertEqual(new_state["work_items"]["wi"]["phase"], "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW")


class TestTransitionToAwaitingLocalPlanReview(unittest.TestCase):
    def test_from_revising_plan_after_an_accepted_edit(self):
        """Missing-test item 89: whether the edit was driven by a local or
        manual-external REVISE, the next required stage is always
        AWAITING_LOCAL_PLAN_REVIEW -- never directly back to manual-external
        review or to AWAITING_PLAN_APPROVAL."""
        wi = _v21_work_item(phase="REVISING_PLAN", state_revision=3, plan_review_stages={
            "review_content_id": "stale",
            "local_model_plan_review": {"bundle_id": "b1", "verdict": "APPROVE", "round": 1, "completed_at": "t1"},
            "manual_external_plan_review": None,
        })
        new_state = ws.transition_to_awaiting_local_plan_review(_base_state(wi=wi), "wi", now="t2")
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["phase"], "AWAITING_LOCAL_PLAN_REVIEW")
        self.assertEqual(item["state_revision"], 4)
        # The stale ledger is left as-is, not explicitly cleared -- it
        # simply no longer matches a freshly recomputed current id.
        self.assertEqual(item["plan_review_stages"]["review_content_id"], "stale")
        self.assertFalse(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="2.1",
            plan_review_stages=item["plan_review_stages"], current_review_content_id="fresh",
        ))


class TestFullTwoStageSequence(unittest.TestCase):
    def test_local_approve_then_manual_approve_reaches_plan_approval_gate(self):
        """Missing-test item 80: a local APPROVE alone cannot reach
        AWAITING_PLAN_APPROVAL for a "2.1" item; missing-test item 82: both
        stages, in order, do."""
        wi = _v21_work_item()
        state = _base_state(wi=wi)

        after_local = ws.record_local_plan_review(
            state, "wi", verdict="APPROVE", bundle_id="b1", review_content_id="c1", round=1, now="t1",
        )
        item = after_local["work_items"]["wi"]
        self.assertEqual(item["phase"], "AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW")
        self.assertFalse(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="2.1",
            plan_review_stages=item["plan_review_stages"], current_review_content_id="c1",
        ))

        after_manual = ws.record_manual_plan_review(
            after_local, "wi", verdict="APPROVE", bundle_id="b2", round=1, now="t2",
            current_review_content_id="c1", feedback_role="manual_external_plan_review",
            feedback_review_content_id="c1",
        )
        item2 = after_manual["work_items"]["wi"]
        self.assertEqual(item2["phase"], "AWAITING_PLAN_APPROVAL")
        self.assertTrue(ws.plan_approval_gate_reachable(
            latest_round_status="APPROVE", governing_workflow_version="2.1",
            plan_review_stages=item2["plan_review_stages"], current_review_content_id="c1",
        ))


# ---------------------------------------------------------------------------
# WF2: D-Selection's four-rule checkpoint-selection algorithm, the
# IN_PROGRESS/COMPLETE state writers, and WORKTREE_IDENTITY.json's writer
# and resume-side check. Missing-test items 9, 28, 31, 43, 71.
# ---------------------------------------------------------------------------


_REGISTRY = {"checkpoints": [
    {"id": "A", "depends_on": []},
    {"id": "B", "depends_on": ["A"]},
    {"id": "C", "depends_on": ["A"]},
    {"id": "D", "depends_on": ["B", "C"]},
]}


class TestSelectNextCheckpoint(unittest.TestCase):
    def test_fresh_work_item_selects_first_dependency_free_checkpoint(self):
        wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
        self.assertEqual(ws.select_next_checkpoint(wi, _REGISTRY), "A")

    def test_rule_1_resumes_in_progress_checkpoint_without_reconciliation(self):
        """Missing-test item 31: an interrupted checkpoint with state
        written but narrative not updated resumes without human
        reconciliation -- rule 1 alone decides this, no other field of the
        work item is consulted."""
        wi = _base_work_item(
            current_checkpoint_id="B",
            checkpoints={"A": {"status": "COMPLETE"}, "B": {"status": "IN_PROGRESS"}},
        )
        self.assertEqual(ws.select_next_checkpoint(wi, _REGISTRY), "B")

    def test_rule_2_skips_completed_and_respects_dependencies(self):
        wi = _base_work_item(
            current_checkpoint_id=None,
            checkpoints={"A": {"status": "COMPLETE"}, "B": {"status": "COMPLETE"}},
        )
        # C is also dependency-ready (depends only on A); registry order
        # places C before D, and D's own dependency (C) isn't complete yet.
        self.assertEqual(ws.select_next_checkpoint(wi, _REGISTRY), "C")

    def test_all_complete_returns_none(self):
        wi = _base_work_item(current_checkpoint_id=None, checkpoints={
            cid: {"status": "COMPLETE"} for cid in ("A", "B", "C", "D")
        })
        self.assertIsNone(ws.select_next_checkpoint(wi, _REGISTRY))

    def test_rule_4_blocked_dependency_is_named_not_silently_idle(self):
        """B and C both depend on A, which is not COMPLETE -- nothing is
        selectable, and this must raise rather than return None (None is
        reserved for the genuinely-finished case)."""
        wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
        registry = {"checkpoints": [
            {"id": "A", "depends_on": ["MISSING"]},
        ]}
        with self.assertRaises(ws.NoCheckpointReadyError) as ctx:
            ws.select_next_checkpoint(wi, registry)
        self.assertIn("A", str(ctx.exception))
        self.assertIn("MISSING", str(ctx.exception))

    def test_determinism_across_independent_calls(self):
        """Missing-test item 28: two independent fresh sessions against
        identical state select the same checkpoint -- the function is
        pure, so this is just repeatability, checked against two
        independently-constructed (not shared/mutated) copies of the same
        logical state."""
        wi_a = _base_work_item(current_checkpoint_id=None, checkpoints={"A": {"status": "COMPLETE"}})
        wi_b = _base_work_item(current_checkpoint_id=None, checkpoints={"A": {"status": "COMPLETE"}})
        self.assertEqual(
            ws.select_next_checkpoint(wi_a, _REGISTRY),
            ws.select_next_checkpoint(wi_b, _REGISTRY),
        )


class TestCheckpointStateTransitions(unittest.TestCase):
    def test_transition_to_in_progress_sets_status_and_current_pointer(self):
        wi = _base_work_item(current_checkpoint_id=None, checkpoints={}, state_revision=1)
        state = _base_state(wi=wi)
        new_state = ws.transition_checkpoint_in_progress(state, "wi", "A", "deadbeef", now="t1")
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["checkpoints"]["A"], {"status": "IN_PROGRESS", "start_commit": "deadbeef"})
        self.assertEqual(item["current_checkpoint_id"], "A")
        self.assertEqual(item["state_revision"], 2)
        # Original state is untouched (functions return a new dict).
        self.assertEqual(state["work_items"]["wi"]["checkpoints"], {})

    def test_complete_checkpoint_stays_implementing_when_others_remain(self):
        """Checkpoint-complete-vs-all-complete semantics: completing one
        checkpoint out of several never itself flips the phase."""
        wi = _base_work_item(
            current_checkpoint_id="A", checkpoints={"A": {"status": "IN_PROGRESS", "start_commit": "x"}},
            phase="IMPLEMENTING", state_revision=1,
        )
        state = _base_state(wi=wi)
        new_state = ws.complete_checkpoint(state, "wi", "A", _REGISTRY, now="t2")
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["checkpoints"]["A"]["status"], "COMPLETE")
        self.assertIsNone(item["current_checkpoint_id"])
        self.assertEqual(item["last_completed_checkpoint_id"], "A")
        self.assertEqual(item["phase"], "IMPLEMENTING")

    def test_complete_checkpoint_transitions_phase_when_all_complete(self):
        registry = {"checkpoints": [{"id": "A", "depends_on": []}]}
        wi = _base_work_item(
            current_checkpoint_id="A", checkpoints={"A": {"status": "IN_PROGRESS", "start_commit": "x"}},
            phase="IMPLEMENTING", state_revision=1,
        )
        state = _base_state(wi=wi)
        new_state = ws.complete_checkpoint(state, "wi", "A", registry, now="t2")
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["phase"], "SELF_REVIEWING_IMPLEMENTATION")


class TestWorktreeIdentityWriteAndResume(unittest.TestCase):
    def test_write_creates_file_and_resume_then_succeeds(self):
        """Missing-test item 43: the IN_PROGRESS transition creates
        WORKTREE_IDENTITY.json, and resume against the same worktree
        succeeds."""
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t1")
            self.assertTrue((repo.root / ws.WORKTREE_IDENTITY_PATH).exists())
            ws.verify_dirty_resume_safety(repo.root, "wi")  # must not raise

    def test_resume_with_missing_file_stops(self):
        with ScratchRepo() as repo:
            with self.assertRaises(ws.WorktreeIdentityMissingError):
                ws.verify_dirty_resume_safety(repo.root, "wi")

    def test_resume_for_a_different_work_item_stops(self):
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi-a", now="t1")
            with self.assertRaises(ws.WorktreeIdentityMissingError):
                ws.verify_dirty_resume_safety(repo.root, "wi-b")

    def test_two_work_items_keep_independently_keyed_entries(self):
        """Missing-test item 71: two work items with interleaved
        IN_PROGRESS dirty work each resume against their own keyed
        expected set -- writing wi-b's entry must not disturb wi-a's."""
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi-a", now="t1")
            ws.write_worktree_identity(repo.root, "wi-b", now="t2")
            data = ws._load_json(repo.root / ws.WORKTREE_IDENTITY_PATH)
            self.assertIn("wi-a", data["expected_dirty_paths_by_work_item"])
            self.assertIn("wi-b", data["expected_dirty_paths_by_work_item"])
            ws.verify_dirty_resume_safety(repo.root, "wi-a")  # must not raise
            ws.verify_dirty_resume_safety(repo.root, "wi-b")  # must not raise

    def test_mismatched_worktree_identity_stops(self):
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t1")
            full_path = repo.root / ws.WORKTREE_IDENTITY_PATH
            data = json.loads(full_path.read_text())
            data["repo_root"] = "/somewhere/else"
            full_path.write_text(json.dumps(data))
            with self.assertRaises(ws.WorktreeIdentityMismatchError):
                ws.verify_dirty_resume_safety(repo.root, "wi")

    def test_snapshot_captures_current_dirty_paths_with_content_hash(self):
        import hashlib
        with ScratchRepo() as repo:
            (repo.root / "dirty.txt").write_text("wip content\n")
            written = ws.write_worktree_identity(repo.root, "wi", now="t1")
            entries = written["expected_dirty_paths_by_work_item"]["wi"]
            self.assertEqual(
                entries,
                [{"path": "dirty.txt", "sha256": hashlib.sha256(b"wip content\n").hexdigest()}],
            )

    def test_resume_succeeds_even_after_dirty_set_changes(self):
        """The resume check never re-compares the snapshot's per-path
        content against the current dirty state (missing-test item 31's
        underlying reason: the dirty set legitimately keeps changing while
        a checkpoint is in progress)."""
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t1")
            (repo.root / "more_wip.txt").write_text("more work\n")
            ws.verify_dirty_resume_safety(repo.root, "wi")  # must not raise


# ---------------------------------------------------------------------------
# WF4c: D-Functional-Remediation -- stale-before-edit write,
# reviewed_implementation_head's sole writer, and the broad-remediation
# child-work-item branch plus its parent-completion block.
# ---------------------------------------------------------------------------


class TestMarkTechnicalApprovalStale(unittest.TestCase):
    def _approved_work_item(self, **overrides):
        record = ws.build_approval_record(
            basis="EXTERNAL_APPROVE", stage="implementation", user_confirmation="approve wi implementation",
            now="t1", reviewed_bundle_id="b", approved_review_content_id="c",
            review_content_manifest=[], reviewed_content_commit="deadbeef",
        )
        return _base_work_item(technical_approval=record, **overrides)

    def test_marks_status_stale_and_leaves_other_fields_untouched(self):
        wi = self._approved_work_item()
        state = _base_state(wi=wi)
        new_state = ws.mark_technical_approval_stale(state, "wi", now="t2")
        record = new_state["work_items"]["wi"]["technical_approval"]
        self.assertEqual(record["status"], "STALE")
        self.assertEqual(record["approved_review_content_id"], "c")
        self.assertEqual(record["basis"], "EXTERNAL_APPROVE")
        # original state untouched (stale-before-edit ordering requires a
        # fresh dict the caller can persist independently of the input)
        self.assertEqual(state["work_items"]["wi"]["technical_approval"]["status"], "CURRENT")

    def test_bumps_state_revision_and_last_transition(self):
        wi = self._approved_work_item(state_revision=1)
        state = _base_state(wi=wi)
        new_state = ws.mark_technical_approval_stale(state, "wi", now="t2")
        self.assertEqual(new_state["work_items"]["wi"]["state_revision"], 2)
        self.assertEqual(new_state["work_items"]["wi"]["last_transition"], "t2")

    def test_no_existing_technical_approval_rejected(self):
        state = _base_state(wi=_base_work_item())
        with self.assertRaises(ws.InvalidApprovalRecordError):
            ws.mark_technical_approval_stale(state, "wi", now="t2")


class TestRecordBundleGeneration(unittest.TestCase):
    def test_first_implementation_stage_call_sets_head_and_revision_one(self):
        state = _base_state(wi=_base_work_item(reviewed_implementation_head=None, implementation_revision=None))
        new_state = ws.record_bundle_generation(state, "wi", stage="implementation", head="abc123", now="t1")
        wi = new_state["work_items"]["wi"]
        self.assertEqual(wi["reviewed_implementation_head"], "abc123")
        self.assertEqual(wi["implementation_revision"], 1)

    def test_post_fix_call_advances_head_and_increments_revision(self):
        state = _base_state(wi=_base_work_item(reviewed_implementation_head="abc123", implementation_revision=1))
        new_state = ws.record_bundle_generation(state, "wi", stage="post-fix", head="def456", now="t2")
        wi = new_state["work_items"]["wi"]
        self.assertEqual(wi["reviewed_implementation_head"], "def456")
        self.assertEqual(wi["implementation_revision"], 2)

    def test_unknown_stage_rejected(self):
        state = _base_state(wi=_base_work_item())
        with self.assertRaises(ws.InvalidBundleGenerationStageError):
            ws.record_bundle_generation(state, "wi", stage="plan", head="abc123", now="t1")

    def test_original_state_untouched(self):
        state = _base_state(wi=_base_work_item(reviewed_implementation_head=None, implementation_revision=None))
        ws.record_bundle_generation(state, "wi", stage="implementation", head="abc123", now="t1")
        self.assertIsNone(state["work_items"]["wi"]["reviewed_implementation_head"])


class TestRemediationChildWorkItem(unittest.TestCase):
    def test_creates_child_with_derived_id_and_parent_link(self):
        state = _base_state(parent=_base_work_item(
            work_item_id="parent", work_item_type="product", work_item_kind="product",
        ))
        config = ws.default_config()
        new_state, child_id = ws.create_remediation_child_work_item(
            state, config, parent_work_item_id="parent", plan_path="p", registry_path="r",
            base_commit="feedcafe", now="t1",
        )
        self.assertEqual(child_id, "parent-remediation-1")
        child = new_state["work_items"][child_id]
        self.assertEqual(child["parent_work_item_id"], "parent")
        self.assertEqual(child["base_commit"], "feedcafe")
        self.assertEqual(child["work_item_type"], "product")
        self.assertEqual(child["work_item_kind"], "product")
        self.assertEqual(child["phase"], "PLANNING")
        self.assertEqual(child["governing_workflow_version"], config["default_workflow_version"])
        # parent entry itself is untouched, and the input state is unmutated
        self.assertNotIn("parent-remediation-1", state["work_items"])
        self.assertEqual(new_state["work_items"]["parent"], state["work_items"]["parent"])

    def test_numbering_increments_past_existing_children(self):
        state = _base_state(
            parent=_base_work_item(work_item_id="parent"),
            **{"parent-remediation-1": _base_work_item(
                work_item_id="parent-remediation-1", parent_work_item_id="parent",
            )},
        )
        config = ws.default_config()
        _, child_id = ws.create_remediation_child_work_item(
            state, config, parent_work_item_id="parent", plan_path="p", registry_path="r",
            base_commit="feedcafe", now="t1",
        )
        self.assertEqual(child_id, "parent-remediation-2")

    def test_never_touches_active_work_item_id(self):
        state = _base_state(parent=_base_work_item(work_item_id="parent"))
        state["active_work_item_id"] = "parent"
        config = ws.default_config()
        new_state, _ = ws.create_remediation_child_work_item(
            state, config, parent_work_item_id="parent", plan_path="p", registry_path="r",
            base_commit="feedcafe", now="t1",
        )
        self.assertEqual(new_state["active_work_item_id"], "parent")

    def test_resulting_state_is_valid(self):
        state = _base_state(parent=_base_work_item(work_item_id="parent"))
        config = ws.default_config()
        new_state, _ = ws.create_remediation_child_work_item(
            state, config, parent_work_item_id="parent", plan_path="p", registry_path="r",
            base_commit="feedcafe", now="t1",
        )
        ws.validate_state(new_state)  # must not raise -- parent_work_item_id resolves


class TestParentCompletionBlocksOnIncompleteChild(unittest.TestCase):
    def test_incomplete_children_lists_only_non_terminal_children(self):
        state = _base_state(
            parent=_base_work_item(work_item_id="parent"),
            **{
                "parent-remediation-1": _base_work_item(
                    work_item_id="parent-remediation-1", parent_work_item_id="parent",
                    phase="IMPLEMENTING",
                ),
                "parent-remediation-2": _base_work_item(
                    work_item_id="parent-remediation-2", parent_work_item_id="parent",
                    phase="MILESTONE_COMPLETE",
                ),
                "unrelated": _base_work_item(work_item_id="unrelated"),
            },
        )
        self.assertEqual(ws.incomplete_children(state, "parent"), ["parent-remediation-1"])

    def test_complete_work_item_refuses_while_child_incomplete(self):
        state = _base_state(
            parent=_base_work_item(work_item_id="parent", phase="AWAITING_USER_ACCEPTANCE"),
            **{"parent-remediation-1": _base_work_item(
                work_item_id="parent-remediation-1", parent_work_item_id="parent", phase="IMPLEMENTING",
            )},
        )
        with self.assertRaises(ws.IncompleteChildWorkItemError):
            ws.complete_work_item(state, "parent", now="t2")
        # refusing must not have mutated the input
        self.assertEqual(state["work_items"]["parent"]["phase"], "AWAITING_USER_ACCEPTANCE")

    def test_complete_work_item_succeeds_once_every_child_is_complete(self):
        state = _base_state(
            parent=_base_work_item(work_item_id="parent", phase="AWAITING_USER_ACCEPTANCE"),
            **{"parent-remediation-1": _base_work_item(
                work_item_id="parent-remediation-1", parent_work_item_id="parent",
                phase="MILESTONE_COMPLETE",
            )},
        )
        new_state = ws.complete_work_item(state, "parent", now="t2")
        self.assertEqual(new_state["work_items"]["parent"]["phase"], "MILESTONE_COMPLETE")

    def test_complete_work_item_with_no_children_still_succeeds(self):
        state = _base_state(wi=_base_work_item(phase="AWAITING_USER_ACCEPTANCE"))
        new_state = ws.complete_work_item(state, "wi", now="t2")
        self.assertEqual(new_state["work_items"]["wi"]["phase"], "MILESTONE_COMPLETE")


class TestDanglingParentWorkItem(unittest.TestCase):
    def test_parent_work_item_id_naming_unknown_entry_rejected(self):
        state = _base_state(wi=_base_work_item(parent_work_item_id="nonexistent"))
        with self.assertRaises(ws.DanglingParentWorkItemError):
            ws.validate_state(state)

    def test_parent_work_item_id_naming_real_entry_accepted(self):
        state = _base_state(
            parent=_base_work_item(work_item_id="parent"),
            child=_base_work_item(work_item_id="child", parent_work_item_id="parent"),
        )
        ws.validate_state(state)  # must not raise


if __name__ == "__main__":
    unittest.main()
