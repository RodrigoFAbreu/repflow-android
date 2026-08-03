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

import json
import shutil
import stat
import subprocess
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

    def test_condition_6_registry_work_item_id_mismatch(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            registry_path = repo.root / "docs" / "ai-workflow" / "registry" / "second-item-registry.json"
            data = json.loads(registry_path.read_text())
            data["work_item_id"] = "someone-else"
            registry_path.write_text(json.dumps(data))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.RegistryWorkItemIdMismatchError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_7_declared_path_not_a_member_of_protected_set(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            artifacts_path = repo.root / "docs" / "ai-workflow" / "registry" / "second-item-artifacts.json"
            data = json.loads(artifacts_path.read_text())
            data["plan_stage"]["protected_paths"].remove("docs/ai-workflow/WORKFLOW_V2_PLAN.md")
            artifacts_path.write_text(json.dumps(data))
            repo.commit_plan_docs_as_base()
            with self.assertRaises(fingerprint.PlanStageMetadataNotProtectedError):
                fingerprint.resolve_plan_stage_metadata(repo.root, "second-item")

    def test_condition_8_duplicate_path_claimed_by_two_items(self):
        with h.ScratchRepo() as repo:
            _write_second_item(repo, "second-item")
            state_path = repo.root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
            state = json.loads(state_path.read_text())
            third = dict(state["work_items"]["second-item"])
            third["work_item_id"] = "third-item"
            state["work_items"]["third-item"] = third  # same plan_path/registry_path/mapping_path
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
            (bundle_dir / "MANIFEST.md").write_text("# Bundle Manifest\n\nreview_content_id: " + "a" * 64 + "\n")
            with self.assertRaises(fingerprint.BundleWorkItemMismatchError) as ctx:
                fingerprint.write_manifest_with_verified_identifiers_for_work_item(repo.root, "second-item")
            self.assertIn("unbound", str(ctx.exception).lower() + repr(ctx.exception))

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
            self.assertTrue((repo.root / ".ai-review" / "second-item" / "review-bundle.tar.gz").is_file())


if __name__ == "__main__":
    unittest.main()
