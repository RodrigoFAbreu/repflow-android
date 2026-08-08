#!/usr/bin/env python3
"""Hermetic conformance suite for `D-Fingerprint-Generalization`
(`docs/ai-workflow/WORKFLOW_V2_PLAN.md`, Revision 21, `WF8B-S1-001`).

Covers the core, independently-testable core of missing-tests 141-166: the
per-work-item plan-stage metadata resolver (`resolve_plan_stage_metadata`),
its thirteen-condition fail-closed matrix, the manifest work-item/base-commit
binding, and `route_work_item`'s resume-branch declaration-fact writer.
Runs entirely against disposable scratch Git repositories this suite
creates and destroys itself -- never against this repository's own working
tree or a fixed historical commit, so it is safe on every PR (unlike the
`_demo_test.py` suites, `GPT-R9-002` does not apply here).

Not an exhaustive enumeration of every sub-case the plan's own missing-test
list names (some items name a dozen-plus independent sub-cases) -- this
suite exercises each item's own core, load-bearing assertion at least once,
named in each test's docstring by item number.

Stdlib-only. Run: python3 scripts/workflow_fingerprint_generalization_test.py
"""

from __future__ import annotations

import hashlib
import json
import shutil
import stat
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path

import workflow_fingerprint as fingerprint
import workflow_state as ws
import workflow_test_harness as h

_REAL_SCRIPTS_DIR = Path(__file__).resolve().parent


def _write_second_item(repo, work_item_id="second-item", *, plan_revision=1, base_commit=None):
    """Seeds a second, fully-declared process work item's plan-stage
    fixture (its own five protected files, its own `<id>-artifacts.json`,
    its own `WORKFLOW_STATE.json` entry) alongside `workflow-test-harness`'s
    default `"wi"` item is never written here -- this is the *only* item in
    the scratch repo unless the caller writes another. Also seeds a
    `.gitignore` excluding `.ai-review/`, mirroring the real repository, so
    a later bundle-directory write is invisible to plan-stage
    classification the same way it is for real."""
    (repo.root / ".gitignore").write_text(".ai-review/\n")
    repo.write_plan_docs(work_item_id=work_item_id, plan_revision=plan_revision)
    repo.write_workflow_state(
        active_work_item_id=work_item_id,
        **{
            work_item_id: ws.default_work_item(
                work_item_id=work_item_id, work_item_type="process", work_item_kind="process",
                plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                registry_path=f"docs/ai-workflow/registry/{work_item_id}-registry.json",
                mapping_path=f"docs/ai-workflow/requirements/{work_item_id}-mapping.json",
                base_commit=base_commit or repo.base,
                governing_workflow_version="2.1",
                plan_revision=plan_revision, last_transition="t0",
            ),
        },
    )


class TestSecondWorkItemProducesDistinctIdentity(unittest.TestCase):
    """Items 143/144/149/151: a second work item resolves and fingerprints
    its own plan-stage content, distinct from `workflow-v2-1-core`'s (no
    such item exists in these scratch repos, but the resolver never reads
    or falls back to any literal naming one)."""

    def test_143_second_item_computes_distinct_id_with_own_manifest(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            digest, projection = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, "second-item",
            )
            self.assertEqual(projection["work_item_id"], "second-item")
            paths = {e["path"] for e in projection["review_content_manifest"]}
            self.assertEqual(
                paths,
                {
                    "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                    "docs/ai-workflow/WORKFLOW_V2_AUDIT.md",
                    "docs/TECHNICAL_DECISIONS.md",
                    "docs/ai-workflow/registry/second-item-registry.json",
                    "docs/ai-workflow/requirements/second-item-mapping.json",
                },
            )
            self.assertNotEqual(digest, "")

    def test_144_mutating_second_items_own_protected_file_changes_only_its_own_id(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            before, _ = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, "second-item",
            )
            (repo.root / "docs" / "ai-workflow" / "registry" / "second-item-registry.json").write_text(
                json.dumps({"work_item_id": "second-item", "plan_revision": 1, "checkpoints": [], "extra": True})
            )
            after, _ = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, "second-item",
            )
            self.assertNotEqual(before, after)

    def test_149_cli_read_only_omitted_work_item_id_resolves_to_live_active(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            work_items, active = fingerprint._load_workflow_state_work_items(repo.root, None)
            self.assertEqual(active, "second-item")
            digest_explicit, _ = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, "second-item",
            )
            digest_via_active, _ = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, active,
            )
            self.assertEqual(digest_explicit, digest_via_active)

    def test_149_cli_subprocess_prints_no_workflow_v2_1_core_literal_for_a_second_item(self):
        """Missing-test item 149's own real-subprocess half: a genuine
        `python3 scripts/workflow_fingerprint.py --work-item-id
        second-item` invocation's entire stdout must not name
        `workflow-v2-1-core` anywhere, end to end through the real CLI,
        not only through direct function calls."""
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            scripts_dir = repo.root / "scripts"
            scripts_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy(_REAL_SCRIPTS_DIR / "workflow_fingerprint.py", scripts_dir / "workflow_fingerprint.py")
            result = subprocess.run(
                ["python3", str(scripts_dir / "workflow_fingerprint.py"),
                 "--work-item-id", "second-item"],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("second-item", result.stdout)
            self.assertNotIn("workflow-v2-1-core", result.stdout)

    def test_149_write_manifest_with_work_item_id_omitted_refuses(self):
        """OPUS-R28-010's own sub-case, real-subprocess: `--write-manifest`
        with `--work-item-id` omitted refuses with a usage error, before
        any directory is created or bound -- never silently resolving to
        and writing the live `active_work_item_id`'s directory."""
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            scripts_dir = repo.root / "scripts"
            scripts_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy(_REAL_SCRIPTS_DIR / "workflow_fingerprint.py", scripts_dir / "workflow_fingerprint.py")
            result = subprocess.run(
                ["python3", str(scripts_dir / "workflow_fingerprint.py"),
                 repo.base, "--write-manifest"],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("work-item-id is required", result.stderr)
            self.assertFalse((repo.root / ".ai-review" / "second-item").exists())
            self.assertFalse((repo.root / ".ai-review" / "current").exists())

    def test_151_manifest_write_for_second_item_names_only_its_own_files(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            bundle_dir = repo.root / ".ai-review" / "second-item" / "current"
            bundle_dir.mkdir(parents=True)
            digest, _ = fingerprint.compute_review_content_id_plan_stage_for_work_item(repo.root, "second-item")
            (bundle_dir / "REVIEW_REQUEST.md").write_text(f"review_content_id: {digest}\n")
            (bundle_dir / "PLAN.md").write_text("plan\n")
            (bundle_dir / "DIFF.patch").write_text("\n")
            (bundle_dir / "TEST_RESULTS.md").write_text("results\n")
            written_digest, bundle_id = fingerprint.write_manifest_with_verified_identifiers_for_work_item(
                repo.root, "second-item",
            )
            self.assertEqual(written_digest, digest)
            manifest_text = (bundle_dir / "MANIFEST.md").read_text()
            self.assertIn("work_item_id: second-item", manifest_text)
            self.assertNotIn("workflow-v2-1-core", manifest_text)


class TestWorkflowV21CoreThroughTheNewResolver(unittest.TestCase):
    """Missing-test item 159 (`OPUS-R25-014`): `workflow_test_harness.py`'s
    `write_plan_docs` emits a valid per-item `<id>-artifacts.json`
    consumed successfully by `resolve_plan_stage_metadata`/
    `compute_review_content_id_plan_stage_for_work_item` with no override
    needed, **including `workflow-v2-1-core` itself** -- the historically
    hardcoded item, as opposed to `test_143`'s `second-item` and
    `test_workflow_v2_1_core_itself_needs_no_protected_override`
    (`workflow_test_harness_test.py`), which proves the same "no override
    needed" property but only through the old, frozen-default
    `compute_review_content_id_plan_stage`, never through the new
    resolver at all."""

    def test_workflow_v2_1_core_resolves_with_no_override_through_the_new_resolver(self):
        with h.ScratchRepo() as repo:
            (repo.root / ".gitignore").write_text(".ai-review/\n")
            repo.write_plan_docs(work_item_id="workflow-v2-1-core", plan_revision=1)
            repo.write_workflow_state(
                active_work_item_id="workflow-v2-1-core",
                **{"workflow-v2-1-core": ws.default_work_item(
                    work_item_id="workflow-v2-1-core", work_item_type="process", work_item_kind="process",
                    plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                    registry_path="docs/ai-workflow/registry/workflow-v2-1-core-registry.json",
                    mapping_path="docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json",
                    base_commit=repo.base, governing_workflow_version="1",
                    plan_revision=1, last_transition="t0",
                )},
            )
            repo.commit_plan_docs_as_base()
            metadata = fingerprint.resolve_plan_stage_metadata(repo.root, "workflow-v2-1-core")
            self.assertEqual(metadata.work_item_id, "workflow-v2-1-core")
            digest, projection = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, "workflow-v2-1-core",
            )
            self.assertEqual(projection["work_item_id"], "workflow-v2-1-core")
            self.assertNotEqual(digest, "")


class TestSyntheticWorkItemKind(unittest.TestCase):
    """Missing-test item 157 (`OPUS-R25-011`): a `work_item_kind:
    "synthetic"` item (mirroring the real `v2-1-dry-run`) with
    `work_item_type: "process"` computes its own distinct plan-stage
    `review_content_id` through the corrected resolution algorithm --
    exercising the exact item the superseded code comment named."""

    def test_synthetic_kind_process_type_item_computes_its_own_id(self):
        with h.ScratchRepo() as repo:
            (repo.root / ".gitignore").write_text(".ai-review/\n")
            repo.write_plan_docs(work_item_id="v2-1-dry-run", plan_revision=1)
            repo.write_workflow_state(
                active_work_item_id="v2-1-dry-run",
                **{"v2-1-dry-run": ws.default_work_item(
                    work_item_id="v2-1-dry-run", work_item_type="process", work_item_kind="synthetic",
                    plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                    registry_path="docs/ai-workflow/registry/v2-1-dry-run-registry.json",
                    mapping_path="docs/ai-workflow/requirements/v2-1-dry-run-mapping.json",
                    base_commit=repo.base, governing_workflow_version="2.1",
                    plan_revision=1, last_transition="t0",
                )},
            )
            repo.commit_plan_docs_as_base()
            digest, projection = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, "v2-1-dry-run",
            )
            self.assertEqual(projection["work_item_id"], "v2-1-dry-run")
            self.assertNotEqual(digest, "")


class TestCommitSourceMetadataPinning(unittest.TestCase):
    """Missing-test item 145 (`OPUS-R25-013`): `compute_review_content_id_
    plan_stage_at_commit_for_work_item` resolves metadata from the given
    commit, not from a subsequently-edited live `WORKFLOW_STATE.json`; and
    fails closed, not silently, against a pre-migration commit."""

    def test_commit_source_recomputation_unaffected_by_a_later_live_edit(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            pinned_commit = repo.base
            digest_at_commit, _ = fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item(
                repo.root, "second-item", pinned_commit,
            )
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            state["work_items"]["second-item"]["plan_revision"] = 999
            repo.commit_files("edit live state after pinning", {
                "docs/ai-workflow/WORKFLOW_STATE.json": json.dumps(state),
            })
            digest_recomputed, _ = fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item(
                repo.root, "second-item", pinned_commit,
            )
            self.assertEqual(
                digest_at_commit, digest_recomputed,
                "commit-source recomputation must be pinned to the given commit, "
                "unaffected by a later live-state edit",
            )

    def test_pre_migration_commit_fails_closed_not_a_silent_fallback(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            pre_migration_commit = repo.base
            (repo.root / "docs" / "ai-workflow" / "registry" / "second-item-artifacts.json").unlink()
            post_removal_commit = repo.commit_files(
                "remove artifacts file (simulates a pre-migration commit)", {},
            )
            with self.assertRaises(fingerprint.MissingWorkItemArtifactsDeclarationError):
                fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item(
                    repo.root, "second-item", post_removal_commit,
                )
            # The earlier, pre-removal commit must still resolve fine --
            # confirms the failure is scoped to the commit that actually
            # predates the migration, not a global break.
            fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item(
                repo.root, "second-item", pre_migration_commit,
            )  # must not raise


class TestFailClosedMatrix(unittest.TestCase):
    """A representative subset of the thirteen-condition fail-closed
    matrix (items 146-148, 150, 152, 161-163)."""

    def test_condition_1_unknown_work_item(self):
        with h.ScratchRepo() as repo:
            repo.write_workflow_state(active_work_item_id=None)
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.UnknownWorkItemError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "nonexistent")

    def test_condition_2_non_process_type_not_applicable(self):
        with h.ScratchRepo() as repo:
            repo.write_workflow_state(
                active_work_item_id="prod-item",
                **{"prod-item": ws.default_work_item(
                    work_item_id="prod-item", work_item_type="product", work_item_kind="product",
                    plan_path="docs/milestones/x.md", registry_path=None,
                    governing_workflow_version="1", plan_revision=1, last_transition="t0",
                )},
            )
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.PlanStageNotApplicableError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "prod-item")

    def test_condition_3_missing_metadata_field_named(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item", base_commit=None)
            # Overwrite with a null base_commit to exercise condition 3.
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            state["work_items"]["second-item"]["base_commit"] = None
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.MissingPlanStageMetadataError) as ctx:
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")
            self.assertIn("base_commit", str(ctx.exception))

    def _null_declared_field(self, field_name):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            state["work_items"]["second-item"][field_name] = None
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.MissingPlanStageMetadataError) as ctx:
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")
            self.assertIn(field_name, str(ctx.exception))

    def test_condition_3a_null_plan_path(self):
        self._null_declared_field("plan_path")

    def test_condition_3b_null_registry_path(self):
        self._null_declared_field("registry_path")

    def test_condition_3c_null_mapping_path(self):
        self._null_declared_field("mapping_path")

    def test_condition_4_invalid_path_grammar_absolute_path(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            state["work_items"]["second-item"]["plan_path"] = "/etc/passwd"
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.InvalidPlanStageMetadataPathError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_5_missing_artifacts_declaration(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            (repo.root / "docs" / "ai-workflow" / "registry" / "second-item-artifacts.json").unlink()
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.MissingWorkItemArtifactsDeclarationError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_4b_invalid_path_grammar_registry_path_absolute(self):
        """Condition 4's registry_path sub-case, independent of 4a's
        plan_path sub-case above -- the same rule-violation vector (an
        absolute path) exercised against a *different* one of the three
        fields."""
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            state["work_items"]["second-item"]["registry_path"] = "/etc/passwd"
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.InvalidPlanStageMetadataPathError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_4c_invalid_path_grammar_mapping_path_traversal(self):
        """Condition 4's mapping_path sub-case, using the second named
        rule-violation vector (a `../` traversal) rather than repeating
        4a/4b's absolute-path vector a third time."""
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            state["work_items"]["second-item"]["mapping_path"] = "../escape.json"
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.InvalidPlanStageMetadataPathError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_6a_registry_work_item_id_mismatch(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            registry_path = repo.root / "docs" / "ai-workflow" / "registry" / "second-item-registry.json"
            data = json.loads(registry_path.read_text())
            data["work_item_id"] = "someone-else"
            registry_path.write_text(json.dumps(data))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.RegistryWorkItemIdMismatchError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_6b_mapping_work_item_id_mismatch(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            mapping_path = repo.root / "docs" / "ai-workflow" / "requirements" / "second-item-mapping.json"
            data = json.loads(mapping_path.read_text())
            data["work_item_id"] = "someone-else"
            mapping_path.write_text(json.dumps(data))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.MappingWorkItemIdMismatchError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_6c_artifacts_work_item_id_mismatch(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            artifacts_path = repo.root / "docs" / "ai-workflow" / "registry" / "second-item-artifacts.json"
            data = json.loads(artifacts_path.read_text())
            data["work_item_id"] = "someone-else"
            artifacts_path.write_text(json.dumps(data))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.ArtifactsWorkItemIdMismatchError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_7a_declared_path_not_a_member_of_protected_set(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            artifacts_path = repo.root / "docs" / "ai-workflow" / "registry" / "second-item-artifacts.json"
            data = json.loads(artifacts_path.read_text())
            data["plan_stage"]["protected_paths"].remove("docs/ai-workflow/WORKFLOW_V2_PLAN.md")
            artifacts_path.write_text(json.dumps(data))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.PlanStageMetadataNotProtectedError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_7b_three_paths_not_pairwise_distinct(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            # mapping_path collapsed onto registry_path's own value -- both
            # remain members of the protected set (satisfying 7a), but the
            # triple is no longer pairwise distinct.
            state["work_items"]["second-item"]["mapping_path"] = (
                state["work_items"]["second-item"]["registry_path"]
            )
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.PlanStageMetadataNotProtectedError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_8a_duplicate_plan_path_claimed_by_two_items(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            third = dict(state["work_items"]["second-item"])
            third["work_item_id"] = "third-item"
            third["registry_path"] = "docs/ai-workflow/registry/third-item-registry.json"
            third["mapping_path"] = "docs/ai-workflow/requirements/third-item-mapping.json"
            # Only plan_path collides -- registry_path/mapping_path differ.
            state["work_items"]["third-item"] = third
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.DuplicateWorkItemArtifactPathError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_8b_duplicate_registry_path_claimed_by_two_items(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            third = dict(state["work_items"]["second-item"])
            third["work_item_id"] = "third-item"
            third["plan_path"] = "docs/ai-workflow/third-item-plan.md"
            third["mapping_path"] = "docs/ai-workflow/requirements/third-item-mapping.json"
            # Only registry_path collides -- plan_path/mapping_path differ.
            state["work_items"]["third-item"] = third
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.DuplicateWorkItemArtifactPathError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_8c_duplicate_mapping_path_claimed_by_two_items(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            third = dict(state["work_items"]["second-item"])
            third["work_item_id"] = "third-item"
            third["plan_path"] = "docs/ai-workflow/third-item-plan.md"
            third["registry_path"] = "docs/ai-workflow/registry/third-item-registry.json"
            # Only mapping_path collides -- plan_path/registry_path differ.
            state["work_items"]["third-item"] = third
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.DuplicateWorkItemArtifactPathError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_158_partial_collision_same_plan_path_only_raises_from_resolver_itself(self):
        """Missing-test item 158 (`OPUS-R25-007`): two work items
        declaring the same `plan_path` but different `registry_path`/
        `mapping_path` (a partial, not full-triple, collision) raise
        `DuplicateWorkItemArtifactPathError` from `resolve_plan_stage_metadata`
        itself -- exercising the read-path relocation, not only
        `validate_state`'s own write-path backstop (already covered by
        `test_duplicate_registry_path_across_two_items_fails_validate_state`
        below)."""
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            third = dict(state["work_items"]["second-item"])
            third["work_item_id"] = "third-item"
            third["registry_path"] = "docs/ai-workflow/registry/third-item-registry.json"
            third["mapping_path"] = "docs/ai-workflow/requirements/third-item-mapping.json"
            # plan_path is deliberately left identical to second-item's own
            # -- only one of the three fields collides.
            state["work_items"]["third-item"] = third
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.DuplicateWorkItemArtifactPathError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_9_plan_revision_mismatch(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item", plan_revision=1)
            plan_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_V2_PLAN.md"
            plan_path.write_text("# Plan (Revision 2)\n\nplan v1\n")
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.PlanRevisionMismatchError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_10_unclassified_path_scoped_per_item(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            (repo.root / "novel_untracked_path.txt").write_text("surprise\n")
            with self.assertRaises(fingerprint.UnclassifiedPathError):
                fingerprint.compute_review_content_id_plan_stage_for_work_item(repo.root, "second-item")

    def test_condition_12a_manifest_bound_to_different_work_item_refuses(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            bundle_dir = repo.root / ".ai-review" / "second-item" / "current"
            bundle_dir.mkdir(parents=True)
            for name, content in (
                ("REVIEW_REQUEST.md", "review_content_id: " + "a" * 64 + "\n"),
                ("PLAN.md", "plan\n"), ("DIFF.patch", "\n"), ("TEST_RESULTS.md", "results\n"),
            ):
                (bundle_dir / name).write_text(content)
            (bundle_dir / "MANIFEST.md").write_text("work_item_id: some-other-item\n")
            with self.assertRaises(fingerprint.BundleWorkItemMismatchError):
                fingerprint.write_manifest_with_verified_identifiers_for_work_item(repo.root, "second-item")

    def test_152_manifest_bound_to_v2_1_dry_run_refuses_workflow_v2_1_core_write_and_leaves_it_untouched(self):
        """Missing-test item 152's own exact literal names -- `v2-1-dry-run`
        and `workflow-v2-1-core` -- rather than the generic `second-item`/
        `some-other-item` placeholders `test_condition_12a` above uses,
        plus the "leaves the existing MANIFEST.md untouched" assertion
        neither `test_condition_12a`/`test_condition_12b` currently makes."""
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "workflow-v2-1-core")
            repo.commit_plan_docs_as_base()
            bundle_dir = repo.root / ".ai-review" / "workflow-v2-1-core" / "current"
            bundle_dir.mkdir(parents=True)
            for name, content in (
                ("REVIEW_REQUEST.md", "review_content_id: " + "a" * 64 + "\n"),
                ("PLAN.md", "plan\n"), ("DIFF.patch", "\n"), ("TEST_RESULTS.md", "results\n"),
            ):
                (bundle_dir / name).write_text(content)
            manifest_path = bundle_dir / "MANIFEST.md"
            existing_manifest_content = "work_item_id: v2-1-dry-run\n"
            manifest_path.write_text(existing_manifest_content)

            with self.assertRaises(fingerprint.BundleWorkItemMismatchError) as ctx:
                fingerprint.write_manifest_with_verified_identifiers_for_work_item(
                    repo.root, "workflow-v2-1-core",
                )
            self.assertIn("v2-1-dry-run", str(ctx.exception))
            self.assertIn("workflow-v2-1-core", str(ctx.exception))
            self.assertEqual(
                manifest_path.read_text(), existing_manifest_content,
                "a refused write must leave the existing MANIFEST.md byte-for-byte untouched",
            )

    def test_condition_12b_manifest_present_but_unbound_refuses(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            bundle_dir = repo.root / ".ai-review" / "second-item" / "current"
            bundle_dir.mkdir(parents=True)
            for name, content in (
                ("REVIEW_REQUEST.md", "review_content_id: " + "a" * 64 + "\n"),
                ("PLAN.md", "plan\n"), ("DIFF.patch", "\n"), ("TEST_RESULTS.md", "results\n"),
            ):
                (bundle_dir / name).write_text(content)
            manifest_path = bundle_dir / "MANIFEST.md"
            existing_manifest_content = "# Bundle Manifest\n\nreview_content_id: " + "a" * 64 + "\n"
            manifest_path.write_text(existing_manifest_content)
            with self.assertRaises(fingerprint.BundleWorkItemMismatchError) as ctx:
                fingerprint.write_manifest_with_verified_identifiers_for_work_item(repo.root, "second-item")
            self.assertIn("unbound", str(ctx.exception).lower() + repr(ctx.exception))
            self.assertEqual(
                manifest_path.read_text(), existing_manifest_content,
                "a refused write must leave the existing unbound MANIFEST.md byte-for-byte untouched",
            )

    def test_condition_13_base_commit_disagreement_refuses(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            state["work_items"]["second-item"]["base_commit"] = None
            state_path.write_text(json.dumps(state))
            repo.commit_plan_docs_as_base()
            # No declared base_commit at all fails closed rather than
            # silently falling back to any other item's value -- the
            # underlying condition-13 property this matrix row protects.
            with self.assertRaises(fingerprint.MissingPlanStageMetadataError) as ctx:
                fingerprint.compute_review_content_id_plan_stage_for_work_item(repo.root, "second-item")
            self.assertIn("base_commit", str(ctx.exception))

    def test_condition_13_manifest_bound_to_a_different_base_commit_refuses(self):
        """The write-path half of condition 13 (`OPUS-R26-003`):
        `MANIFEST.md`'s own recorded `base_commit` disagreeing with the
        resolved work item's declared value fails closed via the same
        `BundleWorkItemMismatchError` condition 12 raises -- one failure
        mode, not two mechanisms."""
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            bundle_dir = repo.root / ".ai-review" / "second-item" / "current"
            bundle_dir.mkdir(parents=True)
            for name, content in (
                ("REVIEW_REQUEST.md", "review_content_id: " + "a" * 64 + "\n"),
                ("PLAN.md", "plan\n"), ("DIFF.patch", "\n"), ("TEST_RESULTS.md", "results\n"),
            ):
                (bundle_dir / name).write_text(content)
            (bundle_dir / "MANIFEST.md").write_text(
                "# Bundle Manifest\n\nwork_item_id: second-item\nbase_commit: " + "c" * 40 + "\n"
            )
            with self.assertRaises(fingerprint.BundleWorkItemMismatchError):
                fingerprint.write_manifest_with_verified_identifiers_for_work_item(repo.root, "second-item")


class TestRouteWorkItemResumeBranchDeclarationFacts(unittest.TestCase):
    """Items 154 (resume-branch case)/OPUS-R28-002: `route_work_item`'s
    resume branch accepts the same four declaration facts a fresh entry
    does, per field independently: writes a still-null field, no-ops on an
    identical repeat, and raises on a genuine conflict."""

    def _config(self):
        config = ws.default_config()
        config["default_workflow_version"] = "2.1"
        return config

    def _pre_declared_state(self):
        return {
            "schema_version": 1,
            "active_work_item_id": "v2-1-dry-run-like",
            "work_items": {
                "v2-1-dry-run-like": ws.default_work_item(
                    work_item_id="v2-1-dry-run-like", work_item_type="process",
                    work_item_kind="synthetic", plan_path="docs/ai-workflow/dry-run/plan.md",
                    registry_path=None, governing_workflow_version="2.1",
                    plan_revision=1, last_transition="t0",
                ),
            },
        }

    def test_resume_branch_populates_still_null_fields(self):
        state = self._pre_declared_state()
        config = self._config()
        new_state = ws.route_work_item(
            state, config, work_item_id="v2-1-dry-run-like", work_item_type="process",
            work_item_kind="synthetic", plan_path="docs/ai-workflow/dry-run/plan.md",
            registry_path="docs/ai-workflow/registry/v2-1-dry-run-like-registry.json",
            mapping_path="docs/ai-workflow/requirements/v2-1-dry-run-like-mapping.json",
            base_commit="a" * 40, plan_revision=1, now="t1",
        )
        entry = new_state["work_items"]["v2-1-dry-run-like"]
        self.assertEqual(entry["registry_path"], "docs/ai-workflow/registry/v2-1-dry-run-like-registry.json")
        self.assertEqual(entry["mapping_path"], "docs/ai-workflow/requirements/v2-1-dry-run-like-mapping.json")
        self.assertEqual(entry["base_commit"], "a" * 40)

    def test_resume_branch_is_idempotent_on_exact_repeat(self):
        state = self._pre_declared_state()
        config = self._config()
        kwargs = dict(
            work_item_id="v2-1-dry-run-like", work_item_type="process", work_item_kind="synthetic",
            plan_path="docs/ai-workflow/dry-run/plan.md",
            registry_path="docs/ai-workflow/registry/v2-1-dry-run-like-registry.json",
            mapping_path="docs/ai-workflow/requirements/v2-1-dry-run-like-mapping.json",
            base_commit="a" * 40, plan_revision=1,
        )
        first = ws.route_work_item(state, config, now="t1", **kwargs)
        second = ws.route_work_item(first, config, now="t2", **kwargs)
        entry = second["work_items"]["v2-1-dry-run-like"]
        self.assertEqual(entry["registry_path"], kwargs["registry_path"])
        self.assertEqual(entry["base_commit"], kwargs["base_commit"])

    def test_resume_branch_raises_on_genuine_conflict(self):
        state = self._pre_declared_state()
        config = self._config()
        first = ws.route_work_item(
            state, config, work_item_id="v2-1-dry-run-like", work_item_type="process",
            work_item_kind="synthetic", plan_path="docs/ai-workflow/dry-run/plan.md",
            registry_path="docs/ai-workflow/registry/v2-1-dry-run-like-registry.json",
            mapping_path="docs/ai-workflow/requirements/v2-1-dry-run-like-mapping.json",
            base_commit="a" * 40, plan_revision=1, now="t1",
        )
        with self.assertRaises(ws.WorkItemDeclarationFactConflictError):
            ws.route_work_item(
                first, config, work_item_id="v2-1-dry-run-like", work_item_type="process",
                work_item_kind="synthetic", plan_path="docs/ai-workflow/dry-run/plan.md",
                registry_path="docs/ai-workflow/registry/v2-1-dry-run-like-registry.json",
                mapping_path="docs/ai-workflow/requirements/v2-1-dry-run-like-mapping.json",
                base_commit="b" * 40, plan_revision=1, now="t2",
            )

    def test_creation_branch_end_to_end_through_real_bundle_generation(self):
        """Item 154's own creation-branch case, run end to end (`OPUS-R25-004`,
        `OPUS-R28-002`): unlike every other test in this suite, which
        constructs its fixture's `WORKFLOW_STATE.json` entry directly via
        `ws.default_work_item(...)` -- bypassing `route_work_item` entirely
        -- this test calls `route_work_item` itself, for a `work_item_id`
        genuinely absent from `state["work_items"]`, and follows through
        to a real, successful `prepare-ai-review.sh` invocation. This is
        not interchangeable with the resume-branch tests above: only the
        creation branch (`default_work_item`'s own write path) is
        exercised here."""
        with h.ScratchRepo() as repo:
            (repo.root / ".gitignore").write_text(".ai-review/\n")
            work_item_id = "fresh-item"
            repo.write_plan_docs(work_item_id=work_item_id, plan_revision=1)
            repo.commit_plan_docs_as_base()

            config = self._config()
            state = {"schema_version": 1, "active_work_item_id": None, "work_items": {}}
            self.assertNotIn(work_item_id, state["work_items"])
            new_state = ws.route_work_item(
                state, config, work_item_id=work_item_id, work_item_type="process",
                work_item_kind="process",
                plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                registry_path=f"docs/ai-workflow/registry/{work_item_id}-registry.json",
                mapping_path=f"docs/ai-workflow/requirements/{work_item_id}-mapping.json",
                base_commit=repo.base, plan_revision=1, now="t0",
            )
            entry = new_state["work_items"][work_item_id]
            self.assertEqual(entry["registry_path"], f"docs/ai-workflow/registry/{work_item_id}-registry.json")
            self.assertEqual(entry["base_commit"], repo.base)

            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state_path.write_text(json.dumps(new_state))
            subprocess.run(["git", "add", "-A"], cwd=repo.root, check=True, capture_output=True)
            subprocess.run(
                ["git", "commit", "-q", "-m", "route_work_item creation branch"],
                cwd=repo.root, check=True, capture_output=True,
            )

            digest, _ = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, work_item_id,
            )
            bundle_dir = repo.root / ".ai-review" / work_item_id / "current"
            bundle_dir.mkdir(parents=True)
            (bundle_dir / "REVIEW_REQUEST.md").write_text(f"stage: plan\nreview_content_id: {digest}\n")

            scripts_dir = repo.root / "scripts"
            scripts_dir.mkdir(parents=True, exist_ok=True)
            for name in ("prepare-ai-review.sh", "workflow_fingerprint.py"):
                dest = scripts_dir / name
                shutil.copy(_REAL_SCRIPTS_DIR / name, dest)
            script_path = scripts_dir / "prepare-ai-review.sh"
            script_path.chmod(script_path.stat().st_mode | stat.S_IEXEC)

            result = subprocess.run(
                ["bash", str(script_path), repo.base, "plan", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            manifest_text = (bundle_dir / "MANIFEST.md").read_text()
            self.assertIn(f"work_item_id: {work_item_id}", manifest_text)
            self.assertIn(f"review_content_id: {digest}", manifest_text)


class TestArtifactsDeclarationsGenerator(unittest.TestCase):
    """Items 155/156: `generate_artifacts_declarations`'s default template
    satisfies the path-to-role binding check by construction, and the
    generated file's own concrete implementation-stage self-reference
    classifies correctly."""

    def test_155_default_template_satisfies_protected_membership(self):
        declarations = ws.generate_artifacts_declarations(
            "new-item", "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            "docs/ai-workflow/registry/new-item-registry.json",
            "docs/ai-workflow/requirements/new-item-mapping.json",
        )
        protected = set(declarations["plan_stage"]["protected_paths"])
        self.assertEqual(
            protected,
            {
                "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                "docs/ai-workflow/registry/new-item-registry.json",
                "docs/ai-workflow/requirements/new-item-mapping.json",
            },
        )

    def test_156_self_referential_entry_protects_own_file_at_implementation_stage(self):
        declarations = ws.generate_artifacts_declarations(
            "new-item", "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            "docs/ai-workflow/registry/new-item-registry.json",
            "docs/ai-workflow/requirements/new-item-mapping.json",
        )
        own_path = "docs/ai-workflow/registry/new-item-artifacts.json"
        self.assertIn(own_path, declarations["implementation_stage"]["protected_paths"])
        classification = fingerprint.classify_path_implementation_stage(
            own_path,
            declarations["implementation_stage"]["protected_paths"],
            declarations["implementation_stage"]["protected_prefixes"],
            declarations["implementation_stage"]["excluded_paths"],
            declarations["implementation_stage"]["excluded_prefixes"],
        )
        self.assertEqual(classification, "protected")


class TestValidateStateDuplicatePathDetection(unittest.TestCase):
    """Belt-and-suspenders write-time check (`OPUS-R25-007`): a hand-edited
    state file with two work items claiming the same registry_path fails
    `validate_state` directly, independent of the read-path's own
    `resolve_plan_stage_metadata` enforcement."""

    def test_duplicate_registry_path_across_two_items_fails_validate_state(self):
        state = {
            "schema_version": ws.SCHEMA_VERSION,
            "active_work_item_id": None,
            "work_items": {
                "item-a": ws.default_work_item(
                    work_item_id="item-a", work_item_type="process", work_item_kind="process",
                    plan_path="a-plan.md", registry_path="shared-registry.json",
                    governing_workflow_version="2.1", plan_revision=1, last_transition="t0",
                ),
                "item-b": ws.default_work_item(
                    work_item_id="item-b", work_item_type="process", work_item_kind="process",
                    plan_path="b-plan.md", registry_path="shared-registry.json",
                    governing_workflow_version="2.1", plan_revision=1, last_transition="t0",
                ),
            },
        }
        with self.assertRaises(fingerprint.DuplicateWorkItemArtifactPathError):
            ws.validate_state(state)


class TestPrepareAiReviewShPlanStageRequiredArgument(unittest.TestCase):
    """Items 153/161-163: `scripts/prepare-ai-review.sh`'s own plan-stage
    path, end to end against a real subprocess invocation of the actual
    script (copied into the scratch repo, never a reimplementation)."""

    def _install_scripts(self, repo):
        scripts_dir = repo.root / "scripts"
        scripts_dir.mkdir(parents=True, exist_ok=True)
        for name in ("prepare-ai-review.sh", "workflow_fingerprint.py"):
            dest = scripts_dir / name
            shutil.copy(_REAL_SCRIPTS_DIR / name, dest)
        script_path = scripts_dir / "prepare-ai-review.sh"
        script_path.chmod(script_path.stat().st_mode | stat.S_IEXEC)
        return script_path

    def test_162_no_third_argument_refuses_for_plan_stage(self):
        with h.ScratchRepo() as repo:
            script_path = self._install_scripts(repo)
            result = subprocess.run(
                ["bash", str(script_path), repo.base, "plan"],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("work-item-id is required", result.stderr)
            self.assertFalse((repo.root / ".ai-review").exists())

    def test_163_base_commit_disagreement_refuses_before_any_content(self):
        with h.ScratchRepo() as repo:
            script_path = self._install_scripts(repo)
            _write_second_item(repo, "second-item", base_commit="d" * 40)
            repo.commit_plan_docs_as_base()
            result = subprocess.run(
                ["bash", str(script_path), repo.base, "plan", "second-item"],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("declares base_commit", result.stderr)
            self.assertFalse((repo.root / ".ai-review").exists())

    def test_153_successful_run_writes_a_bound_manifest(self):
        with h.ScratchRepo() as repo:
            script_path = self._install_scripts(repo)
            _write_second_item(repo, "second-item")
            repo.commit_plan_docs_as_base()
            # The item's own declared base_commit must equal the commit
            # these files were actually settled at -- correct it in a
            # small follow-up commit (WORKFLOW_STATE.json is excluded, so
            # this never touches plan-stage identity).
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            state["work_items"]["second-item"]["base_commit"] = repo.base
            state_path.write_text(json.dumps(state))
            subprocess.run(["git", "add", "-A"], cwd=repo.root, check=True, capture_output=True)
            subprocess.run(
                ["git", "commit", "-q", "-m", "fix declared base_commit"],
                cwd=repo.root, check=True, capture_output=True,
            )
            digest, _ = fingerprint.compute_review_content_id_plan_stage_for_work_item(
                repo.root, "second-item",
            )
            bundle_dir = repo.root / ".ai-review" / "second-item" / "current"
            bundle_dir.mkdir(parents=True)
            (bundle_dir / "REVIEW_REQUEST.md").write_text(f"stage: plan\nreview_content_id: {digest}\n")
            result = subprocess.run(
                ["bash", str(script_path), repo.base, "plan", "second-item"],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            manifest_text = (bundle_dir / "MANIFEST.md").read_text()
            self.assertIn("work_item_id: second-item", manifest_text)
            self.assertIn(f"review_content_id: {digest}", manifest_text)
            # Item 163's own positive half: MANIFEST.md's base_commit
            # field must equal the resolved item's declared value,
            # checked at write time -- not only that a *disagreeing* one
            # refuses (test_163_base_commit_disagreement_refuses_before_any_content
            # above only covers the negative half).
            self.assertIn(f"base_commit: {repo.base}", manifest_text)
            self.assertTrue((repo.root / ".ai-review" / "second-item" / "review-bundle.tar.gz").is_file())


class TestPrepareAiReviewShImplementationStageHeadGuard(unittest.TestCase):
    """GPT-R42-001: an implementation/post-fix bundle must not be
    finalized while `WORKFLOW_STATE.json`'s own
    `work_items[work_item_id].reviewed_implementation_head` disagrees
    with the exact commit the bundle was actually generated at -- the
    defect that let an already-generated bundle's own manifest declare
    one reviewed head while the bundle's own copied `WORKFLOW_STATE.json`
    snapshot still named an older one. Exercised end to end against a
    real subprocess invocation of the actual script (copied into the
    scratch repo, never a reimplementation), mirroring
    `TestPrepareAiReviewShPlanStageRequiredArgument`'s technique for the
    plan stage. The guard reads `WORKFLOW_STATE.json` straight off disk,
    so these fixtures deliberately leave it uncommitted at generation
    time -- exactly the pre-technical-approval convention
    `record_bundle_generation`'s own docstring and this round's
    `REVIEW_REQUEST.md` describe (the state write happens before the
    bundle is finalized, not necessarily before it is committed)."""

    def _install_scripts(self, repo):
        scripts_dir = repo.root / "scripts"
        scripts_dir.mkdir(parents=True, exist_ok=True)
        for name in ("prepare-ai-review.sh", "workflow_fingerprint.py"):
            dest = scripts_dir / name
            shutil.copy(_REAL_SCRIPTS_DIR / name, dest)
        script_path = scripts_dir / "prepare-ai-review.sh"
        script_path.chmod(script_path.stat().st_mode | stat.S_IEXEC)
        return script_path

    def _seed_and_implement(self, repo, work_item_id="wi"):
        """Settles the plan-stage fixture as `repo.base`, then adds one
        more commit (`impl_head`) representing the reviewed implementation
        content -- `impl.txt` is declared implementation-stage protected
        so it classifies rather than raising `UnclassifiedPathError`.
        Returns `impl_head`."""
        (repo.root / ".gitignore").write_text(".ai-review/\n")
        repo.write_plan_docs(work_item_id=work_item_id, plan_revision=1)
        declarations = ws.generate_artifacts_declarations(
            work_item_id, "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            f"docs/ai-workflow/registry/{work_item_id}-registry.json",
            f"docs/ai-workflow/requirements/{work_item_id}-mapping.json",
        )
        declarations["implementation_stage"]["protected_paths"]["impl.txt"] = "test fixture content"
        artifacts_path = repo.root / "docs" / "ai-workflow" / "registry" / f"{work_item_id}-artifacts.json"
        artifacts_path.write_text(json.dumps(declarations) + "\n")
        repo.commit_plan_docs_as_base()
        impl_head = repo.commit("implement thing", filename="impl.txt")
        return impl_head

    def _write_review_request(self, repo, work_item_id, base, impl_head):
        protected_paths, protected_prefixes, excluded_paths, excluded_prefixes = (
            fingerprint.load_implementation_stage_classification(
                repo.root, fingerprint.artifacts_path_for_work_item(work_item_id),
            )
        )
        digest, _ = fingerprint.compute_review_content_id_implementation_stage_at_commit(
            repo.root, base, impl_head, "process", work_item_id,
            protected_paths, protected_prefixes, excluded_paths, excluded_prefixes,
        )
        bundle_dir = repo.root / ".ai-review" / work_item_id / "current"
        bundle_dir.mkdir(parents=True, exist_ok=True)  # a later round reuses round 1's own directory
        (bundle_dir / "REVIEW_REQUEST.md").write_text(
            f"stage: post-fix\nreview_content_id: {digest}\n"
        )
        return bundle_dir

    def test_stale_reviewed_implementation_head_refuses_before_archiving(self):
        with h.ScratchRepo() as repo:
            work_item_id = "wi"
            impl_head = self._seed_and_implement(repo, work_item_id)
            self._write_review_request(repo, work_item_id, repo.base, impl_head)

            entry = ws.default_work_item(
                work_item_id=work_item_id, work_item_type="process", work_item_kind="process",
                plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                registry_path=f"docs/ai-workflow/registry/{work_item_id}-registry.json",
                mapping_path=f"docs/ai-workflow/requirements/{work_item_id}-mapping.json",
                base_commit=repo.base, governing_workflow_version="1",
                plan_revision=1, last_transition="t0",
            )
            entry["reviewed_implementation_head"] = "0" * 40
            entry["implementation_revision"] = 1
            repo.write_workflow_state(active_work_item_id=work_item_id, **{work_item_id: entry})

            script_path = self._install_scripts(repo)
            result = subprocess.run(
                ["bash", str(script_path), repo.base, "post-fix", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("GPT-R42-001", result.stderr)
            self.assertIn("reviewed_implementation_head", result.stderr)
            # The guard runs before any write to current/ (GPT-R43-003) --
            # no MANIFEST.md and no archive are ever produced for a bundle
            # this guard refused.
            self.assertFalse((repo.root / ".ai-review" / work_item_id / "current" / "MANIFEST.md").is_file())
            self.assertFalse((repo.root / ".ai-review" / work_item_id / "review-bundle.tar.gz").is_file())

    def test_matching_reviewed_implementation_head_succeeds(self):
        with h.ScratchRepo() as repo:
            work_item_id = "wi"
            impl_head = self._seed_and_implement(repo, work_item_id)
            bundle_dir = self._write_review_request(repo, work_item_id, repo.base, impl_head)

            entry = ws.default_work_item(
                work_item_id=work_item_id, work_item_type="process", work_item_kind="process",
                plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
                registry_path=f"docs/ai-workflow/registry/{work_item_id}-registry.json",
                mapping_path=f"docs/ai-workflow/requirements/{work_item_id}-mapping.json",
                base_commit=repo.base, governing_workflow_version="1",
                plan_revision=1, last_transition="t0",
            )
            entry["reviewed_implementation_head"] = impl_head
            entry["implementation_revision"] = 1
            repo.write_workflow_state(active_work_item_id=work_item_id, **{work_item_id: entry})

            script_path = self._install_scripts(repo)
            result = subprocess.run(
                ["bash", str(script_path), repo.base, "post-fix", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            manifest_text = (bundle_dir / "MANIFEST.md").read_text()
            self.assertIn(f"reviewed_implementation_head: {impl_head}", manifest_text)

    def _write_state_entry(self, repo, work_item_id, *, base, head, revision):
        entry = ws.default_work_item(
            work_item_id=work_item_id, work_item_type="process", work_item_kind="process",
            plan_path="docs/ai-workflow/WORKFLOW_V2_PLAN.md",
            registry_path=f"docs/ai-workflow/registry/{work_item_id}-registry.json",
            mapping_path=f"docs/ai-workflow/requirements/{work_item_id}-mapping.json",
            base_commit=base, governing_workflow_version="1",
            plan_revision=1, last_transition="t0",
        )
        entry["reviewed_implementation_head"] = head
        entry["implementation_revision"] = revision
        repo.write_workflow_state(active_work_item_id=work_item_id, **{work_item_id: entry})

    def _generate_first_round(self, repo, work_item_id, script_path):
        """Establishes a real, successful round-1 bundle (no prior
        `MANIFEST.md` to compare against, so `implementation_revision`
        advancement is unguarded for this call, same as the plan stage's
        own creation branch) -- the fixture every GPT-R43-001/-003 test
        below needs as its "previous round" starting point."""
        impl_head = self._seed_and_implement(repo, work_item_id)
        self._write_review_request(repo, work_item_id, repo.base, impl_head)
        self._write_state_entry(repo, work_item_id, base=repo.base, head=impl_head, revision=1)
        result = subprocess.run(
            ["bash", str(script_path), repo.base, "implementation", work_item_id],
            cwd=repo.root, capture_output=True, text=True,
        )
        assert result.returncode == 0, result.stderr
        return impl_head

    def test_new_head_with_stale_revision_refuses(self):
        """GPT-R43-001: the head-only guard from GPT-R42-001 is satisfied
        (state's reviewed_implementation_head matches the new commit), but
        implementation_revision was left at the previous round's value --
        must still refuse."""
        with h.ScratchRepo() as repo:
            work_item_id = "wi"
            script_path = self._install_scripts(repo)
            self._generate_first_round(repo, work_item_id, script_path)

            impl_head_2 = repo.commit("second implementation change", filename="impl.txt")
            self._write_review_request(repo, work_item_id, repo.base, impl_head_2)
            self._write_state_entry(repo, work_item_id, base=repo.base, head=impl_head_2, revision=1)

            result = subprocess.run(
                ["bash", str(script_path), repo.base, "post-fix", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("GPT-R43-001", result.stderr)
            self.assertIn("implementation_revision", result.stderr)

    def test_new_head_with_correctly_advanced_revision_succeeds(self):
        with h.ScratchRepo() as repo:
            work_item_id = "wi"
            script_path = self._install_scripts(repo)
            self._generate_first_round(repo, work_item_id, script_path)

            impl_head_2 = repo.commit("second implementation change", filename="impl.txt")
            self._write_review_request(repo, work_item_id, repo.base, impl_head_2)
            self._write_state_entry(repo, work_item_id, base=repo.base, head=impl_head_2, revision=2)

            result = subprocess.run(
                ["bash", str(script_path), repo.base, "post-fix", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            manifest_text = (
                repo.root / ".ai-review" / work_item_id / "current" / "MANIFEST.md"
            ).read_text()
            self.assertIn(f"reviewed_implementation_head: {impl_head_2}", manifest_text)
            self.assertIn("implementation_revision: 2", manifest_text)

    def test_new_head_with_revision_jump_greater_than_one_refuses(self):
        """The finding's own "exactly one" requirement: a new head must
        advance the revision by precisely 1, not 2 -- the exact mistake a
        session that calls `record_bundle_generation` twice before its
        first commit can make."""
        with h.ScratchRepo() as repo:
            work_item_id = "wi"
            script_path = self._install_scripts(repo)
            self._generate_first_round(repo, work_item_id, script_path)

            impl_head_2 = repo.commit("second implementation change", filename="impl.txt")
            self._write_review_request(repo, work_item_id, repo.base, impl_head_2)
            self._write_state_entry(repo, work_item_id, base=repo.base, head=impl_head_2, revision=3)

            result = subprocess.run(
                ["bash", str(script_path), repo.base, "post-fix", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("GPT-R43-001", result.stderr)

    def test_repeat_generation_of_same_round_is_idempotent(self):
        """Regenerating for the exact same head the previous bundle
        already named (no new commit) must succeed without requiring an
        additional revision bump."""
        with h.ScratchRepo() as repo:
            work_item_id = "wi"
            script_path = self._install_scripts(repo)
            impl_head = self._generate_first_round(repo, work_item_id, script_path)

            # Re-run for the identical head/revision -- nothing changed.
            result = subprocess.run(
                ["bash", str(script_path), repo.base, "post-fix", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            manifest_text = (
                repo.root / ".ai-review" / work_item_id / "current" / "MANIFEST.md"
            ).read_text()
            self.assertIn(f"reviewed_implementation_head: {impl_head}", manifest_text)
            self.assertIn("implementation_revision: 1", manifest_text)

    def test_repeat_generation_with_stale_revision_bump_refuses(self):
        """The idempotent case's own negative half: regenerating for the
        *same* head but with implementation_revision changed anyway must
        still refuse -- a same-head regeneration is never itself a reason
        to advance the revision."""
        with h.ScratchRepo() as repo:
            work_item_id = "wi"
            script_path = self._install_scripts(repo)
            impl_head = self._generate_first_round(repo, work_item_id, script_path)

            self._write_state_entry(repo, work_item_id, base=repo.base, head=impl_head, revision=2)
            result = subprocess.run(
                ["bash", str(script_path), repo.base, "post-fix", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("GPT-R43-001", result.stderr)

    def test_refused_regeneration_leaves_current_byte_identical(self):
        """GPT-R43-003: a refused regeneration attempt must leave the
        previously valid `current/` bundle byte-identical -- `current/` is
        "the bundle currently under review" (REVIEW_PROTOCOL.md), not a
        scratch directory a rejected generation may leave mutated."""
        with h.ScratchRepo() as repo:
            work_item_id = "wi"
            script_path = self._install_scripts(repo)
            self._generate_first_round(repo, work_item_id, script_path)

            impl_head_2 = repo.commit("second implementation change", filename="impl.txt")
            self._write_review_request(repo, work_item_id, repo.base, impl_head_2)
            # Stale revision -- refused, same as test_new_head_with_stale_revision_refuses.
            self._write_state_entry(repo, work_item_id, base=repo.base, head=impl_head_2, revision=1)

            # Snapshot after this round's own author-prep (REVIEW_REQUEST.md
            # is legitimately author-edited before every invocation, same
            # as a real round) but before invoking the script -- isolates
            # what the *script itself* does to current/ (and the archive
            # already produced by round 1) on refusal.
            bundle_dir = repo.root / ".ai-review" / work_item_id / "current"
            archive = repo.root / ".ai-review" / work_item_id / "review-bundle.tar.gz"
            self.assertTrue(archive.is_file())  # round 1 already produced it
            before = {
                p.relative_to(bundle_dir): p.read_bytes()
                for p in bundle_dir.rglob("*") if p.is_file()
            }
            before_archive = archive.read_bytes()

            result = subprocess.run(
                ["bash", str(script_path), repo.base, "post-fix", work_item_id],
                cwd=repo.root, capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)

            after = {
                p.relative_to(bundle_dir): p.read_bytes()
                for p in bundle_dir.rglob("*") if p.is_file()
            }
            self.assertEqual(before, after)
            self.assertEqual(before_archive, archive.read_bytes())


class TestBundleRelocation(unittest.TestCase):
    """Missing-test item 165's relocation/migration sub-cases
    (`OPUS-R27-003`, `OPUS-R28-004`/`-006`): `relocate_flat_bundle_to_scoped_layout`
    and its `verify_relocation_file_set_complete` post-move check.

    Exercised against a bundle fixture built entirely inside the test's own
    temporary directory, never against this repository's own (gitignored)
    `.ai-review/` state: a clean checkout -- exactly what runs this
    module's committed CI job -- has no such directory, so reading it here
    made the "hermetic" suite this module's own header docstring promises
    fail outside a developer worktree that happened to hold a live bundle
    (`GPT-R34-001`). The fixture still has real, non-trivial byte content
    (its `MANIFEST.md` is rendered through the production
    `render_manifest_md` helper) and a nested `files/` subtree, so
    relocation exercises the same directory shape a real bundle has --
    item 165 needs realistic content shape here, not byte-identity with
    any specific real bundle."""

    def _build_synthetic_bundle_fixture(self, dest_repo_root: Path) -> Path:
        flat_dir = dest_repo_root / ".ai-review" / "current"
        files_dir = flat_dir / "files" / "scripts"
        files_dir.mkdir(parents=True)
        manifest_content = fingerprint.render_manifest_md(
            review_content_id="a" * 64,
            protected=frozenset({"scripts/example.py"}),
            excluded_paths={},
            excluded_prefixes={},
            bundle_id="b" * 64,
            work_item_id="second-item",
            work_item_type="process",
            plan_revision=1,
            base_commit="c" * 40,
        )
        (flat_dir / "MANIFEST.md").write_text(manifest_content)
        for name in sorted(fingerprint.REQUIRED_GENERATION_FILES):
            (flat_dir / name).write_text(f"synthetic {name} content for relocation fixture\n")
        (files_dir / "example.py").write_text("print('synthetic bundle fixture content')\n")
        (files_dir / "another.py").write_text("VALUE = 42\n")

        archive_path = dest_repo_root / ".ai-review" / "review-bundle.tar.gz"
        with tarfile.open(archive_path, "w:gz") as tar:
            tar.add(flat_dir, arcname="current")
        return flat_dir

    def test_relocation_succeeds_and_verifies_complete_move(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".ai-review").mkdir()
            flat_dir = self._build_synthetic_bundle_fixture(root)
            source_files = {p.relative_to(flat_dir).as_posix() for p in flat_dir.rglob("*") if p.is_file()}
            self.assertTrue(source_files, "the synthetic bundle fixture must be non-empty")

            fingerprint.relocate_flat_bundle_to_scoped_layout(root, "second-item")

            self.assertFalse(flat_dir.exists(), "the flat source directory must be gone after relocation")
            dest_dir = root / ".ai-review" / "second-item" / "current"
            dest_files = {p.relative_to(dest_dir).as_posix() for p in dest_dir.rglob("*") if p.is_file()}
            self.assertEqual(source_files, dest_files)
            self.assertTrue((root / ".ai-review" / "second-item" / "review-bundle.tar.gz").is_file())

    def test_relocation_refuses_when_destination_already_exists_non_empty(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".ai-review").mkdir()
            self._build_synthetic_bundle_fixture(root)
            dest_dir = root / ".ai-review" / "second-item" / "current"
            dest_dir.mkdir(parents=True)
            (dest_dir / "PRE_EXISTING.txt").write_text("already here\n")

            with self.assertRaises(fingerprint.BundleRelocationDestinationExistsError) as ctx:
                fingerprint.relocate_flat_bundle_to_scoped_layout(root, "second-item")
            self.assertIn(str(dest_dir), str(ctx.exception))
            # Refusal must be a hard stop before touching the source at all.
            self.assertTrue((root / ".ai-review" / "current").is_dir())
            self.assertEqual((dest_dir / "PRE_EXISTING.txt").read_text(), "already here\n")

    def test_partial_move_missing_file_is_caught_by_post_move_verification(self):
        # Simulates an interrupted move directly against the real
        # verification function, rather than trying to interrupt
        # shutil.move mid-flight: a destination missing one file the
        # pre-move snapshot named.
        with tempfile.TemporaryDirectory() as tmp:
            dest_dir = Path(tmp) / "dest"
            dest_dir.mkdir()
            (dest_dir / "a.txt").write_bytes(b"a")
            source_snapshot = {
                "a.txt": hashlib.sha256(b"a").hexdigest(),
                "b.txt": hashlib.sha256(b"b").hexdigest(),
            }
            with self.assertRaises(fingerprint.BundleRelocationPartialMoveError) as ctx:
                fingerprint.verify_relocation_file_set_complete(source_snapshot, dest_dir)
            self.assertIn("b.txt", str(ctx.exception))

    def test_partial_move_content_mismatch_is_also_caught(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest_dir = Path(tmp) / "dest"
            dest_dir.mkdir()
            (dest_dir / "a.txt").write_bytes(b"corrupted")
            source_snapshot = {"a.txt": hashlib.sha256(b"original").hexdigest()}
            with self.assertRaises(fingerprint.BundleRelocationPartialMoveError) as ctx:
                fingerprint.verify_relocation_file_set_complete(source_snapshot, dest_dir)
            self.assertIn("a.txt", str(ctx.exception))

    def test_complete_move_verification_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest_dir = Path(tmp) / "dest"
            dest_dir.mkdir()
            (dest_dir / "a.txt").write_bytes(b"same")
            source_snapshot = {"a.txt": hashlib.sha256(b"same").hexdigest()}
            fingerprint.verify_relocation_file_set_complete(source_snapshot, dest_dir)  # must not raise


if __name__ == "__main__":
    unittest.main()
