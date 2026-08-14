"""Stress pass 9 (revision-63 authoring) -- scope boundaries and ordering claims.

Three claims the revision makes that are cheap to assert and expensive to be
wrong about: that the defect is specific to an *uncommitted* `IN_PROGRESS`
transition (so the bootstrap driver, which never writes one, is genuinely out of
scope); that release-after-durable-completion is load-bearing rather than
stylistic; and that a `governing_workflow_version: "1"` item never touches the
mechanism at all. Plus a read-only re-verification against the real repository.
"""

from __future__ import annotations

import json
import subprocess
from pathlib import Path

from harness import REGISTRY, Fixture, git, step1_fixed, step1_today, ws
import checkpoint_ownership as co

RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f"  -- {detail}" if detail else ""))


def expect(name, fn, exc_type):
    try:
        fn()
    except exc_type as exc:
        check(name, True, str(exc)[:90])
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")
    else:
        check(name, False, "no exception raised")


# ---------------------------------------------------------------------------
# P1 -- a COMMITTED IN_PROGRESS marker was never vulnerable
# ---------------------------------------------------------------------------
print("== P1. committed vs uncommitted IN_PROGRESS (the scope boundary) ==")
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=False)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "start S-CP3 (committed IN_PROGRESS marker)")
    b = fx.add_worktree_b()
    git(b, "merge", "-q", "--ff-only", "main")
    check("P1. B can see a COMMITTED IN_PROGRESS marker",
          fx.work_item(b)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS")
    expect("P1b. so even the pre-fix procedure already refused it -- the defect is "
           "specific to the UNCOMMITTED transition `/milestone-implement` writes, "
           "which is why the bootstrap driver (trailer-derived, never writing an "
           "uncommitted IN_PROGRESS) is out of this contract's scope",
           lambda: step1_today(fx, b), ws.WorktreeIdentityMissingError)
    expect("P1c. and the fixed procedure refuses it identically",
           lambda: step1_fixed(fx, b), ws.WorktreeIdentityMissingError)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# P2 -- release ordering: releasing before the completion is durable reopens it
# ---------------------------------------------------------------------------
print("\n== P2. release ordering, both arms ==")
for order in ("release-after-commit", "release-before-commit CONTROL"):
    fx = Fixture()
    try:
        fx.start_cp3_in_a(claim=True)
        state = ws.complete_checkpoint(fx.read_state(fx.a), "v2-1-dry-run", "S-CP3",
                                       {"checkpoints": [{"id": "S-CP1"}, {"id": "S-CP2"},
                                                        {"id": "S-CP3"}]}, now="t9")
        fx.write_state(fx.a, state)
        b = fx.add_worktree_b()
        if order.startswith("release-after"):
            git(fx.a, "add", "-A")
            git(fx.a, "commit", "-qm", "complete S-CP3")
            co.release_checkpoint(fx.a, "v2-1-dry-run", "S-CP3",
                                  owner_token=co.resolve_claim(fx.a, "v2-1-dry-run")["owner_token"])
            durable = co.committed_checkpoint_status(fx.a, "v2-1-dry-run", "S-CP3")
            check("P2. release-after-commit: the claim is dropped only once the "
                  "completion is durable", durable == "COMPLETE"
                  and co.resolve_claim(fx.a, "v2-1-dry-run") is None)
        else:
            co.release_checkpoint(  # the mistake
                fx.a, "v2-1-dry-run", "S-CP3",
                owner_token=co.resolve_claim(fx.a, "v2-1-dry-run")["owner_token"])
            durable = co.committed_checkpoint_status(fx.a, "v2-1-dry-run", "S-CP3")
            leaked = step1_fixed(fx, b)["mutated"]
            check("P2b. CONTROL ARM: releasing before the commit hands the work item "
                  "to another worktree while the completion is still uncommitted -- "
                  "proving the ordering is load-bearing", durable != "COMPLETE" and leaked,
                  f"durable={durable} B mutated={leaked}")
    finally:
        fx.cleanup()

# ---------------------------------------------------------------------------
# P3 -- a v1-governed work item never touches the mechanism
# ---------------------------------------------------------------------------
print("\n== P3. governing_workflow_version '1' inertness ==")
fx = Fixture()
try:
    state = fx.read_state(fx.a)
    v1 = json.loads(json.dumps(state["work_items"]["v2-1-dry-run"]))
    v1["work_item_id"] = "legacy-v1-item"
    v1["governing_workflow_version"] = "1"
    state["work_items"]["legacy-v1-item"] = v1
    fx.write_state(fx.a, state)
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "add a v1 item")
    # `/milestone-implement` step 0 routes a "1" item to steps 1-5, which contain
    # no [2.1 step 1] and therefore no ownership resolution at all.
    check("P3. the v1 item has no claim and nothing creates one",
          co.resolve_claim(fx.a, "legacy-v1-item") is None)
    fx.start_cp3_in_a(claim=True)          # the 2.1 item claims normally
    check("P3b. the 2.1 item's claim does not create, touch or imply one for the "
          "v1 item -- claims are per work item, so v1-inertness is structural",
          co.resolve_claim(fx.a, "v2-1-dry-run") is not None
          and co.resolve_claim(fx.a, "legacy-v1-item") is None)
    claim_files = sorted(p.name for p in co.claims_dir(fx.a).iterdir())
    check("P3c. exactly one claim record exists on disk", len(claim_files) == 1)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# P4 -- the real repository, read-only
# ---------------------------------------------------------------------------
print("\n== P4. real repository, read-only ==")
REPO = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"],
                           cwd=Path(__file__).resolve().parent, capture_output=True,
                           text=True, check=True).stdout.strip())
state = json.loads((REPO / "docs/ai-workflow/WORKFLOW_STATE.json").read_text())
dry = state["work_items"]["v2-1-dry-run"]
check("P4. S-CP3 is still IN_PROGRESS at start_commit 8375b64, state_revision 18",
      dry["checkpoints"]["S-CP3"] == {"status": "IN_PROGRESS",
                                      "start_commit": "8375b64f9ad9ad44afe7574841a62457f5d83cea"}
      and dry["current_checkpoint_id"] == "S-CP3" and dry["state_revision"] == 18,
      f"rev={dry['state_revision']}")
committed = json.loads(subprocess.run(
    ["git", "show", "HEAD:docs/ai-workflow/WORKFLOW_STATE.json"], cwd=REPO,
    capture_output=True, text=True, check=True).stdout)
# AMENDED, third session (2026-08-14): S-CP3's IN_PROGRESS transition was
# genuinely uncommitted when this pass was authored. The Revision 80
# plan-approval commit (8f8d878, `workflow-v2-1-core`'s own plan-stage
# protected-path commit) swept the whole working tree, including
# `v2-1-dry-run`'s own dirty WORKFLOW_STATE.json delta, and committed it --
# see WF8B_S14_FINDING_worktree_b_invisible_to_uncommitted_checkpoint.md's
# "This verdict no longer holds" addendum for the verified evidence. That is
# now a second, live instance of exactly what P4c already documents for
# `workflow-v2-1-core`/WF8b, so the assertion is restated to match.
check("P4b. and that transition is now ALSO committed, exactly like WF8b's own "
      "(a second live instance of the same 'checkout can supply IN_PROGRESS' "
      "mechanism P4c documents)",
      "S-CP3" in committed["work_items"]["v2-1-dry-run"]["checkpoints"]
      and committed["work_items"]["v2-1-dry-run"]["checkpoints"]["S-CP3"]["status"]
      == "IN_PROGRESS")
check("P4c. `workflow-v2-1-core`'s own WF8b IN_PROGRESS marker IS committed, so it "
      "was never exposed to this defect",
      committed["work_items"]["workflow-v2-1-core"]["checkpoints"]["WF8b"]["status"]
      == "IN_PROGRESS")
check("P4d. no claim record exists anywhere in the real repository -- this analysis "
      "wrote none",
      not co.claims_dir(REPO).exists() or not list(co.claims_dir(REPO).iterdir()))
dirty = subprocess.run(["git", "status", "--porcelain"], cwd=REPO, capture_output=True,
                       text=True, check=True).stdout.split("\n")
tracked_mod = sorted(line[3:] for line in dirty if line[:2].strip() == "M")
# Amended in place, third session (2026-08-14): see pass5.py's
# ALLOWED_TRACKED_EDITS note -- the Revision 80 plan-approval commit
# committed the other four members, leaving only the scenario doc dirty.
check("P4e. the only modified tracked file is this work item's own scenario doc "
      "-- nothing outside that surface",
      tracked_mod == ["docs/ai-workflow/dry-run/WF8B_SCENARIOS.md"],
      str(tracked_mod))
untracked = sorted(line[3:] for line in dirty if line.startswith("??"))
check("P4f. and every untracked addition is WF8b dry-run evidence",
      all(u.startswith("docs/ai-workflow/dry-run/") for u in untracked), str(untracked))

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 9: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
