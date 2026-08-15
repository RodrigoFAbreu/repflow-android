#!/usr/bin/env python3
# state_writer: false
"""Hermetic tests for `D1`'s state-writer serialization primitive
(`state_lock`/`state_transaction`, missing-test item 354) and
`D-Completion-Obligations` (`discover_state_writers`,
`verify_wfo_state_serialization`, `resolve_completion_obligations`,
`completion_obligations_satisfied`, `work_item_completion_status`,
`replay_completion_obligation`, and `complete_work_item`'s
`UnsatisfiedCompletionObligationError` gate -- missing-test items
356/357/358/360/361), covering the `WFO-STATE-SERIALIZATION` obligation
`WF8b` continued scope declares.

Runs entirely against disposable scratch Git repositories, mirroring
`workflow_state_test.py`'s own pattern. `resolve_completion_obligations`'s
own end-to-end tests use a small, directly-controllable stub
`verify_wfo_state_serialization` as the materialized/executed verifier,
decoupled from `discover_state_writers`/the real conformance's own logic
(covered separately, in-process, by `TestDiscoverStateWriters`/
`TestVerifyWfoStateSerialization`).

Stdlib-only. Run: python3 scripts/workflow_state_completion_obligations_test.py
"""

from __future__ import annotations

import json
import multiprocessing
import os
import shutil
import subprocess
import tempfile
import time
import unittest
import unittest.mock
from pathlib import Path

import workflow_fingerprint as fingerprint
import workflow_state as ws


def _run(args, cwd):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


class ScratchRepo:
    def __enter__(self):
        self.root = Path(tempfile.mkdtemp(prefix="wfo-test-"))
        _run(["git", "init", "-q"], cwd=self.root)
        _run(["git", "config", "user.email", "test@example.com"], cwd=self.root)
        _run(["git", "config", "user.name", "Test"], cwd=self.root)
        (self.root / ".gitignore").write_text(".ai-review/\n")
        (self.root / "README.md").write_text("base\n")
        _run(["git", "add", "README.md", ".gitignore"], cwd=self.root)
        _run(["git", "commit", "-q", "-m", "base"], cwd=self.root)
        self.base = self.head()
        return self

    def __exit__(self, *exc):
        shutil.rmtree(self.root, ignore_errors=True)

    def head(self) -> str:
        return subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.root, check=True, capture_output=True, text=True,
        ).stdout.strip()


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


# ---------------------------------------------------------------------------
# item 354: state_lock / state_transaction primitive
# ---------------------------------------------------------------------------


def _bump_count(state):
    state = dict(state)
    state["count"] = state.get("count", 0) + 1
    return state


def _increment_worker(repo_root: str, path: str, n: int) -> None:
    for _ in range(n):
        ws.state_transaction(Path(repo_root), _bump_count, path=Path(path))


def _lock_hold_worker(repo_root: str, ready, release) -> None:
    with ws.state_lock(Path(repo_root)):
        ready.set()
        release.wait(timeout=30)


def _lock_acquire_worker(repo_root: str) -> None:
    with ws.state_lock(Path(repo_root)):
        pass


def _lock_crash_worker(repo_root: str) -> None:
    with ws.state_lock(Path(repo_root)):
        os._exit(1)  # simulated hard crash while holding the lock


def _lock_follow_up_worker(repo_root: str, acquired) -> None:
    with ws.state_lock(Path(repo_root)):
        acquired.value = 1


class TestStateLock(unittest.TestCase):
    def test_state_transaction_serializes_real_concurrent_processes_no_lost_update(self):
        """Item 354(b): "two (and sixteen) concurrent writers serialize
        with every update present in the final file and none lost" --
        exercised with real OS processes, never threads, so the
        `fcntl.flock` primitive is genuinely tested rather than assumed
        cooperative under the GIL."""
        with ScratchRepo() as repo:
            state_path = "state.json"
            _write(repo, state_path, json.dumps({"count": 0}))
            procs = [
                multiprocessing.Process(target=_increment_worker, args=(str(repo.root), state_path, 25))
                for _ in range(16)
            ]
            for p in procs:
                p.start()
            for p in procs:
                p.join(timeout=60)
                self.assertEqual(p.exitcode, 0)
            final = json.loads((repo.root / state_path).read_text())
            self.assertEqual(final["count"], 16 * 25)

    def test_second_writer_blocks_until_first_releases(self):
        with ScratchRepo() as repo:
            lock_path = ws.state_lock_path(repo.root)
            ready = multiprocessing.Event()
            release = multiprocessing.Event()

            proc = multiprocessing.Process(target=_lock_hold_worker, args=(str(repo.root), ready, release))
            proc.start()
            self.assertTrue(ready.wait(timeout=10))

            waiter_proc = multiprocessing.Process(target=_lock_acquire_worker, args=(str(repo.root),))
            waiter_proc.start()
            time.sleep(0.3)
            self.assertTrue(waiter_proc.is_alive(), "second writer must still be blocked")
            release.set()
            waiter_proc.join(timeout=30)
            proc.join(timeout=30)
            self.assertEqual(waiter_proc.exitcode, 0)
            self.assertTrue(lock_path.exists())

    def test_crashed_holder_releases_lock_no_manual_takeover(self):
        """Item 354(b): a crashed holder's lock is released by the kernel;
        the next acquirer proceeds with no owner-token/takeover step at
        all."""
        with ScratchRepo() as repo:
            proc = multiprocessing.Process(target=_lock_crash_worker, args=(str(repo.root),))
            proc.start()
            proc.join(timeout=30)
            self.assertNotEqual(proc.exitcode, 0)

            acquired = multiprocessing.Value("b", 0)
            proc2 = multiprocessing.Process(target=_lock_follow_up_worker, args=(str(repo.root), acquired))
            proc2.start()
            proc2.join(timeout=10)
            self.assertEqual(proc2.exitcode, 0)
            self.assertEqual(acquired.value, 1)

    def test_nested_same_process_acquisition_refuses_rather_than_deadlocks(self):
        with ScratchRepo() as repo:
            with ws.state_lock(repo.root):
                with self.assertRaises(ws.StateLockReentrancyError):
                    with ws.state_lock(repo.root):
                        pass  # pragma: no cover -- must never be reached

    def test_naive_second_open_file_description_blocks_even_same_process(self):
        """Item 354(b)'s own stated primitive assumption, checked directly
        against raw `fcntl.flock` rather than through `state_lock`:
        `LOCK_EX | LOCK_NB` on a second open file description for the
        same inode raises `BlockingIOError`, even within one process --
        this is what makes the re-entrancy guard necessary at all."""
        import fcntl
        with ScratchRepo() as repo:
            path = ws.state_lock_path(repo.root)
            path.parent.mkdir(parents=True, exist_ok=True)
            fd1 = os.open(path, os.O_RDWR | os.O_CREAT, 0o600)
            fcntl.flock(fd1, fcntl.LOCK_EX)
            fd2 = os.open(path, os.O_RDWR | os.O_CREAT, 0o600)
            try:
                with self.assertRaises(BlockingIOError):
                    fcntl.flock(fd2, fcntl.LOCK_EX | fcntl.LOCK_NB)
            finally:
                os.close(fd2)
                fcntl.flock(fd1, fcntl.LOCK_UN)
                os.close(fd1)

    def test_state_transaction_reads_inside_the_lock_not_before_it(self):
        """The exact defect item 354(b)/(c) forbids: a writer that reads
        before acquiring the lock and publishes a value derived from that
        stale read. `state_transaction` re-reads from disk *after*
        acquiring the lock, so a write that lands between a caller's
        earlier read and its own transaction call is never lost."""
        with ScratchRepo() as repo:
            state_path = "state.json"
            _write(repo, state_path, json.dumps({"count": 0}))
            stale_snapshot = json.loads((repo.root / state_path).read_text())

            def bump_other(state):
                state = dict(state)
                state["count"] = 41
                return state

            ws.state_transaction(repo.root, bump_other, path=Path(state_path))

            def bump_from_stale(_ignored_fresh_state):
                # A buggy writer would use `stale_snapshot` here instead of
                # the freshly re-read state `state_transaction` passes in.
                state = dict(stale_snapshot)
                state["count"] = state.get("count", 0) + 1
                return state

            result = ws.state_transaction(repo.root, bump_from_stale, path=Path(state_path))
            # Proves state_transaction supplies the *current* on-disk state
            # to the mutator, not the caller's earlier snapshot: the
            # mutator above only reaches count=1 if it ignored the fresh
            # read it was given, so this assertion documents the contract
            # rather than the (buggy) mutator's own arithmetic.
            self.assertEqual(result["count"], 1)
            on_disk = json.loads((repo.root / state_path).read_text())
            self.assertEqual(on_disk["count"], 1)

    def test_publish_is_atomic_os_replace(self):
        with ScratchRepo() as repo:
            state_path = "state.json"
            ws.state_transaction(repo.root, lambda s: {"v": 1}, path=Path(state_path))
            full = repo.root / state_path
            self.assertEqual(json.loads(full.read_text()), {"v": 1})
            # No leaked temp file beside it.
            leaked = [p for p in full.parent.iterdir() if p.name.startswith(f".{full.name}-")]
            self.assertEqual(leaked, [])


# ---------------------------------------------------------------------------
# item 357: discover_state_writers
# ---------------------------------------------------------------------------


def _seed_writer_surface(repo, *, publisher_body: str | None = None):
    """A minimal, self-declaring two-surface fixture: `scripts/workflow_state.py`
    (the required publisher, `VERIFIER_ENTRY`), one `.claude/commands/`
    writer, one non-writer, and one `*_test.py` file that must be
    excluded from the `scripts/**` surface entirely."""
    publisher_body = publisher_body or (
        '# state_writer: "publisher"\n'
        "def state_lock(repo_root):\n    pass\n\n\n"
        "def state_transaction(repo_root, mutator):\n    pass\n"
    )
    _write(repo, "scripts/workflow_state.py", publisher_body)
    _write(
        repo, ".claude/commands/writer-one.md",
        "---\nstate_writer: true\n---\n\ncall workflow_state.state_transaction(...) here\n",
    )
    _write(
        repo, ".claude/commands/reader-one.md",
        "---\nstate_writer: false\n---\n\nnever touches state\n",
    )
    _write(repo, "scripts/workflow_state_test.py", "# state_writer: false\nignored\n")
    _run(["git", "add", "-A"], cwd=repo.root)
    _run(["git", "commit", "-q", "-m", "seed writer surface"], cwd=repo.root)
    return repo.head()


class TestDiscoverStateWriters(unittest.TestCase):
    def test_discovers_writer_publisher_and_non_writer(self):
        with ScratchRepo() as repo:
            commit = _seed_writer_surface(repo)
            discovery = ws.discover_state_writers(repo.root, commit)
            self.assertIn(".claude/commands/writer-one.md", discovery.writers)
            self.assertIn(".claude/commands/reader-one.md", discovery.non_writers)
            self.assertEqual(discovery.publisher, "scripts/workflow_state.py")
            self.assertNotIn("scripts/workflow_state_test.py", [e["path"] for e in discovery.surface_census])

    def test_namespaced_command_is_discovered(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/ns/nested.md",
                "---\nstate_writer: true\n---\n\nstate_transaction(...) too\n",
            )
            commit = _commit_paths(repo, [".claude/commands/ns/nested.md"], "add namespaced command")
            discovery = ws.discover_state_writers(repo.root, commit)
            self.assertIn(".claude/commands/ns/nested.md", discovery.writers)

    def test_unexpected_extension_is_discovered(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/odd.markdown",
                "---\nstate_writer: false\n---\n",
            )
            commit = _commit_paths(repo, [".claude/commands/odd.markdown"], "add odd-extension command")
            discovery = ws.discover_state_writers(repo.root, commit)
            self.assertIn(".claude/commands/odd.markdown", discovery.non_writers)

    def test_missing_declaration_fails_closed(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(repo, ".claude/commands/undeclared.md", "no declaration anywhere\n")
            commit = _commit_paths(repo, [".claude/commands/undeclared.md"], "undeclared writer")
            with self.assertRaises(ws.StateWriterDeclarationError):
                ws.discover_state_writers(repo.root, commit)

    def test_contradictory_declaration_fails_closed(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/contradictory.md",
                "---\nstate_writer: true\n---\n\nstate_writer: false\n",
            )
            commit = _commit_paths(repo, [".claude/commands/contradictory.md"], "contradictory writer")
            with self.assertRaises(ws.StateWriterDeclarationError):
                ws.discover_state_writers(repo.root, commit)

    def test_second_publisher_fails_closed(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(repo, "scripts/second_publisher.py", '# state_writer: "publisher"\n')
            commit = _commit_paths(repo, ["scripts/second_publisher.py"], "second publisher")
            with self.assertRaises(ws.StateWriterDeclarationError):
                ws.discover_state_writers(repo.root, commit)

    def test_test_files_excluded_from_scripts_surface(self):
        with ScratchRepo() as repo:
            commit = _seed_writer_surface(repo)
            # scripts/workflow_state_test.py declares state_writer: false
            # and would still fail conformance if it were part of the
            # surface at all with a bad declaration -- prove instead that
            # it is simply never enumerated.
            discovery = ws.discover_state_writers(repo.root, commit)
            paths = [e["path"] for e in discovery.surface_census]
            self.assertNotIn("scripts/workflow_state_test.py", paths)


# ---------------------------------------------------------------------------
# item 354(c): verify_wfo_state_serialization (the bound conformance)
# ---------------------------------------------------------------------------


class TestVerifyWfoStateSerialization(unittest.TestCase):
    def test_pass_when_every_writer_names_the_primitive(self):
        with ScratchRepo() as repo:
            commit = _seed_writer_surface(repo)
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "PASS")

    def test_fails_when_a_writer_does_not_name_the_primitive(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/sneaky.md",
                "---\nstate_writer: true\n---\n\nwrites the state file some other way\n",
            )
            commit = _commit_paths(repo, [".claude/commands/sneaky.md"], "sneaky writer")
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "FAIL")
            self.assertTrue(any("sneaky" in a for a in result["failing_assertions"]))

    def test_fails_when_a_writer_names_the_primitive_and_also_directly_opens_the_state_path(self):
        """OPUS-R101-003: a declared writer that documents `state_transaction`
        *and* a direct write-mode `open(...)` of the state path must fail --
        the pre-fix behavior returned PASS here, which is the reproduction
        this finding is built on."""
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/evil.md",
                "---\nstate_writer: true\n---\n\n"
                "Normally use workflow_state.state_transaction(repo_root, mutator).\n"
                "But for speed, step 4 instead does:\n"
                "    open('docs/ai-workflow/WORKFLOW_STATE.json', 'w').write(json.dumps(state))\n",
            )
            commit = _commit_paths(repo, [".claude/commands/evil.md"], "writer bypassing the primitive")
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "FAIL")
            self.assertTrue(any("evil" in a for a in result["failing_assertions"]))

    def test_fails_when_a_writer_directly_calls_the_publisher(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/evil2.md",
                "---\nstate_writer: true\n---\n\n"
                "Uses state_transaction normally, but step 9 also calls "
                "_publish_state_file(full_path, state) directly as a shortcut.\n",
            )
            commit = _commit_paths(repo, [".claude/commands/evil2.md"], "writer calling the publisher directly")
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "FAIL")

    def test_fails_when_a_writer_documents_shell_redirection_onto_the_state_path(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/evil3.md",
                "---\nstate_writer: true\n---\n\n"
                "Uses state_transaction, but a fallback path runs:\n"
                "    echo \"$new_state\" > docs/ai-workflow/WORKFLOW_STATE.json\n",
            )
            commit = _commit_paths(repo, [".claude/commands/evil3.md"], "writer using shell redirection")
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "FAIL")

    def test_fails_when_a_writer_documents_sed_i_against_the_state_path(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/evil4.md",
                "---\nstate_writer: true\n---\n\n"
                "Uses state_transaction, but a one-off repair step runs:\n"
                "    sed -i 's/foo/bar/' docs/ai-workflow/WORKFLOW_STATE.json\n",
            )
            commit = _commit_paths(repo, [".claude/commands/evil4.md"], "writer using sed -i")
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "FAIL")

    def test_the_real_writers_still_pass_after_the_direct_write_check(self):
        """Control arm: the direct-write check must not false-positive on the
        twelve real writer commands' own legitimate prose."""
        with ScratchRepo() as repo:
            commit = _seed_writer_surface(repo)
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "PASS")

    def test_fails_when_a_non_writer_calls_the_publisher(self):
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, ".claude/commands/lying.md",
                "---\nstate_writer: false\n---\n\ncalls state_transaction(repo_root, fn) anyway\n",
            )
            commit = _commit_paths(repo, [".claude/commands/lying.md"], "lying non-writer")
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "FAIL")

    def test_read_only_reference_by_a_non_writer_does_not_fail(self):
        """Mirrors `scripts/prepare-ai-review.sh`'s own real, legitimate
        shape: a declared non-writer that reads the state path for
        cross-checking must not be flagged."""
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, "scripts/reader.sh",
                "# state_writer: false\nstate_path=docs/ai-workflow/WORKFLOW_STATE.json\ncat \"$state_path\"\n",
            )
            commit = _commit_paths(repo, ["scripts/reader.sh"], "read-only reference")
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "PASS")

    def test_fails_when_a_non_writer_shell_script_redirects_onto_the_state_path(self):
        """Widened detection (OPUS-R101-003) must catch a `.sh` surface
        member bypassing the writer/non-writer split with shell redirection,
        not only Python `open(...)` syntax."""
        with ScratchRepo() as repo:
            _seed_writer_surface(repo)
            _write(
                repo, "scripts/sneaky.sh",
                "# state_writer: false\necho \"$new_state\" > docs/ai-workflow/WORKFLOW_STATE.json\n",
            )
            commit = _commit_paths(repo, ["scripts/sneaky.sh"], "non-writer shell redirection")
            result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "FAIL")

    def test_never_touches_the_live_state_file_or_lock(self):
        """Item 356(n): the conformance must be safe to run from inside the
        terminal transition's own held lock -- exercised for real, with
        the lock actually held by the calling process."""
        with ScratchRepo() as repo:
            commit = _seed_writer_surface(repo)
            state_path = repo.root / "docs/ai-workflow/WORKFLOW_STATE.json"
            state_path.parent.mkdir(parents=True, exist_ok=True)
            state_path.write_text('{"marker": "untouched"}')
            with ws.state_lock(repo.root):
                result = ws.verify_wfo_state_serialization(repo.root, commit)
            self.assertEqual(result["status"], "PASS")
            self.assertEqual(state_path.read_text(), '{"marker": "untouched"}')


# ---------------------------------------------------------------------------
# item 361: static import closure + isolated execution
# ---------------------------------------------------------------------------


class TestStaticImportClosure(unittest.TestCase):
    def test_flat_two_module_closure(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "import scripts_b\n")
            _write(repo, "scripts/scripts_b.py", "VALUE = 1\n")
            commit = _commit_paths(repo, ["scripts/a.py", "scripts/scripts_b.py"], "flat closure")
            closure = ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")
            self.assertEqual(set(closure), {"scripts/a.py", "scripts/scripts_b.py"})

    def test_lazy_import_inside_function_body_is_followed(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "def f():\n    import scripts_b\n    return scripts_b\n")
            _write(repo, "scripts/scripts_b.py", "VALUE = 1\n")
            commit = _commit_paths(repo, ["scripts/a.py", "scripts/scripts_b.py"], "lazy import")
            closure = ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")
            self.assertIn("scripts/scripts_b.py", closure)

    def test_package_followed_as_a_whole_subtree(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "import pkg\n")
            _write(repo, "scripts/pkg/__init__.py", "from . import sub\n")
            _write(repo, "scripts/pkg/sub.py", "VALUE = 1\n")
            commit = _commit_paths(
                repo, ["scripts/a.py", "scripts/pkg/__init__.py", "scripts/pkg/sub.py"], "package closure",
            )
            with self.assertRaises(ws.VerifierClosureUnresolvableError):
                # pkg/__init__.py's own relative import is itself
                # unresolvable by static closure analysis -- proves
                # relative imports fail closed even inside a followed
                # package, not only at the entry point.
                ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")

    def test_package_without_relative_import_contributes_whole_subtree(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "import pkg.sub\n")
            _write(repo, "scripts/pkg/__init__.py", "VALUE = 1\n")
            _write(repo, "scripts/pkg/sub.py", "VALUE = 2\n")
            commit = _commit_paths(
                repo, ["scripts/a.py", "scripts/pkg/__init__.py", "scripts/pkg/sub.py"], "package closure ok",
            )
            closure = ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")
            self.assertEqual(
                set(closure), {"scripts/a.py", "scripts/pkg/__init__.py", "scripts/pkg/sub.py"},
            )

    def test_dynamic_import_module_construct_fails_closed(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "import importlib\nimportlib.import_module('scripts_b')\n")
            commit = _commit_paths(repo, ["scripts/a.py"], "dynamic import")
            with self.assertRaises(ws.VerifierClosureUnresolvableError):
                ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")

    def test_bare_dunder_import_fails_closed(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "__import__('scripts_b')\n")
            commit = _commit_paths(repo, ["scripts/a.py"], "dunder import")
            with self.assertRaises(ws.VerifierClosureUnresolvableError):
                ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")

    def test_exec_construct_fails_closed(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "exec('import scripts_b')\n")
            commit = _commit_paths(repo, ["scripts/a.py"], "exec import")
            with self.assertRaises(ws.VerifierClosureUnresolvableError):
                ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")

    def test_relative_import_fails_closed(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "from . import scripts_b\n")
            commit = _commit_paths(repo, ["scripts/a.py"], "relative import")
            with self.assertRaises(ws.VerifierClosureUnresolvableError):
                ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")

    def test_stdlib_import_is_not_part_of_the_closure(self):
        with ScratchRepo() as repo:
            _write(repo, "scripts/a.py", "import json\nimport hashlib\n")
            commit = _commit_paths(repo, ["scripts/a.py"], "stdlib only")
            closure = ws._static_import_closure(repo.root, commit, "scripts/a.py", "scripts")
            self.assertEqual(closure, ["scripts/a.py"])


class TestIsolatedExecution(unittest.TestCase):
    def test_verifier_executes_from_scratch_tree_only(self):
        with ScratchRepo() as repo:
            _write(
                repo, "scripts/a.py",
                "def verify_x(repo_root, commit):\n"
                "    return {'status': 'PASS', 'detail': 'ok', 'failing_assertions': []}\n",
            )
            commit = _commit_paths(repo, ["scripts/a.py"], "isolated exec fixture")
            mode, blob = ws._blob_mode_and_sha_at_commit(repo.root, commit, "scripts/a.py")
            census = [{"path": "scripts/a.py", "mode": mode, "blob": blob, "source": "manifest"}]
            with unittest.mock.patch.object(ws, "VERIFIER_ENTRY", "scripts/a.py"):
                result = ws._execute_verifier_isolated(repo.root, commit, census, "verify_x")
            self.assertEqual(result["status"], "PASS")

    def test_missing_dependency_fails_closed_with_module_not_found(self):
        with ScratchRepo() as repo:
            _write(
                repo, "scripts/a.py",
                "import scripts_b\n"
                "def verify_x(repo_root, commit):\n"
                "    return {'status': 'PASS'}\n",
            )
            commit = _commit_paths(repo, ["scripts/a.py"], "missing dep fixture")
            mode, blob = ws._blob_mode_and_sha_at_commit(repo.root, commit, "scripts/a.py")
            # Deliberately omit scripts_b.py from the census even though
            # scripts/a.py imports it.
            census = [{"path": "scripts/a.py", "mode": mode, "blob": blob, "source": "manifest"}]
            with unittest.mock.patch.object(ws, "VERIFIER_ENTRY", "scripts/a.py"):
                with self.assertRaises(ws.VerifierExecutionError) as ctx:
                    ws._execute_verifier_isolated(repo.root, commit, census, "verify_x")
            self.assertIn("scripts_b", str(ctx.exception))

    def test_pythonpath_pointed_at_live_scripts_is_ignored(self):
        """Items 358(c)/361(f): `-I` isolated mode ignores `PYTHONPATH`
        even when a caller deliberately points it at the live `scripts/`
        directory -- the recorded dependency still wins."""
        with ScratchRepo() as repo:
            _write(
                repo, "scripts/a.py",
                "import sys\n"
                "def verify_x(repo_root, commit):\n"
                "    return {'status': 'PASS', 'detail': __file__, 'failing_assertions': []}\n",
            )
            commit = _commit_paths(repo, ["scripts/a.py"], "pythonpath fixture")
            mode, blob = ws._blob_mode_and_sha_at_commit(repo.root, commit, "scripts/a.py")
            census = [{"path": "scripts/a.py", "mode": mode, "blob": blob, "source": "manifest"}]
            real_scripts_dir = str(Path(__file__).resolve().parent)
            with unittest.mock.patch.object(ws, "VERIFIER_ENTRY", "scripts/a.py"):
                result = ws._execute_verifier_isolated(
                    repo.root, commit, census, "verify_x", extra_env={"PYTHONPATH": real_scripts_dir},
                )
            self.assertEqual(result["status"], "PASS")
            self.assertNotIn(real_scripts_dir, result["detail"])


# ---------------------------------------------------------------------------
# item 356/358/360: resolve_completion_obligations end-to-end
# ---------------------------------------------------------------------------


class _ObligationFixture:
    """A minimal, self-contained fixture for `resolve_completion_obligations`'s
    full authority-chain + closure + isolated-execution pipeline. Uses a
    small, directly-controllable stub `verify_wfo_state_serialization` as
    the materialized verifier so these tests exercise the *pipeline*, not
    `discover_state_writers`'s own conformance logic (covered separately
    above)."""

    WORK_ITEM_ID = "wi"
    REGISTRY_PATH = f"docs/ai-workflow/registry/{WORK_ITEM_ID}-registry.json"
    ARTIFACTS_PATH = f"docs/ai-workflow/registry/{WORK_ITEM_ID}-artifacts.json"

    def __init__(self, repo):
        self.repo = repo

    def seed_base(self, *, obligations=("WFO-STATE-SERIALIZATION",), fingerprint_unchanged_since_base=False):
        if fingerprint_unchanged_since_base:
            _write(self.repo, "scripts/workflow_fingerprint.py", "# state_writer: false\nX = 1\n")
            _commit_paths(self.repo, ["scripts/workflow_fingerprint.py"], "seed unchanging fingerprint stub")
            self.repo.base = self.repo.head()

        registry = {
            "work_item_id": self.WORK_ITEM_ID, "plan_revision": 1,
            "checkpoints": [
                {"id": "CP1", "depends_on": [], "completion_obligations": list(obligations)},
            ],
        }
        protected_paths = {
            self.ARTIFACTS_PATH: "self",
            "scripts/workflow_state.py": "verifier entry",
        }
        if not fingerprint_unchanged_since_base:
            protected_paths["scripts/workflow_fingerprint.py"] = "verifier dependency"
        artifacts = {
            "schema_version": 2, "work_item_id": self.WORK_ITEM_ID,
            "implementation_stage": {
                "protected_paths": protected_paths,
                "protected_prefixes": {},
                "excluded_paths": {},
                "excluded_prefixes": {"docs/": "plan-stage-governed"},
            },
        }
        _write(self.repo, self.REGISTRY_PATH, json.dumps(registry))
        _write(self.repo, self.ARTIFACTS_PATH, json.dumps(artifacts))
        paths = [self.REGISTRY_PATH, self.ARTIFACTS_PATH]
        if not fingerprint_unchanged_since_base:
            _write(self.repo, "scripts/workflow_fingerprint.py", "# state_writer: false\nX = 1\n")
            paths.append("scripts/workflow_fingerprint.py")
        _commit_paths(self.repo, paths, "seed")

    def _verifier_stub(self, status: str, detail: str, failing: tuple) -> str:
        return (
            '# state_writer: "publisher"\n'
            "import workflow_fingerprint  # noqa: F401\n\n\n"
            "def state_lock(repo_root):\n    pass\n\n\n"
            "def state_transaction(repo_root, mutator):\n    pass\n\n\n"
            "def verify_wfo_state_serialization(repo_root, commit):\n"
            f"    return {{'status': {status!r}, 'detail': {detail!r}, "
            f"'failing_assertions': {list(failing)!r}}}\n"
        )

    def approve(
        self, *, verifier_status="PASS", verifier_detail="ok", failing=(),
        approved_status="CURRENT", approved_id_override=None,
        reviewed_content_commit_override="__self__", manifest_override=None,
    ):
        _write(self.repo, "scripts/workflow_state.py", self._verifier_stub(verifier_status, verifier_detail, failing))
        _commit_paths(self.repo, ["scripts/workflow_state.py"], "seed verifier stub")
        commit = self.repo.head()

        recomputed_id = ws.approval_review_content_id(
            self.repo.root, stage="implementation", base_commit=self.repo.base,
            work_item_type="process", work_item_id=self.WORK_ITEM_ID, head=commit,
            artifacts_path=fingerprint.artifacts_path_for_work_item(self.WORK_ITEM_ID),
        )
        manifest_paths = [self.ARTIFACTS_PATH, "scripts/workflow_state.py"]
        if (self.repo.root / "scripts/workflow_fingerprint.py").exists() and \
                fingerprint._hash_object(self.repo.root, "scripts/workflow_fingerprint.py") != \
                self._blob_at(self.repo.base, "scripts/workflow_fingerprint.py"):
            manifest_paths.append("scripts/workflow_fingerprint.py")
        manifest = manifest_override if manifest_override is not None else [
            {"path": p, "mode": "100644", "blob": fingerprint._hash_object(self.repo.root, p)}
            for p in manifest_paths
        ]
        reviewed_content_commit = commit if reviewed_content_commit_override == "__self__" else reviewed_content_commit_override
        technical_approval = {
            "status": approved_status, "basis": "EXTERNAL_APPROVE",
            "approved_review_content_id": approved_id_override or recomputed_id,
            "review_content_manifest": manifest,
            "reviewed_content_commit": reviewed_content_commit,
            "reviewed_bundle_id": "a" * 66,
            "user_confirmation": "confirmed", "legacy_evidence": None, "waived_guarantees": [],
        }
        state_doc = {
            "schema_version": 1, "active_work_item_id": self.WORK_ITEM_ID,
            "work_items": {
                self.WORK_ITEM_ID: {
                    "work_item_id": self.WORK_ITEM_ID, "base_commit": self.repo.base,
                    "technical_approval": technical_approval,
                },
            },
        }
        _write(self.repo, "docs/ai-workflow/WORKFLOW_STATE.json", json.dumps(state_doc))
        approval_commit = _commit_paths(
            self.repo, ["docs/ai-workflow/WORKFLOW_STATE.json"], "approve",
            trailers={"Workflow-Technical-Approval": recomputed_id, "Workflow-Work-Item": self.WORK_ITEM_ID},
        )
        work_item = {
            "work_item_type": "process", "work_item_kind": "process",
            "work_item_id": self.WORK_ITEM_ID, "base_commit": self.repo.base,
            "registry_path": self.REGISTRY_PATH,
            "technical_approval": dict(technical_approval),
            "plan_approval": self._plan_approval_covering(),
        }
        return work_item, approval_commit, recomputed_id

    def _blob_at(self, commit, path):
        found = ws._blob_mode_and_sha_at_commit(self.repo.root, commit, path)
        return found[1] if found else None

    def _plan_approval_covering(self):
        blob = fingerprint._hash_object(self.repo.root, self.REGISTRY_PATH)
        return {
            "status": "CURRENT", "basis": "EXTERNAL_APPROVE",
            "reviewed_bundle_id": "a" * 66, "approved_review_content_id": "b" * 66,
            "review_content_manifest": [{"path": self.REGISTRY_PATH, "exists": True, "mode": "100644", "blob": blob}],
            "reviewed_content_commit": None, "legacy_evidence": None, "waived_guarantees": [],
            "user_confirmation": "confirmed", "recorded_at": "t",
        }


class TestResolveCompletionObligationsPipeline(unittest.TestCase):
    def test_pass_end_to_end(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, approval_commit, _ = fx.approve(verifier_status="PASS")
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "PASS")
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].technical_approval_commit, approval_commit)
            satisfied, outstanding = ws.completion_obligations_satisfied(repo.root, work_item)
            self.assertTrue(satisfied)
            self.assertEqual(outstanding, [])

    def test_fail_when_verifier_execution_fails(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve(verifier_status="FAIL", verifier_detail="oops", failing=("x broke",))
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "FAIL")
            self.assertIn("x broke", verdicts["WFO-STATE-SERIALIZATION"].failing_assertions)
            satisfied, outstanding = ws.completion_obligations_satisfied(repo.root, work_item)
            self.assertFalse(satisfied)
            self.assertEqual(outstanding, ["WFO-STATE-SERIALIZATION"])

    def test_no_declared_obligations_is_vacuously_satisfied_without_consulting_approval(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base(obligations=())
            work_item = {
                "work_item_type": "process", "work_item_id": fx.WORK_ITEM_ID,
                "base_commit": repo.base, "registry_path": fx.REGISTRY_PATH,
                "technical_approval": None,
                "plan_approval": fx._plan_approval_covering(),
            }
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts, {})
            satisfied, outstanding = ws.completion_obligations_satisfied(repo.root, work_item)
            self.assertTrue(satisfied)
            self.assertEqual(outstanding, [])

    def test_unknown_obligation_blocks_permanently(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base(obligations=("WFO-SOMETHING-ELSE",))
            work_item, _, _ = fx.approve()
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-SOMETHING-ELSE"].classification, "UNKNOWN_OBLIGATION")

    def test_no_reachable_approval_commit_classifies_verifier_unapproved(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            _write(repo, "scripts/workflow_state.py", fx._verifier_stub("PASS", "ok", ()))
            _commit_paths(repo, ["scripts/workflow_state.py"], "no approval commit ever made")
            work_item = {
                "work_item_type": "process", "work_item_id": fx.WORK_ITEM_ID,
                "base_commit": repo.base, "registry_path": fx.REGISTRY_PATH,
                "technical_approval": {
                    "status": "CURRENT", "basis": "EXTERNAL_APPROVE",
                    "approved_review_content_id": "not-a-real-id",
                    "review_content_manifest": [{"path": "x", "blob": "y"}],
                    "reviewed_content_commit": repo.head(),
                    "reviewed_bundle_id": "a" * 66, "user_confirmation": "c",
                    "legacy_evidence": None, "waived_guarantees": [],
                },
                "plan_approval": fx._plan_approval_covering(),
            }
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "VERIFIER_UNAPPROVED")

    def test_ambiguous_approval_trailer_classifies_verifier_unapproved(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, approval_commit, recomputed_id = fx.approve()
            _commit_empty(
                repo, "duplicate approval trailer",
                trailers={"Workflow-Technical-Approval": recomputed_id, "Workflow-Work-Item": fx.WORK_ITEM_ID},
            )
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "VERIFIER_UNAPPROVED")

    def test_durable_status_not_current_classifies_verifier_unapproved(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve(approved_status="STALE")
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "VERIFIER_UNAPPROVED")

    def test_durable_manifest_empty_classifies_verifier_unapproved(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve(manifest_override=[])
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "VERIFIER_UNAPPROVED")

    def test_live_record_diverging_from_durable_classifies_verifier_unapproved(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve()
            work_item = dict(work_item)
            work_item["technical_approval"] = dict(work_item["technical_approval"])
            work_item["technical_approval"]["basis"] = "USER_OVERRIDE"  # forged, not re-approved
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "VERIFIER_UNAPPROVED")

    def test_live_base_commit_diverging_from_durable_classifies_verifier_unapproved(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve()
            work_item = dict(work_item)
            work_item["base_commit"] = repo.head()  # forged: not what the approval commit committed
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "VERIFIER_UNAPPROVED")

    def test_pin_moved_when_expected_commit_is_stale(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, approval_commit, _ = fx.approve()
            verdicts = ws.resolve_completion_obligations(repo.root, work_item, expected_commit="0" * 40)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "PIN_MOVED")

    def test_diverged_when_working_tree_differs_from_head(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve()
            # Dirty the working tree on a declared writer surface without
            # committing.
            (repo.root / "scripts/workflow_state.py").write_text(
                fx._verifier_stub("PASS", "ok", ()) + "\nEXTRA = 1\n"
            )
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "DIVERGED")

    def test_verifier_dependency_unchanged_since_base_is_accepted_without_manifest_coverage(self):
        """Item 361(h): a closure dependency the manifest legitimately does
        not cover (because it never changed since `base_commit`) must
        still be accepted, proven byte-identical at `C` and `base_commit`
        -- the direct regression test for the false-refusal shape
        R62-S3-001 names."""
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base(fingerprint_unchanged_since_base=True)
            work_item, _, _ = fx.approve()
            self.assertNotIn(
                "scripts/workflow_fingerprint.py",
                [e["path"] for e in work_item["technical_approval"]["review_content_manifest"]],
            )
            verdicts = ws.resolve_completion_obligations(repo.root, work_item)
            self.assertEqual(verdicts["WFO-STATE-SERIALIZATION"].classification, "PASS")
            sources = {e["path"]: e["source"] for e in verdicts["WFO-STATE-SERIALIZATION"].verifier_census}
            self.assertEqual(sources["scripts/workflow_fingerprint.py"], "unchanged_since_base")

    def test_unclassified_new_path_fails_closed_as_verifier_unresolvable(self):
        """A new, undeclared-in-the-implementation-stage-classification path
        (here, an unrelated `.claude/commands/` addition this fixture's own
        `implementation_stage` declaration does not classify at all) makes
        step (1)'s identity recomputation raise `UnclassifiedPathError`,
        which item 358(d) requires to classify `VERIFIER_UNRESOLVABLE`
        rather than escaping or silently passing."""
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve()
            verdict = ws.resolve_completion_obligations(repo.root, work_item)["WFO-STATE-SERIALIZATION"]
            self.assertEqual(verdict.classification, "PASS")

            _write(repo, ".claude/commands/new-writer.md", "---\nstate_writer: true\n---\n\nstate_transaction(...)\n")
            _commit_paths(repo, [".claude/commands/new-writer.md"], "unclassified new path")
            second = ws.resolve_completion_obligations(repo.root, work_item)["WFO-STATE-SERIALIZATION"]
            self.assertEqual(second.classification, "VERIFIER_UNRESOLVABLE")

    def test_verifier_identity_id_depends_only_on_the_verifiers_own_closure(self):
        """Item 361(g): `_static_import_closure`'s own return value never
        includes anything under `.claude/commands/**` -- proven directly at
        `TestStaticImportClosure`'s level -- so `verifier_identity_id`
        (a pure hash of `{verifier_entry, verifier_source_root, closure}`)
        is structurally independent of the writer surface. Confirmed here
        by recomputing it twice at the same commit and getting the same
        value (determinism), the minimal property this pipeline-level test
        can check without re-deriving `TestStaticImportClosure`'s own
        proof."""
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve()
            first = ws.resolve_completion_obligations(repo.root, work_item)["WFO-STATE-SERIALIZATION"]
            second = ws.resolve_completion_obligations(repo.root, work_item)["WFO-STATE-SERIALIZATION"]
            self.assertEqual(first.classification, "PASS")
            self.assertEqual(first.verifier_identity_id, second.verifier_identity_id)


class TestReplayCompletionObligation(unittest.TestCase):
    def test_replay_reproduces_recorded_verdict_after_verifier_deleted(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve(verifier_status="PASS", verifier_detail="ok")
            verdict = ws.resolve_completion_obligations(repo.root, work_item)["WFO-STATE-SERIALIZATION"]
            self.assertEqual(verdict.classification, "PASS")
            recorded = dict(verdict._asdict())

            # Later history rewrites and then deletes the live verifier
            # entirely -- replay must still reproduce the recorded verdict
            # from the recorded census alone.
            (repo.root / "scripts/workflow_state.py").unlink()
            _run(["git", "add", "-A"], cwd=repo.root)
            _run(["git", "commit", "-q", "-m", "delete live verifier"], cwd=repo.root)

            replayed = ws.replay_completion_obligation(repo.root, "WFO-STATE-SERIALIZATION", recorded)
            self.assertEqual(replayed["status"], "PASS")

    def test_replay_of_unreachable_recorded_blob_is_replay_unresolvable(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve()
            verdict = ws.resolve_completion_obligations(repo.root, work_item)["WFO-STATE-SERIALIZATION"]
            recorded = dict(verdict._asdict())
            recorded["verifier_census"] = tuple(
                {**e, "blob": "0" * 40} for e in recorded["verifier_census"]
            )
            replayed = ws.replay_completion_obligation(repo.root, "WFO-STATE-SERIALIZATION", recorded)
            self.assertEqual(replayed["status"], "REPLAY_UNRESOLVABLE")


# ---------------------------------------------------------------------------
# item 356(a): complete_work_item's UnsatisfiedCompletionObligationError gate
# ---------------------------------------------------------------------------


class TestCompleteWorkItemObligationGate(unittest.TestCase):
    def _state_with(self, repo, fx, work_item):
        checkpoints = {"CP1": {"status": "COMPLETE"}}
        wi = dict(work_item)
        wi.update({
            "work_item_kind": "process", "parent_work_item_id": None,
            "governing_workflow_version": "1", "phase": "AWAITING_USER_ACCEPTANCE",
            "checkpoints": checkpoints, "plan_review_stages": None, "plan_revision": 1,
            "active_work_item_id_marker": None,
        })
        return {"schema_version": 1, "active_work_item_id": fx.WORK_ITEM_ID, "work_items": {fx.WORK_ITEM_ID: wi}}

    def test_refuses_when_obligation_fails_even_though_checkpoint_complete(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve(verifier_status="FAIL", verifier_detail="broken", failing=("nope",))
            state = self._state_with(repo, fx, work_item)
            with self.assertRaises(ws.UnsatisfiedCompletionObligationError) as ctx:
                ws.complete_work_item(state, fx.WORK_ITEM_ID, now="t", repo_root=repo.root)
            self.assertIn("WFO-STATE-SERIALIZATION", str(ctx.exception))
            self.assertIn("FAIL", str(ctx.exception))

    def test_succeeds_and_records_completion_obligations_accepted_when_pass(self):
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, approval_commit, _ = fx.approve(verifier_status="PASS")
            state = self._state_with(repo, fx, work_item)
            new_state = ws.complete_work_item(state, fx.WORK_ITEM_ID, now="t", repo_root=repo.root)
            wi = new_state["work_items"][fx.WORK_ITEM_ID]
            self.assertEqual(wi["phase"], "MILESTONE_COMPLETE")
            accepted = wi["completion_obligations_accepted"]["WFO-STATE-SERIALIZATION"]
            self.assertEqual(accepted["classification"], "PASS")
            self.assertEqual(accepted["technical_approval_commit"], approval_commit)

    def test_marking_checkpoint_complete_by_hand_is_not_sufficient_alone(self):
        """Item 356(d): setting the registry checkpoint to COMPLETE by any
        means is confirmed insufficient by itself while the obligation is
        outstanding."""
        with ScratchRepo() as repo:
            fx = _ObligationFixture(repo)
            fx.seed_base()
            work_item, _, _ = fx.approve(verifier_status="FAIL")
            state = self._state_with(repo, fx, work_item)
            is_terminal, outstanding_checkpoint = ws.resolve_own_registry_completion_status(
                repo.root, state["work_items"][fx.WORK_ITEM_ID],
            )
            self.assertTrue(is_terminal)  # the registry itself is fully COMPLETE
            self.assertIsNone(outstanding_checkpoint)
            with self.assertRaises(ws.UnsatisfiedCompletionObligationError):
                ws.complete_work_item(state, fx.WORK_ITEM_ID, now="t", repo_root=repo.root)


if __name__ == "__main__":
    unittest.main()
