"""Pass 17 -- revision 70's own validation of `OPUS-R87-001` through `-006`.

Every claim revision 70 makes about the revision-69 mechanism, and every claim
it makes about the *corrected* mechanism, is run here rather than asserted.

Three groups:

  V1-V3  the defects, reproduced against the REAL `adopt_claim` /
         `verify_dirty_resume_safety_strict` / `takeover_*` in real
         two-worktree fixtures -- these are what makes the findings accepted
         rather than taken on faith;
  C1-C4  the *corrected* origination reference, prototyped here and checked in
         both directions before the plan specified it -- it refuses every
         reproduced bypass arm, fails closed on every undecidable read, and
         still admits the migration case adoption exists for;
  L1     the live-repository conformance arm behind `OPUS-R87-003`'s
         rejection: read-only, mutating nothing, publishing no claim.

Nothing here writes to the real repository. L1 performs read-only probes only,
and asserts afterwards that the claims directory it could have created does not
exist.

Run: python3 pass17.py
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

REPO = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"],
                           cwd=Path(__file__).resolve().parent, capture_output=True,
                           text=True, check=True).stdout.strip())
sys.path.insert(0, str(REPO / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import workflow_state as ws  # noqa: E402
import checkpoint_ownership as co  # noqa: E402
from harness import Fixture, git, step1_fixed  # noqa: E402

STATE_REL = "docs/ai-workflow/WORKFLOW_STATE.json"

PASS = FAIL = 0


def chk(label: str, cond: bool, extra: str = "") -> None:
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [ok]   {label} {extra}")
    else:
        FAIL += 1
        print(f"  [FAIL] {label} {extra}")


# ---------------------------------------------------------------------------
# The corrected origination reference, prototyped (revision 70)
# ---------------------------------------------------------------------------


class OriginationUndecidable(Exception):
    """Every undecidable origination read is a refusal, never an admission.

    In the design this is `CheckpointOriginationUnprovableError`; it is a
    distinct class here only so the prototype can tell the two refusal reasons
    apart in its own assertions.
    """


def origination_reference_status(repo_root: Path, work_item_id: str, checkpoint_id: str,
                                 *, state_rel_path: str = STATE_REL):
    """`D-Checkpoint-Ownership`'s "The origination reference" (revision 70).

    The reference is **every commit reachable from every ref**, path-limited to
    the state document -- never the invoking worktree's own `HEAD`, which that
    worktree can move without touching its working tree. `--all` examines every
    working tree by default (`--single-worktree` is the documented opt-out), so
    a second worktree's own `HEAD`, detached included, is inside the reference
    and one worktree rewinding its own branch does not remove another's.

    Only a positive, **decidable** absence admits: an unresolvable reference or
    any undecidable state document refuses.
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


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def fixture_inherited_in_progress():
    """A commits its own `IN_PROGRESS` mid-checkpoint (an ordinary whole-tree
    commit) and keeps editing; B is created at that commit, so B inherits the
    `IN_PROGRESS` **by checkout** and holds a legitimate identity entry of its
    own. This is `pass16.py` `W1`'s shape, reduced to what these arms need."""
    fx = Fixture()
    state = ws.transition_checkpoint_in_progress(
        fx.read_state(fx.a), "v2-1-dry-run", "S-CP3", fx.head, "t1")
    fx.write_state(fx.a, state)
    (fx.a / "docs/ai-workflow/dry-run").mkdir(parents=True, exist_ok=True)
    (fx.a / "docs/ai-workflow/dry-run/c.txt").write_text("A's uncommitted work\n")
    ws.write_worktree_identity(fx.a, "v2-1-dry-run", now="t1")
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "mid-checkpoint whole-tree commit")
    h1 = git(fx.a, "rev-parse", "HEAD")
    (fx.a / "docs/ai-workflow/dry-run/c.txt").write_text("A's uncommitted work, extended\n")
    b = fx.tmp / "B"
    git(fx.a, "worktree", "add", "-q", str(b), "-b", "scratch-B", h1)
    ws.write_worktree_identity(b, "v2-1-dry-run", now="t1b")
    return fx, b, h1


def scratch_repo(prefix: str) -> Path:
    root = Path(tempfile.mkdtemp(prefix=prefix))
    subprocess.run(["git", "init", "-q", "-b", "main", str(root)], check=True)
    for key, value in (("user.email", "t@t"), ("user.name", "t")):
        subprocess.run(["git", "-C", str(root), "config", key, value], check=True)
    (root / ".gitignore").write_text(".ai-review/\n")
    subprocess.run(["git", "-C", str(root), "add", "-A"], check=True)
    subprocess.run(["git", "-C", str(root), "commit", "-qm", "base"], check=True)
    return root


# ---------------------------------------------------------------------------
# V1 -- OPUS-R87-001: the admission direction is selectable by the adopter
# ---------------------------------------------------------------------------

print("V1  OPUS-R87-001 -- the revision-69 reference is one the adopter selects")

fx, b, _ = fixture_inherited_in_progress()
try:
    co.adopt_claim(b, "v2-1-dry-run", fx.work_item(b), now="tB",
                   verify_resume_safety=co.verify_dirty_resume_safety_strict)
    chk("V1a baseline: revision 69 refuses B", False, "-> adopted")
except co.CheckpointOriginationUnprovableError:
    chk("V1a baseline: revision 69 refuses B with CheckpointOriginationUnprovableError", True)
chk("V1b baseline: no claim published", co.resolve_claim(b, "v2-1-dry-run") is None)

git(b, "reset", "-q", "--soft", "HEAD~1")
chk("V1c after `git reset --soft HEAD~1`: B's working tree still records IN_PROGRESS",
    fx.work_item(b)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS")
chk("V1d the revision-69 HEAD-relative read now reports absent",
    co.committed_checkpoint_status(b, "v2-1-dry-run", "S-CP3") is None)
record = co.adopt_claim(b, "v2-1-dry-run", fx.work_item(b), now="tB2",
                        verify_resume_safety=co.verify_dirty_resume_safety_strict)
chk("V1e ADOPTION ADMITTED -- the defect", record.get("adopted") is True)
chk("V1f a claim is published from B", co.resolve_claim(b, "v2-1-dry-run") is not None)
try:
    co.resolve_ownership(fx.a, fx.work_item(fx.a), "v2-1-dry-run", "S-CP3",
                         verify_resume_safety=co.verify_dirty_resume_safety_strict, now="tA")
    chk("V1g the true originator is locked out", False, "-> A still admitted")
except co.CheckpointOwnedByOtherWorktreeError:
    chk("V1g the true originator A is locked out (CheckpointOwnedByOtherWorktreeError)", True)
chk("V1h corrected reference refuses this arm",
    origination_reference_status(b, "v2-1-dry-run", "S-CP3")[0] == "IN_PROGRESS")
fx.cleanup()

# HEAD unmoved: the content is acquired without touching any ref at all.
fx = Fixture()
state = ws.transition_checkpoint_in_progress(
    fx.read_state(fx.a), "v2-1-dry-run", "S-CP3", fx.head, "t1")
fx.write_state(fx.a, state)
ws.write_worktree_identity(fx.a, "v2-1-dry-run", now="t1")
git(fx.a, "add", "-A")
git(fx.a, "commit", "-qm", "mid-checkpoint whole-tree commit")
h1 = git(fx.a, "rev-parse", "HEAD")
b = fx.tmp / "B2"
git(fx.a, "worktree", "add", "-q", str(b), "-b", "scratch-B2", fx.head)
ws.write_worktree_identity(b, "v2-1-dry-run", now="t1b")
git(b, "checkout", "-q", h1, "--", STATE_REL)
git(b, "reset", "-q")
chk("V1i `git checkout <commit> -- <state path>`: B's HEAD is unmoved",
    git(b, "rev-parse", "HEAD") == fx.head)
chk("V1j the revision-69 read reports absent",
    co.committed_checkpoint_status(b, "v2-1-dry-run", "S-CP3") is None)
record = co.adopt_claim(b, "v2-1-dry-run", fx.work_item(b), now="tB3",
                        verify_resume_safety=co.verify_dirty_resume_safety_strict)
chk("V1k ADOPTION ADMITTED with no ref moved -- the defect", record.get("adopted") is True)
chk("V1l corrected reference refuses this arm too",
    origination_reference_status(b, "v2-1-dry-run", "S-CP3")[0] == "IN_PROGRESS")
fx.cleanup()

# ---------------------------------------------------------------------------
# V2 -- OPUS-R87-002: the read fails open, and one branch escapes undeclared
# ---------------------------------------------------------------------------

print("V2  OPUS-R87-002 -- the revision-69 read fails open on everything undecidable")

for label, corrupt in (("unparseable committed blob", "{ this is not json"),
                       ("valid JSON that is not an object", '["not","an","object"]')):
    fx = Fixture()
    b = fx.tmp / "BX"
    git(fx.a, "worktree", "add", "-q", str(b), "-b", f"br-{abs(hash(label)) % 9999}", fx.head)
    (b / STATE_REL).write_text(corrupt)
    git(b, "add", "-A")
    git(b, "commit", "-qm", "corrupt committed state")
    fx.write_state(b, ws.transition_checkpoint_in_progress(
        fx.base_state(), "v2-1-dry-run", "S-CP3", fx.head, "t1"))
    try:
        got = co.committed_checkpoint_status(b, "v2-1-dry-run", "S-CP3")
        chk(f"V2 [{label}] revision-69 read -> {got!r} (fails OPEN)", got is None)
    except AttributeError:
        chk(f"V2 [{label}] revision-69 read escapes as UNDECLARED AttributeError", True)
    try:
        origination_reference_status(b, "v2-1-dry-run", "S-CP3")
        chk(f"V2 [{label}] corrected reference refuses", False, "-> admitted")
    except OriginationUndecidable:
        chk(f"V2 [{label}] corrected reference refuses (undecidable)", True)
    fx.cleanup()

# ---------------------------------------------------------------------------
# V3 -- OPUS-R87-005: the partition is not closed over what the path can be
# ---------------------------------------------------------------------------

print("V3  OPUS-R87-005 -- identity path kind: reader, writer and observer disagree")

DECLARED = (ws.CorruptJsonError, ws.WorktreeIdentityMissingError,
            ws.WorktreeIdentityMismatchError, co.CheckpointOwnershipUnavailableError)


def identity_doc(root: Path) -> str:
    repo_root, common, worktree = ws._git_identity(root)
    return json.dumps({"repo_root": repo_root, "git_common_dir": common,
                       "worktree_root": worktree,
                       "expected_dirty_paths_by_work_item": {"wi": []},
                       "generated_at": "n"}, indent=2) + "\n"


def probe(setup, consumer: str) -> str:
    """Each consumer gets its OWN fresh fixture: probing them in sequence lets
    the writer replace the object before the observer reads it, which reports a
    symlink as `valid` and hides the disagreement."""
    root = scratch_repo("pass17-path-")
    path = co.identity_document_path(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    setup(root, path, identity_doc(root))
    try:
        if consumer == "reader":
            co.verify_dirty_resume_safety_strict(root, "wi")
            result = "admits"
        elif consumer == "writer":
            co.establish_worktree_identity(root, "wi", now="n")
            result = "writes"
        else:
            result = co.local_identity_observation(root).get("state")
    except DECLARED as exc:
        result = f"declared {type(exc).__name__}"
    except Exception as exc:  # noqa: BLE001 -- classifying undeclared escapes is the point
        result = f"UNDECLARED {type(exc).__name__}"
    shutil.rmtree(root, ignore_errors=True)
    return result


def eacces(root, path, doc):
    path.write_text(doc)
    os.chmod(path, 0o000)


# `want_invariant` is whether revision 69 satisfies the plan's stated
# reader/observer invariant --
#     local_identity_observation(...).state == "undecidable"
#       IFF  the reader refuses with a class FROM THE PARTITION
# -- for that row. `False` is the *reported defect* for that row, asserted as
# an expected outcome so the arm fails if the disagreement silently goes away
# for the wrong reason, exactly as a control arm does elsewhere in these passes.
CASES = (
    ("valid regular file", lambda r, p, d: p.write_text(d),
     "admits", "writes", "valid", True),
    ("directory", lambda r, p, d: p.mkdir(),
     "UNDECLARED IsADirectoryError", "UNDECLARED IsADirectoryError", "undecidable", False),
    ("EACCES file", eacces,
     "UNDECLARED PermissionError", "UNDECLARED PermissionError", "undecidable", False),
    ("symlink -> valid", lambda r, p, d: ((p.parent / "real.json").write_text(d),
                                          p.symlink_to(p.parent / "real.json")),
     "admits", "writes", "undecidable", False),
    ("symlink -> missing", lambda r, p, d: p.symlink_to(p.parent / "nope.json"),
     "declared WorktreeIdentityMissingError", "writes", "undecidable", True),
)

for label, setup, want_reader, want_writer, want_observer, want_invariant in CASES:
    reader, writer, observer = (probe(setup, c) for c in ("reader", "writer", "observer"))
    chk(f"V3 [{label}] reader={reader}", reader == want_reader)
    chk(f"V3 [{label}] writer={writer}", writer == want_writer)
    chk(f"V3 [{label}] observer={observer}", observer == want_observer)
    reader_refuses_declared = reader.startswith("declared")
    observer_undecidable = observer == "undecidable"
    holds = reader_refuses_declared == observer_undecidable
    chk(f"V3 [{label}] reader/observer invariant holds under revision 69: {holds}",
        holds == want_invariant,
        "" if want_invariant else "-- the reported disagreement")

# The fifo row is a hang rather than an exception, so it is asserted under a
# bounded timeout in a subprocess -- an exception assertion cannot catch it.
fifo_probe = Path(tempfile.mkdtemp(prefix="pass17-fifo-")) / "probe.py"
fifo_probe.write_text(f"""
import os, subprocess, sys, tempfile
from pathlib import Path
sys.path.insert(0, {str(REPO / 'scripts')!r}); sys.path.insert(0, {str(Path(__file__).resolve().parent)!r})
import checkpoint_ownership as co
root = Path(tempfile.mkdtemp())
subprocess.run(["git","init","-q","-b","main",str(root)],check=True)
for k,v in (("user.email","t@t"),("user.name","t")):
    subprocess.run(["git","-C",str(root),"config",k,v],check=True)
(root/".gitignore").write_text(".ai-review/\\n")
subprocess.run(["git","-C",str(root),"add","-A"],check=True)
subprocess.run(["git","-C",str(root),"commit","-qm","b"],check=True)
p = co.identity_document_path(root); p.parent.mkdir(parents=True, exist_ok=True)
os.mkfifo(p)
co.verify_dirty_resume_safety_strict(root, "wi")
""")
timed_out = subprocess.run([sys.executable, str(fifo_probe)],
                           capture_output=True, timeout=None if False else 10,
                           check=False) if False else None
try:
    subprocess.run([sys.executable, str(fifo_probe)], capture_output=True, timeout=10, check=False)
    chk("V3 [fifo] the 1c refusal BLOCKS rather than refusing", False, "-> it returned")
except subprocess.TimeoutExpired:
    chk("V3 [fifo] the 1c refusal BLOCKS rather than refusing (bounded-timeout probe)", True)
shutil.rmtree(fifo_probe.parent, ignore_errors=True)

# ---------------------------------------------------------------------------
# C -- the corrected reference, checked in both directions
# ---------------------------------------------------------------------------

print("C   the corrected origination reference")

root = Path(tempfile.mkdtemp(prefix="pass17-revlist-"))
a = root / "A"
a.mkdir()
git(root, "init", "-q", "-b", "main", str(a))
for key, value in (("user.email", "t@t"), ("user.name", "t")):
    git(a, "config", key, value)
(a / "f").write_text("1")
git(a, "add", "-A")
git(a, "commit", "-qm", "c1")
base = git(a, "rev-parse", "HEAD")
(a / "f").write_text("2")
git(a, "add", "-A")
git(a, "commit", "-qm", "c2")
tip = git(a, "rev-parse", "HEAD")
git(a, "worktree", "add", "-q", "--detach", str(root / "B"), tip)
git(a, "reset", "-q", "--hard", base)
chk("C1 `rev-list --all` spans a second worktree's own DETACHED HEAD",
    tip in git(a, "rev-list", "--all").split())
chk("C2 `--single-worktree` does not -- so the span is the documented default, not luck",
    tip not in git(a, "rev-list", "--single-worktree", "--all").split())
shutil.rmtree(root, ignore_errors=True)

fx = Fixture()
fx.start_cp3_in_a(claim=False)
status, _ = origination_reference_status(fx.a, "v2-1-dry-run", "S-CP3")
chk("C3 an uncommitted transition is absent from the reference -- migration still admits",
    status is None)
fx.cleanup()

started = time.time()
live_wf8b, first_hit = origination_reference_status(REPO, "workflow-v2-1-core", "WF8b")
elapsed = time.time() - started
chk(f"C4 live `WF8b` resolves IN_PROGRESS in {elapsed:.2f}s", live_wf8b == "IN_PROGRESS",
    f"(first hit {first_hit[:8] if first_hit else None})")

# ---------------------------------------------------------------------------
# L1 -- the live-repository conformance arm (read-only)
# ---------------------------------------------------------------------------

print("L1  live-repository conformance (read-only) -- the basis for rejecting OPUS-R87-003")

claims = co.claims_dir(REPO)
claims_existed = claims.exists()
identity = co.identity_document_path(REPO)
chk("L1a the identity document EXISTS in this worktree", identity.exists(), str(identity))
if identity.exists():
    doc = json.loads(identity.read_text())
    chk("L1b it carries an entry for workflow-v2-1-core",
        "workflow-v2-1-core" in doc.get("expected_dirty_paths_by_work_item", {}))
try:
    ws.verify_dirty_resume_safety(REPO, "workflow-v2-1-core")
    chk("L1c the installed verify_dirty_resume_safety PASSES", True)
except Exception as exc:  # noqa: BLE001
    chk("L1c the installed verify_dirty_resume_safety PASSES", False, f"-> {type(exc).__name__}")
try:
    co.verify_dirty_resume_safety_strict(REPO, "workflow-v2-1-core")
    chk("L1d the hardened reader PASSES too", True)
except Exception as exc:  # noqa: BLE001
    chk("L1d the hardened reader PASSES too", False, f"-> {type(exc).__name__}")

state = json.loads((REPO / STATE_REL).read_text())
work_item = state["work_items"]["workflow-v2-1-core"]
try:
    co.resolve_ownership(REPO, work_item, "workflow-v2-1-core", "WF8b",
                         verify_resume_safety=co.verify_dirty_resume_safety_strict, now="probe")
    chk("L1e the live 1c resolver refuses", False, "-> it returned")
except co.CheckpointOriginationUnprovableError as exc:
    chk("L1e the live 1c resolver raises CheckpointOriginationUnprovableError "
        "(NOT WorktreeIdentityMissingError)", True)
    chk("L1f that refusal's message names a concrete escape",
        "take the claim over explicitly" in str(exc))
    chk("L1g but it carries NO structured ownership evidence -- the accepted residual",
        getattr(exc, "ownership_hint", None) is None
        and getattr(exc, "ownership_claim", None) is None)
except Exception as exc:  # noqa: BLE001
    chk("L1e the live 1c resolver raises CheckpointOriginationUnprovableError", False,
        f"-> {type(exc).__name__}")

chk("L1h the probe published nothing: the claims directory is still as it was",
    claims.exists() == claims_existed, f"(exists={claims.exists()})")

print()
print(f"{PASS}/{PASS + FAIL} checks passed")
sys.exit(1 if FAIL else 0)
