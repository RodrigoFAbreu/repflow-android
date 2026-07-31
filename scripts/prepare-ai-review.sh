#!/usr/bin/env bash
# Build a review bundle under .ai-review/<work-item-id>/current/ (or the
# flat .ai-review/current/ compatibility path if no work-item-id is given)
# and archive it. See docs/ai-workflow/REVIEW_PROTOCOL.md.
#
# Usage: scripts/prepare-ai-review.sh <base-sha> <stage> [work-item-id]
#   stage: plan | implementation | post-fix | functional-review
#   work-item-id: optional. Omitted -> the flat .ai-review/current/
#     compatibility layout. Given -> .ai-review/<work-item-id>/current/
#     (WF5's relayout, D-Bundle-Manifest).
set -euo pipefail

usage() {
  echo "Usage: $0 <base-sha> <stage> [work-item-id]" >&2
  echo "  stage: plan | implementation | post-fix | functional-review" >&2
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
if [[ -n "$WORK_ITEM_ID" ]]; then
  ROOT_DIR=".ai-review/$WORK_ITEM_ID"
else
  ROOT_DIR=".ai-review"
fi
BUNDLE_DIR="$ROOT_DIR/current"
FILES_DIR="$BUNDLE_DIR/files"
mkdir -p "$FILES_DIR"

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

# --- archive ---
ARCHIVE="$ROOT_DIR/review-bundle.tar.gz"
tar -czf "$ARCHIVE" -C "$ROOT_DIR" current

echo "Bundle ready:"
echo "  stage:        $STAGE"
echo "  work_item_id: ${WORK_ITEM_ID:-(none -- flat compatibility layout)}"
echo "  branch:       $BRANCH"
echo "  base:         $BASE_SHA"
echo "  head:         $HEAD_SHA"
echo "  bundle:       $BUNDLE_DIR"
echo "  archive:      $ARCHIVE"
