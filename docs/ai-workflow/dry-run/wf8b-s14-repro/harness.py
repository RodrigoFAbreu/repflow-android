"""Hermetic two-worktree fixture for the WF8b S14 stress passes.

Uses the repository's REAL `scripts/workflow_state.py` functions against a
throwaway scratch repository. Nothing here touches the real repository.
"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"],
                           cwd=Path(__file__).resolve().parent, capture_output=True, text=True,
                           check=True).stdout.strip())
sys.path.insert(0, str(REPO / "scripts"))
sys.path.insert(0, str(Path(__file__).parent))

import workflow_state as ws  # noqa: E402
import checkpoint_ownership as co  # noqa: E402

STATE_REL = "docs/ai-workflow/WORKFLOW_STATE.json"
REGISTRY = {
    "work_item_id": "v2-1-dry-run",
    "plan_revision": 5,
    "checkpoints": [
        {"id": "S-CP1", "name": "scratch A", "depends_on": [], "complexity": 1, "session_target": 1},
        {"id": "S-CP2", "name": "scratch B", "depends_on": ["S-CP1"], "complexity": 1, "session_target": 1},
        {"id": "S-CP3", "name": "scratch C", "depends_on": ["S-CP2"], "complexity": 1, "session_target": 1},
    ],
}


def git(root: Path, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True,
                          check=True).stdout.strip()


class Fixture:
    """Worktree A (primary) + worktree B (`git worktree add`), mirroring the
    real S-CP1/S-CP2 COMPLETE, S-CP3 uncommitted-IN_PROGRESS shape."""

    def __init__(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="wf8b-s14-stress-"))
        self.a = self.tmp / "A"
        self.a.mkdir()
        git(self.a, "init", "-q", "-b", "main")
        git(self.a, "config", "user.email", "t@t")
        git(self.a, "config", "user.name", "t")
        (self.a / ".gitignore").write_text(".ai-review/\n")
        (self.a / "docs/ai-workflow").mkdir(parents=True)
        self.write_state(self.a, self.base_state())
        git(self.a, "add", "-A")
        git(self.a, "commit", "-qm", "base")
        self.head = git(self.a, "rev-parse", "HEAD")
        self.b: Path | None = None

    # -- state helpers -----------------------------------------------------
    def base_state(self) -> dict:
        """S-CP1/S-CP2 COMPLETE, nothing in progress -- the committed shape."""
        return {
            "schema_version": 1,
            "active_work_item_id": "v2-1-dry-run",
            "work_items": {
                "v2-1-dry-run": {
                    "work_item_id": "v2-1-dry-run",
                    "phase": "IMPLEMENTING",
                    "governing_workflow_version": "2.1",
                    "current_checkpoint_id": None,
                    "last_completed_checkpoint_id": "S-CP2",
                    "checkpoints": {
                        "S-CP1": {"status": "COMPLETE", "start_commit": "0" * 40},
                        "S-CP2": {"status": "COMPLETE", "start_commit": "0" * 40},
                    },
                    "state_revision": 17,
                    "last_transition": "t0",
                }
            },
        }

    def write_state(self, root: Path, state: dict) -> None:
        (root / STATE_REL).parent.mkdir(parents=True, exist_ok=True)
        (root / STATE_REL).write_text(json.dumps(state, indent=2) + "\n")

    def read_state(self, root: Path) -> dict:
        return json.loads((root / STATE_REL).read_text())

    def work_item(self, root: Path) -> dict:
        return self.read_state(root)["work_items"]["v2-1-dry-run"]

    # -- fixture setup -----------------------------------------------------
    def add_worktree_b(self, name: str = "B") -> Path:
        self.b = self.tmp / name
        git(self.a, "worktree", "add", "-q", str(self.b), "-b", f"scratch-{name}", self.head)
        return self.b

    def start_cp3_in_a(self, *, claim: bool, now: str = "t1") -> None:
        """Exactly what step 1d does today (+ the proposed claim), left
        deliberately uncommitted -- the real S14/S15 setup step."""
        self.owner_token = None
        if claim:
            self.owner_token = co.claim_checkpoint(
                self.a, "v2-1-dry-run", "S-CP3", now=now)["owner_token"]
        state = ws.transition_checkpoint_in_progress(
            self.read_state(self.a), "v2-1-dry-run", "S-CP3", self.head, now)
        self.write_state(self.a, state)
        (self.a / "docs/ai-workflow/dry-run").mkdir(parents=True, exist_ok=True)
        (self.a / "docs/ai-workflow/dry-run/c.txt").write_text("scratch C\n")
        ws.write_worktree_identity(self.a, "v2-1-dry-run", now=now)

    def snapshot(self) -> dict:
        """Everything the no-mutation claim must be checked against."""
        ident = self.a / ".ai-review/runtime/WORKTREE_IDENTITY.json"
        own = co.claim_path(self.a, "v2-1-dry-run")
        return {
            "A_state_bytes": (self.a / STATE_REL).read_bytes(),
            "A_identity_bytes": ident.read_bytes() if ident.exists() else None,
            "A_status": git(self.a, "status", "--porcelain"),
            "A_head": git(self.a, "rev-parse", "HEAD"),
            "A_scratch": (self.a / "docs/ai-workflow/dry-run/c.txt").read_bytes()
            if (self.a / "docs/ai-workflow/dry-run/c.txt").exists() else None,
            "ownership_bytes": own.read_bytes() if own.exists() else None,
            "B_state_bytes": (self.b / STATE_REL).read_bytes() if self.b else None,
            "B_status": git(self.b, "status", "--porcelain") if self.b else None,
            "B_head": git(self.b, "rev-parse", "HEAD") if self.b else None,
            "B_identity": (self.b / ".ai-review/runtime/WORKTREE_IDENTITY.json").exists()
            if self.b else None,
        }

    def cleanup(self) -> None:
        shutil.rmtree(self.tmp, ignore_errors=True)


# -- the two command procedures under test ---------------------------------

def step1_today(fx: Fixture, root: Path) -> dict:
    """`/milestone-implement` `[2.1 step 1]` exactly as installed today."""
    wi = fx.work_item(root)
    selected = ws.select_next_checkpoint(wi, REGISTRY)                       # 1b
    is_resume = (selected is not None
                 and selected == wi.get("current_checkpoint_id")
                 and wi["checkpoints"].get(selected, {}).get("status") == "IN_PROGRESS")
    if is_resume:
        ws.verify_dirty_resume_safety(root, "v2-1-dry-run")                  # 1c
        return {"selected": selected, "mode": "resume", "mutated": False}
    state = ws.transition_checkpoint_in_progress(                            # 1d
        fx.read_state(root), "v2-1-dry-run", selected, git(root, "rev-parse", "HEAD"), "tX")
    fx.write_state(root, state)
    ws.write_worktree_identity(root, "v2-1-dry-run", now="tX")
    return {"selected": selected, "mode": "fresh", "mutated": True}


def step1_fixed(fx: Fixture, root: Path, *, now: str = "tX",
                identity_first: bool = True, verify_resume_safety=None,
                adopt_origination_guard: bool = True) -> dict:
    """The same procedure with a distinct step 1c that resolves ownership
    against the shared claim as well as the local state file
    (`D-Checkpoint-Ownership`). Step 1b (`select_next_checkpoint`) is unchanged
    and still pure.

    **Revision 64 (`GPT-R81-001`/`-003`)**: the step-1d state write now runs
    inside the mutation/handoff guard, whose first act is `assert_claim_owner`,
    so a takeover between 1c and 1d refuses this session instead of letting two
    worktrees mutate; and the terminal `NO_CHECKPOINT` outcome returns without
    reaching 1d at all.

    **Revision 68 (`OPUS-R85-001`)**: step 1d establishes this worktree's own
    identity record **before** publishing the claim, so the crash window
    between publication and the state write can never be entered without the
    only durable evidence of worktree *instance* already existing. That is what
    makes `CONTINUE_CLAIM` reachable in the window it is specified for
    (`OPUS-R84-001`) without relaxing step 1c's origination check, which
    revision 67 did and which `OPUS-R85-001` shows admits a different worktree
    occupying the holder's path. `identity_first=False` carries revision 67's
    claim-first ordering as a live control arm.

    **Revision 69 (`OPUS-R86-001`/`-004`)**: step 1c's refusal is the
    hardened reader (`verify_dirty_resume_safety_strict`), so the refusal
    classification is closed over document shape rather than escaping the
    refusal path itself as `AttributeError`/`TypeError`; and adoption is
    guarded on the checkpoint's `IN_PROGRESS` **not** being present in the
    state committed at `HEAD`, so a checkout can no longer supply the
    origination evidence. `verify_resume_safety=ws.verify_dirty_resume_safety`
    and `adopt_origination_guard=False` carry the revision-68 shapes as live
    control arms.
    """
    wi = fx.work_item(root)
    selected = ws.select_next_checkpoint(wi, REGISTRY)                       # 1b
    verify = verify_resume_safety or co.verify_dirty_resume_safety_strict
    # The seam is passed only when it is *off*, so the historical control-arm
    # resolvers passes 14/15 monkeypatch in keep receiving exactly the call
    # shape they were written against and stay verbatim copies.
    seam = {} if adopt_origination_guard else {"adopt_origination_guard": False}
    mode, checkpoint_id, owner_token = co.resolve_ownership(                 # 1c
        root, wi, "v2-1-dry-run", selected,
        verify_resume_safety=verify, now=now, state_rel_path=STATE_REL, **seam)
    if mode == co.NO_CHECKPOINT:
        return {"selected": None, "mode": "no_checkpoint", "mutated": False}
    if mode == co.RESUME:
        return {"selected": checkpoint_id, "mode": "resume", "mutated": False}
    if mode == co.FRESH:                                       # 1d: identity, then acquire
        if identity_first:
            co.establish_worktree_identity(root, "v2-1-dry-run", now=now)
        owner_token = co.claim_checkpoint(
            root, "v2-1-dry-run", checkpoint_id, now=now)["owner_token"]
    with co.owner_mutation(root, "v2-1-dry-run", owner_token,                # 1d: then write
                           checkpoint_id=checkpoint_id, step="1d-state-write",
                           step_class=co.DESTRUCTIVE, now=now):
        state = ws.transition_checkpoint_in_progress(
            fx.read_state(root), "v2-1-dry-run", checkpoint_id,
            git(root, "rev-parse", "HEAD"), now)
        fx.write_state(root, state)
        co.establish_worktree_identity(root, "v2-1-dry-run", now=now)
    return {"selected": checkpoint_id,
            "mode": "fresh" if mode == co.FRESH else "continue_claim",
            "mutated": True}


def step1f_commit(fx: Fixture, root: Path, checkpoint_id: str, owner_token: str, *,
                  now: str = "t9", release: bool = True) -> None:
    """Step 1f in full: the checkpoint commit (carrying `complete_checkpoint`'s
    state write) inside the **destructive** guard, then -- only once that
    completion is durable -- the fenced compare-and-delete release."""
    with co.owner_mutation(root, "v2-1-dry-run", owner_token, checkpoint_id=checkpoint_id,
                           step="1f-commit", step_class=co.DESTRUCTIVE, now=now):
        state = ws.complete_checkpoint(fx.read_state(root), "v2-1-dry-run", checkpoint_id,
                                       REGISTRY, now=now)
        fx.write_state(root, state)
        git(root, "add", "-A")
        git(root, "commit", "-qm", f"complete {checkpoint_id}")
    if release:
        co.release_checkpoint(root, "v2-1-dry-run", checkpoint_id,
                              owner_token=owner_token, now=now)


def step1_with(module, fx: Fixture, root: Path) -> dict:
    """`step1_fixed` parameterized by ownership module, so pass 6 can run the
    frozen revision-62 draft (`checkpoint_ownership_r62draft`) and the
    corrected revision-63 design side by side rather than asserting anything
    about code that no longer exists."""
    wi = fx.work_item(root)
    selected = ws.select_next_checkpoint(wi, REGISTRY)
    mode = module.classify_selection(root, wi, "v2-1-dry-run", selected,
                                     verify_resume_safety=ws.verify_dirty_resume_safety,
                                     now="tX")
    if mode == "resume":
        return {"selected": selected, "mode": "resume", "mutated": False}
    if mode == "fresh":
        module.claim_checkpoint(root, "v2-1-dry-run", selected, now="tX")
    state = ws.transition_checkpoint_in_progress(
        fx.read_state(root), "v2-1-dry-run", selected, git(root, "rev-parse", "HEAD"), "tX")
    fx.write_state(root, state)
    ws.write_worktree_identity(root, "v2-1-dry-run", now="tX")
    return {"selected": selected, "mode": mode, "mutated": True}
