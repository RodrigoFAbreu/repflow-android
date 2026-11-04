# chore-archive

Retire what the council marked outdated: old audits, finished roadmap items, D163-D165, old notes, dead code, the stale brief promise; retire the rest-after-single-set note

**Complexity:** low. **Tier:** Setup: small chores, one PR each, no milestone.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished; items that depend on it wait until then.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**15. Rest after a single-set exercise** [A-15, O-f4-2]
- *Today:* a rest follows every set, as drawn. All 51 rows set their own rest, so the 90 s default never fires for them.
- *Options:* (a) keep; (b) none after the last planned set.
- *Recommendation:* **(a),** and retire O-f4-2 unless you mind. Both agree.

## Could be archived

| What | Why | Id |
|---|---|---|
| `RepFlow Roadmap.dc.html` | Lists units, supersets, muscle groups and days as built; contradicted by the app and later boards | D-36 |
| Archive canvas boards 1a, 2a to 2c, 3a to 3d, 4a, 5a, 5b, 6a, 6b, 6c, 8b, 8e, 9a to 9f | Fully replaced. Keep the file with a banner. 5c and 5d (only through 8c), 7a, 7b, 7c, 8a, 8c, 8d and the Future list stay live until moved; 1b and 1c stay as Future | D-37 |
| `DESIGN_FOLLOWUP.md` and its designer copy | Finished history. Remaining items now live here. Retire after this council | D-38 |
| `docs/improvements/FUNCTIONAL_FEATURE_AUDIT.md` | Describes the app before the redesign | R-1 |
| `docs/improvements/POST_MVP_ENGINEERING_REVIEW.md` | Snapshot at database version 6; its UI-test gap is closed | R-2 |
| Roadmap items 2.1, 2.2, 3.1, 3.2, 4.1, 6.1, 7.1, 8.1, 8.2, 9.8 | Done or no benefit to someone logging a workout. Keep 5.1, 8.3, 9.7 and whatever decision 14 keeps | R-3 |
| Follow-ups D163, D164, D165 | Built and verified in code; leftovers are app items 25, 32, 33 | R-4 |
| Postponed notes O3, O4, r9 items; "strips overlay content" | Harmless or bundle-only. The strip doesn't overlay; it shrinks the scroll area, covered by app item 10 | R-5 |
| Dead code: unused workout start path; `RepFlowShapes.fab` and "18 FAB" in `DESIGN_SYSTEM.md` | No screen uses them | A-37, D-39 |
| `BUILT_IN` promise in `PROJECT_BRIEF.md` | The library starts empty by decision (50a). Reword the brief, and keep the enum only if stored rows use it, which nobody checked | A-38 |

**Withdrawn:** the request to build the after-restore Backup hero [A-33], replaced by design item 8. **Merged:** D-12 into decision 4; D-23 and D-13 into decision 1 and app item 8; D-24 and D-26 into app items 9, 10 and 26; D-30 into app items 13, 14, 15 and 21.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
