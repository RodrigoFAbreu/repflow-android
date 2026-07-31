#!/usr/bin/env python3
"""Hermetic unit tests for workflow_fingerprint.py.

Runs entirely against disposable scratch Git repositories this suite
creates and destroys itself — never against this repository's own working
tree or a hardcoded historical commit. The real-repository demonstration
lives separately in `workflow_fingerprint_demo_test.py`.

Covers round-6 required acceptance criteria items 2-3 (the plan's own
test-vector table implemented as real tests), round-6 missing-test items
89-96/110-111, round-7's findings (`PROTO-R7-001` through `-013`), and
round-8's findings (`OPUS-R8-001` through `-019`, missing-test items
21-50 in `REVIEW_FEEDBACK.md`).

This docstring intentionally states exactly which items are implemented,
per OPUS-R8-012's finding that the previous version's coverage claim
("round-6 missing-test items 89-96/110-111") overstated what the suite
actually contained: items 90, 94, and 96 were absent despite being
claimed. All of 89-96, 110, 111, and 21-50 (round 8) are implemented
below, each tagged with its item/finding ID in the test name or
docstring. Round 9's findings (`GPT-R9-001`, `-009`, `-010`, `-014`;
missing-test items 1-3, 22, 23, 25) are implemented too.

`WF4a-i`: missing-test items 138 (`OPUS-R20-001`, atomic manifest write),
139 (`OPUS-R20-002`, root build files pinned unclassified), and 140
(`OPUS-R20-003`, plan-stage/implementation-stage opposite classification)
are implemented, plus coverage for the new implementation-stage
classification/manifest/identity functions
(`TestImplementationStageClassification`).

Stdlib-only. Run: python3 scripts/workflow_fingerprint_test.py
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import workflow_fingerprint as wf


def _run(args, cwd):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


def _write_review_request_with_content_id(repo, bundle_dir, **compute_overrides):
    """Seed REVIEW_REQUEST.md with the review_content_id
    `write_manifest_with_verified_identifiers` will compute, mirroring the
    real operator workflow: read the identifier (read-only), state it in
    REVIEW_REQUEST.md, then run the write path, which recomputes and
    asserts the two still agree (OPUS-R18-005)."""
    digest, _ = repo.compute(**compute_overrides)
    (bundle_dir / "REVIEW_REQUEST.md").write_text(f"stage: plan\nreview_content_id: {digest}\n")
    return digest


# Realistic 64-hex-character placeholders for bundle_id test fixtures --
# the field contract (OPUS-R8-013) requires exactly 64 hex characters, so
# short placeholders like "deadbeef" no longer match and must not be used.
FAKE_ID_A = "a" * 64
FAKE_ID_B = "b" * 64


class ScratchRepo:
    """A disposable Git repo with the three plan-stage protected files
    present, mirroring this repository's actual layout."""

    def __enter__(self):
        self.root = Path(tempfile.mkdtemp(prefix="wf-fingerprint-test-"))
        _run(["git", "init", "-q"], cwd=self.root)
        _run(["git", "config", "user.email", "test@example.com"], cwd=self.root)
        _run(["git", "config", "user.name", "Test"], cwd=self.root)
        (self.root / "README.md").write_text("base\n")
        _run(["git", "add", "README.md"], cwd=self.root)
        _run(["git", "commit", "-q", "-m", "base"], cwd=self.root)
        self.base = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()
        (self.root / "docs" / "ai-workflow").mkdir(parents=True)
        return self

    def write_plan_docs(
        self, plan_text="plan v1\n", audit_text="audit v1\n", decisions_text="decisions v1\n",
        registry_text='{"checkpoints": []}\n', mapping_text='{"requirements": {}}\n',
    ):
        (self.root / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md").write_text(plan_text)
        (self.root / "docs" / "ai-workflow" / "WORKFLOW_V2_AUDIT.md").write_text(audit_text)
        (self.root / "docs" / "TECHNICAL_DECISIONS.md").write_text(decisions_text)
        (self.root / "docs" / "ai-workflow" / "registry").mkdir(parents=True, exist_ok=True)
        (self.root / "docs" / "ai-workflow" / "registry" / "workflow-v2-1-core-registry.json").write_text(registry_text)
        (self.root / "docs" / "ai-workflow" / "requirements").mkdir(parents=True, exist_ok=True)
        (self.root / "docs" / "ai-workflow" / "requirements" / "workflow-v2-1-core-mapping.json").write_text(mapping_text)

    def commit_plan_docs_as_base(self):
        """Commit the three plan docs and advance `self.base` to that
        commit, so nothing is changed/untracked relative to base -- used
        by tests that vary the *protected set parameter* itself rather
        than file content, where the docs must already be settled,
        unclassified-content-neutral history."""
        _run(["git", "add", "-A"], cwd=self.root)
        _run(["git", "commit", "-q", "-m", "settle plan docs"], cwd=self.root)
        self.base = self.head()

    def compute(self, base=None, **overrides):
        kwargs = dict(
            work_item_type="process",
            work_item_id="workflow-v2-1-core",
            plan_revision=7,
        )
        kwargs.update(overrides)
        return wf.compute_review_content_id_plan_stage(self.root, base or self.base, **kwargs)

    def head(self) -> str:
        return subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.root, check=True,
            capture_output=True, text=True,
        ).stdout.strip()

    def __exit__(self, *exc):
        shutil.rmtree(self.root, ignore_errors=True)


class TestFailClosedClassification(unittest.TestCase):
    """PROTO-R7-001: classification must actually gate manifest
    construction, not merely exist as an untriggered helper."""

    def test_089_untracked_files_produce_nonempty_manifest_with_real_shas(self):
        """Every entry in PLAN_STAGE_PROTECTED now names a file
        write_plan_docs() actually creates (OPUS-R14-001/-009 moved the
        one previously-unwritten path, the operator guide, to the excluded
        set), so this manifest has no tombstone entries at all."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest, projection = repo.compute()
            manifest = projection["review_content_manifest"]
            self.assertEqual(len(manifest), 5)
            for entry in manifest:
                self.assertTrue(entry["exists"])
                expected = wf._hash_object(repo.root, entry["path"])
                self.assertEqual(entry["blob"], expected)

    def test_090_manifest_identical_whether_or_not_add_N_was_applied(self):
        """Round-6 missing-test item 90, absent from the previous suite
        despite being claimed covered (OPUS-R8-012): a prior caller's
        `git add -N` (e.g. `prepare-ai-review.sh`'s intent-to-add step)
        must not change the plan-stage manifest."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest_before, _ = repo.compute()
            _run(["git", "add", "-N", "-A"], cwd=repo.root)
            digest_after, _ = repo.compute()
            self.assertEqual(digest_before, digest_after)

    def test_095_unclassified_changed_path_fails_closed_end_to_end(self):
        """`app/` is now an excluded prefix (OPUS-R18-004), so this uses a
        genuinely novel path outside every protected/excluded set instead
        of `app/`, which no longer demonstrates fail-closed behavior."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            (repo.root / "totally_unmapped_surprise").mkdir()
            (repo.root / "totally_unmapped_surprise" / "unclassified.txt").write_text("surprise\n")
            with self.assertRaises(wf.UnclassifiedPathError):
                repo.compute()

    def test_unclassified_untracked_path_fails_closed(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            (repo.root / "random_new_file.txt").write_text("x\n")
            with self.assertRaises(wf.UnclassifiedPathError):
                wf.assert_all_changed_paths_classified_worktree(
                    repo.root, wf.resolve_base(repo.root, repo.base)
                )

    def test_excluded_path_does_not_raise_and_does_not_affect_identity(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest1, _ = repo.compute()
            (repo.root / ".gitignore").write_text("*.log\n")
            digest2, _ = repo.compute()
            self.assertEqual(digest1, digest2)

    def test_096_every_path_this_milestone_creates_is_classified_by_exactly_one_list(self):
        """Round-6 missing-test item 96, previously absent (OPUS-R8-012):
        the classifier's exhaustiveness over this milestone's own known
        artifact set."""
        known_paths = [
            "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            "docs/ai-workflow/WORKFLOW_V2_AUDIT.md",
            "docs/TECHNICAL_DECISIONS.md",
            "docs/ai-workflow/registry/workflow-v2-1-core-registry.json",
            "docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json",
            "scripts/workflow_fingerprint.py",
            "scripts/workflow_fingerprint_test.py",
            "scripts/workflow_fingerprint_demo_test.py",
            ".gitignore",
            ".github/workflows/ci.yml",
            "docs/ai-workflow/WORKFLOW_STATE.json",
            "docs/ai-workflow/WORKFLOW_CONFIG.json",
            # Implementation-phase artifacts (OPUS-R10-001): every path this
            # milestone's own checkpoints are known to create or modify past
            # the plan stage must classify too, or plan-approval durability
            # becomes unevaluable the moment the first one lands.
            "CLAUDE.md",
            "docs/ai-workflow/REVIEW_PROTOCOL.md",
            "docs/ai-workflow/MILESTONE_WORKFLOW.md",
            ".claude/commands/bootstrap-workflow-v2.md",
            ".claude/commands/approve-review.md",
            ".claude/commands/accept-milestone.md",
            ".claude/commands/milestone-plan.md",
            ".claude/commands/milestone-implement.md",
            ".claude/commands/apply-plan-review.md",
            ".claude/commands/apply-implementation-review.md",
            ".claude/commands/prepare-functional-review.md",
            ".claude/commands/apply-functional-review.md",
            ".claude/commands/prepare-review.md",
            "scripts/prepare-ai-review.sh",
            "scripts/validate_workflow_state.py",
            "scripts/workflow_state_test.py",
            "docs/ai-workflow/requirements/workflow-v2-1-core-ledger.md",
            # Concurrent-work-item artifacts (OPUS-R16-001, missing-test item
            # 121): the classification input is repository-wide, not scoped
            # to this work item, so a path any *other* work item's own
            # commands are documented to write must classify too -- not just
            # this milestone's own artifacts.
            "docs/ACTIVE_MILESTONE.md",
            "docs/ROADMAP.md",
            "docs/milestones/completed/milestone-8-execution.md",
            "docs/ai-workflow/archive/workflow-v2.1-core-final-state.json",
            # WF8b's own dry-run evidence and the synthetic item's artifact
            # tree (remediation: docs/ai-workflow/dry-run/ added to
            # PLAN_STAGE_EXCLUDED_PREFIXES after the real
            # docs/ai-workflow/dry-run/WF8B_SCENARIOS.md commit surfaced an
            # UnclassifiedPathError the classifier's prior exhaustive list
            # never covered).
            "docs/ai-workflow/dry-run/WF8B_SCENARIOS.md",
            "docs/ai-workflow/dry-run/v2-1-dry-run-plan.md",
        ]
        for path in known_paths:
            with self.subTest(path=path):
                result = wf.classify_path(
                    path, wf.PLAN_STAGE_PROTECTED, wf.PLAN_STAGE_EXCLUDED_PATHS,
                    wf.PLAN_STAGE_EXCLUDED_PREFIXES,
                )
                self.assertIn(result, {"protected", "excluded"})

    def test_055_plan_stage_id_recomputes_across_a_bootstrap_checkpoint_commit(self):
        """OPUS-R10-001, missing-test item 55: the exact end-to-end scenario
        the reviewer reproduced -- a WF0 commit (plan docs) followed by a
        WF1a-shaped commit that adds a command file, a script, and the
        ledger -- must not make plan-stage `review_content_id` unevaluable."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            approval_digest, _ = repo.compute()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "WF0: bootstrap"], cwd=repo.root)

            (repo.root / ".claude" / "commands").mkdir(parents=True)
            (repo.root / ".claude" / "commands" / "bootstrap-workflow-v2.md").write_text("x\n")
            (repo.root / "scripts").mkdir(exist_ok=True)
            (repo.root / "scripts" / "validate_workflow_state.py").write_text("x\n")
            (repo.root / "docs" / "ai-workflow" / "requirements").mkdir(parents=True, exist_ok=True)
            (repo.root / "docs" / "ai-workflow" / "requirements" / "workflow-v2-1-core-ledger.md").write_text("x\n")
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "WF1a"], cwd=repo.root)

            post_checkpoint_digest, _ = repo.compute()
            self.assertEqual(
                approval_digest, post_checkpoint_digest,
                "plan-approval durability must survive a checkpoint commit "
                "that adds implementation-phase artifacts",
            )

    def test_056_every_registry_declared_checkpoint_artifact_classifies(self):
        """Missing-test item 56 (best available proxy: the registry JSON does
        not yet declare per-checkpoint artifact paths -- WF1b's stated future
        scope -- so this exercises the concrete, currently-known set from
        test_096 as the exhaustiveness check available today)."""
        for path in [
            ".claude/commands/bootstrap-workflow-v2.md",
            "scripts/validate_workflow_state.py",
            "docs/ai-workflow/requirements/workflow-v2-1-core-ledger.md",
            "CLAUDE.md",
            "docs/ai-workflow/REVIEW_PROTOCOL.md",
            "docs/ai-workflow/MILESTONE_WORKFLOW.md",
        ]:
            with self.subTest(path=path):
                self.assertEqual(
                    wf.classify_path(
                        path, wf.PLAN_STAGE_PROTECTED, wf.PLAN_STAGE_EXCLUDED_PATHS,
                        wf.PLAN_STAGE_EXCLUDED_PREFIXES,
                    ),
                    "excluded",
                )

    def test_101_full_checkpoint_sequence_leaves_plan_stage_id_unchanged(self):
        """Missing-test item 101 (OPUS-R14-001, acceptance criterion 2):
        simulates one commit per excluded artifact class the real
        17-checkpoint registry is known to create -- a command file, a
        script, a registry-directory sibling, a requirements-directory
        sibling, and (now that OPUS-R14-001/-009 moved it) the plan-review
        operator guide itself -- and asserts plan-stage `review_content_id`
        is identical to its approval-time value after every single one,
        not just after the whole sequence. Extends test_055's one-commit
        proxy to a full walk of every excluded prefix/path this milestone's
        own checkpoints are known to populate."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            approval_digest, _ = repo.compute()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "WF0: bootstrap"], cwd=repo.root)

            simulated_checkpoint_artifacts = [
                (".claude/commands", "bootstrap-workflow-v2.md"),
                (".claude/commands", "review-plan.md"),
                (".claude/commands", "record-manual-plan-review.md"),
                ("scripts", "validate_workflow_state.py"),
                ("scripts", "prepare-ai-review.sh"),
                ("docs/ai-workflow/requirements", "workflow-v2-1-core-ledger.md"),
                ("docs/ai-workflow/registry", "some-future-registry-artifact.json"),
                ("docs/ai-workflow", "PLAN_REVIEW_WORKFLOW.md"),
            ]
            for i, (directory, filename) in enumerate(simulated_checkpoint_artifacts):
                (repo.root / directory).mkdir(parents=True, exist_ok=True)
                (repo.root / directory / filename).write_text(f"checkpoint {i}\n")
                _run(["git", "add", "-A"], cwd=repo.root)
                _run(["git", "commit", "-q", "-m", f"simulated checkpoint {i}: {filename}"], cwd=repo.root)

                digest, _ = repo.compute()
                self.assertEqual(
                    approval_digest, digest,
                    f"plan-stage review_content_id changed after simulated "
                    f"checkpoint {i} ({directory}/{filename})",
                )

    def test_117_120_concurrent_work_item_writes_mid_sequence_do_not_raise_or_change_id(self):
        """Missing-test items 117-120 (OPUS-R16-001): the classification
        input is repository-wide (git diff/untracked over the whole
        worktree), not scoped to this work item, so a *different* work
        item's own documented write -- Milestone 8 adoption writing
        docs/ACTIVE_MILESTONE.md, /accept-milestone writing docs/ROADMAP.md
        or docs/milestones/, or this work item's own eventual
        process-completion archival under docs/ai-workflow/archive/ -- must
        neither raise nor change this work item's plan-stage
        review_content_id, even while this work item is itself mid-sequence
        (some checkpoints already committed, more remaining)."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            approval_digest, _ = repo.compute()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "WF0: bootstrap"], cwd=repo.root)

            # Simulate this work item already mid-sequence: one checkpoint
            # commit landed before the concurrent write below arrives.
            (repo.root / "scripts").mkdir(exist_ok=True)
            (repo.root / "scripts" / "validate_workflow_state.py").write_text("x\n")
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "simulated checkpoint: WF1a"], cwd=repo.root)
            mid_sequence_digest, _ = repo.compute()
            self.assertEqual(approval_digest, mid_sequence_digest)

            concurrent_work_item_writes = [
                ("docs", "ACTIVE_MILESTONE.md"),  # item 117
                ("docs", "ROADMAP.md"),  # item 118
                ("docs/milestones/completed", "milestone-8-execution.md"),  # item 119
                ("docs/ai-workflow/archive", "workflow-v2.1-core-final-state.json"),  # item 120
            ]
            for i, (directory, filename) in enumerate(concurrent_work_item_writes):
                (repo.root / directory).mkdir(parents=True, exist_ok=True)
                (repo.root / directory / filename).write_text(f"concurrent write {i}\n")
                _run(["git", "add", "-A"], cwd=repo.root)
                _run(["git", "commit", "-q", "-m", f"concurrent work item write {i}: {filename}"], cwd=repo.root)

                digest, _ = repo.compute()
                self.assertEqual(
                    approval_digest, digest,
                    f"plan-stage review_content_id changed after a concurrent "
                    f"work item's write to {directory}/{filename}",
                )

    def test_057_a_genuinely_unknown_path_still_fails_closed(self):
        """Missing-test item 57: widening the lists must not turn the
        classifier into a rubber stamp. `app/Foo.kt` was this test's
        original example; OPUS-R18-004 excluded `app/` as a genuine
        concurrent-work-item path, so a path outside every named set is
        used here instead (item 135)."""
        with self.assertRaises(wf.UnclassifiedPathError):
            wf.classify_path(
                "totally_unmapped_surprise/Foo.kt", wf.PLAN_STAGE_PROTECTED, wf.PLAN_STAGE_EXCLUDED_PATHS,
                wf.PLAN_STAGE_EXCLUDED_PREFIXES,
            )

    def test_technical_decisions_is_protected_not_excluded(self):
        """OPUS-R8-008 bonus-answer 5: a technical-decision entry created
        specifically to satisfy a review finding is durable design content,
        not incidental housekeeping -- it must be protected, so editing it
        changes review_content_id."""
        self.assertIn("docs/TECHNICAL_DECISIONS.md", wf.PLAN_STAGE_PROTECTED)
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest1, _ = repo.compute()
            repo.write_plan_docs(decisions_text="decisions v2 -- changed\n")
            digest2, _ = repo.compute()
            self.assertNotEqual(digest1, digest2)

    def test_079_plan_review_workflow_guide_is_excluded_not_protected(self):
        """Missing-test item 79, corrected per OPUS-R14-001/-009: the
        concise two-stage plan-review operator guide's final path must
        classify as excluded, not protected, so WF4a-iv's creation of it
        never stales a plan approval -- pre-declaring it protected while
        unwritten (GPT-R11-008's original resolution) made D-States'
        checkpoint-invariance claim false the moment WF4a-iv actually wrote
        it, since that write is itself a checkpoint touching a protected
        path. Excluding it (like MILESTONE_WORKFLOW.md/REVIEW_PROTOCOL.md)
        means the operator guide is treated as documentation of the
        approved design, not the design itself."""
        path = "docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md"
        self.assertNotIn(path, wf.PLAN_STAGE_PROTECTED)
        self.assertIn(path, wf.PLAN_STAGE_EXCLUDED_PATHS)
        self.assertEqual(
            wf.classify_path(
                path, wf.PLAN_STAGE_PROTECTED, wf.PLAN_STAGE_EXCLUDED_PATHS,
                wf.PLAN_STAGE_EXCLUDED_PREFIXES,
            ),
            "excluded",
        )

    def test_wf8b_dry_run_prefix_is_excluded(self):
        """Remediation test: `docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`
        (committed by WF8b's evidence-prep commit) raised
        `UnclassifiedPathError` because the exhaustive prefix list predated
        that directory. `docs/ai-workflow/dry-run/` was added to
        `PLAN_STAGE_EXCLUDED_PREFIXES` -- it is WF8b's own dry-run evidence
        and the throwaway synthetic work item's artifact tree, not design
        content for this work item's own plan."""
        path = "docs/ai-workflow/dry-run/WF8B_SCENARIOS.md"
        self.assertNotIn(path, wf.PLAN_STAGE_PROTECTED)
        self.assertEqual(
            wf.classify_path(
                path, wf.PLAN_STAGE_PROTECTED, wf.PLAN_STAGE_EXCLUDED_PATHS,
                wf.PLAN_STAGE_EXCLUDED_PREFIXES,
            ),
            "excluded",
        )

    def test_wf8b_dry_run_prefix_match_is_precise_not_a_substring_match(self):
        """A lookalike path that merely shares the `dry-run` substring but is
        not actually under the `docs/ai-workflow/dry-run/` directory must
        still fail closed -- proves the exclusion is a real path-segment
        prefix, not a loose substring match that could be widened by
        accident."""
        with self.assertRaises(wf.UnclassifiedPathError):
            wf.classify_path(
                "docs/ai-workflow/dry-runner/x.md", wf.PLAN_STAGE_PROTECTED,
                wf.PLAN_STAGE_EXCLUDED_PATHS, wf.PLAN_STAGE_EXCLUDED_PREFIXES,
            )


class TestExclusionListStructure(unittest.TestCase):
    """OPUS-R8-006: exact-path vs. directory-prefix exclusions are
    separated and validated; OPUS-R8-007: every entry is reachable;
    OPUS-R8-008: every entry carries a justification."""

    def test_036_excluded_path_with_appended_suffix_fails_closed(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            (repo.root / ".gitignore.bak").write_text("x\n")
            with self.assertRaises(wf.UnclassifiedPathError):
                repo.compute()

    def test_037_malformed_prefix_constant_rejected_at_import(self):
        with self.assertRaises(wf.MalformedExclusionConstantError):
            wf._validate_exclusion_prefixes({"scripts": "missing trailing slash"})
        # a well-formed prefix is accepted without raising
        wf._validate_exclusion_prefixes({"scripts/": "well-formed"})

    def test_039_every_exclusion_entry_is_reachable(self):
        """OPUS-R8-007: the previous suite proved the exclusion list via a
        scratch repo with no matching `.gitignore` entry, which passed for
        a different reason than production (invisibility, not exclusion).
        Every current exact-path entry must actually classify as
        'excluded' when presented directly."""
        for path in wf.PLAN_STAGE_EXCLUDED_PATHS:
            with self.subTest(path=path):
                result = wf.classify_path(
                    path, wf.PLAN_STAGE_PROTECTED, wf.PLAN_STAGE_EXCLUDED_PATHS,
                    wf.PLAN_STAGE_EXCLUDED_PREFIXES,
                )
                self.assertEqual(result, "excluded")

    def test_040_every_exclusion_entry_has_a_justification(self):
        for path, reason in wf.PLAN_STAGE_EXCLUDED_PATHS.items():
            with self.subTest(path=path):
                self.assertTrue(reason and reason.strip())
        for prefix, reason in wf.PLAN_STAGE_EXCLUDED_PREFIXES.items():
            with self.subTest(prefix=prefix):
                self.assertTrue(reason and reason.strip())

    def test_gitignored_path_in_repo_with_real_gitignore_is_invisible_not_excluded(self):
        """OPUS-R8-007: state and test the actual production path -- a
        gitignored file is invisible to classification because it never
        appears in untracked-path enumeration, not because an exclusion
        entry matches it."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            (repo.root / ".gitignore").write_text("ignored_dir/\n")
            _run(["git", "add", ".gitignore"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "add gitignore"], cwd=repo.root)
            digest1, _ = repo.compute()
            (repo.root / "ignored_dir").mkdir()
            (repo.root / "ignored_dir" / "secret.txt").write_text("hidden\n")
            digest2, _ = repo.compute()
            self.assertEqual(digest1, digest2)


class TestDeterministicBase(unittest.TestCase):
    """PROTO-R7-002: identity must not depend on core.abbrev or on how the
    base commit was spelled."""

    def test_full_short_and_HEAD_base_spellings_agree(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            id_full, _ = repo.compute()
            id_short, _ = repo.compute(base=repo.base[:10])
            self.assertEqual(id_full, id_short)

    def test_core_abbrev_does_not_affect_identity(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            _run(["git", "config", "core.abbrev", "40"], cwd=repo.root)
            id_abbrev40, _ = repo.compute()
            _run(["git", "config", "core.abbrev", "4"], cwd=repo.root)
            id_abbrev4, _ = repo.compute()
            self.assertEqual(id_abbrev40, id_abbrev4)

    def test_ambiguous_base_fails_before_manifest_construction(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            with self.assertRaises(wf.AmbiguousBaseError):
                wf.resolve_base(repo.root, "not-a-real-ref")

    def test_091_one_byte_edit_changes_review_content_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest1, _ = repo.compute()
            repo.write_plan_docs(plan_text="plan v1 X\n")
            digest2, _ = repo.compute()
            self.assertNotEqual(digest1, digest2)


class TestWorktreeCommitParity(unittest.TestCase):
    """PROTO-R7-003: worktree-source and commit-source manifests must be
    genuinely compared, not merely each internally self-consistent."""

    def test_complete_manifests_are_equal_after_approval_commit(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            worktree_id, worktree_projection = repo.compute()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "plan-approval"], cwd=repo.root)
            commit_id, commit_projection = wf.compute_review_content_id_plan_stage_at_commit(
                repo.root, repo.base, repo.head(),
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            self.assertEqual(
                worktree_projection["review_content_manifest"],
                commit_projection["review_content_manifest"],
            )
            self.assertEqual(worktree_id, commit_id)

    def test_tracked_unchanged_protected_path_identical_in_both_modes(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "initial"], cwd=repo.root)
            repo.write_plan_docs(plan_text="plan v2\n")  # audit/decisions unchanged
            worktree_manifest = wf.compute_review_content_manifest_plan_stage_worktree(repo.root)
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "revise"], cwd=repo.root)
            commit_manifest = wf.compute_review_content_manifest_plan_stage_commit(
                repo.root, repo.head()
            )
            self.assertEqual(worktree_manifest, commit_manifest)
            audit_entries = [e for e in commit_manifest if e["path"].endswith("AUDIT.md")]
            self.assertEqual(len(audit_entries), 1)
            self.assertTrue(audit_entries[0]["exists"])
            self.assertNotIn("status", audit_entries[0])

    def test_raw_diff_blob_is_never_a_usable_source_for_untracked_files(self):
        """Renamed per OPUS-R8-016: the previous name
        (`test_removing_hash_object_substitution_would_break_parity`)
        claimed to test parity, but the assertions only show the raw diff
        is empty and blobs are non-zero -- the actual parity guarantee is
        `test_complete_manifests_are_equal_after_approval_commit`, verified
        load-bearing by mutation (replacing `_hash_object` with a
        zero-blob stub fails four tests)."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            raw = subprocess.run(
                ["git", "diff", "--raw", "-z", "--find-renames", "--abbrev=40", repo.base],
                cwd=repo.root, check=True, capture_output=True, text=True,
            ).stdout
            self.assertEqual(raw, "")
            real_manifest = wf.compute_review_content_manifest_plan_stage_worktree(repo.root)
            for entry in real_manifest:
                if not entry["exists"]:
                    # The pre-declared, not-yet-written
                    # PLAN_REVIEW_WORKFLOW.md guide path (GPT-R11-008) has
                    # no blob to hash -- correctly None, not the assertion
                    # this test makes about real files' blobs.
                    continue
                self.assertIsNotNone(entry["blob"])
                self.assertNotEqual(entry["blob"], "0" * 40)


class TestCommitSourceClassification(unittest.TestCase):
    """OPUS-R8-005: the commit-source entry point must enforce the same
    fail-closed classification precondition as the worktree entry point."""

    def test_034_unclassified_path_at_approval_commit_fails_closed(self):
        """`app/` is now an excluded prefix (OPUS-R18-004); a genuinely
        novel path is used here instead so this still demonstrates
        fail-closed behavior."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            (repo.root / "totally_unmapped_surprise").mkdir()
            (repo.root / "totally_unmapped_surprise" / "unclassified.kt").write_text("surprise\n")
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "sneaks in an unclassified file"], cwd=repo.root)
            with self.assertRaises(wf.UnclassifiedPathError):
                wf.compute_review_content_id_plan_stage_at_commit(
                    repo.root, repo.base, repo.head(),
                    work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
                )

    def test_035_both_entry_points_reject_the_same_unclassified_input(self):
        """`app/` is now an excluded prefix (OPUS-R18-004); a genuinely
        novel path is used here instead so this still demonstrates
        fail-closed behavior."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            (repo.root / "totally_unmapped_surprise").mkdir()
            (repo.root / "totally_unmapped_surprise" / "unclassified.kt").write_text("surprise\n")
            with self.assertRaises(wf.UnclassifiedPathError):
                repo.compute()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "commit it"], cwd=repo.root)
            with self.assertRaises(wf.UnclassifiedPathError):
                wf.compute_review_content_id_plan_stage_at_commit(
                    repo.root, repo.base, repo.head(),
                    work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
                )


class TestFileModeParity(unittest.TestCase):
    """OPUS-R8-003: worktree/commit parity must hold under both
    `core.fileMode` settings, matching real Git semantics."""

    def test_028_parity_holds_under_core_fileMode_false_with_a_chmod_applied(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            _run(["git", "config", "core.fileMode", "false"], cwd=repo.root)
            plan_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md"
            os.chmod(plan_path, 0o755)
            worktree_id, worktree_projection = repo.compute()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "plan-approval"], cwd=repo.root)
            commit_id, commit_projection = wf.compute_review_content_id_plan_stage_at_commit(
                repo.root, repo.base, repo.head(),
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            self.assertEqual(
                worktree_projection["review_content_manifest"],
                commit_projection["review_content_manifest"],
            )
            self.assertEqual(worktree_id, commit_id)
            plan_entry = [
                e for e in worktree_projection["review_content_manifest"]
                if e["path"].endswith("PLAN.md")
            ][0]
            self.assertEqual(plan_entry["mode"], "100644")

    def test_029_review_content_id_equal_under_both_fileMode_settings_with_no_chmod(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            _run(["git", "config", "core.fileMode", "true"], cwd=repo.root)
            id_true, _ = repo.compute()
            _run(["git", "config", "core.fileMode", "false"], cwd=repo.root)
            id_false, _ = repo.compute()
            self.assertEqual(id_true, id_false)

    def test_030_genuine_mode_change_reflected_identically_under_fileMode_true(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            _run(["git", "config", "core.fileMode", "true"], cwd=repo.root)
            plan_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md"
            os.chmod(plan_path, 0o755)
            worktree_id, worktree_projection = repo.compute()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "plan-approval"], cwd=repo.root)
            commit_id, commit_projection = wf.compute_review_content_id_plan_stage_at_commit(
                repo.root, repo.base, repo.head(),
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            plan_entry = [
                e for e in worktree_projection["review_content_manifest"]
                if e["path"].endswith("PLAN.md")
            ][0]
            self.assertEqual(plan_entry["mode"], "100755")
            self.assertEqual(worktree_id, commit_id)
            self.assertEqual(
                worktree_projection["review_content_manifest"],
                commit_projection["review_content_manifest"],
            )


class TestSymlinkAndUnsupportedEntries(unittest.TestCase):
    """OPUS-R8-004: a symlink to a directory must fail closed; ordering
    (`is_symlink()` before `is_dir()`) is what makes that reachable."""

    def test_031_symlink_to_directory_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = tmp / "current"
            bundle.mkdir()
            (bundle / "MANIFEST.md").write_text("bundle_id: " + FAKE_ID_A + "\n")
            real_dir = bundle / "realdir"
            real_dir.mkdir()
            (real_dir / "secret.txt").write_text("hidden\n")
            (bundle / "linkdir").symlink_to(real_dir, target_is_directory=True)
            with self.assertRaises(wf.UnsupportedPathTypeError):
                wf.compute_bundle_id(bundle)

    def test_symlink_to_file_still_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = tmp / "current"
            bundle.mkdir()
            (bundle / "MANIFEST.md").write_text("bundle_id: " + FAKE_ID_A + "\n")
            (bundle / "REVIEW_REQUEST.md").write_text("stage: plan\n")
            (bundle / "sneaky_link").symlink_to(bundle / "REVIEW_REQUEST.md")
            with self.assertRaises(wf.UnsupportedPathTypeError):
                wf.compute_bundle_id(bundle)

    def test_032_fifo_entry_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = tmp / "current"
            bundle.mkdir()
            (bundle / "MANIFEST.md").write_text("bundle_id: " + FAKE_ID_A + "\n")
            os.mkfifo(bundle / "a_fifo")
            with self.assertRaises(wf.UnsupportedPathTypeError):
                wf.compute_bundle_id(bundle)

    def test_033_real_nested_directory_still_contributes_its_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = tmp / "current"
            bundle.mkdir()
            (bundle / "MANIFEST.md").write_text("bundle_id: " + FAKE_ID_A + "\n")
            nested = bundle / "files" / "docs"
            nested.mkdir(parents=True)
            (nested / "a.md").write_text("content\n")
            _, entries = wf.compute_bundle_id(bundle)
            self.assertIn("files/docs/a.md", entries)


class TestExcludedFieldWriteDoesNotAffectIdentity(unittest.TestCase):
    def test_093_state_file_write_does_not_change_review_content_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest1, _ = repo.compute()
            (repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json").write_text(
                '{"plan_approval": {"status": "CURRENT"}}\n'
            )
            digest2, _ = repo.compute()
            self.assertEqual(digest1, digest2)


class TestRegistryAndMappingAreProtected(unittest.TestCase):
    """GPT-R9-003, missing-test items 6-7: the registry and requirements-
    mapping JSON files are protected -- editing either changes
    review_content_id, so plan approval cannot be granted over one version
    and silently retargeted at another."""

    def test_editing_registry_json_changes_review_content_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest1, _ = repo.compute()
            repo.write_plan_docs(registry_text='{"checkpoints": [{"id": "WF1a"}]}\n')
            digest2, _ = repo.compute()
            self.assertNotEqual(digest1, digest2)

    def test_editing_mapping_json_changes_review_content_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest1, _ = repo.compute()
            repo.write_plan_docs(mapping_text='{"requirements": {"WFR-01": {}}}\n')
            digest2, _ = repo.compute()
            self.assertNotEqual(digest1, digest2)


class TestLedgerDocIsExcludedFromPlanStageIdentity(unittest.TestCase):
    """WF4b, missing-test item 6: the mutable
    `workflow-v2-1-core-ledger.md` execution ledger (D4b) is physically
    separate from the protected, machine-readable
    `workflow-v2-1-core-mapping.json` -- creating or editing the ledger
    doc must never change plan-stage `review_content_id`, since it is
    appended to on every checkpoint completion and must never stale a
    plan approval or require a fresh review round."""

    def _ledger_path(self, repo):
        return (
            repo.root / "docs" / "ai-workflow" / "requirements"
            / "workflow-v2-1-core-ledger.md"
        )

    def test_creating_ledger_doc_does_not_change_review_content_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest1, _ = repo.compute()
            self._ledger_path(repo).write_text("# ledger v1\n")
            digest2, _ = repo.compute()
            self.assertEqual(digest1, digest2)

    def test_editing_ledger_doc_does_not_change_review_content_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            self._ledger_path(repo).write_text("# ledger v1\n")
            digest1, _ = repo.compute()
            self._ledger_path(repo).write_text("# ledger v1\n\nappended entry\n")
            digest2, _ = repo.compute()
            self.assertEqual(digest1, digest2)


class TestIdentityScalars(unittest.TestCase):
    """OPUS-R8-010: no identity-bearing scalar has a default; `work_item_id`
    is slug-validated; a differing declared revision changes the ID even
    when file content is identical."""

    def test_043_missing_scalar_raises(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            with self.assertRaises(TypeError):
                wf.compute_review_content_id_plan_stage(
                    repo.root, repo.base,
                    work_item_type="process", work_item_id="workflow-v2-1-core",
                )  # plan_revision omitted

    def test_044_invalid_work_item_id_slug_fails_closed(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            for bad_id in ["Has-Upper", "has space", "../traversal", "", "a" * 65]:
                with self.subTest(bad_id=bad_id):
                    with self.assertRaises(wf.InvalidWorkItemIdError):
                        repo.compute(work_item_id=bad_id)

    def test_two_documents_same_content_different_declared_revision_differ(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest_rev7, _ = repo.compute(plan_revision=7)
            digest_rev8, _ = repo.compute(plan_revision=8)
            self.assertNotEqual(digest_rev7, digest_rev8)

    def test_gpt_r9_014_invalid_work_item_type_rejected(self):
        """Missing-test item 25: work_item_type must be one of the
        controlled vocabulary, checked before hashing."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            for bad_type in ["Process", "PRODUCT", "synthetic", "process ", ""]:
                with self.subTest(bad_type=bad_type):
                    with self.assertRaises(wf.InvalidWorkItemTypeError):
                        repo.compute(work_item_type=bad_type)
            # valid values are accepted
            repo.compute(work_item_type="process")
            repo.compute(work_item_type="product")


class TestWF8bSyntheticWorkItemIdCanonicalization(unittest.TestCase):
    """WF8b entry setup found the plan's literal synthetic work-item name
    (`v2.1-dry-run`, D-Self-Governance/D1) violates D1's own
    `WORK_ITEM_ID_RE` grammar (no dot allowed) -- both are already-approved
    protected-path sources. Resolved by canonicalizing the machine
    `work_item_id` to `v2-1-dry-run` without widening the grammar; this
    pins both halves of that decision so a future edit can't silently
    re-admit the dotted literal or reject the canonical one."""

    def test_canonical_synthetic_id_is_accepted(self):
        wf.validate_work_item_id("v2-1-dry-run")  # must not raise

    def test_dotted_literal_from_plan_prose_remains_rejected(self):
        with self.assertRaises(wf.InvalidWorkItemIdError):
            wf.validate_work_item_id("v2.1-dry-run")


class TestProtectedAndExclusionSetsInProjection(unittest.TestCase):
    """OPUS-R8-014: the protected and exclusion sets are themselves part of
    the hashed projection."""

    def test_049_protected_set_edit_with_unchanged_content_changes_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            digest1, _ = repo.compute()
            shrunk_protected = frozenset(
                p for p in wf.PLAN_STAGE_PROTECTED if not p.endswith("AUDIT.md")
            )
            digest2, _ = repo.compute(protected=shrunk_protected)
            self.assertNotEqual(digest1, digest2)

    def test_exclusion_list_edit_changes_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest1, _ = repo.compute()
            from types import MappingProxyType
            shrunk_excluded = MappingProxyType(
                {k: v for k, v in wf.PLAN_STAGE_EXCLUDED_PATHS.items() if k != ".gitignore"}
            )
            digest2, _ = repo.compute(excluded_paths=shrunk_excluded)
            self.assertNotEqual(digest1, digest2)

    def test_050_removed_protected_path_distinguishable_from_deleted_file(self):
        """Corrected per OPUS-R14-001/-006: a plan-stage protected path
        that still exists (Case 1: removed from the protected set) and one
        that no longer exists while still protected (Case 2: deleted) are
        distinguishable -- but Case 2 now fails closed rather than
        producing a silent `exists: False` tombstone, since a plan-stage
        protected path is always supposed to be real, reviewable content."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            _, projection_with_all = repo.compute()
            self.assertEqual(len(projection_with_all["review_content_manifest"]), 5)

            # Case 1: path removed from the protected set entirely -- it
            # simply doesn't appear in the manifest, no error.
            shrunk_protected = frozenset(
                p for p in wf.PLAN_STAGE_PROTECTED if not p.endswith("AUDIT.md")
            )
            _, projection_shrunk_set = repo.compute(protected=shrunk_protected)
            self.assertEqual(len(projection_shrunk_set["review_content_manifest"]), 4)

            # Case 2: path stays protected, but the file itself is deleted
            # -- plan-stage computation fails closed, naming the path
            # (OPUS-R14-001/-006), rather than returning a usable identity
            # over content nobody can actually review.
            (repo.root / "docs" / "ai-workflow" / "WORKFLOW_V2_AUDIT.md").unlink()
            with self.assertRaises(wf.AbsentProtectedPathError) as ctx:
                repo.compute()
            self.assertIn("AUDIT.md", str(ctx.exception))

    def test_102_absent_protected_path_fails_closed_at_plan_stage(self):
        """Missing-test item 102 (OPUS-R14-001/-006): both the worktree-
        source and commit-source plan-stage manifest builders refuse to
        compute an identity over a protected-but-absent path, naming which
        path is missing -- and the two snapshot sources are independent
        (an uncommitted worktree deletion doesn't affect the commit read,
        and vice versa)."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            committed_head = repo.head()

            (repo.root / "docs" / "TECHNICAL_DECISIONS.md").unlink()
            with self.assertRaises(wf.AbsentProtectedPathError) as ctx_worktree:
                wf.compute_review_content_manifest_plan_stage_worktree(repo.root)
            self.assertIn("TECHNICAL_DECISIONS.md", str(ctx_worktree.exception))

            # The deletion above is uncommitted -- the commit-source read
            # of the still-intact prior commit is unaffected.
            manifest_commit = wf.compute_review_content_manifest_plan_stage_commit(
                repo.root, committed_head,
            )
            self.assertEqual(len(manifest_commit), 5)

            # Now commit the deletion and confirm the commit-source read
            # fails closed too, against the commit that actually lacks it.
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "delete decisions doc"], cwd=repo.root)
            deleted_head = repo.head()
            with self.assertRaises(wf.AbsentProtectedPathError) as ctx_commit:
                wf.compute_review_content_manifest_plan_stage_commit(
                    repo.root, deleted_head,
                )
            self.assertIn("TECHNICAL_DECISIONS.md", str(ctx_commit.exception))


class TestMutableDefaultArgumentSafety(unittest.TestCase):
    """OPUS-R8-015: shared defaults must be immutable, so no caller can
    corrupt them for every subsequent call."""

    def test_protected_and_exclusion_constants_are_immutable(self):
        self.assertIsInstance(wf.PLAN_STAGE_PROTECTED, frozenset)
        with self.assertRaises(AttributeError):
            wf.PLAN_STAGE_EXCLUDED_PATHS.update({"x": "y"})
        with self.assertRaises(AttributeError):
            wf.PLAN_STAGE_EXCLUDED_PREFIXES.update({"x": "y"})


class TestModeDetectionIsStatBased(unittest.TestCase):
    """GPT-R9-009, missing-test item 22: mode must come from the file's own
    stored mode bits, not from `os.access()`, which reflects the current
    process's *effective* access and can differ by user/ACL for identical
    stored bits."""

    def test_owner_executable_reads_stat_bits_directly(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            f = tmp / "a.txt"
            f.write_text("x\n")
            f.chmod(0o644)
            self.assertFalse(wf._owner_executable(f.stat().st_mode))
            f.chmod(0o744)
            self.assertTrue(wf._owner_executable(f.stat().st_mode))
            # Independent of os.access's *effective*-access semantics: the
            # function never calls os.access at all -- confirmed by
            # construction (grep the implementation), and here by checking
            # its result depends only on st_mode, not on the live
            # X_OK-effective-access call, which we deliberately do not
            # invoke or need for the assertions above.

    def test_bundle_entries_use_stat_based_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = tmp / "current"
            bundle.mkdir()
            (bundle / "MANIFEST.md").write_text("bundle_id: " + FAKE_ID_A + "\n")
            (bundle / "REVIEW_REQUEST.md").write_text("stage: plan\n")
            (bundle / "REVIEW_REQUEST.md").chmod(0o744)
            _, entries = wf.compute_bundle_id(bundle)
            self.assertEqual(entries["REVIEW_REQUEST.md"]["mode"], "100755")


class TestVerificationIsReadOnly(unittest.TestCase):
    """GPT-R9-010, missing-test item 23: importing/running this module to
    verify a bundle must never write files (bytecode) inside the bundle
    directory it is hashing."""

    def test_dont_write_bytecode_is_set_on_import(self):
        self.assertTrue(sys.dont_write_bytecode)

    def test_verifying_a_bundle_creates_no_new_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = tmp / "current"
            bundle.mkdir()
            (bundle / "MANIFEST.md").write_text("bundle_id: " + FAKE_ID_A + "\n")
            (bundle / "REVIEW_REQUEST.md").write_text("stage: plan\n")
            before = {p.relative_to(bundle).as_posix() for p in bundle.rglob("*")}
            wf.compute_bundle_id(bundle)
            wf.compute_bundle_id(bundle)
            after = {p.relative_to(bundle).as_posix() for p in bundle.rglob("*")}
            self.assertEqual(before, after)


class TestBundleId(unittest.TestCase):
    """PROTO-R7-004/006/007, OPUS-R8-001/002/004/009/013/017: bundle_id
    must exclude only the self-referential `bundle_id:` field wherever it
    appears, normalize only the true timestamp header line (failing closed
    if it's missing/duplicated), represent file mode, use POSIX-style
    relative paths, and require MANIFEST.md."""

    def _make_bundle(self, tmp: Path, generated="2026-07-28T18:31:55Z", bundle_id=None):
        bundle = tmp / "current"
        bundle.mkdir()
        (bundle / "REVIEW_REQUEST.md").write_text("stage: plan\n")
        (bundle / "CHANGED_FILES.txt").write_text(
            f"branch: x\nbase: y\nhead: z\nstage: plan\ngenerated: {generated}\n"
        )
        manifest_line = f"bundle_id: {bundle_id}\n" if bundle_id else ""
        (bundle / "MANIFEST.md").write_text(f"{manifest_line}protected_paths: [a.md, b.md]\n")
        return bundle

    def test_110_idempotent_across_regeneration(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            id1, _ = wf.compute_bundle_id(bundle)
            (bundle / "CHANGED_FILES.txt").write_text(
                "branch: x\nbase: y\nhead: z\nstage: plan\ngenerated: 2026-07-28T19:00:00Z\n"
            )
            id2, _ = wf.compute_bundle_id(bundle)
            self.assertEqual(id1, id2)

    def test_wrapper_word_edit_changes_bundle_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            id1, _ = wf.compute_bundle_id(bundle)
            (bundle / "REVIEW_REQUEST.md").write_text("stage: plan (revised)\n")
            id2, _ = wf.compute_bundle_id(bundle)
            self.assertNotEqual(id1, id2)

    def test_111_manifest_bundle_id_field_excluded_but_rest_of_file_is_not(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "MANIFEST.md").write_text(
                f"bundle_id: {FAKE_ID_A}\nprotected_paths: [a.md, b.md]\n"
            )
            id_before, _ = wf.compute_bundle_id(bundle)
            (bundle / "MANIFEST.md").write_text(
                f"bundle_id: {FAKE_ID_B}\nprotected_paths: [a.md, b.md]\n"
            )
            id_bundle_id_only_changed, _ = wf.compute_bundle_id(bundle)
            self.assertEqual(
                id_before, id_bundle_id_only_changed,
                "changing only the self-referential bundle_id field must not "
                "change the identifier it is excluded from",
            )
            (bundle / "MANIFEST.md").write_text(
                f"bundle_id: {FAKE_ID_B}\nprotected_paths: [a.md, b.md, c.md]\n"
            )
            id_other_field_changed, _ = wf.compute_bundle_id(bundle)
            self.assertNotEqual(
                id_before, id_other_field_changed,
                "changing non-ID manifest content must change the identifier "
                "(PROTO-R7-004: the whole file must not be excluded)",
            )

    def test_gpt_r9_001_a_top_level_bundle_id_field_outside_manifest_fails(self):
        """GPT-R9-001, missing-test item 1: only MANIFEST.md may report the
        bundle's own identity. Round 8's blanket exclusion made a wrong or
        stale `bundle_id:`-shaped line in TEST_RESULTS.md invisible to the
        identifier instead of flagged -- confirmed live in round 9's own
        reviewed bundle. It must now fail closed."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "TEST_RESULTS.md").write_text(f"All tests passed.\nbundle_id: {FAKE_ID_A}\n")
            with self.assertRaises(wf.ForeignBundleIdFieldError):
                wf.compute_bundle_id(bundle)

    def test_gpt_r9_001_item_2_changing_a_bundle_id_looking_line_still_fails(self):
        """Missing-test item 2: whether the specific hex value is stale,
        fresh, or all-zeroes, a conforming line outside MANIFEST.md always
        fails -- there is no value for which it is silently accepted."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "TEST_RESULTS.md").write_text(f"All tests passed.\nbundle_id: {FAKE_ID_A}\n")
            with self.assertRaises(wf.ForeignBundleIdFieldError):
                wf.compute_bundle_id(bundle)
            (bundle / "TEST_RESULTS.md").write_text(f"All tests passed.\nbundle_id: {'0' * 64}\n")
            with self.assertRaises(wf.ForeignBundleIdFieldError):
                wf.compute_bundle_id(bundle)

    def test_gpt_r9_001_item_3_only_the_manifest_self_field_is_excluded(self):
        """Missing-test item 3, positive case: MANIFEST.md's own field is
        still excluded (idempotent across a value-only change), while the
        exact same line in REVIEW_REQUEST.md or PLAN.md fails."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp, bundle_id=FAKE_ID_A)
            id1, _ = wf.compute_bundle_id(bundle)
            (bundle / "MANIFEST.md").write_text(
                f"bundle_id: {FAKE_ID_B}\nprotected_paths: [a.md, b.md]\n"
            )
            id2, _ = wf.compute_bundle_id(bundle)
            self.assertEqual(id1, id2, "MANIFEST.md's own field must still be excluded")
            for other in ("REVIEW_REQUEST.md", "PLAN.md"):
                with self.subTest(other=other):
                    (bundle / other).write_text(f"bundle_id: {FAKE_ID_A}\n")
                    with self.assertRaises(wf.ForeignBundleIdFieldError):
                        wf.compute_bundle_id(bundle)
                    (bundle / other).unlink()

    def test_023_bundle_missing_required_file_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = tmp / "current"
            bundle.mkdir()
            (bundle / "REVIEW_REQUEST.md").write_text("stage: plan\n")
            with self.assertRaises(wf.MissingRequiredBundleFileError):
                wf.compute_bundle_id(bundle)

    def test_025_lf_vs_crlf_variant_of_same_logical_file_differ(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "REVIEW_REQUEST.md").write_bytes(b"line one\nline two\n")
            id_lf, _ = wf.compute_bundle_id(bundle)
            (bundle / "REVIEW_REQUEST.md").write_bytes(b"line one\r\nline two\r\n")
            id_crlf, _ = wf.compute_bundle_id(bundle)
            self.assertNotEqual(id_lf, id_crlf)

    def test_026_trailing_newline_presence_or_absence_differ(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "REVIEW_REQUEST.md").write_bytes(b"stage: plan\n")
            id_with_nl, _ = wf.compute_bundle_id(bundle)
            (bundle / "REVIEW_REQUEST.md").write_bytes(b"stage: plan")
            id_without_nl, _ = wf.compute_bundle_id(bundle)
            self.assertNotEqual(id_with_nl, id_without_nl)

    def test_027_non_utf8_byte_difference_is_preserved_not_collided(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "CHANGED_FILES.txt").write_bytes(
                b"branch: x\nbase: y\nhead: z\nstage: plan\n"
                b"generated: 2026-07-28T18:31:55Z\n\npath-with-byte: \xff\n"
            )
            id_ff, _ = wf.compute_bundle_id(bundle)
            (bundle / "CHANGED_FILES.txt").write_bytes(
                b"branch: x\nbase: y\nhead: z\nstage: plan\n"
                b"generated: 2026-07-28T18:31:55Z\n\npath-with-byte: \xfe\n"
            )
            id_fe, _ = wf.compute_bundle_id(bundle)
            self.assertNotEqual(id_ff, id_fe)

    def test_normalizer_never_raises_on_non_utf8_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "REVIEW_REQUEST.md").write_bytes(b"stage: plan\n\xff\xfe binary noise\n")
            # must not raise
            wf.compute_bundle_id(bundle)

    def test_generated_prefixed_content_outside_header_line_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "CHANGED_FILES.txt").write_text(
                "branch: x\nbase: y\nhead: z\nstage: plan\n"
                "generated: 2026-07-28T18:31:55Z\n\n"
                "generated: this line is real evidence, not the timestamp\n"
            )
            id1, _ = wf.compute_bundle_id(bundle)
            (bundle / "CHANGED_FILES.txt").write_text(
                "branch: x\nbase: y\nhead: z\nstage: plan\n"
                "generated: 2026-07-28T19:00:00Z\n\n"
                "generated: this line is real evidence, not the timestamp\n"
            )
            id2, _ = wf.compute_bundle_id(bundle)
            self.assertEqual(id1, id2)
            (bundle / "CHANGED_FILES.txt").write_text(
                "branch: x\nbase: y\nhead: z\nstage: plan\n"
                "generated: 2026-07-28T19:00:00Z\n\n"
                "generated: this line CHANGED and must affect identity\n"
            )
            id3, _ = wf.compute_bundle_id(bundle)
            self.assertNotEqual(id2, id3)

    def test_041_header_with_timestamp_at_shifted_position_still_normalizes(self):
        """OPUS-R8-009: the header is found by searching the header block
        for the field, not by a fixed line index, so a header gaining a
        field before `generated:` still normalizes correctly."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "CHANGED_FILES.txt").write_text(
                "branch: x\nbase: y\nhead: z\nstage: plan\n"
                "schema_version: 2\ngenerated: 2026-07-28T18:31:55Z\n"
            )
            id1, _ = wf.compute_bundle_id(bundle)
            (bundle / "CHANGED_FILES.txt").write_text(
                "branch: x\nbase: y\nhead: z\nstage: plan\n"
                "schema_version: 2\ngenerated: 2026-07-28T19:00:00Z\n"
            )
            id2, _ = wf.compute_bundle_id(bundle)
            self.assertEqual(id1, id2)

    def test_042_header_with_no_generated_line_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "CHANGED_FILES.txt").write_text("branch: x\nbase: y\nhead: z\nstage: plan\n")
            with self.assertRaises(wf.MissingGeneratedTimestampError):
                wf.compute_bundle_id(bundle)

    def test_042b_header_with_duplicated_generated_line_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "CHANGED_FILES.txt").write_text(
                "branch: x\nbase: y\nhead: z\n"
                "generated: 2026-07-28T18:31:55Z\ngenerated: 2026-07-28T19:00:00Z\n"
            )
            with self.assertRaises(wf.DuplicateGeneratedTimestampError):
                wf.compute_bundle_id(bundle)

    def test_047_manifest_id_line_in_non_conforming_spelling_is_not_stripped(self):
        """OPUS-R8-013: a spelling other than the exact contract
        (`bundle_id: <64-hex-chars>`) is not recognized as the
        self-reference field -- it is ordinary content, so it participates
        in the hash like anything else."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "MANIFEST.md").write_text("- **Bundle ID**: " + FAKE_ID_A + "\n")
            id1, _ = wf.compute_bundle_id(bundle)
            (bundle / "MANIFEST.md").write_text("- **Bundle ID**: " + FAKE_ID_B + "\n")
            id2, _ = wf.compute_bundle_id(bundle)
            self.assertNotEqual(
                id1, id2,
                "a non-conforming spelling must not be treated as the "
                "self-reference field",
            )

    def test_048_duplicated_conforming_id_line_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "MANIFEST.md").write_text(
                f"bundle_id: {FAKE_ID_A}\nbundle_id: {FAKE_ID_B}\n"
            )
            with self.assertRaises(wf.MalformedBundleIdFieldError):
                wf.compute_bundle_id(bundle)

    def test_conforming_manifest_round_trips_write_compute_rewrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            (bundle / "MANIFEST.md").write_text(
                wf.render_manifest_md(
                    review_content_id="c" * 64,
                    protected=wf.PLAN_STAGE_PROTECTED,
                    excluded_paths=wf.PLAN_STAGE_EXCLUDED_PATHS,
                    excluded_prefixes=wf.PLAN_STAGE_EXCLUDED_PREFIXES,
                )
            )
            bid, _ = wf.compute_bundle_id(bundle)
            (bundle / "MANIFEST.md").write_text(
                wf.render_manifest_md(
                    review_content_id="c" * 64,
                    protected=wf.PLAN_STAGE_PROTECTED,
                    excluded_paths=wf.PLAN_STAGE_EXCLUDED_PATHS,
                    excluded_prefixes=wf.PLAN_STAGE_EXCLUDED_PREFIXES,
                    bundle_id=bid,
                )
            )
            bid_after_rewrite, _ = wf.compute_bundle_id(bundle)
            self.assertEqual(bid, bid_after_rewrite)

    def test_bundle_entry_keys_use_posix_separator(self):
        """OPUS-R8-017: nested paths must be keyed with forward slashes."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            nested = bundle / "files" / "docs" / "ai-workflow"
            nested.mkdir(parents=True)
            (nested / "WORKFLOW_V2_PLAN.md").write_text("x\n")
            _, entries = wf.compute_bundle_id(bundle)
            self.assertIn("files/docs/ai-workflow/WORKFLOW_V2_PLAN.md", entries)
            self.assertNotIn("\\", "".join(entries.keys()))

    def test_empty_directory_is_invisible_to_bundle_identity(self):
        """OPUS-R8-018, documented explicitly rather than silently true:
        a bundle differing only by an empty directory has the same ID."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bundle = self._make_bundle(tmp)
            id_before, _ = wf.compute_bundle_id(bundle)
            (bundle / "an_empty_dir").mkdir()
            id_after, _ = wf.compute_bundle_id(bundle)
            self.assertEqual(id_before, id_after)


class TestGeneratorWriteDiscipline(unittest.TestCase):
    """OPUS-R18-001/-002: the generator around the frozen identity
    algorithm -- write_manifest_with_verified_identifiers and __main__ --
    must be as safe as the algorithm itself. Missing-test items 126-130."""

    def test_126_protected_path_edit_between_compute_and_recompute_is_caught(self):
        """Missing-test item 126: simulates the exact PROTO-R7-004-class
        defect OPUS-R18-001 found -- a protected-path edit landing between
        the first review_content_id computation and the final
        recompute-and-assert step must be caught, not silently written."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            # Outside repo.root: a bundle directory nested inside the
            # scratch repo's own working tree would itself be an
            # untracked path the classifier has to see (the real
            # .ai-review/ is gitignored; this scratch repo has no such
            # entry), which is incidental to what these tests check.
            bundle_dir = Path(tempfile.mkdtemp(prefix="wf-fingerprint-bundle-"))
            self.addCleanup(shutil.rmtree, bundle_dir, ignore_errors=True)
            _write_review_request_with_content_id(repo, bundle_dir)

            real_compute = wf.compute_review_content_id_plan_stage
            call_count = {"n": 0}

            def fake_compute(*args, **kwargs):
                call_count["n"] += 1
                result = real_compute(*args, **kwargs)
                if call_count["n"] == 1:
                    (repo.root / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md").write_text(
                        "plan v2 -- edited mid-flight\n"
                    )
                return result

            with mock.patch.object(wf, "compute_review_content_id_plan_stage", side_effect=fake_compute):
                with self.assertRaises(wf.ReviewContentIdNotIdempotentError):
                    wf.write_manifest_with_verified_identifiers(
                        repo.root, bundle_dir, repo.base,
                        work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
                    )

    def test_127_double_generation_idempotent_for_both_identifiers(self):
        """Missing-test item 127: regenerating with no content change must
        be idempotent for review_content_id too, not only bundle_id."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            # Outside repo.root: a bundle directory nested inside the
            # scratch repo's own working tree would itself be an
            # untracked path the classifier has to see (the real
            # .ai-review/ is gitignored; this scratch repo has no such
            # entry), which is incidental to what these tests check.
            bundle_dir = Path(tempfile.mkdtemp(prefix="wf-fingerprint-bundle-"))
            self.addCleanup(shutil.rmtree, bundle_dir, ignore_errors=True)
            _write_review_request_with_content_id(repo, bundle_dir)
            digest1, bundle_id1 = wf.write_manifest_with_verified_identifiers(
                repo.root, bundle_dir, repo.base,
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            digest2, bundle_id2 = wf.write_manifest_with_verified_identifiers(
                repo.root, bundle_dir, repo.base,
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            self.assertEqual(digest1, digest2)
            self.assertEqual(bundle_id1, bundle_id2)

    def test_128_read_only_inspection_leaves_bundle_directory_byte_identical(self):
        """Missing-test item 128: the read-only operations __main__'s
        default invocation performs (recompute bundle_id, read existing
        manifest fields) must not modify anything under the bundle
        directory -- OPUS-R18-002's core acceptance criterion."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            # Outside repo.root: a bundle directory nested inside the
            # scratch repo's own working tree would itself be an
            # untracked path the classifier has to see (the real
            # .ai-review/ is gitignored; this scratch repo has no such
            # entry), which is incidental to what these tests check.
            bundle_dir = Path(tempfile.mkdtemp(prefix="wf-fingerprint-bundle-"))
            self.addCleanup(shutil.rmtree, bundle_dir, ignore_errors=True)
            _write_review_request_with_content_id(repo, bundle_dir)
            wf.write_manifest_with_verified_identifiers(
                repo.root, bundle_dir, repo.base,
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            before = {
                p.relative_to(bundle_dir).as_posix(): p.read_bytes()
                for p in bundle_dir.rglob("*") if p.is_file()
            }
            # Exactly the operations the CLI's default (no --write-manifest) branch performs.
            wf.read_manifest_identifiers(bundle_dir / "MANIFEST.md")
            wf.compute_bundle_id(bundle_dir)
            after = {
                p.relative_to(bundle_dir).as_posix(): p.read_bytes()
                for p in bundle_dir.rglob("*") if p.is_file()
            }
            self.assertEqual(before, after)

    def test_129_manifest_writing_requires_explicit_named_invocation(self):
        """Missing-test item 129 (OPUS-R18-002): the CLI must expose a
        separate, explicitly-named, default-off flag for the one
        invocation that writes -- checked directly against the source,
        the governing artifact, per the same discipline item 123 used."""
        source = Path(wf.__file__).read_text()
        self.assertIn('"--write-manifest"', source)
        self.assertIn('action="store_true"', source)

    def test_130_main_contains_no_unconditional_write(self):
        """Missing-test item 130 (OPUS-R18-002): __main__ itself must
        never call .write_text() directly -- the only writer is
        write_manifest_with_verified_identifiers, reached solely through
        the --write-manifest branch."""
        source = Path(wf.__file__).read_text()
        main_block = source.split('if __name__ == "__main__":', 1)[1]
        self.assertNotIn(".write_text(", main_block)


class TestPlanRevisionFromRegistry(unittest.TestCase):
    """OPUS-R18-003: plan_revision must be sourced from the registry JSON,
    not a caller-supplied literal, and must agree with the plan's own
    declared title. Missing-test items 131-132."""

    def test_131_plan_revision_loaded_from_registry_not_a_literal(self):
        with ScratchRepo() as repo:
            registry_text = json.dumps({
                "schema_version": 1, "work_item_id": "workflow-v2-1-core",
                "plan_revision": 9, "checkpoints": [],
            }) + "\n"
            repo.write_plan_docs(
                plan_text="# Some Process Doc (Revision 9)\n",
                registry_text=registry_text,
            )
            revision = wf.load_plan_revision(
                repo.root,
                registry_path=Path("docs/ai-workflow/registry/workflow-v2-1-core-registry.json"),
                plan_path=Path("docs/ai-workflow/WORKFLOW_V2_PLAN.md"),
            )
            self.assertEqual(revision, 9)
        source = Path(wf.__file__).read_text()
        main_block = source.split('if __name__ == "__main__":', 1)[1]
        self.assertNotRegex(main_block, r"plan_revision\s*=\s*\d+")

    def test_132_registry_title_disagreement_fails_generation(self):
        with ScratchRepo() as repo:
            registry_text = json.dumps({
                "schema_version": 1, "work_item_id": "workflow-v2-1-core",
                "plan_revision": 5, "checkpoints": [],
            }) + "\n"
            repo.write_plan_docs(
                plan_text="# Some Process Doc (Revision 6)\n",
                registry_text=registry_text,
            )
            with self.assertRaises(wf.PlanRevisionMismatchError):
                wf.load_plan_revision(
                    repo.root,
                    registry_path=Path("docs/ai-workflow/registry/workflow-v2-1-core-registry.json"),
                    plan_path=Path("docs/ai-workflow/WORKFLOW_V2_PLAN.md"),
                )


class TestWidenedConcurrentWriteClosure(unittest.TestCase):
    """OPUS-R18-004: the concurrent-write classification closure widened
    to app/, docs/adr/, docs/agent-context/, docs/improvements/, gradle/,
    config/, .github/, and five top-level product docs must not raise or
    change review_content_id, while a genuinely novel path still fails
    closed. Missing-test items 133-135. docs/improvements/ was added
    after Milestone 8's FUNCTIONAL_FEATURE_AUDIT.md landed on the
    workflow-v2-1-core branch as a genuinely novel concurrent-write
    path the durability guard could not classify."""

    def test_133_concurrent_product_code_write_mid_sequence_does_not_raise_or_change_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest_before, _ = repo.compute()
            concurrent_writes = [
                ("app/src/main/kotlin/com/repflow/Foo.kt", "class Foo\n"),
                ("app/src/test/kotlin/com/repflow/FooTest.kt", "class FooTest\n"),
            ]
            for i, (path_str, content) in enumerate(concurrent_writes):
                path = repo.root / path_str
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                _run(["git", "add", "-A"], cwd=repo.root)
                _run(["git", "commit", "-q", "-m", f"functional fix {i}: {path_str}"], cwd=repo.root)
                digest_after, _ = repo.compute()
                self.assertEqual(
                    digest_before, digest_after,
                    f"review_content_id changed after a concurrent product-code write to {path_str}",
                )

    def test_134_concurrent_product_doc_write_mid_sequence_does_not_raise_or_change_id(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            digest_before, _ = repo.compute()
            concurrent_writes = [
                ("AGENTS.md", "agent instructions\n"),
                ("docs/DOMAIN_GLOSSARY.md", "glossary\n"),
                ("docs/adr/0099-new-decision.md", "adr\n"),
                ("docs/improvements/FUNCTIONAL_FEATURE_AUDIT.md", "audit\n"),
                ("gradle/libs.versions.toml", "[versions]\n"),
                ("config/detekt/detekt.yml", "rules: {}\n"),
                (".github/copilot-instructions.md", "instructions\n"),
            ]
            for i, (path_str, content) in enumerate(concurrent_writes):
                path = repo.root / path_str
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                _run(["git", "add", "-A"], cwd=repo.root)
                _run(["git", "commit", "-q", "-m", f"concurrent doc write {i}: {path_str}"], cwd=repo.root)
                digest_after, _ = repo.compute()
                self.assertEqual(
                    digest_before, digest_after,
                    f"review_content_id changed after a concurrent product-doc write to {path_str}",
                )

    def test_135_novel_path_still_fails_closed_after_widened_lists(self):
        with self.assertRaises(wf.UnclassifiedPathError):
            wf.classify_path(
                "yet_another_new_thing/mystery.bin", wf.PLAN_STAGE_PROTECTED,
                wf.PLAN_STAGE_EXCLUDED_PATHS, wf.PLAN_STAGE_EXCLUDED_PREFIXES,
            )


class TestReviewRequestReviewContentIdAgreement(unittest.TestCase):
    """OPUS-R18-005: REVIEW_REQUEST.md and MANIFEST.md must state the same
    review_content_id; disagreement or absence must fail generation.
    Missing-test item 137."""

    def test_137_review_request_and_manifest_review_content_id_agreement(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            digest = "d" * 64

            (bundle_dir / "REVIEW_REQUEST.md").write_text(f"stage: plan\nreview_content_id: {digest}\n")
            wf.assert_review_request_states_review_content_id(bundle_dir, digest)  # must not raise

            (bundle_dir / "REVIEW_REQUEST.md").write_text(f"stage: plan\nreview_content_id: {'e' * 64}\n")
            with self.assertRaises(wf.ReviewContentIdMismatchError):
                wf.assert_review_request_states_review_content_id(bundle_dir, digest)

            (bundle_dir / "REVIEW_REQUEST.md").write_text("stage: plan\n")
            with self.assertRaises(wf.MissingReviewContentIdStatementError):
                wf.assert_review_request_states_review_content_id(bundle_dir, digest)


class TestAtomicManifestWrite(unittest.TestCase):
    """OPUS-R20-001: the write sequence itself must be atomic across the
    idempotence check, not only across the pre-write REVIEW_REQUEST.md
    check -- missing-test item 138."""

    def test_138_protected_path_edit_mid_write_leaves_manifest_untouched(self):
        """A protected-path edit landing between the first
        review_content_id computation and the final recompute-and-assert
        step must still raise (test_126 already covers that), but now
        MANIFEST.md itself must be byte-identical to its pre-invocation
        state afterward -- proof that nothing was written before every
        check passed, not merely that the written value was flagged
        stale."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            bundle_dir = Path(tempfile.mkdtemp(prefix="wf-fingerprint-bundle-"))
            self.addCleanup(shutil.rmtree, bundle_dir, ignore_errors=True)
            _write_review_request_with_content_id(repo, bundle_dir)

            # Establish a real pre-existing MANIFEST.md (a prior, valid
            # generation) so there is real "pre-invocation state" to prove
            # untouched, not just an absent file.
            wf.write_manifest_with_verified_identifiers(
                repo.root, bundle_dir, repo.base,
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            manifest_path = bundle_dir / "MANIFEST.md"
            before = manifest_path.read_bytes()

            real_compute = wf.compute_review_content_id_plan_stage
            call_count = {"n": 0}

            def fake_compute(*args, **kwargs):
                call_count["n"] += 1
                result = real_compute(*args, **kwargs)
                if call_count["n"] == 1:
                    (repo.root / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md").write_text(
                        "plan v3 -- edited mid-flight\n"
                    )
                return result

            with mock.patch.object(wf, "compute_review_content_id_plan_stage", side_effect=fake_compute):
                with self.assertRaises(wf.ReviewContentIdNotIdempotentError):
                    wf.write_manifest_with_verified_identifiers(
                        repo.root, bundle_dir, repo.base,
                        work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
                    )

            after = manifest_path.read_bytes()
            self.assertEqual(before, after, "MANIFEST.md must be byte-identical to its pre-invocation state")
            # No stray temp file left behind either.
            leftovers = [p.name for p in bundle_dir.iterdir() if p.name != "MANIFEST.md" and p.name != "REVIEW_REQUEST.md"]
            self.assertEqual(leftovers, [], f"unexpected files left in bundle_dir: {leftovers}")

    def test_manifest_content_override_lets_bundle_id_be_computed_before_any_write(self):
        """compute_bundle_id's manifest_content_override must reproduce
        exactly the bundle_id an on-disk MANIFEST.md with the same bytes
        would have produced -- the mechanism the atomic write relies on."""
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            content = "# Bundle Manifest\n\nreview_content_id: " + ("a" * 64) + "\n"
            (bundle_dir / "OTHER.md").write_text("some other bundle file\n")

            bid_via_override, _ = wf.compute_bundle_id(bundle_dir, manifest_content_override=content.encode())

            (bundle_dir / "MANIFEST.md").write_text(content)
            bid_via_disk, _ = wf.compute_bundle_id(bundle_dir)

            self.assertEqual(bid_via_override, bid_via_disk)


class TestRootBuildFilesFailClosed(unittest.TestCase):
    """OPUS-R20-002: root-level build files deliberately stay unclassified
    at the plan stage and fail closed -- missing-test item 139."""

    def test_139_root_build_files_raise_unclassified_at_plan_stage(self):
        for path in (
            "build.gradle.kts", "settings.gradle.kts", "gradlew",
            "gradlew.bat", ".editorconfig", "Makefile",
        ):
            with self.subTest(path=path):
                with self.assertRaises(wf.UnclassifiedPathError):
                    wf.classify_path(
                        path, wf.PLAN_STAGE_PROTECTED,
                        wf.PLAN_STAGE_EXCLUDED_PATHS, wf.PLAN_STAGE_EXCLUDED_PREFIXES,
                    )


class TestImplementationStageClassification(unittest.TestCase):
    """OPUS-R20-003, D-Fingerprint's WF4a-i mandate: the implementation-
    stage classification projection is a new, independent mechanism, and
    must classify a representative source file oppositely from the
    plan-stage projection -- missing-test item 140."""

    def _write_artifacts_declarations(self, repo, **overrides):
        data = {
            "schema_version": 1,
            "protected_prefixes": {"app/": "source"},
            "protected_paths": {},
            "excluded_prefixes": {},
            "excluded_paths": {},
        }
        data.update(overrides)
        artifacts_dir = repo.root / "docs" / "ai-workflow" / "registry"
        artifacts_dir.mkdir(parents=True, exist_ok=True)
        (artifacts_dir / "workflow-v2-1-core-artifacts.json").write_text(json.dumps(data))
        # Committed immediately, mirroring the real repository's own
        # tracked artifacts file -- otherwise this write would itself be
        # an untracked path the classifier has to see, incidental to what
        # these tests check.
        _run(["git", "add", "-A"], cwd=repo.root)
        _run(["git", "commit", "-q", "-m", "declare implementation-stage artifacts"], cwd=repo.root)

    def test_140_plan_and_implementation_stage_classify_same_path_oppositely(self):
        path = "app/src/main/kotlin/com/repflow/Foo.kt"
        self.assertEqual(
            wf.classify_path(
                path, wf.PLAN_STAGE_PROTECTED,
                wf.PLAN_STAGE_EXCLUDED_PATHS, wf.PLAN_STAGE_EXCLUDED_PREFIXES,
            ),
            "excluded",
            "app/ is a PLAN_STAGE_EXCLUDED_PREFIXES entry -- not approval-critical before implementation exists",
        )
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            self._write_artifacts_declarations(repo)
            protected_paths, protected_prefixes, excluded_paths, excluded_prefixes = (
                wf.load_implementation_stage_classification(
                    repo.root, artifacts_path=Path("docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json")
                )
            )
            self.assertEqual(
                wf.classify_path_implementation_stage(
                    path, protected_paths, protected_prefixes, excluded_paths, excluded_prefixes
                ),
                "protected",
                "app/ must be protected at the implementation stage -- exactly the source content "
                "technical_approval binds to",
            )

    def test_unclassified_path_fails_closed_at_implementation_stage_too(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            self._write_artifacts_declarations(repo)
            protected_paths, protected_prefixes, excluded_paths, excluded_prefixes = (
                wf.load_implementation_stage_classification(
                    repo.root, artifacts_path=Path("docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json")
                )
            )
            with self.assertRaises(wf.UnclassifiedPathError):
                wf.classify_path_implementation_stage(
                    "yet_another_new_thing/mystery.bin",
                    protected_paths, protected_prefixes, excluded_paths, excluded_prefixes,
                )

    def test_implementation_stage_manifest_tombstones_a_deletion_instead_of_raising(self):
        """Unlike the plan-stage manifest builders (AbsentProtectedPathError,
        OPUS-R14-001/-006), an implementation-stage protected path that no
        longer exists must be represented as a tombstone entry -- a
        deletion is a real, representable change at this stage."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            self._write_artifacts_declarations(repo, protected_prefixes={"app/": "source"})
            (repo.root / "app").mkdir()
            (repo.root / "app" / "Foo.kt").write_text("class Foo\n")
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "add app/Foo.kt"], cwd=repo.root)
            base = repo.head()

            (repo.root / "app" / "Foo.kt").unlink()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "delete app/Foo.kt"], cwd=repo.root)

            protected_paths, protected_prefixes, excluded_paths, excluded_prefixes = (
                wf.load_implementation_stage_classification(
                    repo.root, artifacts_path=Path("docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json")
                )
            )
            manifest = wf.compute_review_content_manifest_implementation_stage_commit(
                repo.root, base, repo.head(),
                protected_paths, protected_prefixes, excluded_paths, excluded_prefixes,
            )
            self.assertEqual(manifest, [{"path": "app/Foo.kt", "exists": False, "mode": None, "blob": None}])

    def test_compute_review_content_id_implementation_stage_hashes_protected_changes(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            self._write_artifacts_declarations(repo)
            registry_exclusion = {"docs/ai-workflow/registry/": "artifact-declarations file itself"}
            digest_before, projection_before = wf.compute_review_content_id_implementation_stage(
                repo.root, repo.base, work_item_type="process", work_item_id="workflow-v2-1-core",
                protected_paths={}, protected_prefixes={"app/": "source"},
                excluded_paths={}, excluded_prefixes=registry_exclusion,
            )
            self.assertEqual(projection_before["review_content_manifest"], [])

            (repo.root / "app").mkdir()
            (repo.root / "app" / "Foo.kt").write_text("class Foo\n")
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "add app/Foo.kt"], cwd=repo.root)

            digest_after, projection_after = wf.compute_review_content_id_implementation_stage(
                repo.root, repo.base, work_item_type="process", work_item_id="workflow-v2-1-core",
                protected_paths={}, protected_prefixes={"app/": "source"},
                excluded_paths={}, excluded_prefixes=registry_exclusion,
            )
            self.assertNotEqual(digest_before, digest_after)
            self.assertEqual(
                [e["path"] for e in projection_after["review_content_manifest"]], ["app/Foo.kt"]
            )
            self.assertEqual(projection_after["stage"], "implementation")
            # reviewed_implementation_head is never derived from live HEAD
            # and hashed here -- folding it in would make review_content_id
            # change on every new commit regardless of content, which is
            # exactly what test_excluded_concurrent_write_does_not_change_
            # implementation_stage_id below proves must not happen.
            self.assertIsNone(projection_after["reviewed_implementation_head"])

    def test_excluded_concurrent_write_does_not_change_implementation_stage_id(self):
        """Mirrors TestWidenedConcurrentWriteClosure at the plan stage: a
        write to a path this work item's own artifacts declarations name
        as excluded must not raise or change review_content_id."""
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            self._write_artifacts_declarations(
                repo,
                protected_prefixes={},
                excluded_paths={"docs/ai-workflow/WORKFLOW_STATE.json": "runtime-mutable state"},
            )
            kwargs = dict(
                work_item_type="process", work_item_id="workflow-v2-1-core",
                protected_paths={}, protected_prefixes={},
                excluded_paths={"docs/ai-workflow/WORKFLOW_STATE.json": "runtime-mutable state"},
                excluded_prefixes={"docs/ai-workflow/registry/": "artifact-declarations file itself"},
            )
            digest_before, _ = wf.compute_review_content_id_implementation_stage(repo.root, repo.base, **kwargs)
            (repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json").write_text('{"phase": "IMPLEMENTING"}\n')
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "state write"], cwd=repo.root)
            digest_after, _ = wf.compute_review_content_id_implementation_stage(repo.root, repo.base, **kwargs)
            self.assertEqual(digest_before, digest_after)


class TestBundleLayoutResolver(unittest.TestCase):
    """WF5's `.ai-review/<work_item_id>/{current,feedback}/` relayout
    resolver, with the stated compatibility fallback (resolves
    `OPUS-R6-021`)."""

    def test_falls_back_to_flat_layout_when_scoped_dir_absent(self):
        with ScratchRepo() as repo:
            self.assertEqual(
                wf.resolve_bundle_dir(repo.root, "workflow-v2-1-core"),
                Path(".ai-review/current"),
            )
            self.assertEqual(
                wf.resolve_feedback_dir(repo.root, "workflow-v2-1-core"),
                Path(".ai-review/feedback"),
            )

    def test_prefers_scoped_layout_once_it_exists(self):
        with ScratchRepo() as repo:
            (repo.root / ".ai-review" / "workflow-v2-1-core" / "current").mkdir(parents=True)
            (repo.root / ".ai-review" / "workflow-v2-1-core" / "feedback").mkdir(parents=True)
            self.assertEqual(
                wf.resolve_bundle_dir(repo.root, "workflow-v2-1-core"),
                Path(".ai-review/workflow-v2-1-core/current"),
            )
            self.assertEqual(
                wf.resolve_feedback_dir(repo.root, "workflow-v2-1-core"),
                Path(".ai-review/workflow-v2-1-core/feedback"),
            )

    def test_two_work_items_resolve_independently(self):
        with ScratchRepo() as repo:
            (repo.root / ".ai-review" / "milestone-8" / "current").mkdir(parents=True)
            self.assertEqual(
                wf.resolve_bundle_dir(repo.root, "milestone-8"),
                Path(".ai-review/milestone-8/current"),
            )
            self.assertEqual(
                wf.resolve_bundle_dir(repo.root, "workflow-v2-1-core"),
                Path(".ai-review/current"),
            )

    def test_rejects_invalid_work_item_id(self):
        with ScratchRepo() as repo:
            with self.assertRaises(wf.InvalidWorkItemIdError):
                wf.resolve_bundle_dir(repo.root, "Not_A_Valid_Slug!")


class TestGenerationDiagnosticMetadata(unittest.TestCase):
    """`worktree_root`/`generation_head` recorded in `MANIFEST.md` as
    diagnostic metadata, and the repository-local-only staleness check
    that reads them back (resolves `OPUS-R6-016`, `WFR-17`)."""

    def _bundle_dir(self, repo):
        # Outside repo.root, same reasoning as TestManifestWriteSafety
        # above: the real .ai-review/ is gitignored, this scratch repo has
        # no such entry, and that is incidental to what these tests check.
        bundle_dir = Path(tempfile.mkdtemp(prefix="wf-fingerprint-bundle-"))
        self.addCleanup(shutil.rmtree, bundle_dir, ignore_errors=True)
        return bundle_dir

    def test_write_manifest_records_current_worktree_root_and_head(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            bundle_dir = self._bundle_dir(repo)
            _write_review_request_with_content_id(repo, bundle_dir)
            wf.write_manifest_with_verified_identifiers(
                repo.root, bundle_dir, repo.base,
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            meta = wf.read_manifest_generation_metadata(bundle_dir / "MANIFEST.md")
            expected_root, expected_head = wf.current_worktree_root_and_head(repo.root)
            self.assertEqual(meta["worktree_root"], expected_root)
            self.assertEqual(meta["generation_head"], expected_head)

    def test_local_generation_check_passes_in_the_generating_worktree(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            bundle_dir = self._bundle_dir(repo)
            _write_review_request_with_content_id(repo, bundle_dir)
            wf.write_manifest_with_verified_identifiers(
                repo.root, bundle_dir, repo.base,
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            wf.assert_local_generation_matches(repo.root, bundle_dir / "MANIFEST.md")

    def test_local_generation_check_stops_on_worktree_root_mismatch(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            bundle_dir = self._bundle_dir(repo)
            _write_review_request_with_content_id(repo, bundle_dir)
            wf.write_manifest_with_verified_identifiers(
                repo.root, bundle_dir, repo.base,
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            manifest_path = bundle_dir / "MANIFEST.md"
            content = manifest_path.read_text()
            content = wf._WORKTREE_ROOT_LINE_RE.sub("worktree_root: /some/other/worktree", content)
            manifest_path.write_text(content)
            with self.assertRaises(wf.WorktreeOrHeadMismatchError):
                wf.assert_local_generation_matches(repo.root, manifest_path)

    def test_local_generation_check_stops_on_head_mismatch(self):
        with ScratchRepo() as repo:
            repo.write_plan_docs()
            repo.commit_plan_docs_as_base()
            bundle_dir = self._bundle_dir(repo)
            _write_review_request_with_content_id(repo, bundle_dir)
            wf.write_manifest_with_verified_identifiers(
                repo.root, bundle_dir, repo.base,
                work_item_type="process", work_item_id="workflow-v2-1-core", plan_revision=7,
            )
            manifest_path = bundle_dir / "MANIFEST.md"
            content = manifest_path.read_text()
            fake_head = "f" * 40
            content = wf._GENERATION_HEAD_LINE_RE.sub(f"generation_head: {fake_head}", content)
            manifest_path.write_text(content)
            with self.assertRaises(wf.WorktreeOrHeadMismatchError):
                wf.assert_local_generation_matches(repo.root, manifest_path)

    def test_missing_manifest_metadata_is_not_a_local_mismatch(self):
        """An older bundle generated before WF5 landed has no
        worktree_root/generation_head lines at all -- absence is not
        itself a mismatch, since there is nothing to compare against."""
        with ScratchRepo() as repo:
            bundle_dir = self._bundle_dir(repo)
            (bundle_dir / "MANIFEST.md").write_text("# Bundle Manifest\n\nreview_content_id: " + "a" * 64 + "\n")
            wf.assert_local_generation_matches(repo.root, bundle_dir / "MANIFEST.md")


class TestStageCompletenessCheck(unittest.TestCase):
    """`assert_stage_completeness`: a bundle's own author-written stage
    document must state the revision the authoritative source currently
    declares (unchanged design from round 5's `R5-PLAN-015`)."""

    def test_plan_stage_passes_when_revision_matches(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "PLAN.md").write_text("# Plan (Revision 7)\n\ncontent\n")
            wf.assert_stage_completeness(bundle_dir, "plan", plan_revision=7)

    def test_plan_stage_fails_on_stale_revision(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "PLAN.md").write_text("# Plan (Revision 6)\n\ncontent\n")
            with self.assertRaises(wf.StageCompletenessError):
                wf.assert_stage_completeness(bundle_dir, "plan", plan_revision=7)

    def test_plan_stage_fails_when_marker_absent(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "PLAN.md").write_text("# Plan\n\ncontent, no revision marker\n")
            with self.assertRaises(wf.StageCompletenessError):
                wf.assert_stage_completeness(bundle_dir, "plan", plan_revision=7)

    def test_implementation_stage_passes_when_revision_matches(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "IMPLEMENTATION_SUMMARY.md").write_text(
                "implementation_revision: 3\n\nwhat was built\n"
            )
            wf.assert_stage_completeness(bundle_dir, "implementation", implementation_revision=3)
            wf.assert_stage_completeness(bundle_dir, "post-fix", implementation_revision=3)

    def test_implementation_stage_fails_on_stale_revision(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "IMPLEMENTATION_SUMMARY.md").write_text(
                "implementation_revision: 2\n\nwhat was built\n"
            )
            with self.assertRaises(wf.StageCompletenessError):
                wf.assert_stage_completeness(bundle_dir, "implementation", implementation_revision=3)

    def test_functional_review_stage_is_a_noop(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            wf.assert_stage_completeness(bundle_dir, "functional-review")

    def test_unknown_stage_raises(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            with self.assertRaises(wf.StageCompletenessError):
                wf.assert_stage_completeness(bundle_dir, "not-a-real-stage")


class TestFeedbackBindingFields(unittest.TestCase):
    """`WFR-03`: external feedback is matched against `bundle_id` exactly;
    stale/missing feedback is rejected naming both values."""

    def _feedback(self, *, status="APPROVE", bundle_id=FAKE_ID_A, base_commit="a" * 40, work_item="workflow-v2-1-core"):
        return (
            f"# Review Decision\n\nStatus: {status}\n\n"
            f"Reviewed bundle ID: {bundle_id}\n"
            f"Reviewed base commit: {base_commit}\n"
            f"Work item: {work_item}\n"
        )

    def test_parses_all_four_fields(self):
        fields = wf.parse_review_feedback_binding_fields(self._feedback())
        self.assertEqual(fields["status"], "APPROVE")
        self.assertEqual(fields["reviewed_bundle_id"], FAKE_ID_A)
        self.assertEqual(fields["reviewed_base_commit"], "a" * 40)
        self.assertEqual(fields["work_item"], "workflow-v2-1-core")

    def test_missing_fields_parse_as_none(self):
        fields = wf.parse_review_feedback_binding_fields("# Review Decision\n\nStatus: APPROVE\n")
        self.assertIsNone(fields["reviewed_bundle_id"])
        self.assertIsNone(fields["reviewed_base_commit"])
        self.assertIsNone(fields["work_item"])

    def test_assert_matches_passes_on_agreement(self):
        fields = wf.parse_review_feedback_binding_fields(self._feedback())
        wf.assert_feedback_matches_bundle(
            fields, bundle_id=FAKE_ID_A, base_commit="a" * 40, work_item_id="workflow-v2-1-core"
        )

    def test_assert_matches_rejects_missing_field_naming_it(self):
        fields = wf.parse_review_feedback_binding_fields("# Review Decision\n\nStatus: APPROVE\n")
        with self.assertRaises(wf.MissingFeedbackBindingFieldError):
            wf.assert_feedback_matches_bundle(
                fields, bundle_id=FAKE_ID_A, base_commit="a" * 40, work_item_id="workflow-v2-1-core"
            )

    def test_assert_matches_rejects_stale_bundle_id(self):
        fields = wf.parse_review_feedback_binding_fields(self._feedback(bundle_id=FAKE_ID_B))
        with self.assertRaises(wf.FeedbackBundleMismatchError):
            wf.assert_feedback_matches_bundle(
                fields, bundle_id=FAKE_ID_A, base_commit="a" * 40, work_item_id="workflow-v2-1-core"
            )

    def test_assert_matches_rejects_stale_base_commit(self):
        fields = wf.parse_review_feedback_binding_fields(self._feedback(base_commit="b" * 40))
        with self.assertRaises(wf.FeedbackBundleMismatchError):
            wf.assert_feedback_matches_bundle(
                fields, bundle_id=FAKE_ID_A, base_commit="a" * 40, work_item_id="workflow-v2-1-core"
            )

    def test_assert_matches_rejects_wrong_work_item(self):
        fields = wf.parse_review_feedback_binding_fields(self._feedback(work_item="milestone-8"))
        with self.assertRaises(wf.FeedbackBundleMismatchError):
            wf.assert_feedback_matches_bundle(
                fields, bundle_id=FAKE_ID_A, base_commit="a" * 40, work_item_id="workflow-v2-1-core"
            )


class TestBinaryAndUnusualPathBundleEntries(unittest.TestCase):
    """`OPUS-R6-019`/`WFR-05`: `compute_bundle_id` treats bundle-file
    content as opaque bytes throughout, so binaries and unusual-but-
    supported paths (newline-in-path, non-ASCII path) round-trip
    deterministically -- restored to explicit test-vector coverage
    (previously only asserted in the module docstring)."""

    def test_binary_file_hashes_by_raw_bytes_never_decoded(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "MANIFEST.md").write_text(f"review_content_id: {'a' * 64}\n")
            binary_content = bytes(range(256)) + b"\xff\xfe\x00\x01"
            (bundle_dir / "files").mkdir()
            (bundle_dir / "files" / "blob.bin").write_bytes(binary_content)
            digest1, entries1 = wf.compute_bundle_id(bundle_dir)
            digest2, entries2 = wf.compute_bundle_id(bundle_dir)
            self.assertEqual(digest1, digest2)
            self.assertEqual(
                entries1["files/blob.bin"]["sha256"], hashlib.sha256(binary_content).hexdigest()
            )
            self.assertEqual(entries1, entries2)

    def test_different_binary_content_changes_bundle_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "MANIFEST.md").write_text(f"review_content_id: {'a' * 64}\n")
            (bundle_dir / "files").mkdir()
            (bundle_dir / "files" / "blob.bin").write_bytes(b"\x00\x01\x02")
            digest1, _ = wf.compute_bundle_id(bundle_dir)
            (bundle_dir / "files" / "blob.bin").write_bytes(b"\x00\x01\x03")
            digest2, _ = wf.compute_bundle_id(bundle_dir)
            self.assertNotEqual(digest1, digest2)

    def test_non_ascii_path_round_trips(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "MANIFEST.md").write_text(f"review_content_id: {'a' * 64}\n")
            (bundle_dir / "files").mkdir()
            (bundle_dir / "files" / "café.txt").write_text("espresso\n")
            digest, entries = wf.compute_bundle_id(bundle_dir)
            self.assertIn("files/café.txt", entries)
            digest_again, _ = wf.compute_bundle_id(bundle_dir)
            self.assertEqual(digest, digest_again)

    def test_newline_in_path_round_trips(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle_dir = Path(tmp)
            (bundle_dir / "MANIFEST.md").write_text(f"review_content_id: {'a' * 64}\n")
            (bundle_dir / "files").mkdir()
            weird_name = "line1\nline2.txt"
            (bundle_dir / "files" / weird_name).write_text("content\n")
            digest, entries = wf.compute_bundle_id(bundle_dir)
            self.assertIn(f"files/{weird_name}", entries)
            digest_again, _ = wf.compute_bundle_id(bundle_dir)
            self.assertEqual(digest, digest_again)


if __name__ == "__main__":
    unittest.main(verbosity=2)
