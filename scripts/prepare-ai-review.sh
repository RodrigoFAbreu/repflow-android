#!/usr/bin/env bash
# state_writer: false
# This script reads docs/ai-workflow/WORKFLOW_STATE.json (to cross-check
# that record_bundle_generation already advanced reviewed_implementation_head
# before regenerating a bundle) but never writes it -- the declaration
# above is about publication, not about reference.
# Build a review bundle under .ai-review/<work-item-id>/current/ (or the
# flat .ai-review/current/ compatibility path for a non-plan stage with no
# work-item-id given) and archive it. See docs/ai-workflow/REVIEW_PROTOCOL.md.
#
# Usage: scripts/prepare-ai-review.sh <base-sha> <stage> [work-item-id]
#   stage: plan | implementation | post-fix | functional-review
#   work-item-id: REQUIRED for stage "plan" (D-Fingerprint-Generalization,
#     OPUS-R27-003) -- never resolved from the live active_work_item_id for
#     this stage, since MANIFEST.md's identity binding depends on it.
#     Optional for every other stage: omitted -> the flat .ai-review/current/
#     compatibility layout; given -> .ai-review/<work-item-id>/current/
#     (WF5's relayout, D-Bundle-Manifest).
set -euo pipefail

usage() {
  echo "Usage: $0 <base-sha> <stage> [work-item-id]" >&2
  echo "  stage: plan | implementation | post-fix | functional-review" >&2
  echo "  work-item-id: required for stage 'plan'; optional otherwise" >&2
  exit 1
}

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
fi

REQUESTED_BASE_SHA=$1
BASE_SHA=$1
STAGE=$2
WORK_ITEM_ID=${3:-}

case "$STAGE" in
  plan | implementation | post-fix | functional-review) ;;
  *)
    echo "error: unknown stage '$STAGE'" >&2
    usage
    ;;
esac

if [[ "$STAGE" == "plan" && -z "$WORK_ITEM_ID" ]]; then
  echo "error: work-item-id is required for stage 'plan' (D-Fingerprint-Generalization) -- never resolved from the live active_work_item_id" >&2
  usage
fi

if [[ -n "$WORK_ITEM_ID" ]] && ! [[ "$WORK_ITEM_ID" =~ ^[a-z0-9][a-z0-9_-]{0,63}$ ]]; then
  echo "error: work-item-id '$WORK_ITEM_ID' does not match ^[a-z0-9][a-z0-9_-]{0,63}\$" >&2
  exit 1
fi

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "error: not inside a git repository" >&2
  exit 1
}
cd "$REPO_ROOT"

if ! BASE_SHA=$(git rev-parse --verify "${BASE_SHA}^{commit}" 2>/dev/null); then
  echo "error: base-sha '$REQUESTED_BASE_SHA' does not resolve to a commit" >&2
  exit 1
fi

# --- plan-stage-only: cross-check the resolved work item's own declared
# base_commit against the resolved BASE_SHA, and its WORKFLOW_STATE.json
# plan_revision mirror against its own registry's plan_revision, before
# generating any bundle content (fail-closed matrix condition 13,
# OPUS-R27-003/OPUS-R28-006; the plan-revision mirror check is
# D-Plan-Revision-Publication/WFR-65's detection half). Routed through
# fingerprint.resolve_plan_stage_metadata -- the same resolver every other
# plan-stage read in this design uses -- never a second, ad hoc metadata
# reader.
if [[ "$STAGE" == "plan" ]]; then
  PLAN_STAGE_BASE_CHECK=$(
    PYTHONPATH="$REPO_ROOT/scripts:${PYTHONPATH:-}" python3 - "$WORK_ITEM_ID" "$BASE_SHA" "$REPO_ROOT" <<'PYEOF'
import json
import sys
from pathlib import Path
import workflow_fingerprint as fingerprint

work_item_id, base_sha, repo_root = sys.argv[1], sys.argv[2], Path(sys.argv[3])
try:
    metadata = fingerprint.resolve_plan_stage_metadata(repo_root, work_item_id)
except Exception as exc:  # noqa: BLE001 -- surfaced verbatim to the operator below
    print(f"error::{type(exc).__name__}: {exc}")
    sys.exit(0)
if metadata.base_commit != base_sha:
    print(f"mismatch_base::{metadata.base_commit}")
    sys.exit(0)
state_path = repo_root / "docs" / "ai-workflow" / "WORKFLOW_STATE.json"
state = json.loads(state_path.read_text())
mirror_plan_revision = state.get("work_items", {}).get(work_item_id, {}).get("plan_revision")
if mirror_plan_revision != metadata.plan_revision:
    print(f"mismatch_revision::{mirror_plan_revision}::{metadata.plan_revision}")
    sys.exit(0)
print("ok")
PYEOF
  )
  case "$PLAN_STAGE_BASE_CHECK" in
    ok) ;;
    mismatch_base::*)
      DECLARED_BASE_COMMIT=${PLAN_STAGE_BASE_CHECK#mismatch_base::}
      echo "error: work item '$WORK_ITEM_ID' declares base_commit '$DECLARED_BASE_COMMIT', but the requested base '$REQUESTED_BASE_SHA' resolves to '$BASE_SHA' -- refusing to generate bundle content for a disagreeing base (D-Fingerprint-Generalization, fail-closed matrix condition 13)" >&2
      exit 1
      ;;
    mismatch_revision::*)
      REVISION_DETAIL=${PLAN_STAGE_BASE_CHECK#mismatch_revision::}
      MIRROR_PLAN_REVISION=${REVISION_DETAIL%%::*}
      REGISTRY_PLAN_REVISION=${REVISION_DETAIL#*::}
      echo "error: work item '$WORK_ITEM_ID' WORKFLOW_STATE.json plan_revision mirror is '$MIRROR_PLAN_REVISION', but its registry declares plan_revision '$REGISTRY_PLAN_REVISION' -- refusing to generate bundle content for a disagreeing plan-revision mirror (D-Plan-Revision-Publication, WFR-65)" >&2
      exit 1
      ;;
    error::*)
      echo "error: could not resolve work item '$WORK_ITEM_ID's plan-stage metadata: ${PLAN_STAGE_BASE_CHECK#error::}" >&2
      exit 1
      ;;
    *)
      echo "error: unexpected plan-stage base-commit/plan-revision check output: $PLAN_STAGE_BASE_CHECK" >&2
      exit 1
      ;;
  esac
fi

HEAD_SHA=$(git rev-parse HEAD)
BRANCH=$(git branch --show-current)
BRANCH=${BRANCH:-"(detached)"}

# Capture real working-tree status before any intent-to-add staging below,
# so the bundle reflects what the user's git state actually looks like.
WORKTREE_STATUS=$(git status --short)

# `git diff <base>` only reports tracked content. Temporarily mark new,
# untracked (but not gitignored) files as intent-to-add so they show up as
# additions in the diff/patch/name-status output below, then restore their
# untracked status on exit so this script has no lasting effect on git state.
mapfile -d '' -t UNTRACKED_FILES < <(git ls-files --others --exclude-standard -z -- .)
if ((${#UNTRACKED_FILES[@]} > 0)); then
  git add -N -- "${UNTRACKED_FILES[@]}"
fi
cleanup() {
  if ((${#UNTRACKED_FILES[@]} > 0)); then
    git reset -- "${UNTRACKED_FILES[@]}" > /dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# --- .ai-review/<work_item_id>/ relayout, with a stated compatibility
# fallback (D-Bundle-Manifest, resolves OPUS-R6-021): a work-item-id
# argument opts into the new per-work-item layout; omitting it keeps
# writing the flat legacy layout, for any caller not yet passing one.
# For stage "plan", WORK_ITEM_ID is always non-empty here (enforced above),
# so ROOT_DIR always resolves scoped -- there is no "else" left to disagree
# with --write-manifest's own resolution below (OPUS-R26-002/OPUS-R27-003).
if [[ -n "$WORK_ITEM_ID" ]]; then
  ROOT_DIR=".ai-review/$WORK_ITEM_ID"
else
  ROOT_DIR=".ai-review"
fi
BUNDLE_DIR="$ROOT_DIR/current"
FILES_DIR="$BUNDLE_DIR/files"
mkdir -p "$FILES_DIR"

# --- round-identity preflight (GPT-R42-001, GPT-R43-001, GPT-R43-003): run
# before any other write below, never after (GPT-R43-003 -- `current/` is
# "the bundle currently under review" per REVIEW_PROTOCOL.md, not a
# scratch directory a rejected generation may leave mutated; `mkdir -p`
# above is the sole exception, a harmless no-op when the directory already
# exists). Proves two things about WORKFLOW_STATE.json's own
# work_items[work_item_id], both required by record_bundle_generation's
# contract (workflow_state.py, D-Approval-Commits' sole writer of both
# fields) before a bundle may be finalized for this head:
#
# 1. reviewed_implementation_head equals $HEAD_SHA, the exact commit this
#    generation run is for (GPT-R42-001) -- record_bundle_generation must
#    already have been called and persisted to the working tree
#    (committed or not) BEFORE this script runs, never after;
# 2. implementation_revision advanced exactly the way a fresh round
#    requires relative to the PREVIOUS bundle already at
#    $BUNDLE_DIR/MANIFEST.md, read here before anything below can
#    overwrite it (GPT-R43-001): regenerating for the *same* head the
#    previous manifest already named must leave the revision unchanged
#    (idempotent re-generation, no bump expected); regenerating for a
#    *new* head must advance the revision by exactly one. A first-ever
#    generation (no prior implementation-stage manifest to compare
#    against) skips this half, same skip condition as below.
#
# Skipped entirely for a work item with no WORKFLOW_STATE.json entry, same
# skip condition as the "record_bundle_generation" step this bundle
# generation precedes (docs/ai-workflow/REVIEW_PROTOCOL.md), and for the
# plan stage / a flat-compatibility (no work-item-id) invocation, which
# this guard does not govern.
if [[ ( "$STAGE" == "implementation" || "$STAGE" == "post-fix" ) && -n "$WORK_ITEM_ID" ]]; then
  PREV_MANIFEST="$BUNDLE_DIR/MANIFEST.md"
  PREV_HEAD=""
  PREV_REVISION=""
  if [[ -f "$PREV_MANIFEST" ]]; then
    PREV_HEAD=$(sed -n 's/^reviewed_implementation_head: //p' "$PREV_MANIFEST")
    PREV_REVISION=$(sed -n 's/^implementation_revision: //p' "$PREV_MANIFEST")
  fi

  GUARD_CHECK=$(
    python3 - "$REPO_ROOT" "$WORK_ITEM_ID" "$HEAD_SHA" "$PREV_HEAD" "$PREV_REVISION" "$BASE_SHA" <<'PYEOF'
import json
import sys
from pathlib import Path

repo_root, work_item_id, head_sha, prev_head, prev_revision, base_sha = sys.argv[1:7]
state_path = Path(repo_root) / "docs/ai-workflow/WORKFLOW_STATE.json"
state = json.loads(state_path.read_text())
work_item = state.get("work_items", {}).get(work_item_id)
if work_item is None:
    print("status: skip")
    sys.exit(0)

recorded_head = work_item.get("reviewed_implementation_head")
if recorded_head != head_sha:
    # Not bare equality -- WF8B-003's remediation (D-Commit-Provenance)
    # allows reviewed_implementation_head to lag head_sha by a bounded,
    # validated provenance interval (the dedicated Workflow-Bundle-
    # Generation-Record commit this generation's own durability write
    # creates, plus any excluded-only commits before it). Reuse the same
    # check /approve-review implementation's own gate uses, never a
    # second, independent notion of "close enough".
    sys.path.insert(0, str(Path(repo_root) / "scripts"))
    import workflow_state as ws
    interval_ok = False
    interval_reason = None
    if recorded_head:
        try:
            interval_ok = ws.implementation_provenance_interval_reachable(
                Path(repo_root), work_item, base_sha, head_sha,
            )
        except Exception:
            interval_ok = False
        if not interval_ok:
            try:
                ws.verify_implementation_provenance_interval(
                    Path(repo_root), work_item, base_sha, head_sha,
                )
            except Exception as exc:
                interval_reason = str(exc)
    if not interval_ok:
        print("status: mismatch")
        print(
            f"reason: reviewed_implementation_head {recorded_head!r} does not "
            f"match this generation's head {head_sha!r} (GPT-R42-001), and no "
            f"valid provenance interval reaches it either"
            + (f": {interval_reason}" if interval_reason else "")
        )
        sys.exit(0)

live_revision = work_item.get("implementation_revision")
if prev_head and prev_revision:
    prev_revision_int = int(prev_revision)
    if prev_head == head_sha:
        if live_revision != prev_revision_int:
            print("status: mismatch")
            print(
                f"reason: regenerating for the same head {head_sha!r} the "
                f"previous bundle already named must leave "
                f"implementation_revision unchanged ({prev_revision_int}), "
                f"got {live_revision} (GPT-R43-001)"
            )
            sys.exit(0)
    elif live_revision != prev_revision_int + 1:
        print("status: mismatch")
        print(
            f"reason: new head {head_sha!r} (previous bundle was "
            f"{prev_head!r}) requires implementation_revision to advance "
            f"by exactly one, from {prev_revision_int} to "
            f"{prev_revision_int + 1}, got {live_revision} (GPT-R43-001)"
        )
        sys.exit(0)
print("status: ok")
PYEOF
  )
  GUARD_STATUS=$(printf '%s\n' "$GUARD_CHECK" | sed -n 's/^status: //p')
  if [[ "$GUARD_STATUS" != "ok" && "$GUARD_STATUS" != "skip" ]]; then
    echo "error: WORKFLOW_STATE.json work_items['$WORK_ITEM_ID'] round identity does not agree with this generation (GPT-R42-001/GPT-R43-001) -- call workflow_state.record_bundle_generation(..., stage=..., head='$HEAD_SHA') and persist the result to the working tree BEFORE regenerating this bundle. No file under $BUNDLE_DIR was written by this run:" >&2
    printf '%s\n' "$GUARD_CHECK" >&2
    exit 1
  fi
fi

# --- author-written files: create empty stubs only if missing, never overwrite ---
for f in REVIEW_REQUEST.md PLAN.md IMPLEMENTATION_SUMMARY.md TEST_RESULTS.md CONTEXT_FILES.txt; do
  path="$BUNDLE_DIR/$f"
  if [[ ! -f "$path" ]]; then
    : > "$path"
  fi
done

# --- generated: CHANGED_FILES.txt (metadata + stat + name-status + worktree status) ---
CHANGED_FILES="$BUNDLE_DIR/CHANGED_FILES.txt"
{
  echo "branch: $BRANCH"
  echo "base: $BASE_SHA"
  echo "head: $HEAD_SHA"
  echo "stage: $STAGE"
  echo "work_item_id: ${WORK_ITEM_ID:-(none -- flat compatibility layout)}"
  echo "generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "## Diff stat (base -> working tree)"
  git diff --stat "$BASE_SHA" -- .
  echo
  echo "## Changed files (name-status, base -> working tree)"
  git diff --name-status "$BASE_SHA" -- .
  echo
  echo "## Working tree status (git status --short)"
  printf '%s\n' "$WORKTREE_STATUS"
} > "$CHANGED_FILES"

# --- generated: COMMITS.txt ---
COMMITS_FILE="$BUNDLE_DIR/COMMITS.txt"
if [[ "$BASE_SHA" == "$HEAD_SHA" ]]; then
  echo "(no commits yet since base $BASE_SHA)" > "$COMMITS_FILE"
else
  git log --oneline --decorate "${BASE_SHA}..HEAD" > "$COMMITS_FILE"
fi

# --- generated: DIFF.patch ---
DIFF_FILE="$BUNDLE_DIR/DIFF.patch"
git diff "$BASE_SHA" -- . > "$DIFF_FILE"

# --- generated: files/ (final copies of changed files, excluding deletions) ---
rm -rf "$FILES_DIR"
mkdir -p "$FILES_DIR"

# NUL-delimited parsing throughout (resolves OPUS-R6-019): the previous
# `IFS=$'\t' read` loop over `git diff --name-status` (no `-z`) broke on a
# path containing a newline, and mis-split under `core.quotePath` on a
# non-ASCII path. `-z` terminates every record (and, for renames, every
# field within a record) with NUL instead, which is unambiguous regardless
# of what bytes the path itself contains.
while IFS= read -r -d '' status && IFS= read -r -d '' path; do
  [[ -z "$path" ]] && continue
  if [[ "$status" == R* ]]; then
    # Renames report as "R100\0old\0new\0" under -z: consume the second
    # (new) path as an extra NUL-delimited field.
    IFS= read -r -d '' new_path
    path=$new_path
  fi
  [[ "$status" == D* ]] && continue
  # Source-proposal exclusion (WFR-16): .ai-review/source/* is never part
  # of a normal review bundle, even if a diff somehow reported one -- it
  # is gitignored and this branch is defense in depth, not the primary
  # enforcement (CONTEXT_FILES.txt below is the one an author actually
  # populates).
  case "$path" in
    .ai-review/source/*) continue ;;
  esac
  if [[ -f "$path" ]]; then
    mkdir -p "$FILES_DIR/$(dirname -- "$path")"
    cp -- "$path" "$FILES_DIR/$path"
  fi
done < <(git diff --name-status -z "$BASE_SHA" -- .)

# --- copy explicitly listed context files, if any ---
CONTEXT_FILES_LIST="$BUNDLE_DIR/CONTEXT_FILES.txt"
if [[ -s "$CONTEXT_FILES_LIST" ]]; then
  while IFS= read -r ctx_path; do
    [[ -z "$ctx_path" ]] && continue
    case "$ctx_path" in
      .ai-review/source/*)
        echo "warning: skipping source-proposal path in CONTEXT_FILES.txt (WFR-16): $ctx_path" >&2
        continue
        ;;
    esac
    if [[ -f "$ctx_path" ]]; then
      mkdir -p "$FILES_DIR/$(dirname -- "$ctx_path")"
      cp -- "$ctx_path" "$FILES_DIR/$ctx_path"
    else
      echo "warning: context file listed but not found: $ctx_path" >&2
    fi
  done < "$CONTEXT_FILES_LIST"
fi

# --- write MANIFEST.md as this script's own final content step, before
# archiving, so the archived bundle actually contains it
# (D-Fingerprint-Generalization, OPUS-R25-012, resolves the affected-
# commands audit gap: MANIFEST.md was previously only ever produced by a
# separate, manual CLI invocation nothing forced to carry a matching
# --work-item-id). Same CLI entry point as every other invocation, not a
# reimplementation; REVIEW_REQUEST.md must already state the same
# review_content_id (OPUS-R18-005, unchanged precondition) -- this call
# does not relax it.
#
# Implementation/post-fix stages get their own implementation-stage
# manifest here too (GPT-R30-001/002): a scoped bundle (WORK_ITEM_ID
# given) previously carried no manifest of its own at these stages,
# silently reusing whatever MANIFEST.md the plan stage had already
# written to the same directory -- a stale plan-stage identity with a
# pre-implementation generation_head, bound to the plan-stage
# review_content_id rather than a digest over the reviewed implementation
# diff. --stage implementation anchors review_content_id at $HEAD_SHA
# (the final reviewed implementation HEAD, recorded as
# reviewed_implementation_head), computed fresh every run, exactly like
# the plan stage's own manifest.
if [[ "$STAGE" == "plan" ]]; then
  python3 "$REPO_ROOT/scripts/workflow_fingerprint.py" "$BASE_SHA" \
    --work-item-id "$WORK_ITEM_ID" --write-manifest
elif [[ ( "$STAGE" == "implementation" || "$STAGE" == "post-fix" ) && -n "$WORK_ITEM_ID" ]]; then
  python3 "$REPO_ROOT/scripts/workflow_fingerprint.py" "$BASE_SHA" \
    --work-item-id "$WORK_ITEM_ID" --stage implementation --write-manifest
fi

# --- archive ---
# Item 318 (WF8c scope clause (j), OPUS-R102-011): write to a temp file
# first, then rename onto the real archive path -- an interrupted or
# failed tar invocation must never leave the previously valid archive
# corrupted or truncated in place.
ARCHIVE="$ROOT_DIR/review-bundle.tar.gz"
ARCHIVE_TMP="$ARCHIVE.tmp"
tar -czf "$ARCHIVE_TMP" -C "$ROOT_DIR" current
mv -f "$ARCHIVE_TMP" "$ARCHIVE"

# --- reproducibility check (D-Fingerprint-Generalization, GPT-R30-001/003):
# when a manifest was written above, require bundle_id equality across
# three independent computations -- the value MANIFEST.md itself declares,
# a fresh recomputation directly over $BUNDLE_DIR, and a fresh
# recomputation over the archive's own extracted content -- so a stale or
# non-reproducible archive fails closed here instead of being discovered
# only by an external reviewer's own independent recomputation.
if [[ -f "$BUNDLE_DIR/MANIFEST.md" ]]; then
  REPRO_CHECK=$(
    PYTHONPATH="$REPO_ROOT/scripts:${PYTHONPATH:-}" python3 - "$BUNDLE_DIR" "$ARCHIVE" <<'PYEOF'
import sys
import tarfile
import tempfile
from pathlib import Path

import workflow_fingerprint as fingerprint

bundle_dir, archive = Path(sys.argv[1]), Path(sys.argv[2])

recorded = fingerprint.read_manifest_identifiers(bundle_dir / "MANIFEST.md")
recorded_bundle_id = recorded.get("bundle_id")
if recorded_bundle_id is None:
    print("status: error")
    print("message: MANIFEST.md has no recorded bundle_id")
    sys.exit(0)

ondisk_bundle_id, _ = fingerprint.compute_bundle_id(bundle_dir)

with tempfile.TemporaryDirectory() as tmp:
    with tarfile.open(archive) as tf:
        tf.extractall(tmp)
    extracted_bundle_id, _ = fingerprint.compute_bundle_id(Path(tmp) / "current")

if recorded_bundle_id == ondisk_bundle_id == extracted_bundle_id:
    print("status: ok")
    print(f"bundle_id: {recorded_bundle_id}")
else:
    print("status: mismatch")
    print(f"manifest_bundle_id: {recorded_bundle_id}")
    print(f"ondisk_bundle_id: {ondisk_bundle_id}")
    print(f"extracted_bundle_id: {extracted_bundle_id}")
PYEOF
  )
  REPRO_STATUS=$(printf '%s\n' "$REPRO_CHECK" | sed -n 's/^status: //p')
  if [[ "$REPRO_STATUS" != "ok" ]]; then
    echo "error: bundle archive is not reproducible -- on-disk, archived, and extracted identifiers must all agree:" >&2
    printf '%s\n' "$REPRO_CHECK" >&2
    exit 1
  fi
fi

echo "Bundle ready:"
echo "  stage:        $STAGE"
echo "  work_item_id: ${WORK_ITEM_ID:-(none -- flat compatibility layout)}"
echo "  branch:       $BRANCH"
echo "  base:         $BASE_SHA"
echo "  head:         $HEAD_SHA"
echo "  bundle:       $BUNDLE_DIR"
echo "  archive:      $ARCHIVE"
