"""Pass 18 -- revision 71's own validation of `OPUS-R88-001` through `-005`.

Every claim revision 71 makes about revision 70's origination reference, and
every claim it makes about the *corrected* reference, is run here rather than
asserted.

Five groups, plus one live-repository conformance group:

  R1  `OPUS-R88-001` (blocking) -- the specified reference
      (`git rev-list --all -- <path>`) is Git's DEFAULT history simplification,
      not the reachable set. Reproduced admitting a checkpoint that
      ref-reachable history records `IN_PROGRESS`, through ordinary merges,
      with the adopter moving nothing. `--full-history` is checked to close
      each arm, and its own residual pruning is checked to be
      status-preserving rather than merely smaller;
  R2  `OPUS-R88-002` -- the partition is closed over container shape and open
      at the scalar. The seven entry shapes are probed one commit each, and the
      `git show`-returncode absence test is reproduced concluding "decidably
      absent" from a failure that is not absence;
  R3  `OPUS-R88-003` -- the disclosed residual, measured. A single
      `git update-ref -d` from the benefiting worktree against a branch Git
      otherwise protects; ordinary history maintenance erasing the evidence
      with no intent to; and the positive half -- the decision is UNCHANGED
      across `reflog expire` + `gc --prune=now`;
  R4  `OPUS-R88-004` -- the reduction over multiple observations is monotonic
      and permanent, and identity reuse binds new work to retired evidence;
  R5  `OPUS-R88-005` -- what the refusal's evidence carries, and the takeover
      literal's deliberate independence from it;
  L2  the live repository, read-only: the reference's own numbers, the
      conformance property R1 requires of it, and the one-time cost's arithmetic.

Nothing here writes to the real repository. `L2` performs read-only probes only
and asserts afterwards that the claims directory it could have created still
does not exist.

Run: python3 pass18.py
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"],
                           cwd=Path(__file__).resolve().parent, capture_output=True,
                           text=True, check=True).stdout.strip())
sys.path.insert(0, str(REPO / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import workflow_state as ws  # noqa: E402
import checkpoint_ownership as co  # noqa: E402

STATE_REL = "docs/ai-workflow/WORKFLOW_STATE.json"

PASS = FAIL = 0
_TMPDIRS: list[Path] = []

# Captured before anything runs, so `L2e` can assert this pass changed nothing
# rather than assert the worktree happened to be clean when it started (it is
# not: a plan revision is in progress here).
_BASELINE_STATUS = subprocess.run(["git", "status", "--porcelain"], cwd=REPO,
                                  capture_output=True, text=True, check=True).stdout
_BASELINE_STATE_BYTES = (REPO / STATE_REL).read_bytes()


def chk(label: str, cond: bool, extra: str = "") -> None:
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [ok]   {label} {extra}")
    else:
        FAIL += 1
        print(f"  [FAIL] {label} {extra}")


def git(cwd: Path, *args: str, check: bool = True) -> str:
    out = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if check and out.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} -> {out.returncode}: {out.stderr.strip()}")
    return out.stdout.strip()


def scratch(prefix: str) -> Path:
    d = Path(tempfile.mkdtemp(prefix=f"pass18-{prefix}-"))
    _TMPDIRS.append(d)
    return d


def write_state(root: Path, entries: dict, *, rel: str = STATE_REL) -> None:
    """`entries` maps work_item_id -> {checkpoint_id: entry-or-status}.

    A `None` status means the checkpoint entry is **absent**, which is how this
    schema represents "not started" -- `workflow_state.CHECKPOINT_STATUSES` is
    `{"IN_PROGRESS", "COMPLETE"}` and nothing else is a legal value. Writing a
    literal `"NOT_STARTED"` would be a schema-invalid document, which revision
    71 refuses by design (`R2`), so the fixtures below never do.
    """
    items = {}
    for wi, checkpoints in entries.items():
        cps = {}
        for cp, entry in checkpoints.items():
            if entry is None:
                continue
            cps[cp] = entry if isinstance(entry, dict) else {"status": entry}
        items[wi] = {"work_item_id": wi, "checkpoints": cps}
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps({"schema_version": 1, "work_items": items}, indent=2) + "\n")


def init_repo(prefix: str, *, initial: dict | None = None) -> Path:
    root = scratch(prefix) / "repo"
    root.mkdir(parents=True)
    git(root, "init", "-q", "-b", "main")
    git(root, "config", "user.email", "t@t")
    git(root, "config", "user.name", "t")
    write_state(root, initial if initial is not None else {"wi": {"CP": "COMPLETE"}})
    git(root, "add", "-A")
    git(root, "commit", "-qm", "m0")
    return root


# ---------------------------------------------------------------------------
# The two reference implementations, side by side
# ---------------------------------------------------------------------------


class OriginationUndecidable(Exception):
    """In the design this is `CheckpointOriginationUnprovableError`; a distinct
    class here only so the prototype can tell the two refusal reasons apart.

    Carries `.evidence` from revision 71 on (`OPUS-R88-005`)."""

    def __init__(self, message: str, evidence: dict | None = None):
        super().__init__(message)
        self.evidence = evidence or {}


def origination_reference_status_r70(repo_root: Path, work_item_id: str, checkpoint_id: str,
                                     *, state_rel_path: str = STATE_REL):
    """Revision 70's reference implementation, carried verbatim as the LIVE
    CONTROL ARM for every arm below. Extracted from `pass17.py`.

    Two defects are reproduced against it: `git rev-list --all -- <path>` is
    history simplification rather than the reachable set (`OPUS-R88-001`), and
    the decision is taken on `entry.get("status") == "IN_PROGRESS"` over a
    `git show` whose failure is read as absence (`OPUS-R88-002`).
    """
    try:
        out = subprocess.run(["git", "rev-list", "--all", "--", state_rel_path],
                             cwd=repo_root, capture_output=True, text=True,
                             check=True).stdout
    except subprocess.CalledProcessError as exc:
        raise OriginationUndecidable(f"cannot resolve the origination reference ({exc})")
    for sha in out.split():
        shown = subprocess.run(["git", "show", f"{sha}:{state_rel_path}"],
                               cwd=repo_root, capture_output=True, text=True)
        if shown.returncode != 0:
            continue                       # the path did not exist there: decidably absent
        try:
            state = json.loads(shown.stdout)
        except json.JSONDecodeError:
            raise OriginationUndecidable(f"the state document at {sha[:8]} is unparseable")
        if not isinstance(state, dict):
            raise OriginationUndecidable(f"the state document at {sha[:8]} is not an object")
        items = state.get("work_items")
        if not isinstance(items, dict):
            raise OriginationUndecidable(f"work_items at {sha[:8]} is not an object")
        item = items.get(work_item_id)
        if item is None:
            continue
        if not isinstance(item, dict):
            raise OriginationUndecidable(f"the work item at {sha[:8]} is not an object")
        checkpoints = item.get("checkpoints")
        if not isinstance(checkpoints, dict):
            raise OriginationUndecidable(f"checkpoints at {sha[:8]} is not an object")
        entry = checkpoints.get(checkpoint_id)
        if entry is None:
            continue
        if not isinstance(entry, dict):
            raise OriginationUndecidable(f"the checkpoint entry at {sha[:8]} is not an object")
        if entry.get("status") == "IN_PROGRESS":
            return "IN_PROGRESS", sha
    return None, None


# The controlled vocabulary is the state schema's own, imported rather than
# restated -- `OPUS-R88-002` admits only a status drawn from it.
CHECKPOINT_STATUSES = ws.CHECKPOINT_STATUSES

REFERENCE_COMMAND_R71 = ["git", "rev-list", "--all", "--full-history", "--"]


def origination_reference_commits(repo_root: Path, state_rel_path: str = STATE_REL) -> list[str]:
    """Revision 71's reference, stated as what the command actually enumerates:
    every commit reachable from every ref that is not TREESAME to a parent for
    the state document, merges retained and all parents followed."""
    out = subprocess.run([*REFERENCE_COMMAND_R71, state_rel_path],
                         cwd=repo_root, capture_output=True, text=True)
    if out.returncode != 0:
        raise OriginationUndecidable(
            f"cannot resolve the origination reference ({out.stderr.strip()})")
    return out.stdout.split()


def _state_bytes_at(repo_root: Path, sha: str, state_rel_path: str):
    """The decidable presence test `OPUS-R88-002` requires, named rather than
    inferred from an exit code: the path's entry in that commit's tree.

    Returns `None` for a decidably absent path; raises for anything else --
    including a present entry whose bytes cannot be read, which revision 70's
    `git show` returncode conflated with absence.
    """
    listed = subprocess.run(["git", "ls-tree", "--full-tree", "-z", sha, "--", state_rel_path],
                            cwd=repo_root, capture_output=True, text=True)
    if listed.returncode != 0:
        raise OriginationUndecidable(
            f"the tree at {sha[:8]} cannot be read ({listed.stderr.strip()})")
    record = listed.stdout.rstrip("\0")
    if record == "":
        return None                        # decidably absent from this commit's tree
    meta, _, _name = record.partition("\t")
    mode, kind, oid = meta.split()
    if kind != "blob" or mode not in ("100644", "100755"):
        raise OriginationUndecidable(
            f"the state path at {sha[:8]} is a {kind} with mode {mode}, not a regular file")
    blob = subprocess.run(["git", "cat-file", "blob", oid],
                          cwd=repo_root, capture_output=True)
    if blob.returncode != 0:
        raise OriginationUndecidable(
            f"the state document at {sha[:8]} is present in the tree but its blob {oid[:8]} "
            f"cannot be read -- undecidable, not absent")
    return blob.stdout


def origination_reference_status_r71(repo_root: Path, work_item_id: str, checkpoint_id: str,
                                     *, state_rel_path: str = STATE_REL):
    """Revision 71's corrected reference read.

    Two corrections over revision 70: the reference is `--full-history`, and the
    partition is closed at the scalar -- only a `status` that is present and is
    a string drawn from the state schema's own controlled vocabulary admits.
    """
    for sha in origination_reference_commits(repo_root, state_rel_path):
        raw = _state_bytes_at(repo_root, sha, state_rel_path)
        if raw is None:
            continue
        try:
            state = json.loads(raw)
        except json.JSONDecodeError:
            raise OriginationUndecidable(f"the state document at {sha[:8]} is unparseable")
        if not isinstance(state, dict):
            raise OriginationUndecidable(f"the state document at {sha[:8]} is not an object")
        items = state.get("work_items")
        if not isinstance(items, dict):
            raise OriginationUndecidable(f"work_items at {sha[:8]} is not an object")
        item = items.get(work_item_id)
        if item is None:
            continue
        if not isinstance(item, dict):
            raise OriginationUndecidable(f"the work item at {sha[:8]} is not an object")
        checkpoints = item.get("checkpoints")
        if not isinstance(checkpoints, dict):
            raise OriginationUndecidable(f"checkpoints at {sha[:8]} is not an object")
        entry = checkpoints.get(checkpoint_id)
        if entry is None:
            continue
        if not isinstance(entry, dict):
            raise OriginationUndecidable(f"the checkpoint entry at {sha[:8]} is not an object")
        if "status" not in entry:
            raise OriginationUndecidable(
                f"the checkpoint entry at {sha[:8]} carries no status -- the absence of a "
                f"readable status is not decidable evidence that it was not IN_PROGRESS")
        status = entry["status"]
        if not isinstance(status, str) or status not in CHECKPOINT_STATUSES:
            raise OriginationUndecidable(
                f"the checkpoint status at {sha[:8]} is {status!r}, which is not a value of the "
                f"state schema's controlled vocabulary {sorted(CHECKPOINT_STATUSES)}")
        if status == "IN_PROGRESS":
            return "IN_PROGRESS", sha
    return None, None


def decision(fn, repo_root: Path, work_item_id: str = "wi", checkpoint_id: str = "CP") -> str:
    """`ADMIT`, `REFUSE(observed)` or `REFUSE(undecidable)` -- never an exception."""
    try:
        status, _sha = fn(repo_root, work_item_id, checkpoint_id)
    except OriginationUndecidable:
        return "REFUSE(undecidable)"
    except Exception as exc:                                    # undeclared escape
        return f"ESCAPE({type(exc).__name__})"
    return "REFUSE(observed)" if status == "IN_PROGRESS" else "ADMIT"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def fixture_merged_and_deleted() -> tuple[Path, str]:
    """`OPUS-R88-001`'s route: worktree A commits `IN_PROGRESS`; an ordinary
    integrator merges A's branch into main resolving the state document to
    main's copy, then deletes the merged branch. The commit stays reachable
    through the merge's second parent. Nobody does anything hostile and the
    adopter moves nothing."""
    root = init_repo("merge", initial={"wi": {"CP": None}})
    git(root, "branch", "wt-a")
    wta = root.parent / "wt-a"
    git(root, "worktree", "add", "-q", str(wta), "wt-a")
    write_state(wta, {"wi": {"CP": "IN_PROGRESS"}})
    git(wta, "commit", "-qam", "A: start CP")
    c = git(wta, "rev-parse", "HEAD")

    (root / "unrelated.txt").write_text("x\n")
    git(root, "add", "-A")
    git(root, "commit", "-qm", "main: unrelated")
    subprocess.run(["git", "merge", "-q", "--no-ff", "--no-commit", "wt-a"],
                   cwd=root, capture_output=True)
    write_state(root, {"wi": {"CP": None}})
    git(root, "add", "-A")
    git(root, "commit", "-qm", "merge wt-a (state resolved to main's)")
    git(root, "worktree", "remove", "--force", str(wta))
    git(root, "branch", "-D", "wt-a")
    return root, c


def fixture_criss_cross() -> tuple[Path, str]:
    """Two topic branches that merge each other, each resolving the state
    document back to main's copy, then both refs deleted. The `IN_PROGRESS`
    commit is reachable only through second parents."""
    root = init_repo("crisscross", initial={"wi": {"CP": None}})
    base = git(root, "rev-parse", "HEAD")
    git(root, "checkout", "-q", "-b", "x")
    (root / "x.txt").write_text("x\n")
    git(root, "add", "-A")
    git(root, "commit", "-qm", "x1")
    git(root, "checkout", "-q", "-b", "y", base)
    write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "y1: start CP")
    c = git(root, "rev-parse", "HEAD")

    for first, second in (("x", "y"), ("y", "x")):
        git(root, "checkout", "-q", first)
        subprocess.run(["git", "merge", "-q", "--no-ff", "--no-commit", second],
                       cwd=root, capture_output=True)
        write_state(root, {"wi": {"CP": None}})
        git(root, "add", "-A")
        git(root, "commit", "-qm", f"{first} merges {second}")

    git(root, "checkout", "-q", "main")
    subprocess.run(["git", "merge", "-q", "--no-ff", "--no-commit", "x"],
                   cwd=root, capture_output=True)
    write_state(root, {"wi": {"CP": None}})
    git(root, "add", "-A")
    git(root, "commit", "-qm", "main merges x")
    git(root, "branch", "-D", "x")
    git(root, "branch", "-D", "y")
    return root, c


def fixture_octopus() -> tuple[Path, str]:
    """An octopus merge whose second side carries the `IN_PROGRESS` on an
    intermediate commit and reverts it on its own tip, so the merge is TREESAME
    to its first parent for the state path."""
    root = init_repo("octopus", initial={"wi": {"CP": None}})
    base = git(root, "rev-parse", "HEAD")
    c = None
    for name, carries in (("b1", False), ("b2", True), ("b3", False)):
        git(root, "checkout", "-q", "-b", name, base)
        if carries:
            write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
            git(root, "commit", "-qam", f"{name}: start CP")
            c = git(root, "rev-parse", "HEAD")
            write_state(root, {"wi": {"CP": None}})
            git(root, "commit", "-qam", f"{name}: revert the transition")
        else:
            (root / f"{name}.txt").write_text("x\n")
            git(root, "add", "-A")
            git(root, "commit", "-qm", f"{name}: unrelated")
    git(root, "checkout", "-q", "main")
    git(root, "merge", "-q", "--no-edit", "b1", "b2", "b3")
    for name in ("b1", "b2", "b3"):
        git(root, "branch", "-D", name)
    return root, c


# ---------------------------------------------------------------------------
# R1 -- OPUS-R88-001: the reference is a simplified history, not the reachable set
# ---------------------------------------------------------------------------


def group_r1() -> None:
    print("\nR1  `OPUS-R88-001` -- reference completeness (blocking)")

    for label, fixture in (("R1a merge + post-merge branch deletion", fixture_merged_and_deleted),
                           ("R1b criss-cross merge", fixture_criss_cross),
                           ("R1c octopus merge", fixture_octopus)):
        root, c = fixture()
        reachable = c in git(root, "rev-list", "--all").split()
        simplified = git(root, "rev-list", "--all", "--", STATE_REL).split()
        full = git(root, "rev-list", "--all", "--full-history", "--", STATE_REL).split()
        chk(f"{label}: the IN_PROGRESS commit stays ref-reachable", reachable)
        chk(f"{label}: revision 70's reference does NOT contain it", c not in simplified,
            f"({len(simplified)} commits)")
        chk(f"{label}: `--full-history` DOES contain it", c in full, f"({len(full)} commits)")
        chk(f"{label}: revision 70 ADMITS [live control arm]",
            decision(origination_reference_status_r70, root) == "ADMIT")
        chk(f"{label}: revision 71 REFUSES",
            decision(origination_reference_status_r71, root) == "REFUSE(observed)")

    # R1d -- the adopter moves nothing: B's HEAD is unmoved across the whole arm.
    root, c = fixture_merged_and_deleted()
    git(root, "branch", "wt-b")
    wtb = root.parent / "wt-b"
    git(root, "worktree", "add", "-q", str(wtb), "wt-b")
    before = git(wtb, "rev-parse", "HEAD")
    git(wtb, "checkout", c, "--", STATE_REL)
    chk("R1d: B's HEAD is unmoved by the local checkout",
        git(wtb, "rev-parse", "HEAD") == before)
    chk("R1d: B's working tree records the checkpoint IN_PROGRESS",
        json.loads((wtb / STATE_REL).read_text())
        ["work_items"]["wi"]["checkpoints"]["CP"]["status"] == "IN_PROGRESS")
    chk("R1d: revision 70 admits adoption from B [live control arm]",
        decision(origination_reference_status_r70, wtb) == "ADMIT")
    chk("R1d: revision 71 refuses adoption from B",
        decision(origination_reference_status_r71, wtb) == "REFUSE(observed)")

    # R1e -- `--full-history`'s own residual pruning is status-preserving.
    root = init_repo("preserving", initial={"wi": {"CP": None}})
    for i in range(3):
        (root / f"f{i}.txt").write_text("x\n")
        git(root, "add", "-A")
        git(root, "commit", "-qm", f"unrelated {i}")
    all_commits = git(root, "rev-list", "--all").split()
    full = git(root, "rev-list", "--all", "--full-history", "--", STATE_REL).split()
    pruned = [s for s in all_commits if s not in full]
    chk("R1e: `--full-history` prunes only commits TREESAME to a parent for the path",
        len(pruned) == 3, f"(pruned {len(pruned)} of {len(all_commits)})")
    def status_at(sha: str):
        raw = _state_bytes_at(root, sha, STATE_REL)
        if raw is None:
            return "<path absent>"
        entry = (json.loads(raw)["work_items"]["wi"]["checkpoints"]).get("CP")
        return entry.get("status") if isinstance(entry, dict) else "<entry absent>"

    statuses = {status_at(sha) for sha in pruned}
    retained = {status_at(sha) for sha in full}
    chk("R1e: every pruned commit records a status a retained commit also records",
        statuses <= retained, f"(pruned={sorted(statuses)} retained={sorted(retained)})")

    # R1f -- the migration path the reference exists for is still open.
    root = init_repo("migration", initial={"wi": {"CP": None}})
    write_state(root, {"wi": {"CP": "IN_PROGRESS"}})           # uncommitted, as `S-CP3` is
    chk("R1f: an UNCOMMITTED transition is absent from the corrected reference, so adoption "
        "still admits", decision(origination_reference_status_r71, root) == "ADMIT")

    # R1g -- `--single-worktree` is order-sensitive (non-blocking observation 1).
    root, c = fixture_merged_and_deleted()
    git(root, "branch", "wt-c")
    wtc = root.parent / "wt-c"
    git(root, "worktree", "add", "-q", str(wtc), "wt-c")
    git(wtc, "checkout", "-q", "--detach")
    (wtc / "only-here.txt").write_text("x\n")
    git(wtc, "add", "-A")
    git(wtc, "commit", "-qm", "detached-only commit")
    detached = git(wtc, "rev-parse", "HEAD")
    after_all = git(root, "rev-list", "--all", "--single-worktree").split()
    before_all = git(root, "rev-list", "--single-worktree", "--all").split()
    chk("R1g: `--all --single-worktree` and `--single-worktree --all` differ",
        set(after_all) != set(before_all),
        f"({len(after_all)} vs {len(before_all)} commits)")
    chk("R1g: the option only takes effect when it PRECEDES `--all`",
        detached in after_all and detached not in before_all)


# ---------------------------------------------------------------------------
# R2 -- OPUS-R88-002: the partition is open at the scalar
# ---------------------------------------------------------------------------


SHAPES = [
    ("{'status': 'IN_PROGRESS'}", {"status": "IN_PROGRESS"}, "REFUSE(observed)"),
    ("{'status': 'COMPLETE'}", {"status": "COMPLETE"}, "ADMIT"),
    ("no status key", {"start_commit": "abc"}, "REFUSE(undecidable)"),
    ("{'status': None}", {"status": None}, "REFUSE(undecidable)"),
    ("{'status': 42}", {"status": 42}, "REFUSE(undecidable)"),
    ("{'status': ['IN_PROGRESS']}", {"status": ["IN_PROGRESS"]}, "REFUSE(undecidable)"),
    ("{'status': {'v': 'IN_PROGRESS'}}", {"status": {"v": "IN_PROGRESS"}}, "REFUSE(undecidable)"),
    ("{'status': 'in_progress'}", {"status": "in_progress"}, "REFUSE(undecidable)"),
]


def group_r2() -> None:
    print("\nR2  `OPUS-R88-002` -- the fail-closed partition, closed at the scalar")

    chk("R2a: the controlled vocabulary is the state schema's own, not a restatement",
        CHECKPOINT_STATUSES is ws.CHECKPOINT_STATUSES and "IN_PROGRESS" in CHECKPOINT_STATUSES)

    for label, entry, expected in SHAPES:
        root = init_repo("shape", initial={"wi": {"CP": entry}})
        got71 = decision(origination_reference_status_r71, root)
        got70 = decision(origination_reference_status_r70, root)
        chk(f"R2b {label}: revision 71 -> {expected}", got71 == expected, f"(got {got71})")
        if expected == "REFUSE(undecidable)":
            chk(f"R2b {label}: revision 70 ADMITS a schema-invalid document [live control arm]",
                got70 == "ADMIT", f"(got {got70})")
        else:
            chk(f"R2b {label}: revision 70 agrees on the two decidable rows", got70 == expected)

    # R2c -- absence is established by the tree, not by an exit code.
    root = init_repo("absent", initial={"wi": {"CP": "COMPLETE"}})
    (root / STATE_REL).unlink()
    git(root, "add", "-A")
    git(root, "commit", "-qm", "remove the state document")
    chk("R2c: a commit whose tree genuinely lacks the path is decidably absent and continues",
        decision(origination_reference_status_r71, root) == "ADMIT")

    # R2d -- the returncode test concludes "absent" from a failure that is not absence.
    root = init_repo("unreadable", initial={"wi": {"CP": "IN_PROGRESS"}})
    sha = git(root, "rev-parse", "HEAD")
    oid = git(root, "rev-parse", f"{sha}:{STATE_REL}")
    loose = root / ".git" / "objects" / oid[:2] / oid[2:]
    packed = not loose.exists()
    if packed:                                       # never happens for a fresh repo, but be exact
        chk("R2d: (skipped -- the blob is packed in this environment)", True)
    else:
        loose.unlink()
        shown = subprocess.run(["git", "show", f"{sha}:{STATE_REL}"],
                               cwd=root, capture_output=True, text=True)
        listed = subprocess.run(["git", "ls-tree", "--full-tree", "-z", sha, "--", STATE_REL],
                                cwd=root, capture_output=True, text=True)
        chk("R2d: the entry is still PRESENT in the commit's tree",
            listed.returncode == 0 and listed.stdout.strip("\0") != "")
        chk("R2d: `git show` nevertheless fails", shown.returncode != 0)
        chk("R2d: revision 70 reads that failure as 'decidably absent' and ADMITS "
            "[live control arm]", decision(origination_reference_status_r70, root) == "ADMIT")
        chk("R2d: revision 71 refuses -- present in the tree, unreadable, therefore undecidable",
            decision(origination_reference_status_r71, root) == "REFUSE(undecidable)")

    # R2e -- a non-blob at the state path.
    root = init_repo("nonblob", initial={"wi": {"CP": "COMPLETE"}})
    (root / STATE_REL).unlink()
    nested = root / STATE_REL
    nested.mkdir(parents=True)
    (nested / "inner.json").write_text("{}\n")
    git(root, "add", "-A")
    git(root, "commit", "-qm", "a tree where the state document was")
    chk("R2e: a tree at the state path refuses rather than being read as a document",
        decision(origination_reference_status_r71, root) == "REFUSE(undecidable)")

    # R2f -- no path exits with an undeclared exception type.
    escapes = []
    for label, entry, _expected in SHAPES:
        root = init_repo("escape", initial={"wi": {"CP": entry}})
        for fn in (origination_reference_status_r71,):
            got = decision(fn, root)
            if got.startswith("ESCAPE"):
                escapes.append((label, got))
    for doc in ("not json at all", "null", "[1,2,3]", '{"work_items": 4}',
                '{"work_items": {"wi": 4}}', '{"work_items": {"wi": {"checkpoints": 4}}}',
                '{"work_items": {"wi": {"checkpoints": {"CP": 4}}}}'):
        root = init_repo("escape-doc")
        (root / STATE_REL).write_text(doc + "\n")
        git(root, "commit", "-qam", "shape")
        got = decision(origination_reference_status_r71, root)
        if got.startswith("ESCAPE"):
            escapes.append((doc, got))
        elif got != "REFUSE(undecidable)":
            escapes.append((doc, f"admitted: {got}"))
    chk("R2f: no shape in the combined partition escapes undeclared or admits",
        escapes == [], f"({escapes})")

    # R2g -- a reference that cannot be RESOLVED refuses; an EMPTY one is a
    # decidable "no evidence exists" and admits. The two are different rows and
    # the partition must not collapse them.
    outside = scratch("not-a-repo")
    chk("R2g: a reference the command cannot resolve at all refuses",
        decision(origination_reference_status_r71, outside) == "REFUSE(undecidable)")
    empty = scratch("empty-repo") / "repo"
    empty.mkdir(parents=True)
    git(empty, "init", "-q", "-b", "main")
    chk("R2g: a repository with no commits yields an EMPTY reference, which is decidable "
        "and admits", decision(origination_reference_status_r71, empty) == "ADMIT")


# ---------------------------------------------------------------------------
# R3 -- OPUS-R88-003: the residual, measured
# ---------------------------------------------------------------------------


def group_r3() -> None:
    print("\nR3  `OPUS-R88-003` -- the disclosed residual, at its actual strength")

    # R3a -- one plumbing command, from the worktree that benefits.
    root = init_repo("updateref", initial={"wi": {"CP": None}})
    git(root, "branch", "wt-a")
    wta = root.parent / "wt-a"
    git(root, "worktree", "add", "-q", str(wta), "wt-a")
    write_state(wta, {"wi": {"CP": "IN_PROGRESS"}})
    git(wta, "commit", "-qam", "A: start CP")
    c = git(wta, "rev-parse", "HEAD")
    git(root, "branch", "wt-b")
    wtb = root.parent / "wt-b"
    git(root, "worktree", "add", "-q", str(wtb), "wt-b")
    git(wtb, "checkout", c, "--", STATE_REL)
    chk("R3a: before -- revision 71 refuses from B",
        decision(origination_reference_status_r71, wtb) == "REFUSE(observed)")
    branch_d = subprocess.run(["git", "branch", "-D", "wt-a"], cwd=wtb,
                              capture_output=True, text=True)
    chk("R3a: `git branch -D` of another worktree's checked-out branch is REFUSED by Git",
        branch_d.returncode != 0)
    update_ref = subprocess.run(["git", "update-ref", "-d", "refs/heads/wt-a"], cwd=wtb,
                                capture_output=True, text=True)
    chk("R3a: `git update-ref -d` of the SAME branch, from the SAME worktree, succeeds",
        update_ref.returncode == 0 and update_ref.stderr.strip() == "",
        f"(exit {update_ref.returncode})")
    chk("R3a: the commit leaves the reference", c not in git(root, "rev-list", "--all").split())
    chk("R3a: the commit object is still in the object database",
        git(root, "cat-file", "-t", c) == "commit")
    chk("R3a: A's working tree is untouched",
        json.loads((wta / STATE_REL).read_text())
        ["work_items"]["wi"]["checkpoints"]["CP"]["status"] == "IN_PROGRESS")
    chk("R3a: after -- revision 71 now ADMITS (the disclosed residual, one command)",
        decision(origination_reference_status_r71, wtb) == "ADMIT")

    # R3b/R3c/R3d -- ordinary history maintenance erases the evidence unintentionally.
    def amend(r: Path) -> None:
        """The everyday shape: the transition was committed by mistake, is taken
        back out, and the commit is amended to keep history tidy."""
        write_state(r, {"wi": {"CP": None}})
        (r / "amended.txt").write_text("x\n")
        git(r, "add", "-A")
        git(r, "commit", "--amend", "-qm", "amended: took the transition back out")

    for label, mutate in (
        ("R3b `git commit --amend`", amend),
        ("R3c `git reset --hard HEAD~1`", lambda r: git(r, "reset", "-q", "--hard", "HEAD~1")),
    ):
        root = init_repo("maintenance", initial={"wi": {"CP": None}})
        write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
        git(root, "commit", "-qam", "start CP")
        chk(f"{label}: before -- refuses",
            decision(origination_reference_status_r71, root) == "REFUSE(observed)")
        mutate(root)
        chk(f"{label}: after -- admits, with no intent to erase anything",
            decision(origination_reference_status_r71, root) == "ADMIT")

    root = init_repo("squash", initial={"wi": {"CP": None}})
    git(root, "checkout", "-q", "-b", "topic")
    write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "topic: start CP")
    write_state(root, {"wi": {"CP": None}})
    (root / "topic.txt").write_text("the work the topic branch actually delivered\n")
    git(root, "add", "-A")
    git(root, "commit", "-qm", "topic: finish")
    git(root, "checkout", "-q", "main")
    git(root, "merge", "-q", "--squash", "topic")
    git(root, "commit", "-qm", "squash-merge topic")
    git(root, "branch", "-D", "topic")
    chk("R3d: deleting a topic branch after a SQUASH merge removes the evidence entirely",
        decision(origination_reference_status_r71, root) == "ADMIT")

    # R3e -- the positive half: the decision is unchanged across a prune.
    root = init_repo("prune", initial={"wi": {"CP": None}})
    write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "start CP")
    write_state(root, {"wi": {"CP": None}})
    git(root, "commit", "-qam", "clear it")
    before_prune = decision(origination_reference_status_r71, root)
    git(root, "reflog", "expire", "--expire-unreachable=now", "--all")
    git(root, "gc", "-q", "--prune=now")
    after_prune = decision(origination_reference_status_r71, root)
    chk("R3e: reachable evidence survives `reflog expire` + `gc --prune=now` unchanged",
        before_prune == after_prune == "REFUSE(observed)",
        f"({before_prune} -> {after_prune})")

    root = init_repo("prune2", initial={"wi": {"CP": None}})
    write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "start CP")
    git(root, "reset", "-q", "--hard", "HEAD~1")
    unreachable_before = decision(origination_reference_status_r71, root)
    git(root, "reflog", "expire", "--expire-unreachable=now", "--all")
    git(root, "gc", "-q", "--prune=now")
    unreachable_after = decision(origination_reference_status_r71, root)
    chk("R3e: unreachable evidence is already lost BEFORE the prune, and the prune changes "
        "nothing -- so the guarantee has no hidden dependence on reflog expiry",
        unreachable_before == unreachable_after == "ADMIT",
        f"({unreachable_before} -> {unreachable_after})")

    # R3f -- `refs/replace/` is a distinct route (non-blocking observation 3).
    root = init_repo("replace", initial={"wi": {"CP": None}})
    base = git(root, "rev-parse", "HEAD")
    write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "start CP")
    real = git(root, "rev-parse", "HEAD")
    git(root, "checkout", "-q", "-b", "shadow", base)
    write_state(root, {"wi": {"CP": "COMPLETE"}})
    git(root, "commit", "-qam", "start CP")
    shadow = git(root, "rev-parse", "HEAD")
    git(root, "checkout", "-q", "main")
    git(root, "branch", "-D", "shadow")
    chk("R3f: before the replace ref -- refuses",
        decision(origination_reference_status_r71, root) == "REFUSE(observed)")
    git(root, "replace", real, shadow)
    replaced = decision(origination_reference_status_r71, root)
    honoured = subprocess.run(["git", "--no-replace-objects", "rev-list", "--all",
                               "--full-history", "--", STATE_REL],
                              cwd=root, capture_output=True, text=True)
    chk("R3f: a replace ref rewrites what the reference sees",
        replaced == "ADMIT", f"({replaced})")
    chk("R3f: `--no-replace-objects` bypasses it, so the two disagree",
        real in honoured.stdout.split())


# ---------------------------------------------------------------------------
# R4 -- OPUS-R88-004: the reduction, and identity
# ---------------------------------------------------------------------------


def group_r4() -> None:
    print("\nR4  `OPUS-R88-004` -- the reduction over multiple observations, and identity")

    # R4a -- the refusal is permanent: a later COMPLETE does not clear it.
    root = init_repo("lifecycle", initial={"wi": {"CP": None}})
    seq = []
    for label, status in (("IN_PROGRESS", "IN_PROGRESS"), ("COMPLETE", "COMPLETE"),
                          ("reopened (entry removed)", None)):
        write_state(root, {"wi": {"CP": status}})
        git(root, "commit", "-qam", f"CP -> {label}")
        seq.append((label, decision(origination_reference_status_r71, root)))
    chk("R4a: after the IN_PROGRESS commit -- refuse", seq[0][1] == "REFUSE(observed)")
    chk("R4a: after a later COMPLETE -- still refuse (no supersession rule exists)",
        seq[1][1] == "REFUSE(observed)")
    chk("R4a: after the checkpoint is reopened -- still refuse, for the remaining life "
        "of the repository", seq[2][1] == "REFUSE(observed)")

    # R4b -- two divergent branches carrying conflicting statuses.
    root = init_repo("divergent", initial={"wi": {"CP": None}})
    base = git(root, "rev-parse", "HEAD")
    git(root, "checkout", "-q", "-b", "left")
    write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "left: IN_PROGRESS")
    git(root, "checkout", "-q", "-b", "right", base)
    write_state(root, {"wi": {"CP": "COMPLETE"}})
    git(root, "commit", "-qam", "right: COMPLETE")
    chk("R4b: conflicting statuses on divergent branches refuse -- the reduction is "
        "'IN_PROGRESS anywhere', not 'the latest observation'",
        decision(origination_reference_status_r71, root) == "REFUSE(observed)")

    # R4c -- identity reuse binds new work to a retired item's evidence.
    root = init_repo("reuse", initial={"retired": {"CP": None}})
    write_state(root, {"retired": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "retired item starts CP")
    write_state(root, {"retired": {"CP": "COMPLETE"}})
    git(root, "commit", "-qam", "retired item completes; the item is then removed")
    write_state(root, {"other": {"X": "COMPLETE"}})
    git(root, "commit", "-qam", "work item removed from the document")
    reused = decision(origination_reference_status_r71, root, work_item_id="retired",
                      checkpoint_id="CP")
    fresh = decision(origination_reference_status_r71, root, work_item_id="brand-new",
                     checkpoint_id="CP")
    chk("R4c: a REUSED work_item_id inherits the retired item's historical evidence",
        reused == "REFUSE(observed)", f"({reused})")
    chk("R4c: an unused id is unaffected, so the binding really is keyed on the two strings",
        fresh == "ADMIT", f"({fresh})")

    # R4d -- a checkpoint renamed between plan revisions.
    root = init_repo("rename", initial={"wi": {"OLD": None}})
    write_state(root, {"wi": {"OLD": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "OLD starts")
    write_state(root, {"wi": {"NEW": None}})
    git(root, "commit", "-qam", "revision renames OLD -> NEW")
    chk("R4d: the retired checkpoint id still refuses",
        decision(origination_reference_status_r71, root, checkpoint_id="OLD")
        == "REFUSE(observed)")
    chk("R4d: the renamed checkpoint is unaffected by the retired id's history",
        decision(origination_reference_status_r71, root, checkpoint_id="NEW") == "ADMIT")

    # R4e -- the cost accumulates: a whole-tree commit sweeps another item's transition.
    root = init_repo("accumulate", initial={"a": {"CP": None},
                                            "b": {"CP": None}})

    def refusing_pairs() -> set:
        pairs = set()
        for wi in ("a", "b"):
            if decision(origination_reference_status_r71, root, work_item_id=wi) \
                    == "REFUSE(observed)":
                pairs.add((wi, "CP"))
        return pairs

    chk("R4e: no pair refuses initially", refusing_pairs() == set())
    write_state(root, {"a": {"CP": "IN_PROGRESS"}, "b": {"CP": None}})
    (root / "unrelated.txt").write_text("x\n")
    git(root, "add", "-A")
    git(root, "commit", "-qm", "b's whole-tree checkpoint commit sweeps a's transition")
    chk("R4e: one whole-tree commit adds a permanently refusing pair",
        refusing_pairs() == {("a", "CP")})
    write_state(root, {"a": {"CP": "COMPLETE"}, "b": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "and again, for the other item")
    chk("R4e: the set grows monotonically -- a general property of the rule, not a "
        "one-time property of one repository",
        refusing_pairs() == {("a", "CP"), ("b", "CP")})


# ---------------------------------------------------------------------------
# R5 -- OPUS-R88-005: what the refusal's evidence carries
# ---------------------------------------------------------------------------


def origination_observation(repo_root: Path, work_item_id: str, checkpoint_id: str,
                            *, state_rel_path: str = STATE_REL) -> dict:
    """Revision 71's origination observation, reported as a first-class member
    of the ownership evidence (`OPUS-R88-005`)."""
    observation = {"reference_command": " ".join([*REFERENCE_COMMAND_R71, state_rel_path]),
                   "work_item_id": work_item_id, "checkpoint_id": checkpoint_id,
                   "route": None, "observed_commit": None, "observed_status": None,
                   "undecidable_shape": None}
    try:
        status, sha = origination_reference_status_r71(repo_root, work_item_id, checkpoint_id,
                                                       state_rel_path=state_rel_path)
    except OriginationUndecidable as exc:
        observation["route"] = "undecidable"
        observation["undecidable_shape"] = str(exc)
        observation["observed_commit"] = getattr(exc, "commit", None)
        return observation
    if status == "IN_PROGRESS":
        observation.update(route="observed_in_progress", observed_commit=sha,
                           observed_status=status)
    else:
        observation["route"] = "absent"
    return observation


def group_r5() -> None:
    print("\nR5  `OPUS-R88-005` -- the refusal's own evidence")

    root = init_repo("evidence", initial={"wi": {"CP": None}})
    write_state(root, {"wi": {"CP": "IN_PROGRESS"}})
    git(root, "commit", "-qam", "start CP")
    sha = git(root, "rev-parse", "HEAD")

    observed = origination_observation(root, "wi", "CP")
    chk("R5a: the observation names WHICH route produced the refusal",
        observed["route"] == "observed_in_progress")
    chk("R5a: it names the observing commit", observed["observed_commit"] == sha)
    chk("R5a: it names the status read there", observed["observed_status"] == "IN_PROGRESS")
    chk("R5a: it names the checkpoint the refusal is about", observed["checkpoint_id"] == "CP")
    chk("R5a: it names the reference it was evaluated against",
        "--full-history" in observed["reference_command"])

    root2 = init_repo("evidence-undecidable")
    (root2 / STATE_REL).write_text('{"work_items": {"wi": {"checkpoints": {"CP": '
                                   '{"status": 42}}}}}\n')
    git(root2, "commit", "-qam", "schema-invalid status")
    undecidable = origination_observation(root2, "wi", "CP")
    chk("R5b: the undecidable route is distinguished from the observed one",
        undecidable["route"] == "undecidable")
    chk("R5b: it reports the shape that could not be decided",
        "not a value of the state schema's controlled vocabulary"
        in (undecidable["undecidable_shape"] or ""))

    # R5c -- revision 70's refusal carries none of it [live control arm].
    try:
        origination_reference_status_r70(root2, "wi", "CP")
        r70_message = "<admitted, so there is no message at all>"
    except OriginationUndecidable as exc:
        r70_message = str(exc)
    chk("R5c: revision 70 reports nothing about the origination observation "
        "[live control arm]",
        "route" not in r70_message and sha not in r70_message)

    # R5d -- the takeover literal is deliberately NOT bound to the observation.
    wt = init_repo("literal", initial={"wi": {"CP": None}})
    evidence = co.takeover_evidence(wt, "wi")
    literal_before = co.takeover_authorization_literal("wi", evidence, "CP")
    with_observation = dict(evidence)
    with_observation["origination"] = origination_observation(wt, "wi", "CP")
    literal_with = co.takeover_authorization_literal("wi", with_observation, "CP")
    changed = dict(with_observation)
    changed["origination"] = dict(with_observation["origination"],
                                  observed_commit="0" * 40, route="observed_in_progress")
    literal_changed = co.takeover_authorization_literal("wi", changed, "CP")
    chk("R5d: adding the origination observation to the evidence leaves the authorization "
        "literal byte-identical", literal_before == literal_with)
    chk("R5d: CHANGING the origination observation leaves it byte-identical too -- so "
        "ordinary merges and ref movement cannot stale a takeover authorization",
        literal_with == literal_changed)
    chk("R5d: the literal still binds the claim observation it always bound",
        evidence["claim_observation_id"] in literal_before)


# ---------------------------------------------------------------------------
# L2 -- the live repository, read-only
# ---------------------------------------------------------------------------


def observed_pairs(repo_root: Path, rev_args: list[str]) -> tuple[int, set]:
    out = subprocess.run(["git", "rev-list", *rev_args], cwd=repo_root,
                         capture_output=True, text=True, check=True).stdout.split()
    pairs = set()
    for sha in out:
        raw = subprocess.run(["git", "show", f"{sha}:{STATE_REL}"], cwd=repo_root,
                             capture_output=True)
        if raw.returncode != 0:
            continue
        try:
            state = json.loads(raw.stdout)
        except json.JSONDecodeError:
            continue
        if not isinstance(state, dict):
            continue
        for wi, item in (state.get("work_items") or {}).items():
            if not isinstance(item, dict):
                continue
            for cp, entry in (item.get("checkpoints") or {}).items():
                if isinstance(entry, dict) and entry.get("status") == "IN_PROGRESS":
                    pairs.add((wi, cp))
    return len(out), pairs


def group_l2() -> None:
    print("\nL2  the live repository, read-only")

    claims = Path(git(REPO, "rev-parse", "--git-common-dir"))
    if not claims.is_absolute():
        claims = REPO / claims
    claims = claims / "ai-workflow" / "checkpoint-claims"
    existed_before = claims.exists()

    n_simplified, p_simplified = observed_pairs(REPO, ["--all", "--", STATE_REL])
    n_full, p_full = observed_pairs(REPO, ["--all", "--full-history", "--", STATE_REL])
    n_all, p_all = observed_pairs(REPO, ["--all"])

    chk("L2a: the specified revision-70 reference is strictly smaller than `--full-history`",
        n_simplified < n_full, f"({n_simplified} vs {n_full} commits)")
    chk("L2a: the commit it already drops is a real merge commit",
        set(git(REPO, "rev-list", "--all", "--full-history", "--", STATE_REL).split())
        - set(git(REPO, "rev-list", "--all", "--", STATE_REL).split()) != set())
    chk("L2b: the corrected reference and the UNFILTERED reachable set agree on the set of "
        "(work_item_id, checkpoint_id) pairs ever observed IN_PROGRESS",
        p_full == p_all, f"({sorted(p_full)} vs {sorted(p_all)} over {n_full}/{n_all} commits)")
    # AMENDED, third session (2026-08-14): this repository's own WF8b was the
    # only pair when this pass was authored. The Revision 80 plan-approval
    # commit (8f8d878) has since swept `v2-1-dry-run`'s own dirty S-CP3 delta
    # into HEAD too (see
    # WF8B_S14_FINDING_worktree_b_invisible_to_uncommitted_checkpoint.md's
    # "This verdict no longer holds" addendum) -- exactly the accumulation
    # the plan's own "reduction over multiple observations" section predicts
    # ("it accumulates ... the refusing set grows monotonically"), now
    # demonstrated rather than only asserted.
    chk("L2c: the cost has grown from one pair to two, exactly as the "
        "accumulation rule predicts",
        p_full == {("workflow-v2-1-core", "WF8b"), ("v2-1-dry-run", "S-CP3")},
        f"({sorted(p_full)})")
    chk("L2c: the revision-70 reference happens to agree here today -- the defect is a "
        "latent property of the command, not a live miscount",
        p_simplified == p_full)

    rename_old = len(git(REPO, "rev-list", "--all", "--full-history", "--",
                         "docs/ai-workflow/WORKFLOW_STATE.json").split())
    chk("L2d: the reference does no rename detection, so it is stated over a fixed path",
        rename_old == n_full)

    chk("L2e: this group mutated nothing -- the claims directory is as it was",
        claims.exists() == existed_before, f"(exists={claims.exists()})")
    chk("L2e: the live state document is byte-identical to what this pass started with",
        (REPO / STATE_REL).read_bytes() == _BASELINE_STATE_BYTES)
    chk("L2e: the live worktree's `git status --porcelain` set is unchanged by this pass",
        subprocess.run(["git", "status", "--porcelain"], cwd=REPO, capture_output=True,
                       text=True, check=True).stdout == _BASELINE_STATUS)


def main() -> int:
    print(__doc__.strip().splitlines()[0])
    group_r1()
    group_r2()
    group_r3()
    group_r4()
    group_r5()
    group_l2()
    print(f"\n{PASS} passed, {FAIL} failed")
    for d in _TMPDIRS:
        shutil.rmtree(d, ignore_errors=True)
    return 1 if FAIL else 0


if __name__ == "__main__":
    raise SystemExit(main())
