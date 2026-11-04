# fix-backup-all-workouts

Backups include running and abandoned workouts, and restore warns about a running workout

**Complexity:** medium. **Tier:** 1. Don't lose data, don't hide failures.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `design-1-wrong-claims`.

## App findings

- **Backups leave out running and abandoned workouts, and restoring wipes a running workout without warning** [A-2, O-f4-4; design item 4] · S. Evidence: `ExportBackup.kt:43` reads completed sessions only, and the backup file format already carries status. Export every session, warn in the restore dialog, and add a round-trip test.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
