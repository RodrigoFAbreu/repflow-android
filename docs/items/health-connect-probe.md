# health-connect-probe

Test on the owner's phone what Samsung Health really shares through Health Connect

**Complexity:** low. Owner's request of 2026-11-05.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished.

## What to find out

Which of sleep, heart rate, steps and exercise reach Health Connect from the owner's Samsung Health and Galaxy Watch 6, with what detail and delay. One third-party source says stress and VO2 max don't; confirm it.

## How

A throwaway test app with its own package name, so RepFlow and its data aren't touched. It only reads. Needs the owner to turn on the sync and to agree a time to use the phone. Write the result into `health-sleep-read` before that item starts.
