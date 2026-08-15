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

import copy
import json
import subprocess
import tempfile
import threading
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
        self._extra_worktrees: list[Path] = []
        _run(["git", "init", "-q"], cwd=self.root)
        _run(["git", "config", "user.email", "test@example.com"], cwd=self.root)
        _run(["git", "config", "user.name", "Test"], cwd=self.root)
        # Mirrors the real repository's own .gitignore: `.ai-review/` (the
        # WORKTREE_IDENTITY.json/.lock/checkpoint-claims home) is never
        # tracked or reported as an untracked dirty path there, and a
        # scratch repo without this ignores nothing, so
        # `identity_document_lock`'s own lock file would otherwise leak
        # into `_hash_dirty_paths`' snapshot.
        (self.root / ".gitignore").write_text(".ai-review/\n")
        (self.root / "README.md").write_text("base\n")
        _run(["git", "add", "README.md", ".gitignore"], cwd=self.root)
        _run(["git", "commit", "-q", "-m", "base"], cwd=self.root)
        self.base = self.head()
        return self

    def __exit__(self, *exc):
        import shutil
        for path in self._extra_worktrees:
            shutil.rmtree(path, ignore_errors=True)
        shutil.rmtree(self.root, ignore_errors=True)

    def worktree(self, name: str) -> Path:
        """A second **linked** worktree of this same repository, sharing
        this repo's `git rev-parse --git-common-dir` -- what a real
        second Claude Code session/worktree looks like to the claim/guard
        mechanism. Cleaned up alongside `self.root`."""
        path = self.root.parent / f"{self.root.name}-{name}"
        _run(["git", "worktree", "add", "-q", "-b", name, str(path), "HEAD"], cwd=self.root)
        self._extra_worktrees.append(path)
        return path

    def remove_worktree(self, path: Path) -> None:
        """Deregisters a linked worktree the way an operator's own
        `git worktree remove` would -- used by the abandoned-guard
        recovery tests, which require the holder to no longer be in
        `git worktree list`."""
        _run(["git", "worktree", "remove", "--force", str(path)], cwd=self.root)
        if path in self._extra_worktrees:
            self._extra_worktrees.remove(path)

    def head(self) -> str:
        return subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()

    def commit(self, subject: str, trailers: dict[str, str] | None = None, filename: str | None = None) -> str:
        filename = filename or f"{subject.replace(' ', '_')}.txt"
        (self.root / filename).parent.mkdir(parents=True, exist_ok=True)
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
        trailer pair is genuine ambiguity only once the role-specific
        verification tie-break (item 39) also fails to pick a single
        survivor -- here neither commit's own committed
        `WORKFLOW_STATE.json` claims `WF0` as `COMPLETE` (neither commit
        touches that file at all), so zero candidates verify and the
        result stays undecidable, not silently resolved."""
        with ScratchRepo() as repo:
            repo.commit("wf0-a", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"})
            repo.commit("wf0-b", trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"})
            with self.assertRaises(ws.AmbiguousCheckpointTrailerError):
                ws.discover_checkpoint_commits(repo.root, "wi", repo.base)

    def test_verification_tie_break_resolves_when_one_candidate_claims_complete(self):
        """Item 39's real requirement: two first-parent-ancestor commits
        carry the same checkpoint trailer, but only one's own committed
        `WORKFLOW_STATE.json` records that checkpoint `COMPLETE` for this
        work item -- that candidate resolves, the other (which claims a
        different status) is rejected by the verification filter."""
        with ScratchRepo() as repo:
            _commit_state_with_trailers(
                repo, _state_json(wi={"checkpoints": {"WF0": {"status": "IN_PROGRESS"}}}),
                trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"},
            )
            complete_sha = _commit_state_with_trailers(
                repo, _state_json(wi={"checkpoints": {"WF0": {"status": "COMPLETE"}}}),
                trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"},
            )
            discovered = ws.discover_checkpoint_commits(repo.root, "wi", repo.base)
            self.assertEqual(discovered, {"WF0": complete_sha})

    def test_verification_tie_break_still_refuses_when_both_candidates_claim_complete(self):
        """Genuine ambiguity persists when *both* first-parent-ancestor
        candidates' own committed state claims `COMPLETE` -- verification
        narrows, it does not manufacture a winner out of two equally
        plausible survivors."""
        with ScratchRepo() as repo:
            _commit_state_with_trailers(
                repo, _state_json_marked("a", wi={"checkpoints": {"WF0": {"status": "COMPLETE"}}}),
                trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"},
            )
            _commit_state_with_trailers(
                repo, _state_json_marked("b", wi={"checkpoints": {"WF0": {"status": "COMPLETE"}}}),
                trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"},
            )
            with self.assertRaises(ws.AmbiguousCheckpointTrailerError):
                ws.discover_checkpoint_commits(repo.root, "wi", repo.base)

    def test_verification_tie_break_refuses_when_zero_candidates_claim_complete(self):
        """Zero valid survivors after verification is still refused, not
        treated as "no opinion, pick the first one"."""
        with ScratchRepo() as repo:
            _commit_state_with_trailers(
                repo, _state_json_marked("a", wi={"checkpoints": {"WF0": {"status": "IN_PROGRESS"}}}),
                trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"},
            )
            _commit_state_with_trailers(
                repo, _state_json_marked("b", wi={"checkpoints": {"WF0": {"status": "IN_PROGRESS"}}}),
                trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"},
            )
            with self.assertRaises(ws.AmbiguousCheckpointTrailerError):
                ws.discover_checkpoint_commits(repo.root, "wi", repo.base)

    def test_duplicate_trailer_resolves_via_first_parent_before_verification(self):
        """The two-filter contract's ordering: when the first-parent
        filter alone already narrows to one candidate, verification is
        never consulted -- a cherry-picked/duplicate off-first-parent
        trailer resolves exactly as before, even if the winning commit's
        own committed state does not (yet) claim `COMPLETE`."""
        with ScratchRepo() as repo:
            _run(["git", "checkout", "-q", "-b", "side"], cwd=repo.root)
            _commit_state_with_trailers(
                repo, _state_json(wi={"checkpoints": {"WF0": {"status": "COMPLETE"}}}),
                trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"},
                message="wf0-side",
            )
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            main_sha = _commit_state_with_trailers(
                repo, _state_json(wi={"checkpoints": {"WF0": {"status": "IN_PROGRESS"}}}),
                trailers={"Workflow-Checkpoint": "WF0", "Workflow-Work-Item": "wi"},
                message="wf0-main",
            )
            # `-X ours`: both branches add the same WORKFLOW_STATE.json
            # path with different content (no common-ancestor version to
            # three-way-merge), so the merge itself needs a resolution
            # strategy -- irrelevant to what this test is proving, which
            # is purely about trailer/commit tie-breaking, not merge
            # content.
            _run(["git", "merge", "-q", "--no-ff", "-X", "ours", "-m", "merge side", "side"], cwd=repo.root)
            discovered = ws.discover_checkpoint_commits(repo.root, "wi", repo.base)
            self.assertEqual(discovered, {"WF0": main_sha})


STATE_REL_PATH = "docs/ai-workflow/WORKFLOW_STATE.json"


def _commit_state(repo: "ScratchRepo", content: str, message: str = "state") -> str:
    """Commits arbitrary bytes at the real `WORKFLOW_STATE.json` path
    inside a `ScratchRepo`, so `checkpoint_origination_provable`'s
    per-commit partition can be exercised against realistic nested-path
    history rather than `ScratchRepo.commit`'s flat scratch files."""
    full = repo.root / STATE_REL_PATH
    full.parent.mkdir(parents=True, exist_ok=True)
    full.write_text(content)
    _run(["git", "add", STATE_REL_PATH], cwd=repo.root)
    _run(["git", "commit", "-q", "-m", message], cwd=repo.root)
    return repo.head()


def _commit_state_with_trailers(
    repo: "ScratchRepo", content: str, trailers: dict[str, str], message: str = "state",
) -> str:
    """Like `_commit_state`, but the commit also carries the given Git
    trailers -- the shape a real checkpoint commit takes (it both advances
    `WORKFLOW_STATE.json` and carries `Workflow-Checkpoint`/
    `Workflow-Work-Item` trailers in the same commit), used by the
    checkpoint-trailer-discovery verification tie-break tests (item 39)."""
    full = repo.root / STATE_REL_PATH
    full.parent.mkdir(parents=True, exist_ok=True)
    full.write_text(content)
    _run(["git", "add", STATE_REL_PATH], cwd=repo.root)
    body = message + "\n\n" + "\n".join(f"{k}: {v}" for k, v in trailers.items())
    _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
    return repo.head()


def _state_json(**work_items) -> str:
    return json.dumps({"schema_version": 1, "work_items": work_items})


def _state_json_marked(marker: str, **work_items) -> str:
    """Like `_state_json`, plus an inert top-level marker field -- used
    where two commits must carry deliberately identical checkpoint status
    (to exercise the verification tie-break's "both claim it" or "neither
    claims it" branches) but still need distinct blob content, since Git
    refuses an empty commit whose tree would be byte-identical to HEAD's."""
    return json.dumps({"schema_version": 1, "work_items": work_items, "_test_marker": marker})


def _write_local_state(repo: "ScratchRepo", content: str) -> None:
    """Writes `WORKFLOW_STATE.json` in the working tree only -- never
    committed -- the exact "uncommitted delta" shape a real interrupted
    checkpoint's fresh-start bookkeeping leaves (`transition_checkpoint_
    in_progress` is a working-tree-only write by design). Used to set up
    `adopt_claim`'s local-`IN_PROGRESS` precondition without polluting the
    origination reference `_commit_state` feeds."""
    full = repo.root / STATE_REL_PATH
    full.parent.mkdir(parents=True, exist_ok=True)
    full.write_text(content)


class TestCheckpointOriginationReference(unittest.TestCase):
    """WF8b's `D-Checkpoint-Ownership` origination-reference slice
    (`checkpoint_origination_provable`/`origination_reference_commits`),
    against the plan's "The read fails closed, and the partition is
    total" table and its reduction/precedence rules."""

    def test_no_reference_commits_admits(self):
        with ScratchRepo() as repo:
            result = ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(result, {
                "decision": "admit", "route": "no_reference_commits",
                "state_rel_path": STATE_REL_PATH, "examined_commits": 0,
            })

    def test_absent_from_every_examined_commit_admits(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json())  # no "wi" entry at all
            result = ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(result["decision"], "admit")
            self.assertEqual(result["route"], "scanned_all_decidable")

    def test_decidable_complete_status_admits(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "COMPLETE"}}}))
            result = ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(result["decision"], "admit")

    def test_observed_in_progress_refuses(self):
        with ScratchRepo() as repo:
            sha = _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "observed")
            self.assertEqual(ctx.exception.evidence["commit"], sha)

    def test_reduction_rule_any_observation_anywhere_binds(self):
        """No supersession, no recency: an `IN_PROGRESS` observed at an
        earlier commit still refuses even though a later commit in the
        same reference records `COMPLETE`."""
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "COMPLETE"}}}))
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "observed")

    def test_undecidable_unparseable_document_refuses(self):
        with ScratchRepo() as repo:
            _commit_state(repo, "{not json")
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "undecidable")

    def test_undecidable_non_object_document_refuses(self):
        with ScratchRepo() as repo:
            _commit_state(repo, json.dumps([1, 2, 3]))
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "undecidable")

    def test_present_but_non_object_work_items_refuses_undecidable(self):
        """A present `work_items: null` is a schema-invalid document, not
        an absence -- absence is a missing key, established with `in`,
        never a falsy or non-object value."""
        with ScratchRepo() as repo:
            _commit_state(repo, json.dumps({"schema_version": 1, "work_items": None}))
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "undecidable")

    def test_present_but_non_object_checkpoint_entry_refuses_undecidable(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": None}}))
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "undecidable")

    def test_status_outside_controlled_vocabulary_refuses_undecidable(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "BOGUS"}}}))
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "undecidable")

    def test_precedence_observed_wins_over_undecidable_in_same_reference(self):
        """`OPUS-R89-001`'s precedence rule: when both refusal routes hold
        in the same reference, the observed route is reported -- the
        decision is refuse either way, but diagnosis should point at the
        actionable fact rather than whichever commit `rev-list` happened
        to reach first."""
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json at all")
            sha = _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "observed")
            self.assertEqual(ctx.exception.evidence["commit"], sha)

    def test_symlinked_state_path_is_undecidable_not_absent(self):
        """A symlink at the state path is never followed -- it is a
        present-but-not-a-regular-file blob, refused as undecidable."""
        with ScratchRepo() as repo:
            full = repo.root / STATE_REL_PATH
            full.parent.mkdir(parents=True, exist_ok=True)
            (full.parent / "elsewhere.json").write_text(
                _state_json(wi={"checkpoints": {"CP": {"status": "COMPLETE"}}})
            )
            full.symlink_to("elsewhere.json")
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "symlink state"], cwd=repo.root)
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "undecidable")

    def test_full_history_retains_a_deleted_branchs_only_observation(self):
        """Reproduces `OPUS-R88-001`'s exact gap: a merge resolved
        `-s ours` produces a merge commit TREESAME to mainline for the
        state path, and once the side branch ref is deleted the only
        remaining path to the side commit is through the merge's second
        parent. A plain `git rev-list --all -- <path>` (Git's default
        History Simplification) prunes that parent entirely and misses
        the side commit's `IN_PROGRESS`; `--full-history` must not."""
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "COMPLETE"}}}))
            _run(["git", "checkout", "-q", "-b", "side"], cwd=repo.root)
            side_sha = _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            _run(["git", "merge", "-q", "-s", "ours", "-m", "merge side (ours)", "side"], cwd=repo.root)
            _run(["git", "branch", "-D", "side"], cwd=repo.root)

            # Control arm: the default, non-full-history walk really does
            # drop the side commit here -- proving this is the exact gap
            # --full-history exists to close, not a hypothetical one.
            pruned = subprocess.run(
                ["git", "rev-list", "--all", "--", STATE_REL_PATH],
                cwd=repo.root, check=True, capture_output=True, text=True,
            ).stdout.splitlines()
            self.assertNotIn(side_sha, pruned)

            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.checkpoint_origination_provable(repo.root, "wi", "CP")
            self.assertEqual(ctx.exception.evidence["route"], "observed")
            self.assertEqual(ctx.exception.evidence["commit"], side_sha)

    def test_origination_reference_commits_matches_rev_list(self):
        with ScratchRepo() as repo:
            sha = _commit_state(repo, _state_json())
            commits = ws.origination_reference_commits(repo.root, STATE_REL_PATH)
            self.assertEqual(commits, [sha])

    def test_unresolvable_reference_refuses(self):
        """A repository the `rev-list` invocation itself cannot run
        against (no `.git` at all) refuses -- an unavailable reference is
        never read as an empty one."""
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.origination_reference_commits(Path(tmp), STATE_REL_PATH)
            self.assertEqual(ctx.exception.evidence["route"], "reference_unresolvable")


class TestIdentityReferenceReadPartition(unittest.TestCase):
    """WFR-66's identity-query enforcement: the read partition
    (`_identity_query_at_commit`/`_scan_identity_reference`), disjoint
    from and stated separately from the origination test's own partition
    though both scan the same reference (`OPUS-R90-003`)."""

    def test_no_reference_commits_admits(self):
        with ScratchRepo() as repo:
            result = ws._scan_identity_reference(repo.root, "wi")
            self.assertEqual(result["decision"], "admit")
            self.assertEqual(result["route"], "no_reference_commits")

    def test_work_item_id_never_observed_admits(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json())  # empty work_items
            result = ws._scan_identity_reference(repo.root, "wi")
            self.assertEqual(result["decision"], "admit")
            self.assertEqual(result["route"], "scanned_all_decidable")

    def test_work_item_id_key_present_is_observed_whatever_its_value(self):
        """Key presence at the queried level is the whole of what an
        existence query asks -- a `null` value still refuses, which is
        exactly where this partition diverges from the origination
        table's own (there, `null` is undecidable)."""
        with ScratchRepo() as repo:
            sha = _commit_state(repo, json.dumps({"schema_version": 1, "work_items": {"wi": None}}))
            result = ws._scan_identity_reference(repo.root, "wi")
            self.assertEqual(result["decision"], "observed")
            self.assertEqual(result["commit"], sha)

    def test_checkpoint_pair_key_present_is_observed_whatever_its_value(self):
        with ScratchRepo() as repo:
            sha = _commit_state(repo, _state_json(wi={"checkpoints": {"CP": None}}))
            result = ws._scan_identity_reference(repo.root, "wi", "CP")
            self.assertEqual(result["decision"], "observed")
            self.assertEqual(result["commit"], sha)

    def test_checkpoint_pair_absent_when_only_a_different_checkpoint_present(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": {"OTHER": {"status": "COMPLETE"}}}))
            result = ws._scan_identity_reference(repo.root, "wi", "CP")
            self.assertEqual(result["decision"], "admit")

    def test_checkpoint_pair_absent_when_work_item_id_itself_never_appears(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json())
            result = ws._scan_identity_reference(repo.root, "wi", "CP")
            self.assertEqual(result["decision"], "admit")

    def test_work_items_non_object_is_undecidable_for_work_item_query(self):
        with ScratchRepo() as repo:
            _commit_state(repo, json.dumps({"schema_version": 1, "work_items": 4}))
            with self.assertRaises(ws.IdentityReferenceUndecidableError) as ctx:
                ws._scan_identity_reference(repo.root, "wi")
            self.assertEqual(len(ctx.exception.evidence["undecidable_commits"]), 1)

    def test_work_item_entry_non_object_is_undecidable_for_pair_query(self):
        with ScratchRepo() as repo:
            _commit_state(repo, json.dumps({"schema_version": 1, "work_items": {"wi": 4}}))
            with self.assertRaises(ws.IdentityReferenceUndecidableError):
                ws._scan_identity_reference(repo.root, "wi", "CP")

    def test_checkpoints_non_object_is_undecidable_for_pair_query(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": 4}))
            with self.assertRaises(ws.IdentityReferenceUndecidableError):
                ws._scan_identity_reference(repo.root, "wi", "CP")

    def test_each_commit_judged_on_its_own_document_decidable_absence_does_not_mask_a_later_undecidable(self):
        """A commit that never mentions `wi` at all is decidably absent
        for the pair query; a *later* commit undecidable for the work
        item entry still raises, since the reduction rule requires every
        examined commit to be decidable before admitting."""
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json())  # no "wi" key: decidable absent
            _commit_state(repo, json.dumps({"schema_version": 1, "work_items": {"wi": 4}}))
            with self.assertRaises(ws.IdentityReferenceUndecidableError):
                ws._scan_identity_reference(repo.root, "wi", "CP")

    def test_unresolvable_reference_raises_identity_specific_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ws.IdentityReferenceUndecidableError) as ctx:
                ws._scan_identity_reference(Path(tmp), "wi")
            self.assertTrue(ctx.exception.evidence.get("reference_unresolvable"))

    def test_reduction_rule_observed_wins_over_undecidable_anywhere_in_scan(self):
        """Any observation anywhere binds, no supersession, no recency --
        and here the two routes differ in whether an escape exists at
        all, so an observed commit's unescapable refusal wins over an
        undecidable commit found in the same scan regardless of order."""
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            sha = _commit_state(repo, json.dumps({"schema_version": 1, "work_items": {"wi": {}}}))
            result = ws._scan_identity_reference(repo.root, "wi")
            self.assertEqual(result["decision"], "observed")
            self.assertEqual(result["commit"], sha)

    def test_full_scan_collects_every_undecidable_commit_not_only_the_first(self):
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json 1", message="bad1")
            _commit_state(repo, "{not valid json 2", message="bad2")
            with self.assertRaises(ws.IdentityReferenceUndecidableError) as ctx:
                ws._scan_identity_reference(repo.root, "wi")
            self.assertEqual(len(ctx.exception.evidence["undecidable_commits"]), 2)


class TestGapObservationIdAndLiteral(unittest.TestCase):
    def test_digest_is_deterministic_and_order_independent(self):
        evidence_a = {
            "work_item_id": "wi", "checkpoint_id": None, "reference_unresolvable": False,
            "undecidable_commits": [{"commit": "b", "failure_class": "x"}, {"commit": "a", "failure_class": "y"}],
        }
        evidence_b = {
            "work_item_id": "wi", "checkpoint_id": None, "reference_unresolvable": False,
            "undecidable_commits": [{"commit": "a", "failure_class": "y"}, {"commit": "b", "failure_class": "x"}],
        }
        self.assertEqual(ws.gap_observation_id(evidence_a), ws.gap_observation_id(evidence_b))

    def test_digest_changes_with_identity(self):
        base = {"work_item_id": "wi", "checkpoint_id": None, "reference_unresolvable": False,
                "undecidable_commits": [{"commit": "a", "failure_class": "x"}]}
        other = {**base, "work_item_id": "other"}
        self.assertNotEqual(ws.gap_observation_id(base), ws.gap_observation_id(other))
        other_cp = {**base, "checkpoint_id": "CP"}
        self.assertNotEqual(ws.gap_observation_id(base), ws.gap_observation_id(other_cp))

    def test_digest_changes_with_undecidable_set(self):
        base = {"work_item_id": "wi", "checkpoint_id": None, "reference_unresolvable": False,
                "undecidable_commits": [{"commit": "a", "failure_class": "x"}]}
        changed = {**base, "undecidable_commits": [{"commit": "a", "failure_class": "different"}]}
        self.assertNotEqual(ws.gap_observation_id(base), ws.gap_observation_id(changed))

    def test_literal_carries_checkpoint_id_only_for_pair_query(self):
        evidence = {"work_item_id": "wi", "checkpoint_id": None, "reference_unresolvable": False,
                    "undecidable_commits": []}
        literal = ws.identity_reference_gap_authorization_literal("wi", None, evidence)
        self.assertTrue(literal.startswith("authorize identity reference gap wi gap "))
        pair_evidence = {**evidence, "checkpoint_id": "CP"}
        pair_literal = ws.identity_reference_gap_authorization_literal("wi", "CP", pair_evidence)
        self.assertIn("checkpoint CP", pair_literal)


class TestAuthorizeIdentityReferenceGap(unittest.TestCase):
    def _undecidable_evidence(self, repo, work_item_id="wi", checkpoint_id=None):
        with self.assertRaises(ws.IdentityReferenceUndecidableError) as ctx:
            ws._scan_identity_reference(repo.root, work_item_id, checkpoint_id)
        return ctx.exception.evidence

    def test_wrong_literal_refused(self):
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            evidence = self._undecidable_evidence(repo)
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.authorize_identity_reference_gap(
                    repo.root, "wi", now="t", user_authorization="wrong", evidence=evidence)

    def test_evidence_identity_mismatch_refused(self):
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            evidence = self._undecidable_evidence(repo)
            literal = ws.identity_reference_gap_authorization_literal("other", None, evidence)
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.authorize_identity_reference_gap(
                    repo.root, "other", now="t", user_authorization=literal, evidence=evidence)

    def test_happy_path_publishes_a_durable_record(self):
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            evidence = self._undecidable_evidence(repo)
            literal = ws.identity_reference_gap_authorization_literal("wi", None, evidence)
            record = ws.authorize_identity_reference_gap(
                repo.root, "wi", now="t1", user_authorization=literal, evidence=evidence)
            self.assertEqual(record["work_item_id"], "wi")
            self.assertIsNone(record["checkpoint_id"])
            self.assertFalse(record["consumed"])
            path = ws.identity_gap_authorization_path(repo.root, ws.gap_observation_id(evidence))
            self.assertTrue(path.exists())

    def test_idempotent_second_call_recognises_existing_record_rather_than_erroring(self):
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            evidence = self._undecidable_evidence(repo)
            literal = ws.identity_reference_gap_authorization_literal("wi", None, evidence)
            first = ws.authorize_identity_reference_gap(
                repo.root, "wi", now="t1", user_authorization=literal, evidence=evidence)
            second = ws.authorize_identity_reference_gap(
                repo.root, "wi", now="t2", user_authorization=literal, evidence=evidence)
            self.assertEqual(first, second)  # t1 preserved -- not re-authorized

    def test_stale_evidence_refused_when_reference_changed(self):
        """A commit repaired (or a new undecidable one added) between the
        evidence and the authorization changes the digest -- refused,
        having recorded nothing."""
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            evidence = self._undecidable_evidence(repo)
            literal = ws.identity_reference_gap_authorization_literal("wi", None, evidence)
            _commit_state(repo, "{also not valid json", message="second bad commit")
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.authorize_identity_reference_gap(
                    repo.root, "wi", now="t", user_authorization=literal, evidence=evidence)

    def test_refused_when_identity_now_decidably_observed(self):
        """Once a *second* commit decidably observes "wi", the reduction
        rule makes the whole scan resolve to "observed" rather than
        "undecidable" (an observed commit always wins) -- so a fresh
        evidence-independent re-scan hits the "now decidably observed"
        branch, never the digest-mismatch one."""
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            evidence = self._undecidable_evidence(repo)
            literal = ws.identity_reference_gap_authorization_literal("wi", None, evidence)
            _commit_state(repo, _state_json(wi={}))
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError) as ctx:
                ws.authorize_identity_reference_gap(
                    repo.root, "wi", now="t", user_authorization=literal, evidence=evidence)
            self.assertIn("now decidably observed", str(ctx.exception))

    def test_refused_when_the_query_now_admits_on_its_own(self):
        """The undecidable commit becoming unreachable from every ref
        (ordinary history maintenance -- `reset --hard` past it here)
        flips the reference's own scan to "admit": no gap remains to
        authorize."""
        with ScratchRepo() as repo:
            before = repo.head()
            _commit_state(repo, "{not valid json")
            evidence = self._undecidable_evidence(repo)
            literal = ws.identity_reference_gap_authorization_literal("wi", None, evidence)
            _run(["git", "reset", "--hard", before], cwd=repo.root)
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError) as ctx:
                ws.authorize_identity_reference_gap(
                    repo.root, "wi", now="t", user_authorization=literal, evidence=evidence)
            self.assertIn("no gap remains to authorize", str(ctx.exception))


class TestIdentityReferenceAdmits(unittest.TestCase):
    def test_decidable_absence_admits(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json())
            result = ws.identity_reference_admits(repo.root, "wi")
            self.assertEqual(result["decision"], "admit")

    def test_observed_work_item_id_raises_reused_error(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={}))
            with self.assertRaises(ws.WorkItemIdReusedError):
                ws.identity_reference_admits(repo.root, "wi")

    def test_observed_checkpoint_pair_raises_reused_error(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "COMPLETE"}}}))
            with self.assertRaises(ws.CheckpointIdReusedError):
                ws.identity_reference_admits(repo.root, "wi", "CP")

    def test_undecidable_without_authorization_raises(self):
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            with self.assertRaises(ws.IdentityReferenceUndecidableError):
                ws.identity_reference_admits(repo.root, "wi")

    def test_undecidable_with_matching_authorization_admits(self):
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            with self.assertRaises(ws.IdentityReferenceUndecidableError) as ctx:
                ws.identity_reference_admits(repo.root, "wi")
            evidence = ctx.exception.evidence
            literal = ws.identity_reference_gap_authorization_literal("wi", None, evidence)
            ws.authorize_identity_reference_gap(
                repo.root, "wi", now="t", user_authorization=literal, evidence=evidence)
            result = ws.identity_reference_admits(repo.root, "wi")
            self.assertEqual(result["decision"], "admit")
            self.assertEqual(result["route"], "authorized_gap")

    def test_authorization_for_a_different_identity_does_not_admit_this_one(self):
        """The digest binds the identity -- an authorized gap for `wi`
        never admits `other`, even against byte-identical undecidable
        commits."""
        with ScratchRepo() as repo:
            _commit_state(repo, "{not valid json")
            with self.assertRaises(ws.IdentityReferenceUndecidableError) as ctx:
                ws.identity_reference_admits(repo.root, "wi")
            evidence = ctx.exception.evidence
            literal = ws.identity_reference_gap_authorization_literal("wi", None, evidence)
            ws.authorize_identity_reference_gap(
                repo.root, "wi", now="t", user_authorization=literal, evidence=evidence)
            with self.assertRaises(ws.IdentityReferenceUndecidableError):
                ws.identity_reference_admits(repo.root, "other")


class TestWorkItemCreationIdentityEnforcement(unittest.TestCase):
    def test_omitted_repo_root_skips_the_check(self):
        """Backward compatible: a caller that never supplies `repo_root`
        (the in-memory/testing convenience this function always had) gets
        the unchanged, repo-independent routing."""
        state = _base_state()
        config = ws.default_config()
        new_state = ws.route_work_item(
            state, config, work_item_id="wi", work_item_type="process",
            work_item_kind="process", plan_path="p", registry_path="r",
            plan_revision=1, now="t",
        )
        self.assertIn("wi", new_state["work_items"])

    def test_fresh_id_never_observed_is_created(self):
        with ScratchRepo() as repo:
            new_state = ws.route_work_item(
                _base_state(), ws.default_config(), work_item_id="wi", work_item_type="process",
                work_item_kind="process", plan_path="p", registry_path="r",
                plan_revision=1, now="t", repo_root=repo.root,
            )
            self.assertIn("wi", new_state["work_items"])

    def test_fresh_id_observed_in_history_is_refused(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={}))
            with self.assertRaises(ws.WorkItemIdReusedError):
                ws.route_work_item(
                    _base_state(), ws.default_config(), work_item_id="wi", work_item_type="process",
                    work_item_kind="process", plan_path="p", registry_path="r",
                    plan_revision=1, now="t", repo_root=repo.root,
                )

    def test_resuming_an_existing_entry_never_reaches_the_check(self):
        """The check applies only to the fresh-id branch -- a resume
        (the id already lives in `state`) is untouched, even for an id
        that also happens to be historically observed."""
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={}))
            state = _base_state(wi=_base_work_item())
            new_state = ws.route_work_item(
                state, ws.default_config(), work_item_id="wi", work_item_type="process",
                work_item_kind="process", plan_path="p", registry_path="r",
                plan_revision=2, now="t", repo_root=repo.root,
            )
            self.assertEqual(new_state["work_items"]["wi"]["plan_revision"], 2)


class TestRegistryCheckpointIdReuseEnforcement(unittest.TestCase):
    def _write(self, repo, checkpoint_ids):
        registry = ws.generate_registry("wi", 1, [
            {"id": cid, "name": cid, "depends_on": [], "complexity": 1, "session_target": "1"}
            for cid in checkpoint_ids
        ])
        mapping = ws.generate_mapping(
            "wi", {"R1": {"description": "d", "checkpoint_ids": checkpoint_ids}}, registry=registry,
        )
        ws.write_registry_and_mapping(repo.root, Path("reg.json"), Path("map.json"), registry, mapping)

    def test_first_write_of_a_never_observed_id_succeeds(self):
        with ScratchRepo() as repo:
            self._write(repo, ["A"])
            self.assertTrue((repo.root / "reg.json").exists())

    def test_new_id_observed_historically_for_this_work_item_is_refused(self):
        with ScratchRepo() as repo:
            _commit_state(repo, _state_json(wi={"checkpoints": {"RETIRED": {"status": "COMPLETE"}}}))
            with self.assertRaises(ws.CheckpointIdReusedError):
                self._write(repo, ["RETIRED"])

    def test_id_kept_live_across_revisions_is_in_place_redefinition_not_reuse(self):
        """A checkpoint id present in the registry currently on disk is
        never re-checked on a later write, even if it is (as it always
        will be, once any checkpoint starts) observed in committed
        history -- in-place redefinition must stay legal."""
        with ScratchRepo() as repo:
            self._write(repo, ["A"])
            _commit_state(repo, _state_json(wi={"checkpoints": {"A": {"status": "IN_PROGRESS"}}}))
            self._write(repo, ["A", "B"])  # "A" unchanged, "B" genuinely new
            registry = json.loads((repo.root / "reg.json").read_text())
            self.assertEqual([c["id"] for c in registry["checkpoints"]], ["A", "B"])


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

    def _init_git_repo(self, root: Path) -> None:
        """`validate_state`'s whole-state registry check now requires a
        resolved `registry_path` to be a Git-tracked file (`GPT-R33-002`),
        not merely present on disk -- every test below that expects its
        fixture registry to actually be read must run inside a real (if
        disposable) Git repository. `git add` alone (no commit, no
        configured identity) is sufficient to make a path satisfy `git
        ls-files --error-unmatch`."""
        subprocess.run(["git", "init", "-q"], cwd=root, check=True)

    def _write_registry_file(
        self, root: Path, rel_path: str, *, work_item_id: str, plan_revision: int, track: bool = True,
    ) -> None:
        full = root / rel_path
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text(json.dumps({
            "work_item_id": work_item_id, "plan_revision": plan_revision, "checkpoints": [],
        }))
        if track:
            subprocess.run(["git", "add", rel_path], cwd=root, check=True)

    def test_repo_root_check_passes_when_every_applicable_items_mirror_matches(self):
        # Missing-test requirement (GPT-R31-003): two process work items,
        # each with its own registry_path and matching mirror.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/a.json", work_item_id="a", plan_revision=3)
            self._write_registry_file(root, "registry/b.json", work_item_id="b", plan_revision=7)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
                b=_base_work_item(work_item_id="b", registry_path="registry/b.json", plan_revision=7),
            )
            ws.validate_state(state, repo_root=root)  # must not raise

    def test_repo_root_check_rejects_either_items_mismatch_independently(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/a.json", work_item_id="a", plan_revision=3)
            self._write_registry_file(root, "registry/b.json", work_item_id="b", plan_revision=7)

            # item-a's own mirror disagrees; item-b's agrees.
            state_a_bad = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=999),
                b=_base_work_item(work_item_id="b", registry_path="registry/b.json", plan_revision=7),
            )
            with self.assertRaises(ws.PlanRevisionMirrorMismatchError):
                ws.validate_state(state_a_bad, repo_root=root)

            # item-a's own mirror agrees; item-b's disagrees -- proves the
            # check is not vacuously passing just because *some* item
            # matches.
            state_b_bad = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
                b=_base_work_item(work_item_id="b", registry_path="registry/b.json", plan_revision=999),
            )
            with self.assertRaises(ws.PlanRevisionMirrorMismatchError):
                ws.validate_state(state_b_bad, repo_root=root)

    def test_repo_root_check_fails_closed_on_missing_registry_file_not_silently(self):
        # "omitted registry coverage fails rather than silently skipping
        # the check" (GPT-R31-003's own required test).
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/does-not-exist.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_exempts_null_registry_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path=None, plan_revision=1),
            )
            ws.validate_state(state, repo_root=root)  # must not raise

    def test_repo_root_check_catches_a_different_items_stale_mirror_even_when_registry_param_names_another(self):
        """The exact failure scenario GPT-R31-003 describes: passing only
        `registry=` (core's own) lets a *different* item's stale mirror
        through; passing `repo_root=` too must catch it."""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/core.json", work_item_id="core", plan_revision=21)
            self._write_registry_file(root, "registry/second.json", work_item_id="second", plan_revision=5)
            state = _base_state(
                core=_base_work_item(work_item_id="core", registry_path="registry/core.json", plan_revision=21),
                second=_base_work_item(work_item_id="second", registry_path="registry/second.json", plan_revision=999),
            )
            core_registry = {"work_item_id": "core", "plan_revision": 21, "checkpoints": []}
            ws.validate_state(state, registry=core_registry)  # passes -- the exact defect GPT-R31-003 flagged
            with self.assertRaises(ws.PlanRevisionMirrorMismatchError):
                ws.validate_state(state, registry=core_registry, repo_root=root)

    # -- GPT-R32-001: whole-state registry_path resolution must go through
    # the shared safe-path resolver, not a bare `repo_root / registry_path`
    # join. --

    def test_repo_root_check_rejects_absolute_registry_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/a.json", work_item_id="a", plan_revision=3)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="/etc/passwd", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_dot_dot_traversal_registry_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            outside = root.parent / "foreign.json"
            outside.write_text(json.dumps({"work_item_id": "a", "plan_revision": 3, "checkpoints": []}))
            try:
                (root / "registry").mkdir(parents=True, exist_ok=True)
                state = _base_state(
                    a=_base_work_item(
                        work_item_id="a", registry_path="registry/../../foreign.json", plan_revision=3,
                    ),
                )
                with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                    ws.validate_state(state, repo_root=root)
            finally:
                outside.unlink()

    def test_repo_root_check_rejects_symlinked_registry_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/real.json", work_item_id="a", plan_revision=3)
            link = root / "registry" / "a.json"
            link.symlink_to(root / "registry" / "real.json")
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    # -- GPT-R33-001: a symlink at an *intermediate* path component must
    # be rejected exactly like a symlinked final component -- checking
    # only `Path.is_symlink()` on the fully joined path missed a symlinked
    # parent directory entirely. --

    def test_repo_root_check_rejects_intermediate_symlinked_directory_component_outside_repo(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            outside = root.parent / "outside_registry_target"
            try:
                outside.mkdir(parents=True, exist_ok=True)
                (outside / "item.json").write_text(json.dumps({
                    "work_item_id": "a", "plan_revision": 3, "checkpoints": [],
                }))
                # "registry" is not a symlink to a file -- it is a symlink
                # to an entire directory *outside* the repository. The
                # joined path `registry/item.json` is itself a real
                # regular file, not a symlink, so a final-component-only
                # check passes it straight through.
                (root / "registry").symlink_to(outside, target_is_directory=True)
                state = _base_state(
                    a=_base_work_item(work_item_id="a", registry_path="registry/item.json", plan_revision=3),
                )
                with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                    ws.validate_state(state, repo_root=root)
            finally:
                (root / "registry").unlink()
                (outside / "item.json").unlink()
                outside.rmdir()

    def test_repo_root_check_rejects_intermediate_symlinked_directory_component_inside_repo(self):
        # The rule is "no symlink component", full stop -- not "no symlink
        # component that happens to escape the repository". A symlinked
        # directory that merely aliases another location *inside* the
        # repository must be rejected too.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "real_registry/item.json", work_item_id="a", plan_revision=3)
            (root / "registry").symlink_to(root / "real_registry", target_is_directory=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/item.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_nested_symlink_chain(self):
        # The symlinked component is not the first one -- proves every
        # component is checked as the path is walked, not only the head
        # or the tail.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "real_registry/item.json", work_item_id="a", plan_revision=3)
            (root / "dir1").mkdir()
            (root / "dir1" / "link2").symlink_to(root / "real_registry", target_is_directory=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="dir1/link2/item.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_accepts_legitimate_nested_path_with_no_symlinks(self):
        # Regression guard for the two tests above: a real (non-symlinked)
        # multi-component path must still validate cleanly.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "a/b/c/item.json", work_item_id="a", plan_revision=3)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="a/b/c/item.json", plan_revision=3),
            )
            ws.validate_state(state, repo_root=root)  # must not raise

    def test_repo_root_check_rejects_non_regular_file_registry_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "registry").mkdir(parents=True, exist_ok=True)
            state = _base_state(
                # "registry" itself is a directory, not a regular file.
                a=_base_work_item(work_item_id="a", registry_path="registry", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    # -- GPT-R32-002: a registry with no valid plan_revision must fail
    # closed, not be silently treated as "nothing to compare". --

    def test_repo_root_check_rejects_registry_missing_plan_revision_field(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            full = root / "registry" / "a.json"
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text(json.dumps({"work_item_id": "a", "checkpoints": []}))
            subprocess.run(["git", "add", "registry/a.json"], cwd=root, check=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.InvalidRegistryPlanRevisionError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_registry_with_null_plan_revision(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            full = root / "registry" / "a.json"
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text(json.dumps({"work_item_id": "a", "plan_revision": None, "checkpoints": []}))
            subprocess.run(["git", "add", "registry/a.json"], cwd=root, check=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.InvalidRegistryPlanRevisionError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_registry_with_string_plan_revision(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            full = root / "registry" / "a.json"
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text(json.dumps({"work_item_id": "a", "plan_revision": "3", "checkpoints": []}))
            subprocess.run(["git", "add", "registry/a.json"], cwd=root, check=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.InvalidRegistryPlanRevisionError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_registry_with_boolean_plan_revision(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            full = root / "registry" / "a.json"
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text(json.dumps({"work_item_id": "a", "plan_revision": True, "checkpoints": []}))
            subprocess.run(["git", "add", "registry/a.json"], cwd=root, check=True)
            state = _base_state(
                # bool is an int subclass in Python -- must still be rejected.
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=1),
            )
            with self.assertRaises(ws.InvalidRegistryPlanRevisionError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_registry_with_out_of_range_plan_revision(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/a.json", work_item_id="a", plan_revision=0)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=0),
            )
            with self.assertRaises(ws.InvalidRegistryPlanRevisionError):
                ws.validate_state(state, repo_root=root)

    # -- GPT-R32-003: the loaded registry must declare the exact state
    # work-item ID -- cross-wiring two items must not pass just because
    # their revision numbers happen to agree. --

    def test_repo_root_check_rejects_cross_wired_registry_with_matching_revision(self):
        # Distinct registry_path values per item (so the pre-existing
        # write-time duplicate-path check does not itself catch this), but
        # item b's own registry file was hand-edited/copied and its content
        # still declares work_item_id "a" -- the exact "validate one
        # artifact while trusting another identity" defect class.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/a.json", work_item_id="a", plan_revision=21)
            self._write_registry_file(root, "registry/b.json", work_item_id="a", plan_revision=21)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=21),
                b=_base_work_item(work_item_id="b", registry_path="registry/b.json", plan_revision=21),
            )
            with self.assertRaises(fingerprint.RegistryWorkItemIdMismatchError):
                ws.validate_state(state, repo_root=root)

    # -- GPT-R33-002: the whole-state registry check must require a
    # *Git-tracked* regular file, not merely a readable one -- otherwise
    # an untracked JSON file dropped anywhere in the worktree can become
    # the authoritative comparison source, invisible to commits, review
    # bundles, fresh sessions, and approval provenance. --

    def test_repo_root_check_accepts_tracked_registry_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/a.json", work_item_id="a", plan_revision=3, track=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            ws.validate_state(state, repo_root=root)  # must not raise

    def test_repo_root_check_rejects_untracked_registry_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            # An otherwise-valid registry file (right shape, right
            # content, right location) that was simply never `git add`ed.
            self._write_registry_file(root, "registry/a.json", work_item_id="a", plan_revision=3, track=False)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_registry_path_removed_from_index_but_left_in_worktree(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            self._write_registry_file(root, "registry/a.json", work_item_id="a", plan_revision=3)
            # Staged and tracked a moment ago; now untracked again while
            # the file itself remains on disk unchanged -- the exact
            # split-brain scenario GPT-R33-002 describes (worktree
            # validates, committed repository does not).
            subprocess.run(["git", "rm", "--cached", "-q", "registry/a.json"], cwd=root, check=True)
            self.assertTrue((root / "registry" / "a.json").is_file())
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    # -- GPT-R33-004: malformed or wrong-shape registry JSON must fail
    # through this check's own named, work-item-specific error, not a raw
    # JSONDecodeError/AttributeError. --

    def test_repo_root_check_rejects_malformed_json_registry(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            full = root / "registry" / "a.json"
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text("{not valid json")
            subprocess.run(["git", "add", "registry/a.json"], cwd=root, check=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_json_array_registry(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            full = root / "registry" / "a.json"
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text(json.dumps(["a", "plan_revision", 3]))
            subprocess.run(["git", "add", "registry/a.json"], cwd=root, check=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

    def test_repo_root_check_rejects_scalar_registry(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._init_git_repo(root)
            full = root / "registry" / "a.json"
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text(json.dumps(3))
            subprocess.run(["git", "add", "registry/a.json"], cwd=root, check=True)
            state = _base_state(
                a=_base_work_item(work_item_id="a", registry_path="registry/a.json", plan_revision=3),
            )
            with self.assertRaises(ws.MissingRegistryForPlanRevisionMirrorCheckError):
                ws.validate_state(state, repo_root=root)

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


class TestPublishPlanRevision(unittest.TestCase):
    """`D-Plan-Revision-Publication`/`WFR-65`, missing-test item 371's
    hermetic half (sub-items a-c, e): the single sanctioned writer of a
    plan-revision bump into `WORKFLOW_STATE.json`'s non-authoritative
    mirror, which also performs the plan-review phase transition in the
    same operation."""

    def test_v1_governed_end_to_end_publication(self):
        """Item 371(a)/(d), v1 half: a governing-v1 continued-scope plan
        revision opened from `IMPLEMENTING` mirrors the registry's
        `plan_revision` immediately and enters
        `AWAITING_EXTERNAL_PLAN_REVIEW`, and the result passes
        `validate_state` against a registry declaring the same revision --
        no manual repair required."""
        wi = _base_work_item(
            governing_workflow_version="1", phase="IMPLEMENTING",
            plan_revision=62, state_revision=5, last_transition="t0",
        )
        state = _base_state(wi=wi)
        new_state = ws.publish_plan_revision(state, "wi", 63, "t1")
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["plan_revision"], 63)
        self.assertEqual(item["phase"], "AWAITING_EXTERNAL_PLAN_REVIEW")
        self.assertEqual(item["state_revision"], 6)
        self.assertEqual(item["last_transition"], "t1")
        # input state untouched (every mutator in this module returns a
        # fresh dict rather than mutating its argument)
        self.assertEqual(state["work_items"]["wi"]["plan_revision"], 62)
        # the mirror now agrees with a registry declaring the same revision
        registry = {"work_item_id": "wi", "plan_revision": 63, "checkpoints": []}
        ws.validate_state(new_state, registry=registry)

    def test_v21_governed_enters_awaiting_local_plan_review(self):
        """Item 371(d): a `"2.1"`-governed item's target phase is
        `AWAITING_LOCAL_PLAN_REVIEW`, never `AWAITING_EXTERNAL_PLAN_REVIEW`
        -- `D-Plan-Review-Stages` always enters local review first."""
        wi = _v21_work_item(phase="REVISING_PLAN", plan_revision=4, state_revision=2)
        new_state = ws.publish_plan_revision(_base_state(wi=wi), "wi", 5, "t1")
        item = new_state["work_items"]["wi"]
        self.assertEqual(item["plan_revision"], 5)
        self.assertEqual(item["phase"], "AWAITING_LOCAL_PLAN_REVIEW")

    def test_idempotent_retry_is_a_true_no_op(self):
        """Item 371(b): re-running with the same `plan_revision` and the
        resulting phase already reached is a no-op -- no `state_revision`/
        `last_transition` bump -- so an interrupted revision is retried
        rather than repaired."""
        wi = _base_work_item(
            governing_workflow_version="1", phase="AWAITING_EXTERNAL_PLAN_REVIEW",
            plan_revision=63, state_revision=6, last_transition="t1",
        )
        state = _base_state(wi=wi)
        result = ws.publish_plan_revision(state, "wi", 63, "t2")
        self.assertIs(result, state)
        self.assertEqual(result["work_items"]["wi"]["state_revision"], 6)
        self.assertEqual(result["work_items"]["wi"]["last_transition"], "t1")

    def test_touches_no_other_work_item(self):
        """Item 371(b): publication alters no other work item's entry."""
        wi = _base_work_item(governing_workflow_version="1", phase="IMPLEMENTING", plan_revision=1)
        other = _base_work_item(
            work_item_id="other", governing_workflow_version="2.1",
            phase="IMPLEMENTING", plan_revision=9, state_revision=3, last_transition="tX",
        )
        state = _base_state(wi=wi, other=other)
        new_state = ws.publish_plan_revision(state, "wi", 2, "t1")
        self.assertEqual(new_state["work_items"]["other"], other)

    def test_refuses_terminal_phase_item(self):
        """Item 371(c): refuses a terminal-phase item outright."""
        wi = _base_work_item(
            governing_workflow_version="1", phase="MILESTONE_COMPLETE", plan_revision=1,
        )
        with self.assertRaises(ws.TerminalPlanRevisionPublicationError):
            ws.publish_plan_revision(_base_state(wi=wi), "wi", 2, "t1")

    def test_unsupported_governing_version_rejected(self):
        wi = _base_work_item(governing_workflow_version="3", phase="IMPLEMENTING", plan_revision=1)
        with self.assertRaises(ws.UnsupportedGoverningVersionError):
            ws.publish_plan_revision(_base_state(wi=wi), "wi", 2, "t1")

    def test_written_through_d1_serialized_state_write_primitive(self):
        """Item 371(c): the exhaustive call sites write through
        `state_transaction` (`D1`'s serialized primitive), never as a
        plain JSON edit -- exercised here end to end against a real
        on-disk state file."""
        with ScratchRepo() as repo:
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state_path.parent.mkdir(parents=True, exist_ok=True)
            wi = _base_work_item(governing_workflow_version="1", phase="IMPLEMENTING", plan_revision=1)
            state_path.write_text(json.dumps(_base_state(wi=wi)))
            mutator = lambda state: ws.publish_plan_revision(state, "wi", 2, "t1")
            result = ws.state_transaction(repo.root, mutator, path=Path("docs/ai-workflow/WORKFLOW_STATE.json"))
            self.assertEqual(result["work_items"]["wi"]["plan_revision"], 2)
            on_disk = json.loads(state_path.read_text())
            self.assertEqual(on_disk["work_items"]["wi"]["plan_revision"], 2)
            self.assertEqual(on_disk["work_items"]["wi"]["phase"], "AWAITING_EXTERNAL_PLAN_REVIEW")


class TestWorkItemCompletion(unittest.TestCase):
    def test_completing_active_item_resets_pointer(self):
        state = _base_state(wi=_base_work_item(phase="AWAITING_USER_ACCEPTANCE"))
        state["active_work_item_id"] = "wi"
        new_state = ws.complete_work_item(state, "wi", now="t", repo_root=Path("."))
        self.assertEqual(new_state["work_items"]["wi"]["phase"], "MILESTONE_COMPLETE")
        self.assertIsNone(new_state["active_work_item_id"])
        ws.validate_state(new_state)  # a completed, unpointed item is valid

    def test_completing_non_active_item_leaves_pointer_alone(self):
        state = _base_state(
            active=_base_work_item(work_item_id="active", phase="IMPLEMENTING"),
            other=_base_work_item(work_item_id="other", phase="AWAITING_USER_ACCEPTANCE"),
        )
        state["active_work_item_id"] = "active"
        new_state = ws.complete_work_item(state, "other", now="t", repo_root=Path("."))
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
        # A real Git repo (OPUS-R89-005's checkpoint-id-reuse check reads
        # D-Checkpoint-Ownership's origination reference, which needs one).
        with ScratchRepo() as repo:
            registry = ws.generate_registry("wi", 1, [
                {"id": "A", "name": "a", "depends_on": [], "complexity": 1, "session_target": "1"},
            ])
            mapping = ws.generate_mapping("wi", {"R1": {"description": "d", "checkpoint_ids": ["A"]}}, registry=registry)
            ws.write_registry_and_mapping(repo.root, Path("reg.json"), Path("map.json"), registry, mapping)
            self.assertEqual(json.loads((repo.root / "reg.json").read_text()), registry)
            self.assertEqual(json.loads((repo.root / "map.json").read_text()), mapping)

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


class TestImplementingEntryReachableSecondItem(unittest.TestCase):
    """Missing-test item 150's third caller (`GPT-R31-002`):
    `implementing_entry_reachable` -- as opposed to `approval_is_current`,
    already exercised against a second item at the implementation stage
    by `TestImplementationStageApprovalSecondItem` above -- gates
    independently for a second, non-`"wi"`, non-`workflow-v2-1-core`
    process work item's own `plan_approval` record, with no `plan_revision`
    parameter passed."""

    def test_second_items_plan_approval_is_reachable_via_implementing_entry_reachable(self):
        with ScratchRepo() as repo:
            work_item_id = "second-item"
            plan_path_rel = f"docs/ai-workflow/{work_item_id}-plan.md"
            registry_rel = f"docs/ai-workflow/registry/{work_item_id}-registry.json"
            mapping_rel = f"docs/ai-workflow/requirements/{work_item_id}-mapping.json"
            artifacts_rel = f"docs/ai-workflow/registry/{work_item_id}-artifacts.json"
            state_rel = "docs/ai-workflow/WORKFLOW_STATE.json"
            for rel, content in (
                (plan_path_rel, "# Plan (Revision 1)\n\nplan v1\n"),
                (registry_rel, json.dumps({"work_item_id": work_item_id, "plan_revision": 1, "checkpoints": []})),
                (mapping_rel, json.dumps({"work_item_id": work_item_id, "requirements": {}})),
                (artifacts_rel, json.dumps({
                    "schema_version": 2, "work_item_id": work_item_id,
                    "plan_stage": {
                        "protected_paths": [plan_path_rel, registry_rel, mapping_rel],
                        "excluded_paths": {
                            state_rel: "runtime-mutable state",
                            artifacts_rel: "non-immutable registry artifact",
                        },
                        "excluded_prefixes": {"scripts/": "checkpoint scaffolding"},
                    },
                })),
                (state_rel, json.dumps({
                    "schema_version": 1, "active_work_item_id": work_item_id,
                    "work_items": {
                        work_item_id: {
                            "work_item_id": work_item_id, "work_item_type": "process",
                            "plan_path": plan_path_rel, "registry_path": registry_rel,
                            "mapping_path": mapping_rel, "base_commit": repo.base,
                        },
                    },
                })),
            ):
                full = repo.root / rel
                full.parent.mkdir(parents=True, exist_ok=True)
                full.write_text(content)
                _run(["git", "add", rel], cwd=repo.root)

            protected = frozenset({plan_path_rel, registry_rel, mapping_rel})
            excluded_paths = {state_rel: "runtime-mutable state", artifacts_rel: "non-immutable registry artifact"}
            excluded_prefixes = {"scripts/": "checkpoint scaffolding"}
            review_content_id, _ = fingerprint.compute_review_content_id_plan_stage(
                repo.root, repo.base, "process", work_item_id, 1, protected, excluded_paths, excluded_prefixes,
            )
            _run(
                ["git", "commit", "-q", "-m",
                 f"approve plan\n\nWorkflow-Plan-Approval: {review_content_id}\n"
                 f"Workflow-Work-Item: {work_item_id}"],
                cwd=repo.root,
            )
            approval_sha = repo.head()

            work_item = {
                "work_item_id": work_item_id, "work_item_type": "process", "plan_revision": 1,
                "plan_approval": {"status": "CURRENT", "approved_review_content_id": review_content_id},
            }
            self.assertTrue(
                ws.implementing_entry_reachable(repo.root, work_item, repo.base),
                "a second item's own plan_approval must be independently reachable, "
                "with no plan_revision parameter passed to implementing_entry_reachable",
            )

            # A checkpoint commit for this second item must keep it
            # reachable, exactly like TestApprovalFreshnessAndEntry proves
            # for "wi" above -- proving the property generalizes, not
            # only holds for one hardcoded item.
            checkpoint_path = repo.root / "scripts" / f"{work_item_id}_checkpoint_1.txt"
            checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
            checkpoint_path.write_text("checkpoint 1\n")
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(
                ["git", "commit", "-q", "-m",
                 f"checkpoint 1\n\nWorkflow-Checkpoint: WF1\nWorkflow-Work-Item: {work_item_id}"],
                cwd=repo.root,
            )
            self.assertTrue(ws.implementing_entry_reachable(repo.root, work_item, repo.base))
            self.assertNotEqual(approval_sha, repo.head())


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
        state = _base_state(wi=_base_work_item(
            phase="SELF_REVIEWING_IMPLEMENTATION",
            reviewed_implementation_head=None, implementation_revision=None,
        ))
        new_state = ws.record_bundle_generation(state, "wi", stage="implementation", head="abc123", now="t1")
        wi = new_state["work_items"]["wi"]
        self.assertEqual(wi["reviewed_implementation_head"], "abc123")
        self.assertEqual(wi["implementation_revision"], 1)

    def test_post_fix_call_advances_head_and_increments_revision(self):
        state = _base_state(wi=_base_work_item(
            phase="APPLYING_REVIEW_FEEDBACK",
            reviewed_implementation_head="abc123", implementation_revision=1,
        ))
        new_state = ws.record_bundle_generation(state, "wi", stage="post-fix", head="def456", now="t2")
        wi = new_state["work_items"]["wi"]
        self.assertEqual(wi["reviewed_implementation_head"], "def456")
        self.assertEqual(wi["implementation_revision"], 2)

    def test_unknown_stage_rejected(self):
        state = _base_state(wi=_base_work_item())
        with self.assertRaises(ws.InvalidBundleGenerationStageError):
            ws.record_bundle_generation(state, "wi", stage="plan", head="abc123", now="t1")

    def test_original_state_untouched(self):
        state = _base_state(wi=_base_work_item(
            phase="SELF_REVIEWING_IMPLEMENTATION",
            reviewed_implementation_head=None, implementation_revision=None,
        ))
        ws.record_bundle_generation(state, "wi", stage="implementation", head="abc123", now="t1")
        self.assertIsNone(state["work_items"]["wi"]["reviewed_implementation_head"])

    def test_first_call_writes_target_phase(self):
        """OPUS-R101-001: `record_bundle_generation` must itself write
        `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`, asserted from the
        returned state (the caller persists it, mirroring the committed
        blob a fresh session would re-read)."""
        state = _base_state(wi=_base_work_item(phase="SELF_REVIEWING_IMPLEMENTATION"))
        new_state = ws.record_bundle_generation(state, "wi", stage="implementation", head="abc123", now="t1")
        self.assertEqual(
            new_state["work_items"]["wi"]["phase"], "AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW",
        )

    def test_post_fix_call_writes_target_phase(self):
        state = _base_state(wi=_base_work_item(phase="APPLYING_REVIEW_FEEDBACK"))
        new_state = ws.record_bundle_generation(state, "wi", stage="post-fix", head="def456", now="t2")
        self.assertEqual(
            new_state["work_items"]["wi"]["phase"], "AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW",
        )

    def test_illegal_source_phase_refused_naming_actual_and_legal_phases(self):
        state = _base_state(wi=_base_work_item(phase="IMPLEMENTING"))
        with self.assertRaises(ws.IllegalBundleGenerationSourcePhaseError) as ctx:
            ws.record_bundle_generation(state, "wi", stage="implementation", head="abc123", now="t1")
        self.assertIn("IMPLEMENTING", str(ctx.exception))
        self.assertIn("SELF_REVIEWING_IMPLEMENTATION", str(ctx.exception))
        self.assertIn("APPLYING_REVIEW_FEEDBACK", str(ctx.exception))

    def test_post_fix_from_illegal_source_phase_also_refused(self):
        state = _base_state(wi=_base_work_item(phase="AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW"))
        with self.assertRaises(ws.IllegalBundleGenerationSourcePhaseError):
            ws.record_bundle_generation(state, "wi", stage="post-fix", head="def456", now="t2")

    def test_bundle_generation_target_and_recovery_phase_pair_are_both_reachable(self):
        """Narrower guard than OPUS-R101-001's own suggested blanket
        all-17-phases sweep (see IMPLEMENTATION_SUMMARY.md for why that
        broader test was not added as-is): the specific pair this finding's
        reproduction is about -- `record_bundle_generation`'s target
        (`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`) and
        `enter_applying_review_feedback`'s target
        (`APPLYING_REVIEW_FEEDBACK`) -- must each be written by a real,
        named module-level function, not merely declared in `KNOWN_PHASES`."""
        import ast
        source = Path(ws.__file__).read_text()
        tree = ast.parse(source)
        written_phases: set[str] = set()
        for node in ast.walk(tree):
            if (
                isinstance(node, ast.Assign)
                and len(node.targets) == 1
                and isinstance(node.targets[0], ast.Subscript)
                and isinstance(node.targets[0].slice, ast.Constant)
                and node.targets[0].slice.value == "phase"
                and isinstance(node.value, ast.Constant)
                and isinstance(node.value.value, str)
            ):
                written_phases.add(node.value.value)
        self.assertIn("AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW", written_phases)
        self.assertIn("APPLYING_REVIEW_FEEDBACK", written_phases)


class TestEnterApplyingReviewFeedback(unittest.TestCase):
    def test_sets_phase_from_legal_source(self):
        state = _base_state(wi=_base_work_item(phase="AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW"))
        new_state = ws.enter_applying_review_feedback(state, "wi", now="t1")
        self.assertEqual(new_state["work_items"]["wi"]["phase"], "APPLYING_REVIEW_FEEDBACK")

    def test_refused_from_illegal_source_phase(self):
        state = _base_state(wi=_base_work_item(phase="IMPLEMENTING"))
        with self.assertRaises(ws.IllegalApplyingReviewFeedbackEntryPhaseError) as ctx:
            ws.enter_applying_review_feedback(state, "wi", now="t1")
        self.assertIn("IMPLEMENTING", str(ctx.exception))
        self.assertIn("AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW", str(ctx.exception))

    def test_original_state_untouched(self):
        state = _base_state(wi=_base_work_item(phase="AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW"))
        ws.enter_applying_review_feedback(state, "wi", now="t1")
        self.assertEqual(
            state["work_items"]["wi"]["phase"], "AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW",
        )


def _write_test_artifacts_declaration(
    repo: "ScratchRepo", work_item_id: str, *, protected_prefixes=None, excluded_prefixes=None,
) -> None:
    """A minimal implementation-stage classification declaration at the
    real `artifacts_path_for_work_item` location: `src/` protected,
    `docs/` (which also covers `WORKFLOW_STATE.json`) excluded, by
    default -- committed immediately so later commits can be diffed
    against it."""
    rel = fingerprint.artifacts_path_for_work_item(work_item_id)
    full = repo.root / rel
    full.parent.mkdir(parents=True, exist_ok=True)
    declaration = {
        "schema_version": 2,
        "work_item_id": work_item_id,
        "implementation_stage": {
            "protected_paths": {},
            "protected_prefixes": {p: "test" for p in (protected_prefixes or ["src/"])},
            "excluded_paths": {},
            "excluded_prefixes": {p: "test" for p in (excluded_prefixes or ["docs/"])},
        },
    }
    full.write_text(json.dumps(declaration))
    _run(["git", "add", str(rel)], cwd=repo.root)
    _run(["git", "commit", "-q", "-m", "artifacts declaration"], cwd=repo.root)


def _commit_state_only(
    repo: "ScratchRepo", work_item_id: str, work_item: dict, message: str,
    trailers: dict[str, str] | None = None,
) -> str:
    """Commits `WORKFLOW_STATE.json` alone -- staging exactly that one
    path, wrapping `work_item` in the real `{"work_items": {...}}` shape
    -- mirroring the ordinary `Workflow-Bundle-Generation-Record` commit
    contract (`validate_bundle_generation_record_commit`: touches only
    `WORKFLOW_STATE.json`)."""
    full = repo.root / STATE_REL_PATH
    full.parent.mkdir(parents=True, exist_ok=True)
    content = {"schema_version": 1, "work_items": {work_item_id: work_item}}
    full.write_bytes(ws._serialize_state(content))
    _run(["git", "add", STATE_REL_PATH], cwd=repo.root)
    body = message
    if trailers:
        body += "\n\n" + "\n".join(f"{k}: {v}" for k, v in trailers.items())
    _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
    return repo.head()


def _seed_base_provenance_state(repo: "ScratchRepo", work_item_id: str) -> None:
    """Commits a baseline `WORKFLOW_STATE.json` (static fields like
    `work_item_id` already present, `reviewed_implementation_head` unset)
    *before* any protected content commit -- mirroring the real repository,
    where `WORKFLOW_STATE.json` already exists and already carries every
    static field by the time a generation-record commit runs. Without
    this, a test's very first commit would show every field as "changed"
    (added from nothing), including static ones no real generation-record
    commit ever touches."""
    _commit_state_only(repo, work_item_id, {
        "work_item_id": work_item_id,
        "reviewed_implementation_head": None,
        "implementation_revision": 0,
        "phase": "IMPLEMENTING",
        "state_revision": 0,
        "last_transition": "t0",
    }, "seed base state")


def _provenance_state(work_item_id: str, *, reviewed_implementation_head, implementation_revision) -> dict:
    """The state as committed *by* the generation-record commit `T` --
    `phase` is therefore the ordinary target
    `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`, not a source phase
    (OPUS-R101-001: a fixture that hard-codes the same phase on both sides
    of `T` can never exercise the phase-transition contract)."""
    return {
        "work_item_id": work_item_id,
        "reviewed_implementation_head": reviewed_implementation_head,
        "implementation_revision": implementation_revision,
        "phase": "AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW",
        "state_revision": implementation_revision + 1,
        "last_transition": f"t{implementation_revision}",
    }


def _record_trailers(work_item_id: str, implementation_revision: int) -> dict[str, str]:
    return {
        "Workflow-Bundle-Generation-Record": f"{work_item_id}/{implementation_revision}",
        "Workflow-Work-Item": work_item_id,
    }


class TestImplementationProvenanceInterval(unittest.TestCase):
    """WF8B-003 remediation (WF8b): `verify_implementation_provenance_interval`
    replaces a bare `reviewed_implementation_head == HEAD` comparison with
    D-Commit-Provenance's "Provenance interval" check (revision 28
    onward)."""

    WI = "wi"

    def test_exact_reviewed_head_zero_gap_is_reachable(self):
        """T immediately follows P (the protected content commit) with no
        intervening commits -- the simplest valid interval."""
        with ScratchRepo() as repo:
            _write_test_artifacts_declaration(repo, self.WI)
            _seed_base_provenance_state(repo, self.WI)
            p = repo.commit("protected fix", filename="src/Foo.kt")
            state = _provenance_state(self.WI, reviewed_implementation_head=p, implementation_revision=1)
            t = _commit_state_only(repo, self.WI, state, "record gen", trailers=_record_trailers(self.WI, 1))
            work_item = state | {"work_item_id": self.WI}
            result = ws.verify_implementation_provenance_interval(repo.root, work_item, repo.base)
            self.assertEqual(result, t)
            self.assertTrue(ws.implementation_provenance_interval_reachable(repo.root, work_item, repo.base))

    def test_one_excluded_commit_between_p_and_t_is_reachable(self):
        """A single excluded-only commit (mirroring a docs/outcome-record
        commit) lands between P and T -- still a valid interval, since it
        touches no protected path."""
        with ScratchRepo() as repo:
            _write_test_artifacts_declaration(repo, self.WI)
            _seed_base_provenance_state(repo, self.WI)
            p = repo.commit("protected fix", filename="src/Foo.kt")
            repo.commit("unrelated excluded commit", filename="docs/notes.md")
            state = _provenance_state(self.WI, reviewed_implementation_head=p, implementation_revision=1)
            t = _commit_state_only(repo, self.WI, state, "record gen", trailers=_record_trailers(self.WI, 1))
            work_item = state | {"work_item_id": self.WI}
            result = ws.verify_implementation_provenance_interval(repo.root, work_item, repo.base)
            self.assertEqual(result, t)

    def test_unrelated_descendant_commit_after_t_refuses(self):
        """Live HEAD is one commit past the discovered T -- the exact
        real-world shape WF8B-003 exists to catch: a further commit
        landed carrying no provenance record of its own."""
        with ScratchRepo() as repo:
            _write_test_artifacts_declaration(repo, self.WI)
            _seed_base_provenance_state(repo, self.WI)
            p = repo.commit("protected fix", filename="src/Foo.kt")
            state = _provenance_state(self.WI, reviewed_implementation_head=p, implementation_revision=1)
            _commit_state_only(repo, self.WI, state, "record gen", trailers=_record_trailers(self.WI, 1))
            repo.commit("trailing commit, no record", filename="docs/more.md")
            work_item = state | {"work_item_id": self.WI}
            with self.assertRaises(ws.HeadPastBundleGenerationRecordError):
                ws.verify_implementation_provenance_interval(repo.root, work_item, repo.base)
            self.assertFalse(ws.implementation_provenance_interval_reachable(repo.root, work_item, repo.base))

    def test_wrong_trailer_key_is_never_discovered_and_refuses(self):
        """A commit that carries a misspelled/wrong trailer key is simply
        not discovered -- the gate refuses with "not found", never a
        silent match."""
        with ScratchRepo() as repo:
            _write_test_artifacts_declaration(repo, self.WI)
            _seed_base_provenance_state(repo, self.WI)
            p = repo.commit("protected fix", filename="src/Foo.kt")
            state = _provenance_state(self.WI, reviewed_implementation_head=p, implementation_revision=1)
            _commit_state_only(repo, self.WI, state, "record gen", trailers={
                "Workflow-Bundle-Generation-Recordx": f"{self.WI}/1",  # typo'd key
                "Workflow-Work-Item": self.WI,
            })
            work_item = state | {"work_item_id": self.WI}
            with self.assertRaises(ws.BundleGenerationRecordNotFoundError):
                ws.verify_implementation_provenance_interval(repo.root, work_item, repo.base)

    def test_reviewed_head_not_an_ancestor_refuses(self):
        """`reviewed_implementation_head` names a commit that isn't
        actually an ancestor of the discovered T at all."""
        with ScratchRepo() as repo:
            _write_test_artifacts_declaration(repo, self.WI)
            _seed_base_provenance_state(repo, self.WI)
            repo.commit("protected fix", filename="src/Foo.kt")
            bogus_p = repo.commit("unrelated commit, never reviewed", filename="src/Bar.kt")
            # Reset to before bogus_p so it's a sibling, not an ancestor, of T.
            _run(["git", "reset", "-q", "--hard", "HEAD^"], cwd=repo.root)
            state = _provenance_state(self.WI, reviewed_implementation_head=bogus_p, implementation_revision=1)
            _commit_state_only(repo, self.WI, state, "record gen", trailers=_record_trailers(self.WI, 1))
            work_item = state | {"work_item_id": self.WI}
            with self.assertRaises(ws.ReviewedImplementationHeadNotAncestorError):
                ws.verify_implementation_provenance_interval(repo.root, work_item, repo.base)

    def test_second_unexpected_descendant_with_same_trailer_value_refuses(self):
        """Two distinct commits both carry a Workflow-Bundle-Generation-Record
        trailer for the exact same `<work_item_id>/<implementation_revision>`
        value -- genuine ambiguity, not silently resolved (mirrors
        `TestCheckpointTrailerDiscovery.test_genuine_ambiguity_raises`)."""
        with ScratchRepo() as repo:
            _write_test_artifacts_declaration(repo, self.WI)
            _seed_base_provenance_state(repo, self.WI)
            p = repo.commit("protected fix", filename="src/Foo.kt")
            state = _provenance_state(self.WI, reviewed_implementation_head=p, implementation_revision=1)
            _commit_state_only(repo, self.WI, state, "record gen a", trailers=_record_trailers(self.WI, 1))
            state_b = state | {"last_transition": "t1-again"}  # distinct content, same trailer value
            _commit_state_only(repo, self.WI, state_b, "record gen b", trailers=_record_trailers(self.WI, 1))
            work_item = state | {"work_item_id": self.WI}
            with self.assertRaises(ws.AmbiguousBundleGenerationRecordTrailerError):
                ws.verify_implementation_provenance_interval(repo.root, work_item, repo.base)

    def test_side_branch_merge_topology_refuses(self):
        """`reviewed_implementation_head` is genuinely reachable from T,
        but only through a merge's non-first-parent side -- T's own
        first-parent chain never passes through it."""
        with ScratchRepo() as repo:
            _write_test_artifacts_declaration(repo, self.WI)
            _seed_base_provenance_state(repo, self.WI)
            fork_point = repo.head()
            _run(["git", "checkout", "-q", "-b", "side"], cwd=repo.root)
            p = repo.commit("protected fix on side", filename="src/Foo.kt")
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            _run(["git", "reset", "-q", "--hard", fork_point], cwd=repo.root)
            repo.commit("excluded commit on main", filename="docs/main-notes.md")
            _run(["git", "merge", "-q", "--no-ff", "-m", "merge side", "side"], cwd=repo.root)
            state = _provenance_state(self.WI, reviewed_implementation_head=p, implementation_revision=1)
            t = _commit_state_only(repo, self.WI, state, "record gen", trailers=_record_trailers(self.WI, 1))
            work_item = state | {"work_item_id": self.WI}
            with self.assertRaises(ws.NonFirstParentProvenanceIntervalError):
                ws.verify_implementation_provenance_interval(repo.root, work_item, repo.base)
            self.assertFalse(ws.implementation_provenance_interval_reachable(repo.root, work_item, repo.base))
            self.assertNotEqual(t, "")  # t is created; just never a valid interval terminus

    def test_real_v2_1_dry_run_s8_to_s9_shape_becomes_reachable(self):
        """Reproduces the exact real-repository shape WF8B-003 surfaced at
        `v2-1-dry-run`'s own S9 gate: a protected scratch-marker fix
        commit (S8's `ae7ef4c`-equivalent), followed by an excluded-only
        docs/outcome-recording commit (S8's `34ce1dd`-equivalent), then a
        dedicated ordinary Workflow-Bundle-Generation-Record commit --
        the gate must become reachable, unlike the pre-remediation bare
        `reviewed_implementation_head == HEAD` rule that blocked it."""
        with ScratchRepo() as repo:
            _write_test_artifacts_declaration(
                repo, "v2-1-dry-run",
                protected_prefixes=["docs/ai-workflow/dry-run/scratch/"],
                excluded_prefixes=["docs/"],
            )
            _seed_base_provenance_state(repo, "v2-1-dry-run")
            p = repo.commit("fix S-CP3 scratch marker", filename="docs/ai-workflow/dry-run/scratch/c.txt")
            repo.commit(
                "docs(wf8b): execute v2-1-dry-run's S8", filename="docs/ai-workflow/dry-run/WF8B_SCENARIOS.md",
            )
            state = _provenance_state("v2-1-dry-run", reviewed_implementation_head=p, implementation_revision=2)
            t = _commit_state_only(
                repo, "v2-1-dry-run", state, "record gen (post-fix)",
                trailers=_record_trailers("v2-1-dry-run", 2),
            )
            work_item = state | {"work_item_id": "v2-1-dry-run"}
            self.assertTrue(ws.implementation_provenance_interval_reachable(repo.root, work_item, repo.base))
            self.assertEqual(
                ws.verify_implementation_provenance_interval(repo.root, work_item, repo.base), t,
            )


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
            ws.complete_work_item(state, "parent", now="t2", repo_root=Path("."))
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
        new_state = ws.complete_work_item(state, "parent", now="t2", repo_root=Path("."))
        self.assertEqual(new_state["work_items"]["parent"]["phase"], "MILESTONE_COMPLETE")

    def test_complete_work_item_with_no_children_still_succeeds(self):
        state = _base_state(wi=_base_work_item(phase="AWAITING_USER_ACCEPTANCE"))
        new_state = ws.complete_work_item(state, "wi", now="t2", repo_root=Path("."))
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


# =============================================================================
# D-Scoped-Remediation-Acceptance (revision 27, GPT-R40-001/-002; resolves
# WF8B-002): registry_completion_status/gate functions, complete_work_item's
# authoritative own-registry guard, the functional-checklist evidence
# trailer/guard machinery, the confirmation binding-field parser, replay/
# duplicate classification, and the acceptance record writer.
# =============================================================================


def _write(repo, rel_path, content):
    full = repo.root / rel_path
    full.parent.mkdir(parents=True, exist_ok=True)
    full.write_text(content)
    return full


def _commit_paths(repo, rel_paths, subject, trailers=None):
    for rel_path in rel_paths:
        _run(["git", "add", rel_path], cwd=repo.root)
    body = subject
    if trailers:
        body += "\n\n" + "\n".join(f"{k}: {v}" for k, v in trailers.items())
    _run(["git", "commit", "-q", "-m", body], cwd=repo.root)
    return repo.head()


def _commit_empty(repo, subject, trailers=None):
    body = subject
    if trailers:
        body += "\n\n" + "\n".join(f"{k}: {v}" for k, v in trailers.items())
    _run(["git", "commit", "-q", "--allow-empty", "-m", body], cwd=repo.root)
    return repo.head()


def _current_plan_approval_covering(repo, *tracked_paths):
    """GPT-R43-002 fixture helper: a minimal `CURRENT` `plan_approval`
    whose `review_content_manifest` names each of `tracked_paths` at its
    *live* git blob hash -- satisfies `_assert_registry_covered_by_
    current_plan_approval` for tests exercising `resolve_own_registry_
    completion_status`/`complete_work_item` that are not themselves about
    plan-approval currency. Each path must already be committed (its live
    blob is read via `git hash-object`, which needs the file to exist)."""
    return {
        "status": "CURRENT",
        "basis": "EXTERNAL_APPROVE",
        "reviewed_bundle_id": "a" * 66,
        "approved_review_content_id": "b" * 66,
        "review_content_manifest": [
            {
                "path": path, "exists": True, "mode": "100644",
                "blob": fingerprint._hash_object(repo.root, path),
            }
            for path in tracked_paths
        ],
        "reviewed_content_commit": None,
        "legacy_evidence": None,
        "user_confirmation": "test fixture approval",
        "waived_guarantees": [],
        "recorded_at": "t0",
    }


class TestRegistryCompletionStatusAndGates(unittest.TestCase):
    REGISTRY = {
        "work_item_id": "wi", "plan_revision": 1,
        "checkpoints": [{"id": "A", "depends_on": []}, {"id": "B", "depends_on": ["A"]}],
    }

    def test_terminal_when_every_checkpoint_complete(self):
        work_item = _base_work_item(checkpoints={
            "A": {"status": "COMPLETE"}, "B": {"status": "COMPLETE"},
        })
        is_terminal, outstanding = ws.registry_completion_status(work_item, self.REGISTRY)
        self.assertTrue(is_terminal)
        self.assertIsNone(outstanding)

    def test_non_terminal_names_next_selectable_checkpoint(self):
        work_item = _base_work_item(checkpoints={"A": {"status": "COMPLETE"}})
        is_terminal, outstanding = ws.registry_completion_status(work_item, self.REGISTRY)
        self.assertFalse(is_terminal)
        self.assertEqual(outstanding, "B")

    def test_non_terminal_names_blocked_checkpoint_via_structured_attribute(self):
        registry = {
            "work_item_id": "wi", "plan_revision": 1,
            "checkpoints": [
                {"id": "A", "depends_on": ["missing"]},
                {"id": "missing", "depends_on": ["A"]},
            ],
        }
        work_item = _base_work_item(checkpoints={})
        is_terminal, outstanding = ws.registry_completion_status(work_item, registry)
        self.assertFalse(is_terminal)
        self.assertEqual(outstanding, "A")  # first incomplete entry in registry order

    def test_milestone_complete_gate_reachable_truth_table(self):
        self.assertTrue(ws.milestone_complete_gate_reachable(phase="AWAITING_FUNCTIONAL_REVIEW", is_terminal=True))
        self.assertTrue(ws.milestone_complete_gate_reachable(phase="AWAITING_USER_ACCEPTANCE", is_terminal=True))
        self.assertFalse(ws.milestone_complete_gate_reachable(phase="AWAITING_FUNCTIONAL_REVIEW", is_terminal=False))
        self.assertFalse(ws.milestone_complete_gate_reachable(phase="IMPLEMENTING", is_terminal=True))

    def test_scoped_remediation_gate_reachable_truth_table(self):
        self.assertTrue(ws.scoped_remediation_gate_reachable(phase="AWAITING_FUNCTIONAL_REVIEW", is_terminal=False))
        self.assertFalse(ws.scoped_remediation_gate_reachable(phase="AWAITING_FUNCTIONAL_REVIEW", is_terminal=True))
        self.assertFalse(ws.scoped_remediation_gate_reachable(phase="IMPLEMENTING", is_terminal=False))

    def test_gates_are_mutually_exclusive(self):
        for phase in ("AWAITING_FUNCTIONAL_REVIEW", "AWAITING_USER_ACCEPTANCE", "IMPLEMENTING"):
            for is_terminal in (True, False):
                self.assertFalse(
                    ws.milestone_complete_gate_reachable(phase=phase, is_terminal=is_terminal)
                    and ws.scoped_remediation_gate_reachable(phase=phase, is_terminal=is_terminal)
                )


class TestCompleteWorkItemOwnRegistryGuard(unittest.TestCase):
    """complete_work_item's authoritative, repo_root-driven own-registry
    resolution (revision 24, GPT-R37-001) -- also exercises "direct
    complete_work_item bypass attempts" and "/accept-milestone refusal with
    incomplete checkpoints" (both route through this same guard) and
    "successful final completion after all checkpoints are complete"."""

    REGISTRY_PATH = "registry.json"

    def _registry(self):
        return {
            "work_item_id": "wi", "plan_revision": 1,
            "checkpoints": [{"id": "A", "depends_on": []}, {"id": "B", "depends_on": ["A"]}],
        }

    def _state(self, *, b_complete, plan_approval=None):
        checkpoints = {"A": {"status": "COMPLETE"}}
        if b_complete:
            checkpoints["B"] = {"status": "COMPLETE"}
        work_item = _base_work_item(
            phase="AWAITING_USER_ACCEPTANCE", registry_path=self.REGISTRY_PATH, checkpoints=checkpoints,
            plan_approval=plan_approval,
        )
        return _base_state(wi=work_item)

    def test_succeeds_when_own_registry_terminal(self):
        with ScratchRepo() as repo:
            _write(repo, self.REGISTRY_PATH, json.dumps(self._registry()))
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            state = self._state(
                b_complete=True, plan_approval=_current_plan_approval_covering(repo, self.REGISTRY_PATH),
            )
            new_state = ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)
            self.assertEqual(new_state["work_items"]["wi"]["phase"], "MILESTONE_COMPLETE")

    def test_raises_incomplete_own_checkpoints_when_own_registry_non_terminal(self):
        """Direct complete_work_item bypass attempt: skipping
        /accept-scoped-remediation and calling straight through must still
        fail closed -- the same guard /accept-milestone's own pre-flight
        relies on."""
        with ScratchRepo() as repo:
            _write(repo, self.REGISTRY_PATH, json.dumps(self._registry()))
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            state = self._state(
                b_complete=False, plan_approval=_current_plan_approval_covering(repo, self.REGISTRY_PATH),
            )
            with self.assertRaises(ws.IncompleteOwnCheckpointsError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)
            is_terminal, outstanding = ws.resolve_own_registry_completion_status(
                repo.root, state["work_items"]["wi"],
            )
            self.assertFalse(is_terminal)
            self.assertEqual(outstanding, "B")
            self.assertFalse(
                ws.milestone_complete_gate_reachable(phase="AWAITING_USER_ACCEPTANCE", is_terminal=is_terminal)
            )

    def test_registry_coverage_missing_file(self):
        with ScratchRepo() as repo:
            state = self._state(b_complete=True)  # registry.json never written
            with self.assertRaises(ws.RegistryCoverageError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)

    def test_registry_coverage_untracked_file(self):
        with ScratchRepo() as repo:
            _write(repo, self.REGISTRY_PATH, json.dumps(self._registry()))
            # deliberately not `git add`/committed
            state = self._state(b_complete=True)
            with self.assertRaises(ws.RegistryCoverageError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)

    def test_registry_coverage_malformed_json(self):
        with ScratchRepo() as repo:
            _write(repo, self.REGISTRY_PATH, "{not json")
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            state = self._state(b_complete=True)
            with self.assertRaises(ws.RegistryCoverageError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)

    def test_registry_coverage_not_a_json_object(self):
        with ScratchRepo() as repo:
            _write(repo, self.REGISTRY_PATH, json.dumps([1, 2, 3]))
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            state = self._state(b_complete=True)
            with self.assertRaises(ws.RegistryCoverageError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)

    def test_registry_coverage_wrong_work_item_id(self):
        with ScratchRepo() as repo:
            foreign = {**self._registry(), "work_item_id": "someone-else"}
            _write(repo, self.REGISTRY_PATH, json.dumps(foreign))
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            state = self._state(b_complete=True)
            with self.assertRaises(ws.RegistryCoverageError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)

    def test_registry_coverage_cannot_be_satisfied_by_a_fabricated_caller_dict(self):
        """GPT-R37-001: complete_work_item no longer accepts a registry
        argument at all -- there is no way to pass a fabricated
        all-complete registry in, even though the on-disk file itself says
        B is incomplete."""
        with ScratchRepo() as repo:
            _write(repo, self.REGISTRY_PATH, json.dumps(self._registry()))
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            state = self._state(
                b_complete=False, plan_approval=_current_plan_approval_covering(repo, self.REGISTRY_PATH),
            )
            import inspect
            self.assertNotIn("registry", inspect.signature(ws.complete_work_item).parameters)
            with self.assertRaises(ws.IncompleteOwnCheckpointsError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)

    def test_incomplete_children_checked_independently_of_own_checkpoints(self):
        with ScratchRepo() as repo:
            _write(repo, self.REGISTRY_PATH, json.dumps(self._registry()))
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            state = self._state(b_complete=True)
            state["work_items"]["child"] = _base_work_item(
                work_item_id="child", parent_work_item_id="wi", phase="IMPLEMENTING",
            )
            with self.assertRaises(ws.IncompleteChildWorkItemError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)


class TestRegistryReadBoundToCurrentPlanApproval(unittest.TestCase):
    """GPT-R43-002: `resolve_own_registry_completion_status` must prove
    the registry bytes it is about to trust are exactly the ones the
    current `plan_approval` covers -- `RegistryCoverageError` alone (safe
    path, exists, readable, valid JSON, declares the right work_item_id)
    never proved that. The registry's own two checkpoints (A, B) never
    encode terminality themselves -- `registry_completion_status` derives
    that from `work_item["checkpoints"]` against the registry's
    dependency graph (same pattern `TestCompleteWorkItemOwnRegistryGuard`
    uses) -- so `tag` is this fixture's only lever for varying the
    registry's own bytes/blob."""

    REGISTRY_PATH = "registry.json"

    def _registry(self, *, tag="v1"):
        return {
            "work_item_id": "wi", "plan_revision": 1, "tag": tag,
            "checkpoints": [{"id": "A", "depends_on": []}, {"id": "B", "depends_on": ["A"]}],
        }

    def _write_registry(self, repo, *, tag="v1"):
        _write(repo, self.REGISTRY_PATH, json.dumps(self._registry(tag=tag)))

    def _checkpoints(self, *, b_complete):
        checkpoints = {"A": {"status": "COMPLETE"}}
        if b_complete:
            checkpoints["B"] = {"status": "COMPLETE"}
        return checkpoints

    def test_dirty_tracked_registry_after_approval_refuses(self):
        with ScratchRepo() as repo:
            self._write_registry(repo)
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            plan_approval = _current_plan_approval_covering(repo, self.REGISTRY_PATH)
            self._write_registry(repo, tag="tampered")  # dirty, never committed
            work_item = _base_work_item(
                registry_path=self.REGISTRY_PATH, plan_approval=plan_approval,
                checkpoints=self._checkpoints(b_complete=False),
            )
            with self.assertRaises(ws.StalePlanApprovalRegistryReadError):
                ws.resolve_own_registry_completion_status(repo.root, work_item)

    def test_clean_committed_but_unapproved_mutation_refuses(self):
        with ScratchRepo() as repo:
            self._write_registry(repo)
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            plan_approval = _current_plan_approval_covering(repo, self.REGISTRY_PATH)
            self._write_registry(repo, tag="mutated")  # committed, no new plan-review round
            _commit_paths(repo, [self.REGISTRY_PATH], "registry mutated post-approval")
            work_item = _base_work_item(
                registry_path=self.REGISTRY_PATH, plan_approval=plan_approval,
                checkpoints=self._checkpoints(b_complete=False),
            )
            with self.assertRaises(ws.StalePlanApprovalRegistryReadError):
                ws.resolve_own_registry_completion_status(repo.root, work_item)

    def test_registry_restored_to_approved_bytes_succeeds(self):
        with ScratchRepo() as repo:
            self._write_registry(repo)
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            plan_approval = _current_plan_approval_covering(repo, self.REGISTRY_PATH)
            original_bytes = (repo.root / self.REGISTRY_PATH).read_text()
            self._write_registry(repo, tag="mutated")
            _commit_paths(repo, [self.REGISTRY_PATH], "mutate")
            _write(repo, self.REGISTRY_PATH, original_bytes)
            _commit_paths(repo, [self.REGISTRY_PATH], "restore to approved bytes")
            work_item = _base_work_item(
                registry_path=self.REGISTRY_PATH, plan_approval=plan_approval,
                checkpoints=self._checkpoints(b_complete=False),
            )
            is_terminal, outstanding = ws.resolve_own_registry_completion_status(repo.root, work_item)
            self.assertFalse(is_terminal)
            self.assertEqual(outstanding, "B")

    def test_newly_plan_approved_registry_revision_succeeds(self):
        """A registry mutation covered by its own fresh plan_approval
        (a real new plan-review/approval round, not merely a commit) must
        be trusted, not treated as automatically stale."""
        with ScratchRepo() as repo:
            self._write_registry(repo, tag="revision-2")
            _commit_paths(repo, [self.REGISTRY_PATH], "registry revision 2")
            plan_approval = _current_plan_approval_covering(repo, self.REGISTRY_PATH)
            work_item = _base_work_item(
                registry_path=self.REGISTRY_PATH, plan_approval=plan_approval,
                checkpoints=self._checkpoints(b_complete=True),
            )
            is_terminal, outstanding = ws.resolve_own_registry_completion_status(repo.root, work_item)
            self.assertTrue(is_terminal)
            self.assertIsNone(outstanding)

    def test_terminal_routing_refuses_stale_plan_metadata(self):
        """`complete_work_item` -- the function `/accept-milestone`'s own
        pre-flight and terminal routing both name -- must refuse rather
        than complete when the registry disagrees with `plan_approval`,
        even though `work_item["checkpoints"]` alone would otherwise
        read as terminal."""
        with ScratchRepo() as repo:
            self._write_registry(repo)
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            plan_approval = _current_plan_approval_covering(repo, self.REGISTRY_PATH)
            self._write_registry(repo, tag="tampered")
            _commit_paths(repo, [self.REGISTRY_PATH], "tampered")
            work_item = _base_work_item(
                registry_path=self.REGISTRY_PATH, plan_approval=plan_approval,
                phase="AWAITING_USER_ACCEPTANCE", checkpoints=self._checkpoints(b_complete=True),
            )
            state = _base_state(wi=work_item)
            with self.assertRaises(ws.StalePlanApprovalRegistryReadError):
                ws.complete_work_item(state, "wi", now="t", repo_root=repo.root)

    def test_non_terminal_routing_refuses_stale_plan_metadata(self):
        """`resolve_own_registry_completion_status` -- the same function
        `/accept-scoped-remediation`'s own pre-flight calls (per
        `TestScopedRemediationEndToEnd._accept`'s orchestration above) --
        must refuse for the non-terminal path too, not only the terminal
        one; staleness is about the bytes, not about which routing
        outcome the tampered registry happens to produce."""
        with ScratchRepo() as repo:
            self._write_registry(repo)
            _commit_paths(repo, [self.REGISTRY_PATH], "registry")
            plan_approval = _current_plan_approval_covering(repo, self.REGISTRY_PATH)
            self._write_registry(repo, tag="tampered-but-still-non-terminal")
            _commit_paths(repo, [self.REGISTRY_PATH], "tampered, committed")
            work_item = _base_work_item(
                registry_path=self.REGISTRY_PATH, plan_approval=plan_approval,
                checkpoints=self._checkpoints(b_complete=False),
            )
            with self.assertRaises(ws.StalePlanApprovalRegistryReadError):
                ws.resolve_own_registry_completion_status(repo.root, work_item)


class TestFunctionalChecklistTrailerDiscovery(unittest.TestCase):
    WI = "swi"
    CHECKLIST_PATH = ws.FUNCTIONAL_CHECKLIST_PATH

    def _seed(self, repo, content="checklist v1\n"):
        _write(repo, self.CHECKLIST_PATH, content)
        return _commit_paths(repo, [self.CHECKLIST_PATH], "seed checklist")

    def _blob(self, repo, ref="HEAD"):
        return subprocess.run(
            ["git", "rev-parse", f"{ref}:{self.CHECKLIST_PATH}"],
            cwd=repo.root, check=True, capture_output=True, text=True,
        ).stdout.strip()

    def _evidence_commit(self, repo, *, implementation_revision, blob=None):
        blob = blob or self._blob(repo)
        sha = _commit_empty(repo, "checklist evidence", trailers={
            "Workflow-Functional-Checklist": f"{self.WI}/{implementation_revision}/{blob}",
            "Workflow-Work-Item": self.WI,
        })
        return sha, blob

    def test_no_evidence_returns_none(self):
        with ScratchRepo() as repo:
            self._seed(repo)
            result = ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 1)
            self.assertIsNone(result)

    def test_single_evidence_is_discovered(self):
        with ScratchRepo() as repo:
            self._seed(repo)
            sha, blob = self._evidence_commit(repo, implementation_revision=1)
            result = ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 1)
            self.assertEqual(result, {"commit_sha": sha, "blob": blob})

    def test_round_scoped_prefix_does_not_cross_implementation_revisions(self):
        with ScratchRepo() as repo:
            self._seed(repo)
            self._evidence_commit(repo, implementation_revision=1)
            result = ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 2)
            self.assertIsNone(result)

    def test_corrected_checklist_produces_new_current_evidence_ahead_of_earlier(self):
        """Unchanged and corrected checklist revisions: an unchanged
        checklist content re-commits nothing (idempotency covered
        separately); a genuinely corrected checklist produces a new,
        distinct, discoverable evidence commit that becomes current, while
        the earlier commit remains separately discoverable by its own
        value (checklist preparation provenance)."""
        with ScratchRepo() as repo:
            self._seed(repo)
            old_sha, old_blob = self._evidence_commit(repo, implementation_revision=1)
            self._seed(repo, content="checklist v2 -- corrected\n")
            new_sha, new_blob = self._evidence_commit(repo, implementation_revision=1)
            self.assertNotEqual(old_blob, new_blob)
            current = ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 1)
            self.assertEqual(current, {"commit_sha": new_sha, "blob": new_blob})
            # the earlier evidence is still independently discoverable
            all_commits = ws.discover_functional_checklist_commits(repo.root, self.WI, repo.base, "HEAD")
            self.assertEqual(all_commits[f"{self.WI}/1/{old_blob}"], old_sha)

    def test_interrupted_preparation_is_safe_to_retry(self):
        """An unchanged checklist re-committed twice (simulating a
        preparation retried after interruption) must resolve to the exact
        same current evidence both times -- discovery is idempotent by
        content, never mistaking existing evidence for missing."""
        with ScratchRepo() as repo:
            self._seed(repo)
            sha, blob = self._evidence_commit(repo, implementation_revision=1)
            first = ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 1)
            second = ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 1)
            self.assertEqual(first, second)
            self.assertEqual(first, {"commit_sha": sha, "blob": blob})

    def test_ambiguous_functional_checklist_trailer_raises(self):
        """Duplicate/ambiguous evidence history: two first-parent-reachable
        commits carrying the exact same trailer value (identical checklist
        content) is genuine ambiguity, never silently resolved (mirrors
        TestCheckpointTrailerDiscovery.test_genuine_ambiguity_raises: two
        commits on the same linear branch are both first-parent ancestors
        of HEAD by construction, so the tie-break cannot narrow to one)."""
        with ScratchRepo() as repo:
            self._seed(repo)
            blob = self._blob(repo)
            self._evidence_commit(repo, implementation_revision=1, blob=blob)
            self._evidence_commit(repo, implementation_revision=1, blob=blob)
            with self.assertRaises(ws.AmbiguousFunctionalChecklistTrailerError):
                ws.discover_functional_checklist_commits(repo.root, self.WI, repo.base, "HEAD")

    def test_evidence_only_on_merged_side_branch_raises(self):
        """Evidence prepared on a side branch, then merged, without any
        matching evidence commit on the resulting branch's first-parent
        chain: the resolver must refuse rather than silently accepting the
        side-branch commit by ordinary reachable-history order
        (`GPT-R41-002`)."""
        with ScratchRepo() as repo:
            self._seed(repo)
            _run(["git", "checkout", "-q", "-b", "side"], cwd=repo.root)
            side_sha, side_blob = self._evidence_commit(repo, implementation_revision=1)
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            _run(["git", "merge", "-q", "--no-ff", "-m", "merge side", "side"], cwd=repo.root)
            with self.assertRaises(ws.NonFirstParentFunctionalChecklistEvidenceError) as ctx:
                ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 1)
            self.assertIn(side_sha, str(ctx.exception))

    def test_two_side_branches_with_different_revisions_both_off_first_parent_raises(self):
        """Two side branches, each carrying its own distinct checklist
        revision for the same round, both merged without either becoming a
        first-parent transition: still a refusal, not a pick between the
        two off-first-parent candidates by log order."""
        with ScratchRepo() as repo:
            self._seed(repo)
            _run(["git", "checkout", "-q", "-b", "side-a"], cwd=repo.root)
            side_a_sha, _ = self._evidence_commit(repo, implementation_revision=1)
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            _run(["git", "checkout", "-q", "-b", "side-b"], cwd=repo.root)
            self._seed(repo, content="checklist v2 -- corrected\n")
            side_b_sha, _ = self._evidence_commit(repo, implementation_revision=1)
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            _run(["git", "merge", "-q", "--no-ff", "-m", "merge side-a", "side-a"], cwd=repo.root)
            _run(["git", "merge", "-q", "--no-ff", "-m", "merge side-b", "side-b"], cwd=repo.root)
            with self.assertRaises(ws.NonFirstParentFunctionalChecklistEvidenceError) as ctx:
                ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 1)
            self.assertIn(side_a_sha, str(ctx.exception))
            self.assertIn(side_b_sha, str(ctx.exception))

    def test_first_parent_evidence_wins_over_newer_side_branch_evidence(self):
        """A first-parent evidence commit exists for the round; a *newer*
        off-first-parent commit (a later-merged side branch) also carries
        round-scoped evidence. The first-parent commit is still current --
        first-parent standing is never overridden by recency."""
        with ScratchRepo() as repo:
            self._seed(repo)
            main_sha, main_blob = self._evidence_commit(repo, implementation_revision=1)
            _run(["git", "checkout", "-q", "-b", "side"], cwd=repo.root)
            self._seed(repo, content="checklist v2 -- corrected\n")
            self._evidence_commit(repo, implementation_revision=1)
            _run(["git", "checkout", "-q", "-"], cwd=repo.root)
            _run(["git", "merge", "-q", "--no-ff", "-m", "merge side", "side"], cwd=repo.root)
            result = ws.discover_current_functional_checklist_evidence(repo.root, self.WI, repo.base, "HEAD", 1)
            self.assertEqual(result, {"commit_sha": main_sha, "blob": main_blob})

    def test_ambiguous_scoped_remediation_trailer_raises(self):
        with ScratchRepo() as repo:
            _commit_empty(repo, "round a", trailers={
                "Workflow-Scoped-Remediation-Acceptance": "B/1", "Workflow-Work-Item": self.WI,
            })
            _commit_empty(repo, "round b", trailers={
                "Workflow-Scoped-Remediation-Acceptance": "B/1", "Workflow-Work-Item": self.WI,
            })
            with self.assertRaises(ws.AmbiguousScopedRemediationTrailerError):
                ws.discover_scoped_remediation_commits(repo.root, self.WI, repo.base, "HEAD")


class TestFunctionalChecklistEvidenceGuard(unittest.TestCase):
    WI = "swi"
    CHECKLIST_PATH = ws.FUNCTIONAL_CHECKLIST_PATH

    def _work_item(self, *, implementation_revision=1, reviewed_implementation_head):
        return {
            "work_item_id": self.WI,
            "implementation_revision": implementation_revision,
            "reviewed_implementation_head": reviewed_implementation_head,
        }

    def _seed(self, repo, content="checklist v1\n"):
        _write(repo, self.CHECKLIST_PATH, content)
        return _commit_paths(repo, [self.CHECKLIST_PATH], "seed checklist")

    def _blob(self, repo, ref="HEAD"):
        return subprocess.run(
            ["git", "rev-parse", f"{ref}:{self.CHECKLIST_PATH}"],
            cwd=repo.root, check=True, capture_output=True, text=True,
        ).stdout.strip()

    def _evidence_commit(self, repo, *, implementation_revision, blob=None):
        blob = blob or self._blob(repo)
        sha = _commit_empty(repo, "checklist evidence", trailers={
            "Workflow-Functional-Checklist": f"{self.WI}/{implementation_revision}/{blob}",
            "Workflow-Work-Item": self.WI,
        })
        return sha, blob

    def test_happy_path_returns_current_evidence(self):
        with ScratchRepo() as repo:
            seed_sha = self._seed(repo)
            sha, blob = self._evidence_commit(repo, implementation_revision=1)
            work_item = self._work_item(reviewed_implementation_head=seed_sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            result = ws.verify_functional_checklist_evidence(
                repo.root, work_item, base_commit=repo.base, head="HEAD",
                confirmed_commit=sha, confirmed_blob=blob, expected_live_snapshot=snapshot,
            )
            self.assertEqual(result, {"commit_sha": sha, "blob": blob})

    def test_missing_evidence(self):
        with ScratchRepo() as repo:
            seed_sha = self._seed(repo)
            work_item = self._work_item(reviewed_implementation_head=seed_sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            with self.assertRaises(ws.MissingFunctionalChecklistEvidenceError):
                ws.verify_functional_checklist_evidence(
                    repo.root, work_item, base_commit=repo.base, head="HEAD",
                    confirmed_commit="a" * 40, confirmed_blob="b" * 40, expected_live_snapshot=snapshot,
                )

    def test_stale_confirmation_after_correction_wrong_commit(self):
        """A checklist corrected after the user's confirmation was drafted:
        the confirmation still names the earlier evidence commit, which
        must be refused as stale even though it remains independently
        discoverable."""
        with ScratchRepo() as repo:
            self._seed(repo)
            old_sha, old_blob = self._evidence_commit(repo, implementation_revision=1)
            self._seed(repo, content="checklist v2 -- corrected\n")
            new_sha, _ = self._evidence_commit(repo, implementation_revision=1)
            work_item = self._work_item(reviewed_implementation_head=new_sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            with self.assertRaises(ws.StaleFunctionalChecklistConfirmationError):
                ws.verify_functional_checklist_evidence(
                    repo.root, work_item, base_commit=repo.base, head="HEAD",
                    confirmed_commit=old_sha, confirmed_blob=old_blob, expected_live_snapshot=snapshot,
                )

    def test_fresh_confirmation_for_corrected_evidence_succeeds(self):
        with ScratchRepo() as repo:
            self._seed(repo)
            self._evidence_commit(repo, implementation_revision=1)
            self._seed(repo, content="checklist v2 -- corrected\n")
            new_sha, new_blob = self._evidence_commit(repo, implementation_revision=1)
            work_item = self._work_item(reviewed_implementation_head=new_sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            result = ws.verify_functional_checklist_evidence(
                repo.root, work_item, base_commit=repo.base, head="HEAD",
                confirmed_commit=new_sha, confirmed_blob=new_blob, expected_live_snapshot=snapshot,
            )
            self.assertEqual(result, {"commit_sha": new_sha, "blob": new_blob})

    def test_stale_confirmation_wrong_blob_same_commit(self):
        with ScratchRepo() as repo:
            self._seed(repo)
            sha, blob = self._evidence_commit(repo, implementation_revision=1)
            work_item = self._work_item(reviewed_implementation_head=sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            with self.assertRaises(ws.StaleFunctionalChecklistConfirmationError):
                ws.verify_functional_checklist_evidence(
                    repo.root, work_item, base_commit=repo.base, head="HEAD",
                    confirmed_commit=sha, confirmed_blob="f" * 40, expected_live_snapshot=snapshot,
                )

    def test_malformed_evidence_trailer_blob_disagrees_with_actual_commit(self):
        """A hand-crafted or corrupted trailer whose embedded blob
        component disagrees with what the commit actually committed."""
        with ScratchRepo() as repo:
            seed_sha = self._seed(repo)
            real_blob = self._blob(repo)
            fake_blob = "0" * 40
            sha = _commit_empty(repo, "checklist evidence", trailers={
                "Workflow-Functional-Checklist": f"{self.WI}/1/{fake_blob}",
                "Workflow-Work-Item": self.WI,
            })
            work_item = self._work_item(reviewed_implementation_head=seed_sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            with self.assertRaises(ws.MalformedFunctionalChecklistEvidenceError):
                ws.verify_functional_checklist_evidence(
                    repo.root, work_item, base_commit=repo.base, head="HEAD",
                    confirmed_commit=sha, confirmed_blob=fake_blob, expected_live_snapshot=snapshot,
                )
            self.assertNotEqual(fake_blob, real_blob)

    def test_dirty_checklist_path_refused(self):
        with ScratchRepo() as repo:
            seed_sha = self._seed(repo)
            sha, blob = self._evidence_commit(repo, implementation_revision=1)
            (repo.root / self.CHECKLIST_PATH).write_text("uncommitted local edit\n")
            work_item = self._work_item(reviewed_implementation_head=seed_sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            with self.assertRaises(ws.DirtyFunctionalChecklistPathError):
                ws.verify_functional_checklist_evidence(
                    repo.root, work_item, base_commit=repo.base, head="HEAD",
                    confirmed_commit=sha, confirmed_blob=blob, expected_live_snapshot=snapshot,
                )

    def test_cross_invocation_reviewed_implementation_head_changed(self):
        with ScratchRepo() as repo:
            seed_sha = self._seed(repo)
            sha, blob = self._evidence_commit(repo, implementation_revision=1)
            work_item = self._work_item(reviewed_implementation_head=seed_sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            # simulate reviewed_implementation_head advancing mid-invocation
            work_item["reviewed_implementation_head"] = "deadbeef" * 5
            with self.assertRaises(ws.ScopedRemediationLiveValueChangedError):
                ws.verify_functional_checklist_evidence(
                    repo.root, work_item, base_commit=repo.base, head="HEAD",
                    confirmed_commit=sha, confirmed_blob=blob, expected_live_snapshot=snapshot,
                )

    def test_cross_invocation_checklist_blob_changed(self):
        with ScratchRepo() as repo:
            seed_sha = self._seed(repo)
            sha, blob = self._evidence_commit(repo, implementation_revision=1)
            work_item = self._work_item(reviewed_implementation_head=seed_sha)
            snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
            self._seed(repo, content="a superseding commit landed mid-invocation\n")
            new_sha, new_blob = self._evidence_commit(repo, implementation_revision=1)
            with self.assertRaises(ws.ScopedRemediationLiveValueChangedError):
                ws.verify_functional_checklist_evidence(
                    repo.root, work_item, base_commit=repo.base, head="HEAD",
                    confirmed_commit=new_sha, confirmed_blob=new_blob, expected_live_snapshot=snapshot,
                )


class TestScopedRemediationConfirmationParsing(unittest.TestCase):
    COMMIT = "a" * 40
    BLOB = "b" * 40

    def test_success(self):
        text = (
            f"scoped_remediation confirmed for swi\n"
            f"Functional checklist evidence commit: {self.COMMIT}\n"
            f"Functional checklist evidence blob: {self.BLOB}\n"
        )
        fields = ws.parse_scoped_remediation_confirmation_binding_fields(text)
        self.assertEqual(fields, {
            "functional_checklist_evidence_commit": self.COMMIT,
            "functional_checklist_evidence_blob": self.BLOB,
        })

    def test_missing_commit_field(self):
        text = f"Functional checklist evidence blob: {self.BLOB}\n"
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.parse_scoped_remediation_confirmation_binding_fields(text)

    def test_missing_blob_field(self):
        text = f"Functional checklist evidence commit: {self.COMMIT}\n"
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.parse_scoped_remediation_confirmation_binding_fields(text)

    def test_malformed_commit_not_hex(self):
        text = (
            f"Functional checklist evidence commit: not-a-real-sha\n"
            f"Functional checklist evidence blob: {self.BLOB}\n"
        )
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.parse_scoped_remediation_confirmation_binding_fields(text)

    def test_malformed_blob_wrong_length(self):
        text = (
            f"Functional checklist evidence commit: {self.COMMIT}\n"
            f"Functional checklist evidence blob: abc123\n"
        )
        with self.assertRaises(ws.UserConfirmationRejectedError):
            ws.parse_scoped_remediation_confirmation_binding_fields(text)


class TestResolveScopedRemediationRound(unittest.TestCase):
    WI = "swi"
    STATE_PATH = ws.DEFAULT_STATE_PATH.as_posix()

    def _live_fields(self, **overrides):
        base = {
            "outstanding_checkpoint_id": "B",
            "implementation_revision": 1,
            "reviewed_implementation_head": "r" * 40,
            "technical_approval_review_content_id": "t" * 40,
            "functional_checklist_path": ws.FUNCTIONAL_CHECKLIST_PATH,
            "functional_checklist_blob": "c" * 40,
            "functional_checklist_evidence_commit": "e" * 40,
            "active_work_item_id": self.WI,
        }
        base.update(overrides)
        return base

    def _entry(self, **overrides):
        live = self._live_fields()
        entry = {
            "outstanding_checkpoint_id": live["outstanding_checkpoint_id"],
            "active_work_item_id_at_acceptance": live["active_work_item_id"],
            "implementation_revision": live["implementation_revision"],
            "reviewed_implementation_head": live["reviewed_implementation_head"],
            "technical_approval_review_content_id": live["technical_approval_review_content_id"],
            "functional_checklist_path": live["functional_checklist_path"],
            "functional_checklist_blob": live["functional_checklist_blob"],
            "functional_checklist_evidence_commit": live["functional_checklist_evidence_commit"],
            "user_confirmation": "I confirm scoped_remediation for swi",
            "recorded_at": "2026-01-01T00:00:00+00:00",
            "acceptance_record_version": 2,
        }
        entry.update(overrides)
        return entry

    def _commit_round(self, repo, entry, *, round_key="B/1"):
        state = {
            "schema_version": 1, "active_work_item_id": self.WI,
            "work_items": {self.WI: {"scoped_remediation_acceptance": [entry]}},
        }
        _write(repo, self.STATE_PATH, json.dumps(state))
        return _commit_paths(repo, [self.STATE_PATH], "scoped remediation acceptance", trailers={
            "Workflow-Scoped-Remediation-Acceptance": round_key, "Workflow-Work-Item": self.WI,
        })

    def test_no_existing_round(self):
        with ScratchRepo() as repo:
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 1, self._live_fields(),
            )
            self.assertEqual(result, ws.NoExistingRound())

    def test_exact_replay(self):
        with ScratchRepo() as repo:
            self._commit_round(repo, self._entry())
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 1, self._live_fields(),
            )
            self.assertIsInstance(result, ws.ExactReplay)
            self.assertEqual(result.commit_sha, repo.head())

    def test_conflicting_duplicate_wrong_active_pointer(self):
        with ScratchRepo() as repo:
            self._commit_round(repo, self._entry())
            live = self._live_fields(active_work_item_id="a-different-item")
            result = ws.resolve_scoped_remediation_round(repo.root, self.WI, repo.base, "HEAD", "B", 1, live)
            self.assertIsInstance(result, ws.ConflictingDuplicate)
            self.assertIn("active_work_item_id_at_acceptance", result.differing_fields)

    def test_conflicting_duplicate_wrong_evidence_commit(self):
        with ScratchRepo() as repo:
            self._commit_round(repo, self._entry())
            live = self._live_fields(functional_checklist_evidence_commit="f" * 40)
            result = ws.resolve_scoped_remediation_round(repo.root, self.WI, repo.base, "HEAD", "B", 1, live)
            self.assertIsInstance(result, ws.ConflictingDuplicate)
            self.assertIn("functional_checklist_evidence_commit", result.differing_fields)

    def test_conflicting_duplicate_wrong_checklist_path(self):
        with ScratchRepo() as repo:
            self._commit_round(repo, self._entry(functional_checklist_path="some/other/path.md"))
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 1, self._live_fields(),
            )
            self.assertIsInstance(result, ws.ConflictingDuplicate)
            self.assertIn("functional_checklist_path", result.differing_fields)

    def test_malformed_unsupported_schema_version(self):
        with ScratchRepo() as repo:
            self._commit_round(repo, self._entry(acceptance_record_version=1))
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 1, self._live_fields(),
            )
            self.assertIsInstance(result, ws.MalformedAcceptanceRecord)
            self.assertIn("acceptance_record_version", result.reason)

    def test_malformed_wrong_field_set(self):
        with ScratchRepo() as repo:
            bad_entry = self._entry()
            del bad_entry["recorded_at"]
            self._commit_round(repo, bad_entry)
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 1, self._live_fields(),
            )
            self.assertIsInstance(result, ws.MalformedAcceptanceRecord)

    def test_malformed_missing_recorded_at(self):
        with ScratchRepo() as repo:
            bad_entry = self._entry(recorded_at="")
            # keep the field present (empty string) so the field-set check
            # passes and the well-formedness check is what actually fires
            bad_entry["recorded_at"] = ""
            self._commit_round(repo, bad_entry)
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 1, self._live_fields(),
            )
            self.assertIsInstance(result, ws.MalformedAcceptanceRecord)
            self.assertIn("recorded_at", result.reason)

    def test_malformed_no_matching_entry_for_round_key(self):
        with ScratchRepo() as repo:
            other_entry = self._entry(outstanding_checkpoint_id="A", implementation_revision=1)
            self._commit_round(repo, other_entry, round_key="B/1")  # trailer/entry desync
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 1, self._live_fields(),
            )
            self.assertIsInstance(result, ws.MalformedAcceptanceRecord)

    def test_ambiguous_history(self):
        with ScratchRepo() as repo:
            # Two commits on the same linear branch, same round key: both
            # are first-parent ancestors of HEAD by construction, so the
            # tie-break cannot narrow to one (mirrors
            # TestCheckpointTrailerDiscovery.test_genuine_ambiguity_raises).
            # Ambiguity is raised purely from trailer matching, before
            # WORKFLOW_STATE.json content is ever read, so --allow-empty
            # commits suffice here.
            _commit_empty(repo, "round a", trailers={
                "Workflow-Scoped-Remediation-Acceptance": "B/1", "Workflow-Work-Item": self.WI,
            })
            _commit_empty(repo, "round b", trailers={
                "Workflow-Scoped-Remediation-Acceptance": "B/1", "Workflow-Work-Item": self.WI,
            })
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 1, self._live_fields(),
            )
            self.assertIsInstance(result, ws.AmbiguousHistory)
            self.assertEqual(result.round_key, "B/1")

    def test_distinct_implementation_revision_is_a_new_round_not_a_replay(self):
        """A genuinely new round for a checkpoint scoped-accepted before
        under a different implementation_revision is NoExistingRound for
        the new round key, never confused with the earlier one."""
        with ScratchRepo() as repo:
            self._commit_round(repo, self._entry(), round_key="B/1")
            result = ws.resolve_scoped_remediation_round(
                repo.root, self.WI, repo.base, "HEAD", "B", 2, self._live_fields(implementation_revision=2),
            )
            self.assertEqual(result, ws.NoExistingRound())


class TestApplyScopedRemediationAcceptance(unittest.TestCase):
    WI = "swi"

    def _state(self):
        work_item = _base_work_item(
            work_item_id=self.WI, phase="AWAITING_FUNCTIONAL_REVIEW",
            implementation_revision=3, reviewed_implementation_head="r" * 40,
            technical_approval={"status": "CURRENT", "approved_review_content_id": "t" * 40},
            plan_approval={"status": "CURRENT", "approved_review_content_id": "p" * 40},
            functional_acceptance_status=None,
            current_checkpoint_id=None,
            checkpoints={"A": {"status": "COMPLETE"}, "B": {"status": "IN_PROGRESS"}},
        )
        state = _base_state(**{self.WI: work_item})
        state["active_work_item_id"] = self.WI
        return state

    def test_entry_has_exactly_eleven_documented_fields(self):
        state = self._state()
        new_state = ws.apply_scoped_remediation_acceptance(
            state, self.WI, outstanding_checkpoint_id="B",
            functional_checklist_evidence_commit="e" * 40, functional_checklist_blob="c" * 40,
            user_confirmation="I confirm scoped_remediation for swi", now="2026-01-01T00:00:00+00:00",
        )
        entries = new_state["work_items"][self.WI]["scoped_remediation_acceptance"]
        self.assertEqual(len(entries), 1)
        self.assertEqual(set(entries[0].keys()), ws.SCOPED_REMEDIATION_ACCEPTANCE_FIELDS)
        self.assertEqual(len(entries[0]), 11)
        self.assertEqual(entries[0]["acceptance_record_version"], 2)
        self.assertEqual(entries[0]["outstanding_checkpoint_id"], "B")
        self.assertEqual(entries[0]["active_work_item_id_at_acceptance"], self.WI)

    def test_sets_phase_implementing(self):
        state = self._state()
        new_state = ws.apply_scoped_remediation_acceptance(
            state, self.WI, outstanding_checkpoint_id="B",
            functional_checklist_evidence_commit="e" * 40, functional_checklist_blob="c" * 40,
            user_confirmation="I confirm scoped_remediation for swi", now="t",
        )
        self.assertEqual(new_state["work_items"][self.WI]["phase"], "IMPLEMENTING")

    def test_leaves_checkpoints_and_approvals_untouched(self):
        state = self._state()
        new_state = ws.apply_scoped_remediation_acceptance(
            state, self.WI, outstanding_checkpoint_id="B",
            functional_checklist_evidence_commit="e" * 40, functional_checklist_blob="c" * 40,
            user_confirmation="I confirm scoped_remediation for swi", now="t",
        )
        old_wi = state["work_items"][self.WI]
        new_wi = new_state["work_items"][self.WI]
        self.assertEqual(new_wi["checkpoints"], old_wi["checkpoints"])
        self.assertEqual(new_wi["current_checkpoint_id"], old_wi["current_checkpoint_id"])
        self.assertEqual(new_wi["plan_approval"], old_wi["plan_approval"])
        self.assertEqual(new_wi["technical_approval"], old_wi["technical_approval"])
        self.assertEqual(new_wi["functional_acceptance_status"], old_wi["functional_acceptance_status"])
        self.assertEqual(new_state["active_work_item_id"], state["active_work_item_id"])

    def test_does_not_mark_any_checkpoint_complete(self):
        state = self._state()
        new_state = ws.apply_scoped_remediation_acceptance(
            state, self.WI, outstanding_checkpoint_id="B",
            functional_checklist_evidence_commit="e" * 40, functional_checklist_blob="c" * 40,
            user_confirmation="I confirm scoped_remediation for swi", now="t",
        )
        self.assertEqual(new_state["work_items"][self.WI]["checkpoints"]["B"]["status"], "IN_PROGRESS")

    def test_is_pure_no_side_effects_until_caller_persists_and_commits(self):
        """Interrupted acceptance commit: the state write is a pure
        in-memory transform. If the caller's own commit step never runs
        (an interruption between the two), nothing at all was persisted --
        neither the original nor the returned state dict is mutated as a
        side effect, and no git commit exists until the caller creates
        one."""
        state = self._state()
        original = copy.deepcopy(state)
        ws.apply_scoped_remediation_acceptance(
            state, self.WI, outstanding_checkpoint_id="B",
            functional_checklist_evidence_commit="e" * 40, functional_checklist_blob="c" * 40,
            user_confirmation="I confirm scoped_remediation for swi", now="t",
        )
        self.assertEqual(state, original)  # input untouched


class TestScopedRemediationEndToEnd(unittest.TestCase):
    """Full-flow scenarios exercising the entry guard (confirmation ->
    evidence-binding -> registry load -> replay classification) and the
    pre-commit evidence guard together, mirroring the exact WF8b shape:
    checkpoint A complete, checkpoint B (the item's own last checkpoint)
    outstanding while continued-scope work landed as extra scope on A."""

    WI = "swi"
    REGISTRY_PATH = "registry.json"
    STATE_PATH = ws.DEFAULT_STATE_PATH.as_posix()
    CHECKLIST_PATH = ws.FUNCTIONAL_CHECKLIST_PATH

    def _registry(self):
        return {
            "work_item_id": self.WI, "plan_revision": 1,
            "checkpoints": [{"id": "A", "depends_on": []}, {"id": "B", "depends_on": ["A"]}],
        }

    def _blob(self, repo, ref="HEAD"):
        return subprocess.run(
            ["git", "rev-parse", f"{ref}:{self.CHECKLIST_PATH}"],
            cwd=repo.root, check=True, capture_output=True, text=True,
        ).stdout.strip()

    def _seed(self, repo, *, implementation_revision=1, checklist_content="checklist v1\n"):
        _write(repo, self.REGISTRY_PATH, json.dumps(self._registry()))
        _write(repo, self.CHECKLIST_PATH, checklist_content)
        base_sha = _commit_paths(repo, [self.REGISTRY_PATH, self.CHECKLIST_PATH], "seed")
        blob = self._blob(repo)
        evidence_sha = _commit_empty(repo, "checklist evidence", trailers={
            "Workflow-Functional-Checklist": f"{self.WI}/{implementation_revision}/{blob}",
            "Workflow-Work-Item": self.WI,
        })
        work_item = {
            "work_item_id": self.WI, "work_item_type": "process",
            "registry_path": self.REGISTRY_PATH,
            "base_commit": repo.base,
            "implementation_revision": implementation_revision,
            "reviewed_implementation_head": evidence_sha,
            "technical_approval": {"status": "CURRENT", "approved_review_content_id": "t" * 40},
            "plan_approval": _current_plan_approval_covering(repo, self.REGISTRY_PATH),
            "phase": "AWAITING_FUNCTIONAL_REVIEW",
            "checkpoints": {"A": {"status": "COMPLETE"}},
        }
        state = {"schema_version": 1, "active_work_item_id": self.WI, "work_items": {self.WI: work_item}}
        return state, evidence_sha, blob

    def _accept(self, repo, state, *, confirmed_commit, confirmed_blob, now="2026-01-01T00:00:00+00:00"):
        """Simulates /accept-scoped-remediation's own orchestration
        end-to-end, calling exactly the functions the command doc
        specifies, in order."""
        work_item = state["work_items"][self.WI]
        confirmation_text = (
            "I confirm scoped_remediation for swi\n"
            f"Functional checklist evidence commit: {confirmed_commit}\n"
            f"Functional checklist evidence blob: {confirmed_blob}\n"
        )
        ws.validate_user_confirmation(confirmation_text, work_item_id=self.WI, stage="scoped_remediation")
        fields = ws.parse_scoped_remediation_confirmation_binding_fields(confirmation_text)
        snapshot = ws.build_scoped_remediation_live_snapshot(repo.root, work_item)
        current = ws.verify_functional_checklist_evidence(
            repo.root, work_item, base_commit=work_item["base_commit"], head="HEAD",
            confirmed_commit=fields["functional_checklist_evidence_commit"],
            confirmed_blob=fields["functional_checklist_evidence_blob"],
            expected_live_snapshot=snapshot,
        )
        is_terminal, outstanding = ws.resolve_own_registry_completion_status(repo.root, work_item)
        self.assertFalse(is_terminal)
        live_fields = ws.build_scoped_remediation_live_fields(
            state, self.WI, outstanding_checkpoint_id=outstanding,
            functional_checklist_evidence_commit=current["commit_sha"],
            functional_checklist_blob=current["blob"],
        )
        resolution = ws.resolve_scoped_remediation_round(
            repo.root, self.WI, work_item["base_commit"], "HEAD", outstanding,
            work_item["implementation_revision"], live_fields,
        )
        if isinstance(resolution, ws.ExactReplay):
            return resolution
        if not isinstance(resolution, ws.NoExistingRound):
            return resolution
        self.assertTrue(
            ws.scoped_remediation_gate_reachable(phase=work_item["phase"], is_terminal=is_terminal)
        )
        self.assertEqual(work_item["technical_approval"]["status"], "CURRENT")
        ws.verify_functional_checklist_evidence(
            repo.root, work_item, base_commit=work_item["base_commit"], head="HEAD",
            confirmed_commit=fields["functional_checklist_evidence_commit"],
            confirmed_blob=fields["functional_checklist_evidence_blob"],
            expected_live_snapshot=snapshot,
        )
        new_state = ws.apply_scoped_remediation_acceptance(
            state, self.WI, outstanding_checkpoint_id=outstanding,
            functional_checklist_evidence_commit=current["commit_sha"],
            functional_checklist_blob=current["blob"],
            user_confirmation=confirmation_text, now=now,
        )
        ws.verify_functional_checklist_evidence(
            repo.root, work_item, base_commit=work_item["base_commit"], head="HEAD",
            confirmed_commit=fields["functional_checklist_evidence_commit"],
            confirmed_blob=fields["functional_checklist_evidence_blob"],
            expected_live_snapshot=snapshot,
        )
        _write(repo, self.STATE_PATH, json.dumps(new_state))
        commit_sha = _commit_paths(repo, [self.STATE_PATH], "scoped remediation acceptance", trailers={
            "Workflow-Scoped-Remediation-Acceptance": f"{outstanding}/{work_item['implementation_revision']}",
            "Workflow-Work-Item": self.WI,
        })
        return new_state, commit_sha

    def test_first_scoped_acceptance_mirrors_wf8b_shape(self):
        with ScratchRepo() as repo:
            state, evidence_sha, blob = self._seed(repo)
            new_state, commit_sha = self._accept(
                repo, state, confirmed_commit=evidence_sha, confirmed_blob=blob,
            )
            self.assertEqual(new_state["work_items"][self.WI]["phase"], "IMPLEMENTING")
            entry = new_state["work_items"][self.WI]["scoped_remediation_acceptance"][0]
            self.assertEqual(entry["outstanding_checkpoint_id"], "B")
            self.assertEqual(entry["functional_checklist_evidence_commit"], evidence_sha)
            status = subprocess.run(
                ["git", "status", "--porcelain"], cwd=repo.root, check=True, capture_output=True, text=True,
            ).stdout
            self.assertEqual(status.strip(), "")  # worktree clean after success

    def test_exact_replay_on_second_invocation(self):
        with ScratchRepo() as repo:
            state, evidence_sha, blob = self._seed(repo)
            _new_state, first_commit = self._accept(
                repo, state, confirmed_commit=evidence_sha, confirmed_blob=blob,
            )
            # Fresh-session resume: reload state from git-committed content
            # (never carried over in-memory) and re-run the same confirmation.
            resumed_state = json.loads(_run_capture(
                ["git", "show", f"HEAD:{self.STATE_PATH}"], repo.root,
            ))
            result = self._accept(repo, resumed_state, confirmed_commit=evidence_sha, confirmed_blob=blob)
            self.assertIsInstance(result, ws.ExactReplay)
            self.assertEqual(result.commit_sha, first_commit)

    def test_conflicting_duplicate_when_active_pointer_moved(self):
        with ScratchRepo() as repo:
            state, evidence_sha, blob = self._seed(repo)
            self._accept(repo, state, confirmed_commit=evidence_sha, confirmed_blob=blob)
            resumed_state = json.loads(_run_capture(["git", "show", f"HEAD:{self.STATE_PATH}"], repo.root))
            resumed_state["active_work_item_id"] = "some-other-item"
            result = self._accept(repo, resumed_state, confirmed_commit=evidence_sha, confirmed_blob=blob)
            self.assertIsInstance(result, ws.ConflictingDuplicate)
            self.assertIn("active_work_item_id_at_acceptance", result.differing_fields)

    def test_wrong_evidence_commit_is_refused_as_stale(self):
        with ScratchRepo() as repo:
            state, evidence_sha, blob = self._seed(repo)
            with self.assertRaises(ws.StaleFunctionalChecklistConfirmationError):
                self._accept(repo, state, confirmed_commit="f" * 40, confirmed_blob=blob)

    def test_wrong_evidence_blob_is_refused_as_stale(self):
        with ScratchRepo() as repo:
            state, evidence_sha, blob = self._seed(repo)
            with self.assertRaises(ws.StaleFunctionalChecklistConfirmationError):
                self._accept(repo, state, confirmed_commit=evidence_sha, confirmed_blob="f" * 40)

    def test_wrong_work_item_confirmation_never_finds_evidence(self):
        with ScratchRepo() as repo:
            state, evidence_sha, blob = self._seed(repo)
            other_work_item = {**state["work_items"][self.WI], "work_item_id": "other-item"}
            with self.assertRaises(ws.MissingFunctionalChecklistEvidenceError):
                ws.verify_functional_checklist_evidence(
                    repo.root, other_work_item, base_commit=other_work_item["base_commit"], head="HEAD",
                    confirmed_commit=evidence_sha, confirmed_blob=blob,
                    expected_live_snapshot=ws.build_scoped_remediation_live_snapshot(repo.root, other_work_item),
                )

    def test_terminal_registry_refuses_scoped_gate(self):
        """Once every checkpoint is COMPLETE, the scoped-remediation gate
        is unreachable -- /accept-milestone is the correct command instead
        (mutually exclusive with the non-terminal path this class
        otherwise exercises)."""
        with ScratchRepo() as repo:
            state, evidence_sha, blob = self._seed(repo)
            state["work_items"][self.WI]["checkpoints"]["B"] = {"status": "COMPLETE"}
            work_item = state["work_items"][self.WI]
            is_terminal, outstanding = ws.resolve_own_registry_completion_status(repo.root, work_item)
            self.assertTrue(is_terminal)
            self.assertIsNone(outstanding)
            self.assertFalse(
                ws.scoped_remediation_gate_reachable(phase=work_item["phase"], is_terminal=is_terminal)
            )
            self.assertTrue(
                ws.milestone_complete_gate_reachable(phase=work_item["phase"], is_terminal=is_terminal)
            )
            # and /accept-milestone now succeeds via complete_work_item directly
            new_state = ws.complete_work_item(state, self.WI, now="t", repo_root=repo.root)
            self.assertEqual(new_state["work_items"][self.WI]["phase"], "MILESTONE_COMPLETE")


def _run_capture(args, cwd):
    return subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True).stdout


# ---------------------------------------------------------------------------
# WF8b: D-Checkpoint-Ownership -- the shared claim record, the mutation/
# handoff guard, the explicit takeover, and the abandoned-guard recovery.
# ---------------------------------------------------------------------------


class TestCheckpointClaimRecord(unittest.TestCase):
    def test_claim_checkpoint_publishes_and_resolves(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            self.assertEqual(claim["work_item_id"], "wi")
            self.assertEqual(claim["checkpoint_id"], "CP")
            self.assertEqual(claim["takeover_count"], 0)
            self.assertEqual(ws.resolve_claim(repo.root, "wi"), claim)

    def test_resolve_claim_absent_returns_none(self):
        with ScratchRepo() as repo:
            self.assertIsNone(ws.resolve_claim(repo.root, "wi"))

    def test_claim_checkpoint_same_checkpoint_is_idempotent(self):
        with ScratchRepo() as repo:
            first = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            second = ws.claim_checkpoint(repo.root, "wi", "CP", now="t2")
            self.assertEqual(first["owner_token"], second["owner_token"])

    def test_claim_checkpoint_different_checkpoint_same_worktree_raises_state_mismatch(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP1", now="t1")
            with self.assertRaises(ws.CheckpointOwnershipStateMismatchError):
                ws.claim_checkpoint(repo.root, "wi", "CP2", now="t2")

    def test_claim_across_worktrees_raises_owned_by_other(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            wt2 = repo.worktree("b")
            with self.assertRaises(ws.CheckpointOwnedByOtherWorktreeError):
                ws.claim_checkpoint(wt2, "wi", "CP", now="t2")

    def test_symlinked_claim_path_refuses_never_followed(self):
        with ScratchRepo() as repo:
            claims = ws.claims_dir(repo.root)
            claims.mkdir(parents=True, exist_ok=True)
            target = claims / "elsewhere.json"
            target.write_text(json.dumps({"schema_version": ws.CLAIM_SCHEMA_VERSION}))
            ws.claim_path(repo.root, "wi").symlink_to(target)
            with self.assertRaises(ws.CheckpointOwnershipUnavailableError):
                ws.resolve_claim(repo.root, "wi")
            with self.assertRaises(ws.CheckpointOwnershipUnavailableError):
                ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")

    def test_observe_claim_never_raises_reports_symlink_with_domain_tagged_id(self):
        with ScratchRepo() as repo:
            claims = ws.claims_dir(repo.root)
            claims.mkdir(parents=True, exist_ok=True)
            target = claims / "elsewhere.json"
            target.write_text("irrelevant")
            ws.claim_path(repo.root, "wi").symlink_to(target)
            oid, claim, error, unreadable = ws.observe_claim(repo.root, "wi")
            self.assertIsNone(claim)
            self.assertIsNotNone(error)
            self.assertEqual(unreadable["kind"], "symlink")
            self.assertNotEqual(oid, ws.ABSENT_OBSERVATION)

    def test_release_checkpoint_is_compare_and_delete(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            ws.release_checkpoint(repo.root, "wi", "CP", owner_token=claim["owner_token"], now="t2")
            self.assertIsNone(ws.resolve_claim(repo.root, "wi"))

    def test_release_checkpoint_from_foreign_worktree_refuses(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            wt2 = repo.worktree("b")
            with self.assertRaises(ws.CheckpointOwnedByOtherWorktreeError):
                ws.release_checkpoint(wt2, "wi", "CP", owner_token=claim["owner_token"], now="t2")


class TestClaimAdoption(unittest.TestCase):
    """`adopt_claim` -- "Reconciling the two authorities" (revisions
    63-72): the one-time migration path for a checkpoint interrupted
    before this mechanism existed."""

    def test_refuses_when_local_state_has_no_such_checkpoint(self):
        with ScratchRepo() as repo:
            with self.assertRaises(ws.CheckpointNotInProgressLocallyError):
                ws.adopt_claim(repo.root, "wi", "CP", now="t1")

    def test_refuses_when_local_status_is_not_in_progress(self):
        with ScratchRepo() as repo:
            _write_local_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "COMPLETE"}}}))
            with self.assertRaises(ws.CheckpointNotInProgressLocallyError):
                ws.adopt_claim(repo.root, "wi", "CP", now="t1")

    def test_dirty_resume_safety_checked_before_origination(self):
        """No `WORKTREE_IDENTITY.json` at all -- `verify_dirty_resume_
        safety`'s own error, not an origination refusal, proving ordering
        (2) runs before (3)."""
        with ScratchRepo() as repo:
            _write_local_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            with self.assertRaises(ws.WorktreeIdentityMissingError):
                ws.adopt_claim(repo.root, "wi", "CP", now="t1")

    def test_adoption_succeeds_when_origination_reference_is_silent(self):
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _write_local_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            record = ws.adopt_claim(repo.root, "wi", "CP", now="t1")
            self.assertEqual(record["checkpoint_id"], "CP")
            self.assertTrue(record["adopted"])
            self.assertEqual(ws.resolve_claim(repo.root, "wi"), record)

    def test_adoption_is_idempotent(self):
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _write_local_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            first = ws.adopt_claim(repo.root, "wi", "CP", now="t1")
            second = ws.adopt_claim(repo.root, "wi", "CP", now="t2")
            self.assertEqual(first["owner_token"], second["owner_token"])

    def test_refuses_when_origination_reference_observes_in_progress_at_head(self):
        """The committed-history case (3): this worktree's own local
        `IN_PROGRESS` is fully explained by a checkout of committed
        history, not by this worktree's own step 1d -- adoption must not
        treat it as proof of origination."""
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.adopt_claim(repo.root, "wi", "CP", now="t1")
            self.assertEqual(ctx.exception.evidence["route"], "observed")

    def test_refuses_foreign_claim_even_when_origination_admits(self):
        with ScratchRepo() as repo:
            wt2 = repo.worktree("b")
            ws.claim_checkpoint(wt2, "wi", "CP", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _write_local_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            with self.assertRaises(ws.CheckpointOwnedByOtherWorktreeError):
                ws.adopt_claim(repo.root, "wi", "CP", now="t1")

    def test_refuses_to_repoint_this_worktrees_own_claim_to_a_different_checkpoint(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP1", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _write_local_state(repo, _state_json(wi={"checkpoints": {"CP2": {"status": "IN_PROGRESS"}}}))
            with self.assertRaises(ws.CheckpointOwnershipStateMismatchError):
                ws.adopt_claim(repo.root, "wi", "CP2", now="t1")


class TestResolveCheckpointOwnership(unittest.TestCase):
    """`resolve_checkpoint_ownership` -- `/milestone-implement`'s step 1c
    ("Where the check belongs, and the ordering"), the reconciliation
    table under "Reconciling the two authorities" in full: every row of

        | local WORKFLOW_STATE.json | shared claim | outcome |

    from docs/ai-workflow/WORKFLOW_V2_PLAN.md, asserted against the real
    function rather than the unwired dry-run prototype it is authored
    fresh against."""

    def test_uncontended_no_claim_no_local_in_progress_is_fresh(self):
        with ScratchRepo() as repo:
            wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
            outcome, checkpoint_id, token = ws.resolve_checkpoint_ownership(
                repo.root, wi, "wi", "CP", now="t1")
            self.assertEqual((outcome, checkpoint_id, token), (ws.FRESH, "CP", None))

    def test_uncontended_nothing_selectable_is_no_checkpoint(self):
        """`GPT-R81-003`: an exhausted registry is terminal, never a
        mutation-capable `FRESH` carrying no checkpoint id."""
        with ScratchRepo() as repo:
            wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
            outcome, checkpoint_id, token = ws.resolve_checkpoint_ownership(
                repo.root, wi, "wi", None, now="t1")
            self.assertEqual((outcome, checkpoint_id, token), (ws.NO_CHECKPOINT, None, None))

    def test_uncontended_case_does_not_require_worktree_identity(self):
        """The ordinary uncontended case must stay exactly as permissive
        as it is today -- no `WORKTREE_IDENTITY.json` is ever required
        when neither authority says this work item is live."""
        with ScratchRepo() as repo:
            self.assertFalse((repo.root / ".ai-review").exists())
            wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
            ws.resolve_checkpoint_ownership(repo.root, wi, "wi", "CP", now="t1")
            self.assertFalse((repo.root / ".ai-review" / "runtime" / "WORKTREE_IDENTITY.json").exists())

    def test_contended_via_local_in_progress_with_no_identity_refuses(self):
        """Local `IN_PROGRESS` alone is enough to make this contended, even
        with no claim at all -- `verify_dirty_resume_safety` still runs."""
        with ScratchRepo() as repo:
            wi = _base_work_item(current_checkpoint_id="CP",
                                 checkpoints={"CP": {"status": "IN_PROGRESS"}})
            with self.assertRaises(ws.WorktreeIdentityMissingError) as ctx:
                ws.resolve_checkpoint_ownership(repo.root, wi, "wi", "CP", now="t1")
            self.assertIsNotNone(ctx.exception.ownership_evidence)
            self.assertIsNone(ctx.exception.ownership_evidence["claim"])
            self.assertIn("takeover", ctx.exception.ownership_evidence["escape"])

    def test_contended_via_foreign_claim_alone_with_no_identity_refuses(self):
        """A published claim alone is enough to make this contended, even
        with nothing locally `IN_PROGRESS`."""
        with ScratchRepo() as repo:
            wt2 = repo.worktree("b")
            ws.claim_checkpoint(wt2, "wi", "CP", now="t0")
            wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
            with self.assertRaises(ws.WorktreeIdentityMissingError):
                ws.resolve_checkpoint_ownership(repo.root, wi, "wi", "CP", now="t1")

    def test_adoption_then_resume_when_origination_reference_is_silent(self):
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _write_local_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            wi = _base_work_item(current_checkpoint_id="CP",
                                 checkpoints={"CP": {"status": "IN_PROGRESS"}})
            outcome, checkpoint_id, token = ws.resolve_checkpoint_ownership(
                repo.root, wi, "wi", "CP", now="t1")
            self.assertEqual((outcome, checkpoint_id), (ws.RESUME, "CP"))
            self.assertIsNotNone(token)
            claim = ws.resolve_claim(repo.root, "wi")
            self.assertTrue(claim["adopted"])
            self.assertEqual(claim["owner_token"], token)

    def test_adoption_refuses_when_origination_reference_observes_in_progress(self):
        """The committed-history case: this worktree's own local
        `IN_PROGRESS` is fully explained by a checkout of committed
        history, not by this worktree's own step 1d. The refusal carries
        both the origination evidence and the local-identity/claim-state
        components revision 71 (`OPUS-R88-005`) adds."""
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "IN_PROGRESS"}}}))
            wi = _base_work_item(current_checkpoint_id="CP",
                                 checkpoints={"CP": {"status": "IN_PROGRESS"}})
            with self.assertRaises(ws.CheckpointOriginationUnprovableError) as ctx:
                ws.resolve_checkpoint_ownership(repo.root, wi, "wi", "CP", now="t1")
            evidence = ctx.exception.ownership_evidence
            self.assertEqual(evidence["claim"], None)
            self.assertEqual(evidence["claim_state"], "absent")
            self.assertEqual(evidence["local_identity"]["state"], "valid")
            self.assertEqual(evidence["origination"]["route"], "observed")

    def test_resume_when_claim_self_owned_matches_local_in_progress(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            wi = _base_work_item(current_checkpoint_id="CP",
                                 checkpoints={"CP": {"status": "IN_PROGRESS"}})
            outcome, checkpoint_id, token = ws.resolve_checkpoint_ownership(
                repo.root, wi, "wi", "CP", now="t1")
            self.assertEqual((outcome, checkpoint_id, token), (ws.RESUME, "CP", claim["owner_token"]))

    def test_refuses_when_claim_self_owned_disagrees_with_local_in_progress(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP1", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            wi = _base_work_item(current_checkpoint_id="CP2",
                                 checkpoints={"CP2": {"status": "IN_PROGRESS"}})
            with self.assertRaises(ws.CheckpointOwnershipStateMismatchError) as ctx:
                ws.resolve_checkpoint_ownership(repo.root, wi, "wi", "CP2", now="t1")
            self.assertEqual(ctx.exception.ownership_evidence["claimed_checkpoint"], "CP1")

    def test_foreign_claim_refuses_even_with_valid_identity_for_this_work_item(self):
        """`S14a`/`S14b` are not the only classes a foreign worktree can
        see: one carrying a valid identity record of its own for this
        work item -- a displaced owner after a takeover, typically --
        passes `verify_dirty_resume_safety` and is refused one line
        later, by the foreign-claim row, with `CheckpointOwnedByOtherWorktreeError`."""
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            wt2 = repo.worktree("b")
            claim = ws.claim_checkpoint(wt2, "wi", "CP", now="t0")
            wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
            with self.assertRaises(ws.CheckpointOwnedByOtherWorktreeError) as ctx:
                ws.resolve_checkpoint_ownership(repo.root, wi, "wi", "CP", now="t1")
            evidence = ctx.exception.ownership_evidence
            self.assertEqual(evidence["holder"], claim["worktree_root"])
            self.assertIn("resume it there", evidence["escape"])

    def test_continue_claim_when_self_claim_has_no_local_entry_at_all(self):
        """The crash window between publishing the claim and writing the
        state (1d's own ordering keeps it narrow, never closes it)."""
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
            outcome, checkpoint_id, token = ws.resolve_checkpoint_ownership(
                repo.root, wi, "wi", None, now="t1")
            self.assertEqual((outcome, checkpoint_id, token),
                            (ws.CONTINUE_CLAIM, "CP", claim["owner_token"]))

    def test_continue_claim_refuses_when_selection_disagrees(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            wi = _base_work_item(current_checkpoint_id=None, checkpoints={})
            with self.assertRaises(ws.CheckpointOwnershipStateMismatchError):
                ws.resolve_checkpoint_ownership(repo.root, wi, "wi", "SOMETHING_ELSE", now="t1")

    def test_durable_completion_releases_and_returns_fresh_with_next_checkpoint(self):
        """The single automatic release in the whole design: a self-owned
        claim on a checkpoint whose completion is durable (committed at
        `HEAD`), i.e. a crash between step 1f's commit and its release."""
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "COMPLETE"}}}))
            wi = _base_work_item(current_checkpoint_id=None,
                                 checkpoints={"CP": {"status": "COMPLETE"}})
            outcome, checkpoint_id, token = ws.resolve_checkpoint_ownership(
                repo.root, wi, "wi", "NEXT", now="t1")
            self.assertEqual((outcome, checkpoint_id, token), (ws.FRESH, "NEXT", None))
            self.assertIsNone(ws.resolve_claim(repo.root, "wi"))

    def test_durable_completion_with_nothing_left_releases_and_returns_no_checkpoint(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            _commit_state(repo, _state_json(wi={"checkpoints": {"CP": {"status": "COMPLETE"}}}))
            wi = _base_work_item(current_checkpoint_id=None,
                                 checkpoints={"CP": {"status": "COMPLETE"}})
            outcome, checkpoint_id, token = ws.resolve_checkpoint_ownership(
                repo.root, wi, "wi", None, now="t1")
            self.assertEqual((outcome, checkpoint_id, token), (ws.NO_CHECKPOINT, None, None))
            self.assertIsNone(ws.resolve_claim(repo.root, "wi"))

    def test_completion_not_yet_durable_refuses_and_keeps_the_claim(self):
        """A6: the working tree saying `COMPLETE` is not enough -- the
        commit hasn't happened yet, so releasing now would hand the work
        item to another worktree while the completion is still
        uncommitted."""
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            wi = _base_work_item(current_checkpoint_id=None,
                                 checkpoints={"CP": {"status": "COMPLETE"}})
            with self.assertRaises(ws.CheckpointOwnershipStateMismatchError):
                ws.resolve_checkpoint_ownership(repo.root, wi, "wi", "NEXT", now="t1")
            self.assertIsNotNone(ws.resolve_claim(repo.root, "wi"))

    def test_self_claim_with_unexpected_local_status_refuses_rather_than_guesses(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            ws.write_worktree_identity(repo.root, "wi", now="t0")
            wi = _base_work_item(current_checkpoint_id=None,
                                 checkpoints={"CP": {"status": "BOGUS"}})
            with self.assertRaises(ws.CheckpointOwnershipStateMismatchError):
                ws.resolve_checkpoint_ownership(repo.root, wi, "wi", None, now="t1")

    def test_outcome_is_always_one_of_the_four_named_constants(self):
        self.assertEqual(ws.CHECKPOINT_OWNERSHIP_OUTCOMES,
                         {ws.RESUME, ws.FRESH, ws.CONTINUE_CLAIM, ws.NO_CHECKPOINT})


class TestOwnershipEvidenceAttachment(unittest.TestCase):
    """`_attach_ownership_evidence` -- carries the claim record, the
    holder, the claimed checkpoint, and the escape on *every* refusal,
    including an absent claim (corrected, revision 70, `OPUS-R87-003`;
    the unwired dry-run prototype's own version returns early on
    `claim is None`, which is exactly the defect withdrawn)."""

    def test_attaches_evidence_even_when_claim_is_absent(self):
        with ScratchRepo() as repo:
            exc = ws.CheckpointOwnershipStateMismatchError("boom")
            ws._attach_ownership_evidence(repo.root, exc, "wi", None)
            self.assertIsNotNone(exc.ownership_evidence)
            self.assertIsNone(exc.ownership_evidence["claim"])
            self.assertIsNone(exc.ownership_evidence["holder"])
            self.assertIn("takeover", exc.ownership_evidence["escape"])

    def test_is_idempotent_keeps_first_attachment(self):
        with ScratchRepo() as repo:
            exc = ws.CheckpointOwnershipStateMismatchError("boom")
            ws._attach_ownership_evidence(repo.root, exc, "wi", None)
            first = exc.ownership_evidence
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            ws._attach_ownership_evidence(repo.root, exc, "wi", claim)
            self.assertIs(exc.ownership_evidence, first)
            self.assertIsNone(exc.ownership_evidence["claim"])

    def test_self_owned_claim_names_continuation_not_a_removal(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t0")
            exc = ws.CheckpointOwnershipStateMismatchError("boom")
            ws._attach_ownership_evidence(repo.root, exc, "wi", claim)
            self.assertIn("continue it here", exc.ownership_evidence["escape"])
            self.assertIn("no takeover applies", exc.ownership_evidence["escape"])


class TestCheckpointMutationGuard(unittest.TestCase):
    def test_owner_mutation_happy_path_releases_guard(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            token = claim["owner_token"]
            with ws.owner_mutation(repo.root, "wi", token, checkpoint_id="CP",
                                   step="1d", step_class=ws.ORDINARY, now="t2") as asserted:
                self.assertEqual(asserted["owner_token"], token)
            self.assertIsNone(ws.read_guard(repo.root, "wi"))

    def test_owner_mutation_fences_displaced_owner_after_takeover(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            token = claim["owner_token"]
            wt2 = repo.worktree("b")
            evidence = ws.takeover_evidence(wt2, "wi")
            literal = ws.takeover_authorization_literal("wi", evidence, "CP")
            ws.take_over_claim(wt2, "wi", "CP", now="t2", user_authorization=literal, evidence=evidence)
            with self.assertRaises(ws.CheckpointOwnedByOtherWorktreeError):
                ws.assert_claim_owner(repo.root, "wi", token)

    def test_superseded_epoch_guard_reclaimed_with_no_authorization(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            # A stale guard left by a session whose ownership has already
            # rotated away -- the current claim's token is `claim
            # ["owner_token"]`, not this one.
            ws._publish_guard(repo.root, "wi", {
                "lease_id": "stale-lease", "holder_owner_token": "bogus-old-token",
                "holder_worktree_git_dir": "/nowhere", "work_item_id": "wi",
                "checkpoint_id": "CP", "step": "old-step", "step_class": ws.ORDINARY,
                "acquired_at": "t0",
            })
            lease = ws.acquire_guard(repo.root, "wi", holder_owner_token=claim["owner_token"],
                                     checkpoint_id="CP", step="1d", step_class=ws.ORDINARY, now="t2")
            self.assertNotEqual(lease["lease_id"], "stale-lease")
            ws.release_guard(repo.root, "wi", lease)

    def test_same_worktree_same_token_guard_reclaimed(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            token = claim["owner_token"]
            first = ws.acquire_guard(repo.root, "wi", holder_owner_token=token, checkpoint_id="CP",
                                     step="1d", step_class=ws.DESTRUCTIVE, now="t2")
            # Simulates a crashed/interrupted session in the *same* worktree
            # re-entering the step without having released -- reclaimed with
            # no authorization (rule 2), never a fence between sessions in
            # one worktree.
            second = ws.acquire_guard(repo.root, "wi", holder_owner_token=token, checkpoint_id="CP",
                                      step="1d", step_class=ws.DESTRUCTIVE, now="t3")
            self.assertNotEqual(second["lease_id"], first["lease_id"])
            ws.release_guard(repo.root, "wi", second)

    def test_destructive_guard_blocks_a_concurrent_owner_mutation(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            token = claim["owner_token"]
            ws.acquire_guard(repo.root, "wi", holder_owner_token=token, checkpoint_id="CP",
                             step="1f-commit", step_class=ws.DESTRUCTIVE, now="t2")
            with self.assertRaises(ws.CheckpointOwnershipUnavailableError):
                ws.acquire_guard(repo.root, "wi", holder_owner_token="a-different-token",
                                 checkpoint_id="CP", step="1d", step_class=ws.ORDINARY, now="t3")

    def test_unknown_step_class_refuses(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            with self.assertRaises(ws.CheckpointOwnershipUnavailableError):
                ws.acquire_guard(repo.root, "wi", holder_owner_token=claim["owner_token"],
                                 checkpoint_id="CP", step="1d", step_class="bogus", now="t2")


class TestExplicitTakeover(unittest.TestCase):
    def test_takeover_requires_exact_literal(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            wt2 = repo.worktree("b")
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.take_over_claim(wt2, "wi", "CP", now="t2", user_authorization="not the literal")

    def test_takeover_rotates_token_and_establishes_taker_identity(self):
        with ScratchRepo() as repo:
            original = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            wt2 = repo.worktree("b")
            evidence = ws.takeover_evidence(wt2, "wi")
            literal = ws.takeover_authorization_literal("wi", evidence, "CP")
            new_claim = ws.take_over_claim(wt2, "wi", "CP", now="t2",
                                           user_authorization=literal, evidence=evidence)
            self.assertNotEqual(new_claim["owner_token"], original["owner_token"])
            self.assertEqual(new_claim["takeover_count"], 1)
            self.assertEqual(new_claim["previous_owner_tokens"], [original["owner_token"]])
            self.assertTrue(ws.claim_is_this_worktree(wt2, new_claim))
            identity = json.loads((wt2 / ws.WORKTREE_IDENTITY_PATH).read_text())
            self.assertIn("wi", identity["expected_dirty_paths_by_work_item"])

    def test_takeover_of_absent_claim_uses_absent_observation(self):
        with ScratchRepo() as repo:
            wt2 = repo.worktree("b")
            evidence = ws.takeover_evidence(wt2, "wi")
            self.assertEqual(evidence["claim_observation_id"], ws.ABSENT_OBSERVATION)
            literal = ws.takeover_authorization_literal("wi", evidence, "CP")
            record = ws.take_over_claim(wt2, "wi", "CP", now="t2",
                                        user_authorization=literal, evidence=evidence)
            self.assertEqual(record["takeover_count"], 1)
            self.assertNotIn("taken_over_from", record)

    def test_takeover_refuses_on_destructive_guard(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            ws.acquire_guard(repo.root, "wi", holder_owner_token=claim["owner_token"],
                             checkpoint_id="CP", step="1f-commit", step_class=ws.DESTRUCTIVE, now="t2")
            wt2 = repo.worktree("b")
            evidence = ws.takeover_evidence(wt2, "wi")
            literal = ws.takeover_authorization_literal("wi", evidence, "CP")
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.take_over_claim(wt2, "wi", "CP", now="t3", user_authorization=literal, evidence=evidence)

    def test_takeover_refuses_on_stale_evidence(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            wt2 = repo.worktree("b")
            stale_evidence = ws.takeover_evidence(wt2, "wi")
            literal = ws.takeover_authorization_literal("wi", stale_evidence, "CP")
            # The claim changes underneath the evidence the user reviewed --
            # here, a takeover from a third worktree beats them to it.
            wt3 = repo.worktree("c")
            fresh_evidence = ws.takeover_evidence(wt3, "wi")
            ws.take_over_claim(wt3, "wi", "CP", now="t2",
                               user_authorization=ws.takeover_authorization_literal("wi", fresh_evidence, "CP"),
                               evidence=fresh_evidence)
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.take_over_claim(wt2, "wi", "CP", now="t3",
                                   user_authorization=literal, evidence=stale_evidence)

    def test_takeover_refuses_when_claim_path_is_a_directory(self):
        with ScratchRepo() as repo:
            claim_path = ws.claim_path(repo.root, "wi")
            claim_path.parent.mkdir(parents=True, exist_ok=True)
            claim_path.mkdir()
            wt2 = repo.worktree("b")
            evidence = ws.takeover_evidence(wt2, "wi")
            self.assertFalse(evidence["claim_replaceable"])
            literal = ws.takeover_authorization_literal("wi", evidence, "CP")
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.take_over_claim(wt2, "wi", "CP", now="t2", user_authorization=literal, evidence=evidence)


class TestAbandonedDestructiveGuardRecovery(unittest.TestCase):
    def test_recovery_refuses_while_holder_still_registered(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            ws.acquire_guard(repo.root, "wi", holder_owner_token=claim["owner_token"],
                             checkpoint_id="CP", step="1f-commit", step_class=ws.DESTRUCTIVE, now="t2")
            wt2 = repo.worktree("b")
            evidence = ws.takeover_evidence(wt2, "wi")
            literal = ws.abandoned_guard_recovery_authorization_literal("wi", evidence)
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.recover_abandoned_destructive_guard(wt2, "wi", "CP", now="t3",
                                                        user_authorization=literal, evidence=evidence)

    def test_recovery_refuses_on_non_destructive_guard(self):
        with ScratchRepo() as repo:
            claim = ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            ws.acquire_guard(repo.root, "wi", holder_owner_token=claim["owner_token"],
                             checkpoint_id="CP", step="1d", step_class=ws.ORDINARY, now="t2")
            wt2 = repo.worktree("b")
            evidence = ws.takeover_evidence(wt2, "wi")
            literal = ws.abandoned_guard_recovery_authorization_literal("wi", evidence)
            with self.assertRaises(ws.CheckpointClaimTakeoverRefusedError):
                ws.recover_abandoned_destructive_guard(wt2, "wi", "CP", now="t3",
                                                        user_authorization=literal, evidence=evidence)

    def test_recovery_succeeds_once_holder_worktree_is_deregistered(self):
        with ScratchRepo() as repo:
            wt_holder = repo.worktree("holder")
            claim = ws.claim_checkpoint(wt_holder, "wi", "CP", now="t1")
            lease = ws.acquire_guard(wt_holder, "wi", holder_owner_token=claim["owner_token"],
                                     checkpoint_id="CP", step="1f-commit", step_class=ws.DESTRUCTIVE, now="t2")
            repo.remove_worktree(wt_holder)
            evidence = ws.takeover_evidence(repo.root, "wi")
            self.assertFalse(evidence["holder_registered"])
            literal = ws.abandoned_guard_recovery_authorization_literal("wi", evidence)
            record = ws.recover_abandoned_destructive_guard(
                repo.root, "wi", "CP", now="t3", user_authorization=literal, evidence=evidence)
            self.assertEqual(record["takeover_count"], 1)
            self.assertEqual(
                record["taken_over_from"]["recovered_from_abandoned_guard"]["lease_id"], lease["lease_id"])
            # the abandoned guard is superseded by construction once the
            # claim rotates -- the recovering worktree can acquire cleanly.
            new_lease = ws.acquire_guard(repo.root, "wi", holder_owner_token=record["owner_token"],
                                         checkpoint_id="CP", step="1d", step_class=ws.ORDINARY, now="t4")
            ws.release_guard(repo.root, "wi", new_lease)


class TestCanonicalStateSerialization(unittest.TestCase):
    """OPUS-R101-005: `_serialize_state` is the single source of truth for
    `WORKFLOW_STATE.json` bytes, deliberately `ensure_ascii=True`, shared by
    production publication and by this suite's own direct-write fixture
    (`_commit_state_only`) so the two can never disagree."""

    def test_round_trip_is_byte_stable_for_non_ascii_content(self):
        state = {
            "schema_version": 1,
            "work_items": {
                "wi": {
                    "note": "WF8b finding disposition (revision 53 → 54) — done",
                    "emoji": "\U0001F600",
                }
            },
        }
        first = ws._serialize_state(state)
        second = ws._serialize_state(json.loads(first.decode("utf-8")))
        self.assertEqual(first, second)

    def test_serialization_is_pure_ascii_bytes(self):
        state = {"schema_version": 1, "work_items": {"wi": {"note": "→—"}}}
        payload = ws._serialize_state(state)
        self.assertTrue(all(b < 128 for b in payload))
        self.assertTrue(payload.endswith(b"\n"))

    def test_publish_state_file_uses_the_canonical_serialization(self):
        with ScratchRepo() as repo:
            full_path = repo.root / "docs/ai-workflow/WORKFLOW_STATE.json"
            state = {"schema_version": 1, "work_items": {"wi": {"note": "→"}}}
            ws._publish_state_file(full_path, state)
            self.assertEqual(full_path.read_bytes(), ws._serialize_state(state))

    def test_live_workflow_state_bytes_equal_canonical_serialization_of_its_own_parsed_content(self):
        """The live repository's own `docs/ai-workflow/WORKFLOW_STATE.json`
        must already be in the canonical form -- proves the silent
        re-encoding OPUS-R101-005 found is not merely fixed going forward
        but that the live file matches the now-explicit contract today."""
        live_path = Path(__file__).resolve().parent.parent / "docs/ai-workflow/WORKFLOW_STATE.json"
        raw = live_path.read_bytes()
        parsed = json.loads(raw.decode("utf-8"))
        self.assertEqual(raw, ws._serialize_state(parsed))


class TestIdentityDocumentSerialization(unittest.TestCase):
    """`OPUS-R86-002`'s lost-update fix: `identity_document_lock` plus the
    single-`os.replace` publish in `_publish_worktree_identity`."""

    def test_write_worktree_identity_leaves_no_temp_file(self):
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t1")
            runtime_dir = repo.root / ".ai-review/runtime"
            leftover = [p for p in runtime_dir.iterdir() if p.name.endswith(".tmp") or ".tmp-" in p.name]
            self.assertEqual(leftover, [])

    def test_concurrent_writes_for_different_work_items_do_not_lose_entries(self):
        """Reproduces the plan's own repro in the fixed direction: through
        revision 68 this lost 12/12 threaded trials; serialized under
        `identity_document_lock`, every work item's entry survives."""
        with ScratchRepo() as repo:
            work_items = [f"wi-{i}" for i in range(10)]
            errors: list[Exception] = []

            def _write(work_item_id: str) -> None:
                try:
                    ws.write_worktree_identity(repo.root, work_item_id, now="t")
                except Exception as exc:  # noqa: BLE001
                    errors.append(exc)

            threads = [threading.Thread(target=_write, args=(wi,)) for wi in work_items]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            self.assertEqual(errors, [])
            doc = json.loads((repo.root / ws.WORKTREE_IDENTITY_PATH).read_text())
            self.assertEqual(set(doc["expected_dirty_paths_by_work_item"]), set(work_items))

    def test_local_identity_observation_absent(self):
        with ScratchRepo() as repo:
            observed = ws.local_identity_observation(repo.root)
            self.assertEqual(observed["state"], "absent")
            self.assertEqual(observed["identity_observation_id"], ws.ABSENT_OBSERVATION)

    def test_local_identity_observation_valid(self):
        with ScratchRepo() as repo:
            ws.write_worktree_identity(repo.root, "wi", now="t1")
            observed = ws.local_identity_observation(repo.root)
            self.assertEqual(observed["state"], "valid")

    def test_local_identity_observation_undecidable_on_corrupt_document(self):
        with ScratchRepo() as repo:
            full = repo.root / ws.WORKTREE_IDENTITY_PATH
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text("{not json")
            observed = ws.local_identity_observation(repo.root)
            self.assertEqual(observed["state"], "undecidable")

    def test_repair_worktree_identity_discards_corrupt_document(self):
        with ScratchRepo() as repo:
            full = repo.root / ws.WORKTREE_IDENTITY_PATH
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text("{not json")
            document = ws.repair_worktree_identity(repo.root, "wi", now="t1")
            self.assertIn("wi", document["expected_dirty_paths_by_work_item"])
            ws.verify_dirty_resume_safety(repo.root, "wi")  # must not raise

    def test_takeover_repairs_takers_own_corrupt_identity_under_explicit_authorization(self):
        with ScratchRepo() as repo:
            ws.claim_checkpoint(repo.root, "wi", "CP", now="t1")
            wt2 = repo.worktree("b")
            full = wt2 / ws.WORKTREE_IDENTITY_PATH
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_text("{not json")
            evidence = ws.takeover_evidence(wt2, "wi")
            self.assertEqual(evidence["local_identity"]["state"], "undecidable")
            literal = ws.takeover_authorization_literal("wi", evidence, "CP")
            self.assertIn("repairing identity", literal)
            ws.take_over_claim(wt2, "wi", "CP", now="t2", user_authorization=literal, evidence=evidence)
            document = json.loads(full.read_text())
            self.assertIn("wi", document["expected_dirty_paths_by_work_item"])


if __name__ == "__main__":
    unittest.main()
