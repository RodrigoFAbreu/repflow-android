# Milestone 8 reference — Post-MVP functional usability stabilization

## Provenance

Milestones 0-7 (`docs/ROADMAP.md`) are complete and shipped. This milestone
is created fresh from the user's own hands-on usage of the app after
Milestone 7 — it is **not** a resumption of any prior "Milestone 8"; no such
milestone existed anywhere in this repository before this plan (verified via
full `git log --all`, `git branch -a`, `git reflog`, and a repo-wide
`git grep` — none found it). It supersedes the previously-planned
`docs/improvements/IMPROVEMENT_ROADMAP.md` §2.1 (`ReturnCount` detekt
tuning) as the active unit of work; §2.1 is deferred, not discarded (see
Non-goals).

## Goals

Close the specific functional/usability gaps the user found by using the
app, organized by area. Each goal below was verified against the current
code (not assumed) — see "Functional audit" for the evidence and
classification behind every item.

0. **P0 - Crash fix**: `RecoveryFutsalViewModel`, `HistoryViewModel`, and
   `BackupViewModel` are all missing `@HiltViewModel` (confirmed by direct
   comparison against `ActiveWorkoutViewModel`, which has it) — since
   their routes obtain them via `hiltViewModel()`
   (`RecoveryFutsalRoute.kt`), this almost certainly crashes on open for
   3 of 6 top-level destinations today. Found during round-3 plan review,
   verified directly against the code, fixed before any other work.
1. **Recovery/futsal**: past-date entry, a 0-5 scale range, a way to view
   saved recovery/futsal history, and save feedback that can't block
   controls or overlap.
2. **Workout/plans**: starting a workout from an existing plan (while
   keeping ad-hoc workouts working), explicit warm-up/working set
   classification reachable from the UI, planned warm-up/working-set
   structure on plans, RPE/duration/pain/technique-quality exposed on set
   entry where they belong in the MVP, History showing what was actually
   recorded, and precise (not generic) recommendation explanations.
3. **History**: useful filtering/sorting (by exercise, date range, and
   training plan), a safe way to remove an accidentally-recorded workout
   without corrupting progression, and consistent list/detail formatting.
4. **Plans/exercises**: a safe archive/restore lifecycle for training plans
   (mirroring Exercise's existing one) and a verified-correct exercise
   archive snackbar (timeout, no overlap, correct Undo target).
5. **Navigation/UI**: a real Material 3 bottom `NavigationBar` for the six
   top-level destinations (Exercises, Workout, Plans, Recovery, History,
   Backup), replacing the ad-hoc `TextButton`s in the Exercises app bar —
   explicitly approved by the user (see "Navigation redesign" below,
   superseding round-3's more cautious "fix the bug only" framing —
   confirmed directly with the user, not inferred) — plus confirming no
   destination crashes (see P0) and a phone-layout usability pass.
6. **Backup**: preserve the existing working export/import round trip,
   close specific untested edge cases, keep every new field introduced by
   this milestone in the backup schema, and confirm restoring a backup
   taken *before* this milestone still works after it ships.
7. **Verification**: full automated + static verification, a functional
   feature audit document, an external implementation-review gate, and a
   manual functional-review checklist for the user. The milestone is not
   accepted until the user performs that review.

## Non-goals

- `docs/improvements/IMPROVEMENT_ROADMAP.md` §2.1 (`ReturnCount` tuning)
  and the rest of that backlog — deferred until this milestone is
  functionally accepted, per the user's explicit priority call. Not
  discarded; the round-1 plan analysis remains valid and unchanged.
- No new Gradle modules, no dependency additions beyond what a checkpoint
  concretely needs (e.g. `Intents.intending` stubbing for SAF, already a
  standard AndroidX Test API — confirm before adding anything new).
- No cloud/remote sync, accounts, or backend (`docs/TECHNICAL_DECISIONS.md`
  "Future backend", "Offline-first" — unchanged).
- No change to how plan-version substitutions affect progression history —
  `docs/TECHNICAL_DECISIONS.md` lists this as a separate "Open" item; this
  milestone doesn't touch it.
- No UI redesign or visual-identity work beyond the approved navigation
  bar (`docs/TECHNICAL_DECISIONS.md` "Exact UI design system and visual
  identity" — Open, untouched otherwise).
- No exact-progression-formula/threshold changes
  (`docs/TECHNICAL_DECISIONS.md` "Exact progression formulas and
  thresholds" — Open, untouched). The recommendation-reason checkpoint
  only changes which *string* is returned for conditions the policy can
  already detect from its existing inputs; it does not change
  `MIN_SETS_FOR_RECOMMENDATION` or any other threshold, and does not add
  detection for conditions the policy cannot currently see (see
  "Recommendation reason scope" below).

## Navigation redesign (resolves "Navigation structure" — previously Open beyond M1)

Round 3's review argued the user had already approved a full bottom
`NavigationBar` redesign; that specific claim wasn't verifiable from this
session's conversation record at the time, so it was put to the user
directly rather than accepted on the review's word. The user then
explicitly approved it themselves, with concrete requirements:

- A Material 3 `NavigationBar` for the six top-level destinations
  (Exercises, Workout, Plans, Recovery, History, Backup), replacing the
  ad-hoc `TextButton`s in `ExerciseListScreen.kt`'s `TopAppBar.actions`.
- Single source of truth for the top-level destination list.
- Correct selected-state, accessible labels/touch targets, labels that
  don't wrap or overflow.
- No duplicate back-stack entries (`launchSingleTop` + appropriate
  `popUpTo`/state restoration); preserve per-tab state where reasonable.
- Top-level destinations: no Up action. Nested editors/detail screens:
  always show one (unchanged from the original audit finding).
- Android system Back returning to Exercises from any top-level
  destination is acceptable for v1 (the user's own explicit call — no
  per-tab back-stack history is required).
- Verify on a phone-sized device and at an increased font scale; add a
  navigation regression test where practical.

This resolves `docs/TECHNICAL_DECISIONS.md`'s "Navigation structure: Open
(approved for M1 only)" row for this specific scope (a bottom nav bar for
the six existing top-level destinations) — it does not pre-approve any
*other* future navigation-structure change.

## Recommendation reason scope (round-3 finding #6, verified against `ProgressionPolicyV1.kt`)

Round 3 asked for richer reason codes distinguishing several conditions.
Checked directly against `ProgressionPolicyV1.evaluate()`'s actual inputs
(`ProgressionPolicyInput`) to see which are genuinely detectable today:

- **Too few working sets** vs. **no planned rep range** — both already
  detectable (`workingSetReps.size`, `plannedRepRange`); today they're
  incorrectly OR'd into one sentence (`ProgressionPolicyV1.kt:45-49`).
  Splitting them is in scope.
- **Only warm-up sets recorded (no working sets)** — detectable with one
  small addition: the caller (`CompleteWorkoutSession.kt:48`) already
  computes both `exercise.sets` (all) and `workingSets` (warm-up
  filtered out); threading a derived flag (e.g.
  `hadOnlyWarmupSets = exercise.sets.isNotEmpty() && workingSets.isEmpty()`)
  into `ProgressionPolicyInput` makes this a real, detectable third
  reason. In scope.
- **"Insufficient eligible completed history"** (a longitudinal/trend
  concept across multiple past occurrences) — **not** currently
  detectable; `ProgressionPolicyV1` only ever sees one occurrence's sets,
  never a history of prior occurrences. Adding this would be a genuine
  policy-logic expansion, not a reason-string wording fix, and would
  touch `docs/TECHNICAL_DECISIONS.md`'s still-Open "exact progression
  formulas and thresholds" row. **Out of scope** for this milestone,
  consistent with round 3's own caution against inventing undetectable
  reasons.
- Recovery/pain-constraint reasons and the Reduce/Increase/Maintain
  outcome reasons are already specific and data-driven today
  (`ProgressionPolicyV1.kt:52-91`) — no change needed.

## Functional audit (evidence-based, per user-reported item)

Full evidence-cited classification of every reported item lives in
`docs/improvements/FUNCTIONAL_FEATURE_AUDIT.md` (the canonical audit, kept
in one place to avoid drift between it and this doc). Summary: of the ~22
distinct items reported, roughly a third are already implemented and
working (ad-hoc workouts, plan-linked working-set filtering, no-crash
navigation, most backup edge cases), a third are built below the UI but
never wired up (start-from-plan, warm-up toggle, RPE/duration entry,
recovery/futsal history view), and the rest are genuinely absent (0-5
scale, past-date entry, plan archive/restore, history filtering, pain/
technique-quality fields, completed-workout removal) or partially done
(save-feedback guard, history detail completeness, snackbar timeout,
Up-arrow consistency, two backup edge cases). Each item maps to a
checkpoint below.

## Decisions — approved v1 assumptions

Several requested items land directly on rows `docs/TECHNICAL_DECISIONS.md`
marks **Open**, which agents "must not silently finalize... during
unrelated tasks." This milestone's entire purpose is to build some of this,
so — mirroring how Milestones 6 and 7 handled the same situation for their
own Open rows — each got an explicit, clearly-labeled v1 assumption rather
than a silent decision. As of round 3's review, items 1-5 below are
**approved** (the reviewer explicitly signed off on all five as stated);
the navigation-structure decision (bottom nav) is separately and directly
**approved by the user** (see "Navigation redesign" above), not inferred
from review feedback.

### 1. "Technique quality" representation (new field, `docs/PROJECT_BRIEF.md`'s deferred item, `docs/milestones/completed/milestone-6-reference.md:19-24`)

**v1 assumption:** nullable `Int?` scale, same `0..5` range as the
(also-updated, CP3) recovery scales, for UI/domain consistency. Optional —
`docs/UX_FLOWS.md:68` already says "record *optional* technique quality" —
so `null` means "not recorded," not zero.

### 2. Per-set pain (new field, distinct from `RecoveryEntry.painWhileWalking`)

**v1 assumption:** nullable `Int?`, same `0..5` scale, same optionality, on
`WorkoutSet` — a separate, in-the-moment-during-a-set concept from
Recovery's session-level, retrospective `painWhileWalking`. Flagging this
explicitly since the two fields will have the same *name* pattern but are
not the same thing; the UI copy must make this distinction clear to avoid
user confusion (e.g. "Pain during this set" vs. Recovery's "Pain while
walking today").

### 3. Planned warm-up/working-set structure (`docs/TECHNICAL_DECISIONS.md`'s "Final Room entity schema" — Open)

**v1 assumption:** add a new nullable `targetWarmupSets: Int?` to
`PlannedExercise`, alongside the existing `targetSets` (already
documented as the working-set target, `TargetSets.kt:5-21` — no rename
needed, just a new sibling field). `null` means "no warm-up guidance for
this exercise," which is backward-compatible with every existing plan
version. No change to `TargetSets`'s existing validation.

### 4. Completed-workout removal ("Whether completed workouts may be manually corrected" — Open, explicitly named in `docs/TECHNICAL_DECISIONS.md`)

**v1 assumption:** add `invalidatedAt: Instant?` to `WorkoutSession`
(mirroring `Exercise.archivedAt`'s established pattern in this codebase),
not a hard delete. An invalidated session is excluded from
`observeCompletedSessions()` and therefore from progression — exactly the
same mechanism already used for `ABANDONED` sessions
(`WorkoutSessionDao.kt:27`) — but the row and its sets are never
physically deleted, keeping this consistent with the "historical
immutability" and "no destructive operations" rules in `CLAUDE.md`, and
fully reversible (un-invalidate is possible later if ever needed). This is
a *correction* mechanism (mark as invalid), not "manually editing" a
session's recorded values — the broader "manually corrected" question
stays Open for anything beyond this specific removal use case.

### 5. History filter/sort dimensions

**Approved (round 3):** filter by exercise, date range, and training plan
(the plan dimension becomes meaningful once CP-start-from-plan links
sessions to a plan version); sort by date, ascending/descending,
newest-first by default. Not itself a `docs/TECHNICAL_DECISIONS.md` row,
but worth stating precisely since the user's original wording
("useful filtering and sorting") didn't specify dimensions and a wrong
guess here means throwaway UI work. If CP11's invalidation model lands
first, add a status/invalidated filter too (show/hide invalidated
sessions) since it falls out naturally once that field exists — not a
new decision, just using a field this milestone already adds.

## Domain invariants added or changed

- Every new field above is nullable/optional and additive — no existing
  data becomes invalid, no existing behavior changes for records that
  predate this milestone (matches the "widen, don't narrow" migration
  safety pattern already used throughout Milestones 1-7).
- Recovery scale widening (0-4 → 0-5) only relaxes an upper bound; no
  existing stored value becomes invalid (all prior values were ≤ 4, still
  ≤ 5).
- `WorkoutSession.invalidatedAt` follows exactly the same
  "excluded-from-completed-query, never deleted" shape as `ABANDONED`
  status — no new invariant category, just a second reason a session can
  be excluded from history/progression.
- `TrainingPlan.archivedAt`/`isArchived` mirrors `Exercise`'s existing
  invariant (`archivedAt` present and before/equal to `createdAt` is
  invalid) — same validation shape, applied to a new entity.

## Migration plan

**Revised in round 3**: originally planned as four separate version bumps
(one per checkpoint); consolidated into **one** additive migration, since
none of these versions have ever shipped/exist in a released build — this
is all still-unreleased, unversioned-to-the-outside-world schema work for
one stabilization milestone, so there's no compatibility reason to chain
four intermediate versions. One `MIGRATION_6_7` (current version: 6):

| Version | Table | Change |
|---|---|---|
| 7 | `workout_sets` | + `pain INTEGER` (nullable), + `technique_quality INTEGER` (nullable) |
| 7 | `planned_exercises` | + `target_warmup_sets INTEGER` (nullable) |
| 7 | `workout_sessions` | + `invalidated_at INTEGER` (nullable epoch millis) |
| 7 | `training_plans` | + `archived_at INTEGER` (nullable epoch millis) |

This lands as its own early checkpoint (schema-only, no UI yet), with a
migration test verifying: every pre-migration row across all four tables
survives with all five new columns `NULL`, and each new column round-trips
a written value post-migration (matching the existing migration-test
pattern in `RepFlowMigrations.kt`'s test suite). Every later checkpoint
that needs one of these columns (pain/technique UI, planned warm-up UI,
invalidation UI, plan archive UI) builds on this already-landed schema
instead of adding its own migration.

## Backup schema impact

`BackupJsonMapper` gains the five new nullable fields (pain, technique
quality, targetWarmupSets, invalidatedAt, archivedAt) in the same
checkpoint as the schema migration, so backup coverage never lags behind
what Room can already store. `BackupSnapshot.CURRENT_SCHEMA_VERSION`
bumps from 1 to 2 alongside it.

**Backward compatibility (round-3 finding #10, verified against the
current parser):** `BackupSnapshot.create` today only rejects
`schemaVersion > CURRENT_SCHEMA_VERSION` (future) or `< 1` (invalid) —
`schemaVersion <= CURRENT_SCHEMA_VERSION` already passes, so a v1 backup
stays acceptable once `CURRENT_SCHEMA_VERSION` becomes 2, *provided* the
five new field reads in `BackupJsonMapper`'s `*FromJson` functions use
null-safe/optional JSON access (e.g. an `optIntOrNull`-style helper)
instead of `getInt`, so a v1 backup's JSON — which simply won't have
those keys — parses cleanly with `null` for all five new fields rather
than throwing. This needs no explicit version-branching logic, just
optional reads for the new fields. Required test additions: restore from
a real v1-shaped fixture (asserting the five new fields come back `null`
and everything else round-trips), and restore from a fabricated
future-version (e.g. 3) fixture (asserting rejection without touching
current data) — the fixture that already exists in
`BackupSnapshotTest."rejects a schema version newer than this app
understands"` validates this at the `BackupSnapshot.create` layer only;
this milestone adds the equivalent through the real end-to-end
`LocalBackupRepository`/`RestoreBackup` path (closing the same gap CP14
already found in the original audit, item "Unsupported schema version").
