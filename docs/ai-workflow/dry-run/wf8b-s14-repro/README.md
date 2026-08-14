# `WF8b` `S14` reproduction and design stress passes

Executable evidence for
`../WF8B_S14_FINDING_worktree_b_invisible_to_uncommitted_checkpoint.md` and for
`WORKFLOW_V2_PLAN.md`'s `D-Checkpoint-Ownership` (plan revisions 63 and 64).
Read the finding first — this directory only *runs* what it argues.

## What these prove

1. **The defect.** `/milestone-implement`'s `[2.1 step 1]` classifies a
   checkpoint left `IN_PROGRESS`-and-uncommitted in one worktree as a **fresh
   start** from any other worktree of the same repository, and mutates.
2. **The design** (`checkpoint_ownership.py`, a prototype — **not installed
   anywhere**), and fifteen bounded adversarial passes against it. Passes 10 and
   11 belong to revision 64, which fences the contract after external plan
   review found it coordinated but not fenced (`GPT-R81-001`/`-002`/`-003`);
   pass 12 to revision 65 (`OPUS-R82-001`/`-002`); pass 13 to revision 66
   (`OPUS-R83-001`/`-002`); pass 14 to revision 67 (`OPUS-R84-001`); and pass 15
   to revision 68 (`OPUS-R85-001`/`-002`), which withdraws revision 67's
   relaxation of step 1c after review showed the ownership key it trusted is
   path-aliasable, and restores `CONTINUE_CLAIM`'s reachability at 1d's ordering
   instead.

## Running

Stdlib-only Python 3, no arguments, no fixtures to install:

```
for p in $(seq 1 14); do python3 pass$p.py || break; done
```

Each pass creates and destroys its own throwaway Git repository under `/tmp`
(two or three real `git worktree add` checkouts per fixture) and imports this
repository's **real** `scripts/workflow_state.py` — `select_next_checkpoint`,
`transition_checkpoint_in_progress`, `complete_checkpoint`,
`write_worktree_identity` and `verify_dirty_resume_safety` are the production
functions, not reimplementations. `pass5.py` and `pass9.py` additionally make
read-only assertions against this repository itself. Nothing here writes to this
repository.

## Files

| file | role |
| --- | --- |
| `checkpoint_ownership.py` | prototype of `D-Checkpoint-Ownership` as revision 67 specifies it |
| `checkpoint_ownership_r62draft.py` | **frozen** revision-62 draft, kept unmodified as pass 6's control arm |
| `harness.py` | two-worktree fixture; `step1_today` / `step1_fixed` / `step1_with` |
| `pass1.py` | defect reproduction, then the seven properties the fix must hold |
| `pass2.py` | crash windows, corruption, unwritable record, two-item interleaving, separate clone, relocation |
| `pass3.py` | concurrency, `git gc`/`worktree prune`, older-commit worktree, third worktree, non-regression |
| `pass4.py` | step-1d write ordering (both arms), nested worktree, symlinked path, discard/restart, repeated refusal |
| `pass5.py` | orphaned claim, `git worktree move`, exhausted registry, real-repository read-only checks |
| `pass6.py` | **five defects in the revision-62 draft**, each run against both the frozen draft and the fix |
| `pass7.py` | adversarial attack on what pass 6's fixes introduced: adoption, `CONTINUE_CLAIM`, durability, takeover |
| `pass8.py` | the repaired `S14`→`S15` end to end; claims-directory symlink, hostile ids, symlinked `.git`, refusal side-effect freedom |
| `pass9.py` | scope boundaries (committed vs uncommitted `IN_PROGRESS`, v1 inertness), release ordering, real-repository re-verification |
| `pass10.py` | **revision 64**: the three blocking review findings, each with a revision-63 control arm; real-process takeover-vs-owner interleavings; durable-release recovery through the real step-1 procedure |
| `pass11.py` | **revision 64**: adversarial attack on the guard itself — planted, corrupted and symlinked guards, assertion placement, guard lifetime, authorized breaks, same-worktree concurrency, the `S14`→`S15` regression |
| `pass12.py` | **revision 65**: the abandoned `"destructive"` guard with no exit, and the `"destructive"` absolute the same-token/superseded-epoch rules broke (`OPUS-R82-001`/`-002`); found this revision's own `H3e` ordering defect |
| `pass13.py` | **revision 66**: guard removal not atomic with its own comparison, with the revision-65 read-then-unlink primitive as a live control arm; the undecidable-claim + `"destructive"`-guard cycle; and claim records whose bytes cannot be read at all (`OPUS-R83-001`/`-002`) |
| `pass14.py` | **revision 67**, re-targeted in revision 68: `CONTINUE_CLAIM` unreachable in the crash window it exists for, so a work item's first checkpoint locked out its own owner, with the revision-66/67 1d ordering carried as a live control arm through the real step-1 caller; plus the scoping, `S14a`/`S14b` non-weakening, diagnosis and absent-`lease_id` arms (`OPUS-R84-001`) |
| `pass15.py` | **revision 68**: the self-owned-claim proof is path-aliasable — a different worktree occupying the holder's recorded path satisfies it, skips `verify_dirty_resume_safety` and mutates under the holder's unrotated token — in both path-reuse constructions, with revision 67's resolver as a live control arm; plus the third `verify_dirty_resume_safety` error class, the undeclared `TypeError` escape, the fencing and durable-release consequences, and `CONTINUE_CLAIM`'s reachability restored at 1d's ordering (`OPUS-R85-001`/`-002`) |

## Result at the time of writing

**462 checks green across the fifteen passes.**

Passes 1–5 were authored against the revision-62 draft; passes 2 and 3 each
found a real defect in it (a stale-claim auto-release that could drop a live
claim; a lost-update race), both fixed then.

Passes 6–9 are the revision-63 authoring passes. They found **seven** further
defects:

| pass | severity | defect | fix |
| --- | --- | --- | --- |
| 6 (A1/A2) | blocking | no adoption: a checkpoint interrupted *before* the mechanism landed — including the real `S-CP3` — stayed unprotected forever, and a legitimate resume never published a claim | `adopt_claim`, plus automatic adoption on the resume branch |
| 6 (A3) | blocking | a claim published before the `IN_PROGRESS` state write permanently locked out the *legitimate owner* | `CONTINUE_CLAIM` |
| 6 (A4) | important | `O_CREAT\|O_EXCL` gives exclusivity but not content atomicity; a torn record failed closed for everyone with no recovery | same-directory temp + `os.link`; explicit takeover as the recovery |
| 6 (A5) | important | the claim path was never required to be a regular file, so a planted symlink was followed out of the claims directory | `lstat`/`O_NOFOLLOW` refusal on both the file and its directory |
| 7 (T1) | important | an authorized takeover unlinked before publishing, so a failure left the work item silently *unclaimed* | atomic `os.rename` replace |
| 7 (T2) | blocking | ownership resolution returned an outcome but not a checkpoint id, so `CONTINUE_CLAIM` with an exhausted registry would write a `None` checkpoint id into the state file | return `(outcome, checkpoint_id)` |
| 7 (T5) | important | a foreign worktree's refusal never named who held the claim or what the escape was | ownership evidence attached to the refusal |

| 11 (G2) | important | **revision 64**: an *undecidable guard* failed closed for every session including the legitimate owner, with no defined escape — a permanent lockout — and one release path would have silently deleted a guard it could not read | explicit, observation-bound `clear_malformed_guard`; no release path removes an undecidable record |
| 15 (Y1/Y2) | blocking | **revision 68**: revision 67's "decidably self-owned claim" proof compares an ownership key made of the worktree's path twice plus the common dir, so a *different* worktree occupying the holder's path satisfies it, skips `verify_dirty_resume_safety`, and mutates authoritative state under the holder's unrotated `owner_token` — two worktrees, one token, no rotation, no audit | the relaxation is withdrawn: `verify_dirty_resume_safety` is unconditional again, and `CONTINUE_CLAIM`'s reachability is restored by establishing the identity record *before* the claim in 1d |
| 15 (Y4/Y7) | important | **revision 68**: `verify_dirty_resume_safety` has a third failure class (`CorruptJsonError`), so skipping it also moved corruption detection after an authoritative state write, where the schema-invalid flavour escapes `write_worktree_identity` as an undeclared `TypeError` | the check stays first; the specified writer validates before mutating and publishes atomically |
| 16 (W1) | blocking | **revision 69**: revision 68's "no checkout can supply `IN_PROGRESS`" premise is false — five commits reachable from `HEAD` carry `workflow-v2-1-core`'s own `WF8b` `IN_PROGRESS`, because a whole-tree checkpoint commit for one work item commits another's in-flight transition — so a worktree that never originated a checkpoint adopts it, publishes a claim, and locks the originator out | adoption additionally requires the checkpoint **not** to be `IN_PROGRESS` in the state committed at `HEAD`; new `CheckpointOriginationUnprovableError`, with the explicit takeover as the escape |
| 16 (W2) | blocking | **revision 69**: `WORKTREE_IDENTITY.json` holds every work item's entry and is written by an unserialized read-modify-write, so a concurrent write for a *different* work item deletes the entry that is now the only proof of origination (12/12 trials) and its owner is locked out at its own next 1c | a per-worktree, stable, never-unlinked `fcntl.flock` **leaf** lock held across the whole load→validate→mutate→publish sequence |
| 16 (W5/W6) | blocking | **revision 69**: the takeover — the plan's own sanctioned repair for a corrupt identity record — refused *on that record* after `_publish_replacing` had already rotated the claim, and every retry rotated again; `recover_abandoned_destructive_guard` had the identical shape | the identity establishment moves **before** the rotation; an undecidable document is repaired only under an authorization component bound to its exact observed bytes |
| 16 (W3) | important | **revision 69**: the third error class was not exhaustive — a non-mapping document escapes the **1c refusal itself** as `AttributeError`, a non-iterable snapshot member as `TypeError`, and a JSON `null` document is silently overwritten by the writer that owes validate-before-mutate | the classification is a closed partition over document shape, enforced in the **validator** and applied to the reader as well as the writer; absence is `exists()`, never a falsy parse |
| 16 (W4) | important | **revision 69**: the plan's claim that atomic publication was "prototyped and asserted in `pass15.py` `Y7`" was false — the specified writer wrote the final pathname with a plain `write_text` *before* its temp-plus-`os.replace`, on every call | the snapshot computation is separated from the write, so the final pathname is written exactly once, by `os.replace` |

Revision 64's own three blocking corrections (`GPT-R81-001` fencing,
`GPT-R81-002` observation-bound takeover authorization, `GPT-R81-003` the
terminal `NO_CHECKPOINT` outcome) are each reproduced in `pass10.py` against a
revision-63 control arm before being fixed.

Passes 8 and 9 found no new Blocking or Important defect, so the revision-63
repair cycle stopped there rather than consuming its remaining budget. Passes 1–5 were re-run
as regressions after every fix; two of their assertions
(`pass2.py` E1, `pass5.py` R3) were amended in place where revision 63
deliberately changes the behaviour they encoded, each with a comment naming the
finding that changed it.

`pass4.py`'s `O1` "state-first" arm and `pass9.py`'s `P2b` "release-before-commit"
arm are **controls**: they deliberately exhibit the failure to show the
acquisition and release orderings are load-bearing rather than stylistic. Both
are reported as passing controls, not as defects.

Passes 16–18 (revisions 69–71) and their own findings are catalogued in
`WORKFLOW_V2_PLAN.md`'s `D-Checkpoint-Ownership` section directly (each pass
file's own docstring states which findings it covers); this table stops at
pass 15 rather than being extended to match, and is not itself normative.
`checkpoint_ownership.py` is a prototype through revision 67's shape with
later passes' corrections layered on as targeted fixes — it has not been
re-authored end-to-end against the fully-approved revision 80 text, and a
real `scripts/` port must be checked against that text directly rather than
assumed complete from this prototype.

**Third-session amendment (2026-08-14).** `workflow-v2-1-core`'s own
Revision 80 plan-approval commit (`8f8d878`) swept `v2-1-dry-run`'s dirty
`S-CP3` delta into committed history as a side effect (the same
"checkout can supply `IN_PROGRESS`" mechanism `pass16.py`'s `W0` documents
for `workflow-v2-1-core`/`WF8b` itself) — see
`../WF8B_S14_FINDING_worktree_b_invisible_to_uncommitted_checkpoint.md`'s
"This verdict no longer holds" addendum for the verified evidence. A
handful of assertions in `pass5.py`, `pass9.py`, `pass16.py` and `pass18.py`
encoded the live-repository fact that only one such pair existed, or that
`S-CP3` was still uncommitted; each is now amended in place, with a dated
comment, to match. All 18 passes are green again after the amendment.
