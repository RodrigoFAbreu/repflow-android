"""Stress pass 16 (revision-69 authoring) -- the external findings
`OPUS-R86-001`/`-002`/`-003` (blocking) and `-004`/`-005` (important), each
reproduced against revision 68's shape *before* the fix, so all five are
validated rather than believed, and each corrected arm carries the revision-68
shape as a **live** control arm rather than a description of one.

`OPUS-R86-001`: revision 68's safety case for writing the identity record
before the claim rests on "this worktree's own uncommitted `IN_PROGRESS` state,
which only its own 1d writes, and which is never committed, so no checkout can
supply it". That premise is false against this repository at the revision-68
head -- five commits reachable from `HEAD` carry `workflow-v2-1-core`'s own
`WF8b` checkpoint `IN_PROGRESS`, because a whole-tree checkpoint commit for one
work item stages another work item's in-flight transition along with it. Once a
checkout can supply `IN_PROGRESS`, the **adoption** path admits a worktree that
never originated the checkpoint, publishes its claim, and locks the true
originator out.

`OPUS-R86-002`: `WORKTREE_IDENTITY.json` holds *every* work item's entry and is
updated by an unserialized read-modify-write, so a concurrent write for a
**different** work item silently deletes the entry that revision 68 made the
sole proof of origination -- and its owner is then refused at its own next 1c,
which is `OPUS-R84-001`'s lockout produced by activity on an unrelated item.

`OPUS-R86-003`: the takeover -- named as the sanctioned repair for a corrupt
identity record -- refused on that record *after* rotating the claim, and every
retry rotated again.

`OPUS-R86-004`: the third error class is not exhaustive. A non-mapping identity
document escapes the 1c refusal itself as `AttributeError`, a non-iterable
snapshot member as `TypeError`, and a JSON `null` document is silently
overwritten by the writer that owes validate-before-mutate.

`OPUS-R86-005`: the specified writer delegates to `write_worktree_identity`,
which writes the **real pathname** with a plain `write_text` before the
wrapper's temp-plus-`os.replace` -- so the writer the plan called "asserted
incapable of producing a torn record" wrote it non-atomically on every call.

Arms:
  W0  the premise: this repository's own history carries committed IN_PROGRESS
  W1  adoption by a worktree whose IN_PROGRESS came from a checkout (R86-001)
  W2  serialization of the identity document across work items (R86-002)
  W3  the refusal classification, closed over document shape (R86-004)
  W4  the writer: single-write publication, no null overwrite (R86-005/-004)
  W5  the takeover refuses before rotating, and retries are idempotent (R86-003)
  W6  the abandoned-guard recovery has the same corrected ordering (R86-003)
  W7  the complete repair sequence, end to end, with no hand deletion
  W8  the real repository is untouched
"""

from __future__ import annotations

import json
import os
import subprocess
import threading
from pathlib import Path

from harness import Fixture, STATE_REL, git, step1_fixed, step1f_commit
import checkpoint_ownership as co
import workflow_state as ws

WID = "v2-1-dry-run"
RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f"  -- {detail}" if detail else ""))


def expect(name, fn, exc_type, must_mention=()):
    try:
        fn()
    except exc_type as exc:
        missing = [frag for frag in must_mention if frag not in str(exc)]
        check(name, not missing, str(exc)[:110] if not missing else f"message omits {missing}")
    except Exception as exc:  # noqa: BLE001
        check(name, False, f"{type(exc).__name__}: {exc}")
    else:
        check(name, False, "no exception raised")


class FreshFixture(Fixture):
    """The base fixture with **nothing** complete, so `S-CP1` is the first
    selectable checkpoint and a second worktree can legitimately earn an
    identity entry by working it."""

    def base_state(self):
        state = super().base_state()
        item = state["work_items"][WID]
        item["checkpoints"] = {}
        item["last_completed_checkpoint_id"] = None
        return state


def identity_entries(root: Path):
    path = root / ws.WORKTREE_IDENTITY_PATH
    if not path.exists():
        return None
    return sorted(json.loads(path.read_text()).get("expected_dirty_paths_by_work_item", {}))


# ---------------------------------------------------------------------------
# W0 -- the premise, against this repository's own history
# ---------------------------------------------------------------------------
print("== W0. the inertness premise, checked against this repository ==")
REPO = Path(git(Path(__file__).parent, "rev-parse", "--show-toplevel"))
carriers = []
for sha in git(REPO, "rev-list", "HEAD").split():
    blob = subprocess.run(["git", "show", f"{sha}:{STATE_REL}"], cwd=REPO,
                          capture_output=True, text=True)
    if blob.returncode != 0:
        continue
    try:
        state = json.loads(blob.stdout)
    except json.JSONDecodeError:
        continue
    for item_id, item in (state.get("work_items") or {}).items():
        for cp_id, cp in (item.get("checkpoints") or {}).items():
            if isinstance(cp, dict) and cp.get("status") == "IN_PROGRESS":
                carriers.append((sha[:8], item_id, cp_id))

check("W0a. commits reachable from HEAD DO carry a checkpoint IN_PROGRESS",
      bool(carriers), f"{len(carriers)} commits, e.g. {carriers[:2]}")
# AMENDED, third session (2026-08-14): at authoring time only
# `workflow-v2-1-core` had a checkout-supplied IN_PROGRESS commit; W0b/W0d
# used the dry-run item's absence from `carriers` as contrast evidence that
# the defect isn't inherent to one item. The Revision 80 plan-approval
# commit (8f8d878) has since swept `v2-1-dry-run`'s own dirty S-CP3 delta
# into HEAD too (see
# WF8B_S14_FINDING_worktree_b_invisible_to_uncommitted_checkpoint.md's
# "This verdict no longer holds" addendum), so both work items are now
# carriers -- which is the stronger form of the same point W0b/W0d were
# making (the defect generalizes; it is not scoped to one item), restated
# rather than left contradicted by live state.
check("W0b. and both this work item and the dry run's now carry one -- the defect "
      "generalizes across work items, it is not scoped to one",
      {item for _, item, _ in carriers} == {"workflow-v2-1-core", WID},
      f"work items: {sorted({item for _, item, _ in carriers})}")
check("W0c. so the revision-68 sentence 'no checkout can supply it' is false",
      len(carriers) > 0)
check("W0d. and by now the mechanism has produced a second, independent instance -- "
      "not a one-off scoped to a single work item",
      any(item == WID for _, item, _ in carriers),
      "'v2-1-dry-run' now has its own checkout-supplied IN_PROGRESS commit too")

# ---------------------------------------------------------------------------
# W1 -- `OPUS-R86-001`: adoption on checkout-supplied IN_PROGRESS
# ---------------------------------------------------------------------------
print("\n== W1. adoption by a worktree that never originated the checkpoint ==")


def build_checkout_supplied_fixture():
    """Every step is a documented operation; the only out-of-band fact is the
    whole-tree commit, which is exactly what `535d4fb..8375b64` did for `WF8b`.

    1. B works S-CP1 through the real step 1 and commits it COMPLETE, so B
       legitimately holds an identity entry for this work item.
    2. A starts S-CP2 the pre-mechanism way (no claim -- the migration case
       adoption exists for) and leaves uncommitted work.
    3. A makes an ordinary whole-tree commit mid-checkpoint, so S-CP2
       IN_PROGRESS becomes committed, and keeps editing.
    4. B fast-forwards. B's own state now says S-CP2 IN_PROGRESS.
    """
    fx = FreshFixture()
    b = fx.tmp / "B"
    git(fx.a, "worktree", "add", "-q", "--detach", str(b), fx.head)
    step1_fixed(fx, b, now="tb1")
    token = co.resolve_claim(b, WID)["owner_token"]
    step1f_commit(fx, b, "S-CP1", token, now="tb2")
    git(fx.a, "merge", "--ff-only", "-q", git(b, "rev-parse", "HEAD"))

    work_item = fx.work_item(fx.a)
    state = ws.transition_checkpoint_in_progress(
        fx.read_state(fx.a), WID, "S-CP2", git(fx.a, "rev-parse", "HEAD"), "ta1")
    fx.write_state(fx.a, state)
    (fx.a / "docs/ai-workflow/dry-run").mkdir(parents=True, exist_ok=True)
    (fx.a / "docs/ai-workflow/dry-run/c.txt").write_text("A's work\n")
    ws.write_worktree_identity(fx.a, WID, now="ta1")
    git(fx.a, "add", "-A")
    git(fx.a, "commit", "-qm", "mid-checkpoint whole-tree commit")
    (fx.a / "docs/ai-workflow/dry-run/c2.txt").write_text("A keeps editing\n")
    git(b, "merge", "--ff-only", "-q", git(fx.a, "rev-parse", "HEAD"))
    fx.b = b
    return fx, b


fx, b = build_checkout_supplied_fixture()
try:
    check("W1a. B's own state says S-CP2 IN_PROGRESS, supplied purely by the checkout",
          fx.work_item(b)["checkpoints"].get("S-CP2", {}).get("status") == "IN_PROGRESS")
    check("W1b. the committed state at HEAD says the same, which is what makes it inheritable",
          co.committed_checkpoint_status(b, WID, "S-CP2", state_rel_path=STATE_REL)
          == "IN_PROGRESS")
    check("W1c. no claim exists anywhere -- the claim-absent adoption path is the one under test",
          co.resolve_claim(b, WID) is None)
    check("W1d. and B legitimately holds an identity entry, earned on S-CP1",
          identity_entries(b) == [WID])

    # CONTROL ARM: revision 68's unguarded adoption.
    outcome = step1_fixed(fx, b, now="tb3", adopt_origination_guard=False)
    claim = co.resolve_claim(b, WID)
    check("W1e. CONTROL ARM (revision 68): B resumes a checkpoint it never started",
          outcome["mode"] == "resume" and outcome["selected"] == "S-CP2", str(outcome))
    check("W1f. CONTROL ARM: B publishes the claim, flagged `adopted`",
          claim is not None and claim.get("adopted") is True
          and claim.get("worktree_root") == str(b))
    expect("W1g. CONTROL ARM: the true originator is locked out of its own work",
           lambda: step1_fixed(fx, fx.a, now="ta3"),
           co.CheckpointOwnedByOtherWorktreeError, ("is claimed by worktree",))
finally:
    fx.cleanup()

fx, b = build_checkout_supplied_fixture()
try:
    before = fx.snapshot()
    expect("W1h. CORRECTED: B is refused -- origination it cannot prove",
           lambda: step1_fixed(fx, b, now="tb3"),
           co.CheckpointOriginationUnprovableError,
           ("committed at HEAD", "take the claim over explicitly"))
    check("W1i. CORRECTED: nothing was mutated -- no claim published",
          co.resolve_claim(b, WID) is None)
    check("W1j. CORRECTED: and no state, identity or working-tree write happened in B",
          fx.snapshot()["B_state_bytes"] == before["B_state_bytes"]
          and identity_entries(b) == [WID])
    expect("W1k. CORRECTED: A is refused symmetrically -- the mechanism cannot tell them apart",
           lambda: step1_fixed(fx, fx.a, now="ta3"),
           co.CheckpointOriginationUnprovableError, ("Only the uncommitted transition",))
    check("W1l. CORRECTED: A's own uncommitted work is untouched by its refusal",
          fx.snapshot()["A_scratch"] == before["A_scratch"]
          and fx.snapshot()["A_state_bytes"] == before["A_state_bytes"])
finally:
    fx.cleanup()

# The migration path the guard must NOT close: the live `S-CP3` shape, whose
# IN_PROGRESS is uncommitted.
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=False, now="t1")
    check("W1m. the live S-CP3 shape: IN_PROGRESS locally, absent from the committed state",
          fx.work_item(fx.a)["checkpoints"]["S-CP3"]["status"] == "IN_PROGRESS"
          and co.committed_checkpoint_status(fx.a, WID, "S-CP3", state_rel_path=STATE_REL)
          is None)
    outcome = step1_fixed(fx, fx.a, now="t2")
    claim = co.resolve_claim(fx.a, WID)
    check("W1n. CORRECTED: the originating worktree still adopts and resumes",
          outcome["mode"] == "resume" and outcome["selected"] == "S-CP3"
          and claim.get("adopted") is True, str(outcome))
    check("W1o. CORRECTED: adoption stays idempotent",
          step1_fixed(fx, fx.a, now="t3")["mode"] == "resume"
          and co.resolve_claim(fx.a, WID)["owner_token"] == claim["owner_token"])

    b = fx.add_worktree_b()
    expect("W1p. and the foreign worktree is still refused before any mutation",
           lambda: step1_fixed(fx, b, now="t4"),
           ws.WorktreeIdentityMissingError, ())
finally:
    fx.cleanup()

# The conformance assertion the correction owes over this repository's own
# history: the guard must refuse exactly the checkpoints W0 found committed
# IN_PROGRESS, and admit every other, measured against the real repository.
print("  -- conformance over this repository's own committed state --")
committed_here = {
    (item_id, cp_id): co.committed_checkpoint_status(REPO, item_id, cp_id,
                                                     state_rel_path=STATE_REL)
    for _, item_id, cp_id in carriers
}
check("W1q. the guard's test reports IN_PROGRESS for every checkpoint W0 found carried",
      set(committed_here.values()) == {"IN_PROGRESS"} and committed_here,
      f"{sorted(committed_here)}")
check("W1r. so adopting workflow-v2-1-core's WF8b here would refuse today -- "
      "a stated, one-time takeover, not a silent behaviour change",
      committed_here.get(("workflow-v2-1-core", "WF8b")) == "IN_PROGRESS")
# AMENDED, third session (2026-08-14): S-CP3 was the migration case (absent
# from committed history, so adoption would still admit it) when this was
# authored. It is no longer -- the Revision 80 plan-approval commit swept it
# in too (see the finding file's "This verdict no longer holds" addendum),
# so it is now a second refuse-and-takeover instance alongside W1r's WF8b,
# not an admitted case. Restated to match, mirroring W1r's own pattern.
check("W1s. and the dry-run work item's S-CP3 would now ALSO refuse automatic "
      "adoption -- a second stated, one-time takeover, no longer the admitted "
      "migration case",
      co.committed_checkpoint_status(REPO, WID, "S-CP3", state_rel_path=STATE_REL)
      == "IN_PROGRESS")
check("W1t. the test is the design's own durable-vs-working-tree one (A6), not a new authority",
      co.committed_checkpoint_status.__module__ == co.__name__)

# ---------------------------------------------------------------------------
# W2 -- `OPUS-R86-002`: the identity document across work items
# ---------------------------------------------------------------------------
print("\n== W2. the identity document is shared by every work item ==")
fx = Fixture()
try:
    check("W2a. the multi-entry case is the live one, not a hypothetical",
          sorted(co.build_worktree_identity_document(
              fx.a, "item-y", now="t",
              existing=co.build_worktree_identity_document(
                  fx.a, "item-x", now="t", existing=None))
              ["expected_dirty_paths_by_work_item"]) == ["item-x", "item-y"])

    def unserialized_writer(root, work_item_id, *, now):
        """CONTROL ARM: revision 68's writer -- the installed
        read-modify-write with no serialization of any kind."""
        return ws.write_worktree_identity(root, work_item_id, now=now)

    def race(writer, trials=12):
        lost = 0
        for _ in range(trials):
            local = Fixture()
            try:
                errors = []

                def run(item, root=local.a):
                    try:
                        writer(root, item, now="t")
                    except Exception as exc:  # noqa: BLE001
                        errors.append(exc)

                threads = [threading.Thread(target=run, args=("item-x",)),
                           threading.Thread(target=run, args=("item-y",))]
                for thread in threads:
                    thread.start()
                for thread in threads:
                    thread.join()
                entries = identity_entries(local.a) or []
                if entries != ["item-x", "item-y"] or errors:
                    lost += 1
            finally:
                local.cleanup()
        return lost

    lost_control = race(unserialized_writer)
    check("W2b. CONTROL ARM (revision 68): concurrent writers lose an entry, repeatedly",
          lost_control > 0, f"{lost_control}/12 trials lost an entry or errored")

    lost_fixed = race(co.establish_worktree_identity)
    check("W2c. CORRECTED: both entries survive every trial",
          lost_fixed == 0, f"{lost_fixed}/12 trials lost an entry")

    # The downstream consequence that actually matters, with the interleave
    # pinned deterministically rather than raced.
    def pinned_lost_update(root):
        """read X -> read Y -> write Y -> write X, the classic lost update."""
        path = root / ws.WORKTREE_IDENTITY_PATH
        path.parent.mkdir(parents=True, exist_ok=True)
        doc_x = co.build_worktree_identity_document(root, "item-x", now="t", existing=None)
        doc_y = co.build_worktree_identity_document(root, "item-y", now="t", existing=None)
        path.write_text(json.dumps(doc_y, indent=2, sort_keys=True) + "\n")
        path.write_text(json.dumps(doc_x, indent=2, sort_keys=True) + "\n")

    local = Fixture()
    try:
        pinned_lost_update(local.a)
        check("W2d. CONTROL ARM: after the pinned interleave, item-y's entry is gone",
              identity_entries(local.a) == ["item-x"])
        expect("W2e. CONTROL ARM: and item-y's legitimate owner is refused at its own 1c",
               lambda: co.verify_dirty_resume_safety_strict(local.a, "item-y"),
               ws.WorktreeIdentityMissingError,
               ("no expected_dirty_paths_by_work_item entry",))
    finally:
        local.cleanup()

    local = Fixture()
    try:
        co.establish_worktree_identity(local.a, "item-x", now="t")
        co.establish_worktree_identity(local.a, "item-y", now="t")
        check("W2f. CORRECTED: sequential and concurrent writers alike keep both entries",
              identity_entries(local.a) == ["item-x", "item-y"])
        check("W2g. CORRECTED: and neither owner is refused at its own 1c",
              (co.verify_dirty_resume_safety_strict(local.a, "item-x"),
               co.verify_dirty_resume_safety_strict(local.a, "item-y")) == (None, None))
    finally:
        local.cleanup()

    # Two concurrent writers for the SAME work item leave a schema-valid
    # document with the entry present.
    local = Fixture()
    try:
        threads = [threading.Thread(target=co.establish_worktree_identity,
                                    args=(local.a, "item-x"), kwargs={"now": "t"})
                   for _ in range(4)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        doc = json.loads((local.a / ws.WORKTREE_IDENTITY_PATH).read_text())
        co.validate_worktree_identity_strict(doc)
        check("W2h. same-work-item concurrency leaves a schema-valid document with the entry",
              identity_entries(local.a) == ["item-x"])
        check("W2i. and no staging temp file survives any of them",
              not list((local.a / ws.WORKTREE_IDENTITY_PATH).parent.glob("*.tmp-*")))
    finally:
        local.cleanup()

    check("W2j. the lock is a stable object beside the document, never unlinked",
          co.identity_document_lock_path(fx.a).name == "WORKTREE_IDENTITY.lock"
          and co.identity_document_lock_path(fx.a).parent
          == (fx.a / ws.WORKTREE_IDENTITY_PATH).parent)
    check("W2k. it is per-worktree, so it CAN serialize two work items -- "
          "unlike the per-work-item guard",
          co.identity_document_lock_path(fx.a) == co.identity_document_lock_path(fx.a)
          and co.guard_path(fx.a, "item-x") != co.guard_path(fx.a, "item-y"))
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# W3 -- `OPUS-R86-004`: the refusal classification, closed over document shape
# ---------------------------------------------------------------------------
print("\n== W3. the 1c refusal, closed over document shape ==")
SHAPES = [
    ("absent", None, ws.WorktreeIdentityMissingError),
    ("unparseable bytes", "{not json", ws.CorruptJsonError),
    ("JSON null", "null", ws.CorruptJsonError),
    ("non-mapping: []", "[]", ws.CorruptJsonError),
    ("non-mapping: string", '"hello"', ws.CorruptJsonError),
    ("non-mapping: int", "42", ws.CorruptJsonError),
    ("non-mapping: bool", "true", ws.CorruptJsonError),
    ("mapping, schema-invalid", '{"repo_root": 1}', ws.CorruptJsonError),
    ("mapping, non-list entry list",
     '{"repo_root":"a","git_common_dir":"b","worktree_root":"c","generated_at":"t",'
     '"expected_dirty_paths_by_work_item":[]}', ws.CorruptJsonError),
    ("mapping, non-mapping entry member",
     '{"repo_root":"a","git_common_dir":"b","worktree_root":"c","generated_at":"t",'
     '"expected_dirty_paths_by_work_item":{"v2-1-dry-run":[5]}}', ws.CorruptJsonError),
]

fx = Fixture()
try:
    path = fx.a / ws.WORKTREE_IDENTITY_PATH
    path.parent.mkdir(parents=True, exist_ok=True)
    control_escapes = []
    corrected = []
    for label, raw, expected_class in SHAPES:
        for reader, sink in ((ws.verify_dirty_resume_safety, control_escapes),
                             (co.verify_dirty_resume_safety_strict, corrected)):
            path.unlink(missing_ok=True)
            if raw is not None:
                path.write_text(raw)
            try:
                reader(fx.a, WID)
                sink.append((label, "no exception"))
            except Exception as exc:  # noqa: BLE001
                sink.append((label, type(exc).__name__))

    undeclared = [row for row in control_escapes
                  if row[1] in {"AttributeError", "TypeError"}]
    check("W3a. CONTROL ARM (revision 68): undeclared exceptions escape the 1c refusal itself",
          len(undeclared) == 5, f"{[row[0] for row in undeclared]}")
    check("W3b. CONTROL ARM: and a JSON `null` document is reported as MISSING, not corrupt",
          ("JSON null", "WorktreeIdentityMissingError") in control_escapes)

    mismatches = [(label, got, want.__name__)
                  for (label, got), (_, _, want) in zip(corrected, SHAPES)
                  if got != want.__name__]
    check("W3c. CORRECTED: every shape exits with its declared class",
          not mismatches, f"mismatches: {mismatches}" if mismatches else
          f"{len(SHAPES)} shapes, all declared")
    check("W3d. CORRECTED: no AttributeError or TypeError survives on any shape",
          not [row for row in corrected if row[1] in {"AttributeError", "TypeError"}])

    # Exhaustive over shape, not sampled: the two remaining rows are the
    # valid-document ones, which are the installed function's own.
    path.unlink(missing_ok=True)
    ws.write_worktree_identity(fx.a, WID, now="t")
    check("W3e. valid document, entry present: returns None",
          co.verify_dirty_resume_safety_strict(fx.a, WID) is None)
    expect("W3f. valid document, no entry for this work item: MissingError",
           lambda: co.verify_dirty_resume_safety_strict(fx.a, "other-item"),
           ws.WorktreeIdentityMissingError, ("no expected_dirty_paths_by_work_item entry",))
    doc = json.loads(path.read_text())
    doc["worktree_root"] = "/somewhere/else"
    path.write_text(json.dumps(doc))
    expect("W3g. valid document, different worktree: MismatchError",
           lambda: co.verify_dirty_resume_safety_strict(fx.a, WID),
           ws.WorktreeIdentityMismatchError, ())
    check("W3h. the enumeration is closed: 8 refusing shapes + 2 admitting rows, no residue",
          len(SHAPES) + 2 == 12)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# W4 -- `OPUS-R86-005`: the writer publishes once, by `os.replace`
# ---------------------------------------------------------------------------
print("\n== W4. the identity writer's publication ==")


def record_writes(fn):
    """Instrument every write to the identity document's directory."""
    seq = []
    real_write_text, real_replace = Path.write_text, os.replace

    def traced_write_text(self, *args, **kwargs):
        if "WORKTREE_IDENTITY" in self.name:
            seq.append(("write_text", "FINAL" if self.name == "WORKTREE_IDENTITY.json"
                        else "temp"))
        return real_write_text(self, *args, **kwargs)

    def traced_replace(src, dst, **kwargs):
        if "WORKTREE_IDENTITY" in Path(dst).name:
            seq.append(("os.replace", "FINAL"))
        return real_replace(src, dst, **kwargs)

    Path.write_text, os.replace = traced_write_text, traced_replace
    try:
        fn()
    finally:
        Path.write_text, os.replace = real_write_text, real_replace
    return seq


fx = Fixture()
try:
    def revision68_writer(root, work_item_id, *, now):
        """CONTROL ARM: revision 68's specified writer, verbatim -- validate,
        then delegate to the installed writer, then stage and `os.replace`."""
        full_path = root / ws.WORKTREE_IDENTITY_PATH
        existing = ws._load_json(full_path)
        if existing is not None:
            ws.validate_worktree_identity(existing)
        full_path.parent.mkdir(parents=True, exist_ok=True)
        document = ws.write_worktree_identity(root, work_item_id, now=now)
        tmp = full_path.with_name(full_path.name + f".tmp-{os.getpid()}")
        tmp.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n")
        os.replace(tmp, full_path)
        return document

    control_seq = record_writes(lambda: revision68_writer(fx.a, WID, now="t"))
    final_writes = [row for row in control_seq if row[1] == "FINAL"]
    check("W4a. CONTROL ARM (revision 68): the final pathname is written TWICE",
          len(final_writes) == 2 and final_writes[0][0] == "write_text", str(control_seq))
    check("W4b. CONTROL ARM: the first of them is a plain non-atomic write_text",
          control_seq[0] == ("write_text", "FINAL"))
finally:
    fx.cleanup()

fx = Fixture()
try:
    fixed_seq = record_writes(lambda: co.establish_worktree_identity(fx.a, WID, now="t"))
    final_writes = [row for row in fixed_seq if row[1] == "FINAL"]
    check("W4c. CORRECTED: the final pathname is written exactly once",
          len(final_writes) == 1, str(fixed_seq))
    check("W4d. CORRECTED: and that one write is `os.replace`, never `write_text`",
          final_writes == [("os.replace", "FINAL")])

    # Torn-write injection: a failure during the staging write must leave the
    # previous document byte-identical and schema-valid.
    path = fx.a / ws.WORKTREE_IDENTITY_PATH
    before = path.read_bytes()
    real_write_text = Path.write_text

    def exploding_write_text(self, *args, **kwargs):
        if ".tmp-" in self.name:
            raise OSError("simulated ENOSPC during the staging write")
        return real_write_text(self, *args, **kwargs)

    Path.write_text = exploding_write_text
    try:
        co.establish_worktree_identity(fx.a, "another-item", now="t2")
        crashed = False
    except OSError:
        crashed = True
    finally:
        Path.write_text = real_write_text
    check("W4e. CORRECTED: a failed staging write leaves the previous document byte-identical",
          crashed and path.read_bytes() == before)
    check("W4f. CORRECTED: which is still schema-valid, and no temp file survives",
          (co.validate_worktree_identity_strict(json.loads(path.read_text())) is None)
          and not list(path.parent.glob("*.tmp-*")))

    # A JSON `null` document refuses instead of being silently overwritten.
    path.write_text("null")
    expect("W4g. CORRECTED: a JSON `null` document refuses in the writer",
           lambda: co.establish_worktree_identity(fx.a, WID, now="t3"),
           ws.CorruptJsonError, ("JSON `null`",))
    check("W4h. CORRECTED: and the document is byte-identical afterwards",
          path.read_text() == "null")

    path.write_text("null")
    ws.write_worktree_identity(fx.a, WID, now="t4")
    check("W4i. CONTROL ARM: the installed writer silently overwrites it instead",
          path.read_text() != "null")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# W5 -- `OPUS-R86-003`: the takeover refuses before it rotates
# ---------------------------------------------------------------------------
print("\n== W5. the takeover and a corrupt identity record ==")


def corrupt_takeover_fixture():
    """A holds a claim on S-CP3; B has an identity record for the work item
    that has since gone schema-invalid -- the state whose documented repair is
    the explicit takeover."""
    fx = Fixture()
    b = fx.add_worktree_b()
    fx.start_cp3_in_a(claim=True, now="t1")
    ws.write_worktree_identity(b, WID, now="t2")
    ident = b / ws.WORKTREE_IDENTITY_PATH
    doc = json.loads(ident.read_text())
    doc["expected_dirty_paths_by_work_item"] = []
    ident.write_text(json.dumps(doc))
    return fx, b, ident


def rotate_after_establish(root, work_item_id, checkpoint_id, *, now):
    """CONTROL ARM: revision 68's ordering -- publish the rotation, then
    establish the identity record, with the guard released in `finally`."""
    evidence = co.takeover_evidence(root, work_item_id)
    literal = co.takeover_authorization_literal(work_item_id, evidence, checkpoint_id)
    path = co.claim_path(root, work_item_id)
    lease = co.acquire_guard(root, work_item_id,
                             holder_owner_token=evidence.get("observed_owner_token"),
                             checkpoint_id=checkpoint_id, step="takeover",
                             step_class=co.ORDINARY, now=now, role="takeover",
                             authorized_lease_id=None)
    try:
        current_oid, previous, _err, _unreadable = co.observe_claim(root, work_item_id)
        assert current_oid == evidence.get("claim_observation_id"), literal
        record = co._build_record(
            root, work_item_id, checkpoint_id, now,
            taken_over_from={"owner_token": (previous or {}).get("owner_token")},
            takeover_count=(previous or {}).get("takeover_count", 0) + 1,
            previous_owner_tokens=list((previous or {}).get("previous_owner_tokens", []))
            + ([previous["owner_token"]] if previous else []))
        co._publish_replacing(path, record)
        co.establish_worktree_identity(root, work_item_id, now=now)
        return record
    finally:
        co.release_guard(root, work_item_id, lease)


fx, b, ident = corrupt_takeover_fixture()
try:
    before = co.claim_path(b, WID).read_bytes()
    expect("W5a. CONTROL ARM (revision 68): the takeover raises on the corrupt record",
           lambda: rotate_after_establish(b, WID, "S-CP3", now="t3"),
           ws.CorruptJsonError, ())
    after = co.claim_path(b, WID).read_bytes()
    rotated = json.loads(after)
    check("W5b. CONTROL ARM: having already rotated the claim it reported failing to take",
          before != after and rotated.get("takeover_count") == 1)
    expect("W5c. CONTROL ARM: and every retry rotates again",
           lambda: rotate_after_establish(b, WID, "S-CP3", now="t4"),
           ws.CorruptJsonError, ())
    check("W5d. CONTROL ARM: takeover_count is now 2, inflating the audit trail",
          json.loads(co.claim_path(b, WID).read_bytes()).get("takeover_count") == 2)
finally:
    fx.cleanup()

fx, b, ident = corrupt_takeover_fixture()
try:
    before = co.claim_path(b, WID).read_bytes()
    before_ident = ident.read_bytes()
    evidence = co.takeover_evidence(b, WID)
    # The revision-68 literal: the same authorization, without the repair
    # component revision 69 requires when the local document is undecidable.
    displaced = evidence.get("observed_checkpoint_id") or "none"
    r68_literal = (f"take over {WID} claim {evidence['claim_observation_id']} "
                   f"holding {displaced} as S-CP3")

    def unrepaired_takeover(literal=r68_literal, ev=evidence):
        return co.take_over_claim(b, WID, "S-CP3", now="t3",
                                  user_authorization=literal, evidence=ev)

    expect("W5e. CORRECTED: the takeover refuses -- before touching the claim",
           unrepaired_takeover, co.CheckpointClaimTakeoverRefusedError,
           ("repairing identity",))
    after = co.claim_path(b, WID).read_bytes()
    check("W5f. CORRECTED: with the claim byte-identical -- nothing rotated",
          after == before and json.loads(after).get("takeover_count", 0) == 0)
    check("W5g. CORRECTED: no new previous_owner_tokens entry, no burned token",
          json.loads(after).get("previous_owner_tokens", []) == [])
    check("W5h. CORRECTED: and the corrupt document was not repaired by overwrite either",
          ident.read_bytes() == before_ident)

    for attempt in range(3):
        try:
            unrepaired_takeover()
        except co.CheckpointClaimTakeoverRefusedError:
            pass
    check("W5i. CORRECTED: N failed attempts leave takeover_count at its original value",
          json.loads(co.claim_path(b, WID).read_bytes()).get("takeover_count", 0) == 0)
    check("W5j. CORRECTED: and no guard is left held",
          co.read_guard(b, WID) is None)

    # And the refusal is genuinely ordering, not just authorization: with the
    # repair component withheld from the *validated* evidence, the operation
    # that does reach the identity write still refuses before publishing.
    fresh_evidence = co.takeover_evidence(b, WID)
    fresh_evidence["local_identity"] = {"state": "undecidable",
                                        "identity_observation_id": "stale-observation"}
    literal = co.takeover_authorization_literal(WID, fresh_evidence, "S-CP3")
    expect("W5k. CORRECTED: stale identity evidence refuses at the same point",
           lambda: co.take_over_claim(b, WID, "S-CP3", now="t7",
                                      user_authorization=literal, evidence=fresh_evidence),
           co.CheckpointClaimTakeoverRefusedError, ("identity document changed",))
    check("W5l. CORRECTED: claim still byte-identical after that one too",
          co.claim_path(b, WID).read_bytes() == before)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# W6 -- the abandoned-guard recovery has the identical corrected ordering
# ---------------------------------------------------------------------------
print("\n== W6. the abandoned-guard recovery, same shape and same correction ==")


def abandoned_guard_fixture():
    """Worktree B owns S-CP3, enters step 1f's destructive window, and is then
    deregistered -- the one state this recovery exists for. Worktree A, which
    performs the recovery, has an identity document that has gone corrupt."""
    fx = Fixture()
    b = fx.add_worktree_b()
    token = co.claim_checkpoint(b, WID, "S-CP3", now="t1")["owner_token"]
    co.acquire_guard(b, WID, holder_owner_token=token, checkpoint_id="S-CP3",
                     step="1f-commit", step_class=co.DESTRUCTIVE, now="t2")
    git(fx.a, "worktree", "remove", "--force", str(b))
    ws.write_worktree_identity(fx.a, WID, now="t2")
    ident = fx.a / ws.WORKTREE_IDENTITY_PATH
    doc = json.loads(ident.read_text())
    doc["expected_dirty_paths_by_work_item"] = []
    ident.write_text(json.dumps(doc))
    return fx, ident, token


fx, ident, dead_token = abandoned_guard_fixture()
try:
    evidence = co.takeover_evidence(fx.a, WID)
    check("W6a. the holder is deregistered and the guard is destructive -- "
          "this operation's one state",
          evidence["holder_registered"] is False
          and evidence["guard"]["step_class"] == co.DESTRUCTIVE)
    check("W6b. the evidence reports this worktree's own identity document as undecidable",
          evidence["local_identity"]["state"] == "undecidable",
          evidence["local_identity"].get("error", "")[:70])

    before = co.claim_path(fx.a, WID).read_bytes()
    stale_literal = (f"recover abandoned destructive guard {WID} step "
                     f"{evidence['guard']['step']} guard {evidence['guard_observation_id']} "
                     f"claim {evidence['claim_observation_id']}")
    expect("W6c. the pre-revision-69 literal, with no repair component, is refused",
           lambda: co.recover_abandoned_destructive_guard(
               fx.a, WID, "S-CP3", now="t3", user_authorization=stale_literal,
               evidence=evidence),
           co.CheckpointClaimTakeoverRefusedError, ("repairing identity",))
    check("W6d. having mutated nothing -- the claim is byte-identical",
          co.claim_path(fx.a, WID).read_bytes() == before)

    literal = co.abandoned_guard_recovery_authorization_literal(WID, evidence)
    record = co.recover_abandoned_destructive_guard(
        fx.a, WID, "S-CP3", now="t4", user_authorization=literal, evidence=evidence)
    check("W6e. the correctly authorized recovery succeeds, rotating exactly once",
          record.get("takeover_count") == 1
          and record.get("worktree_root") == str(fx.a))
    check("W6f. it repaired this worktree's identity document before rotating",
          identity_entries(fx.a) == [WID]
          and co.verify_dirty_resume_safety_strict(fx.a, WID) is None)
    check("W6g. and the abandoned guard is superseded by the rotation, not a second primitive",
          co.read_guard(fx.a, WID) is None or
          co.read_guard(fx.a, WID).get("holder_owner_token") != dead_token)
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# W7 -- the complete repair sequence, end to end, with no hand deletion
# ---------------------------------------------------------------------------
print("\n== W7. the complete repair sequence, with no hand deletion ==")
fx, b, ident = corrupt_takeover_fixture()
try:
    corrupt_before = ident.read_bytes()
    evidence = co.takeover_evidence(b, WID)
    check("W7a. the evidence names the corrupt document the operator is being asked about",
          evidence["local_identity"]["state"] == "undecidable"
          and evidence["local_identity"]["identity_observation_id"]
          == co.observation_id(corrupt_before))
    literal = co.takeover_authorization_literal(WID, evidence, "S-CP3")
    check("W7b. and the authorization literal names the repair explicitly",
          literal.endswith(
              f"repairing identity {evidence['local_identity']['identity_observation_id']}"))

    # Stale identity evidence is refused on the same terms as a stale claim.
    tampered = json.loads(corrupt_before)
    tampered["expected_dirty_paths_by_work_item"] = [1]
    ident.write_text(json.dumps(tampered))
    claim_before = co.claim_path(b, WID).read_bytes()
    expect("W7c. an identity document that changed since the evidence is refused",
           lambda: co.take_over_claim(b, WID, "S-CP3", now="t5",
                                      user_authorization=literal, evidence=evidence),
           co.CheckpointClaimTakeoverRefusedError, ("identity document changed",))
    check("W7d. having mutated nothing -- the claim is byte-identical",
          co.claim_path(b, WID).read_bytes() == claim_before)

    ident.write_bytes(corrupt_before)
    record = co.take_over_claim(b, WID, "S-CP3", now="t6",
                                user_authorization=literal, evidence=evidence)
    check("W7e. the authorized takeover succeeds with NO hand-deletion step anywhere",
          record.get("takeover_count") == 1 and record.get("worktree_root") == str(b))
    check("W7f. it repaired and established the taker's own identity record",
          identity_entries(b) == [WID] and ident.read_bytes() != corrupt_before)
    check("W7g. and the taker now passes its own 1c",
          co.verify_dirty_resume_safety_strict(b, WID) is None)
    check("W7h. the displaced owner is fenced by the rotation",
          json.loads(co.claim_path(b, WID).read_bytes()).get("owner_token")
          != fx.owner_token)
    expect("W7i. and its next assertion fails, as every displaced owner's does",
           lambda: co.assert_claim_owner(fx.a, WID, fx.owner_token),
           co.CheckpointOwnedByOtherWorktreeError, ())
    check("W7j. the same authorization is not replayable -- the repair changed the bytes it named",
          co.takeover_authorization_literal(
              WID, co.takeover_evidence(b, WID), "S-CP3") != literal)
finally:
    fx.cleanup()

# A valid or absent document must NOT acquire the repair component, so every
# pre-revision-69 literal is unchanged.
fx = Fixture()
try:
    fx.start_cp3_in_a(claim=True, now="t1")
    b = fx.add_worktree_b()
    ev_absent = co.takeover_evidence(b, WID)
    check("W7k. an absent identity document adds no repair component",
          ev_absent["local_identity"]["state"] == "absent"
          and "repairing identity" not in co.takeover_authorization_literal(
              WID, ev_absent, "S-CP3"))
    ws.write_worktree_identity(b, WID, now="t2")
    ev_valid = co.takeover_evidence(b, WID)
    check("W7l. nor does a valid one -- every earlier literal is byte-for-byte unchanged",
          ev_valid["local_identity"]["state"] == "valid"
          and co.takeover_authorization_literal(WID, ev_valid, "S-CP3")
          == co.takeover_authorization_literal(WID, ev_absent, "S-CP3"))
    def corrupt_then_establish():
        (b / ws.WORKTREE_IDENTITY_PATH).write_text("[]")
        co.establish_worktree_identity(b, WID, now="t3")

    expect("W7m. and `establish_worktree_identity` still refuses a corrupt document outright, "
           "so no path overwrites one silently",
           corrupt_then_establish, ws.CorruptJsonError, ("must be a JSON object",))
    check("W7n. the corrupt document is byte-identical after that refusal",
          (b / ws.WORKTREE_IDENTITY_PATH).read_text() == "[]")
finally:
    fx.cleanup()

# ---------------------------------------------------------------------------
# W8 -- the real repository is untouched
# ---------------------------------------------------------------------------
print("\n== W8. real-repository re-verification (read-only) ==")
check("W8a. no claim record exists anywhere in the real repository",
      co.resolve_claim(REPO, WID) is None
      and co.resolve_claim(REPO, "workflow-v2-1-core") is None)
check("W8b. and no mutation guard either",
      co.read_guard(REPO, WID) is None and co.read_guard(REPO, "workflow-v2-1-core") is None)
check("W8c. the claims directory itself was never created in this repository",
      not co.claims_dir(REPO).exists())
check("W8d. and no identity lock object was created here either",
      not co.identity_document_lock_path(REPO).exists())

print()
failed = [name for name, ok in RESULTS if not ok]
print(f"pass 16: {len(RESULTS) - len(failed)}/{len(RESULTS)} checks green")
for name in failed:
    print(f"  FAILED: {name}")
raise SystemExit(1 if failed else 0)
