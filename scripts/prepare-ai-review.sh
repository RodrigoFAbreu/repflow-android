#!/usr/bin/env bash
# Build a review bundle under .ai-review/current/ and archive it to
# .ai-review/review-bundle.tar.gz. See docs/ai-workflow/REVIEW_PROTOCOL.md.
#
# Usage: scripts/prepare-ai-review.sh <base-sha> <stage>
#   stage: plan | implementation | post-fix | functional-review
set -euo pipefail

usage() {
  echo "Usage: $0 <base-sha> <stage>" >&2
  echo "  stage: plan | implementation | post-fix | functional-review" >&2
  exit 1
}

if [[ $# -ne 2 ]]; then
  usage
fi

BASE_SHA=$1
STAGE=$2

case "$STAGE" in
  plan | implementation | post-fix | functional-review) ;;
  *)
    echo "error: unknown stage '$STAGE'" >&2
    usage
    ;;
esac

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "error: not inside a git repository" >&2
  exit 1
}
cd "$REPO_ROOT"

if ! BASE_SHA=$(git rev-parse --verify "${BASE_SHA}^{commit}" 2>/dev/null); then
  echo "error: base-sha '$2' does not resolve to a commit" >&2
  exit 1
fi

HEAD_SHA=$(git rev-parse HEAD)
BRANCH=$(git branch --show-current || echo "(detached)")

# Capture real working-tree status before any intent-to-add staging below,
# so the bundle reflects what the user's git state actually looks like.
WORKTREE_STATUS=$(git status --short)

# `git diff <base>` only reports tracked content. Temporarily mark new,
# untracked (but not gitignored) files as intent-to-add so they show up as
# additions in the diff/patch/name-status output below, then restore their
# untracked status on exit so this script has no lasting effect on git state.
mapfile -t UNTRACKED_FILES < <(git ls-files --others --exclude-standard -- .)
if ((${#UNTRACKED_FILES[@]} > 0)); then
  git add -N -- "${UNTRACKED_FILES[@]}"
fi
cleanup() {
  if ((${#UNTRACKED_FILES[@]} > 0)); then
    git reset -- "${UNTRACKED_FILES[@]}" > /dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

BUNDLE_DIR=".ai-review/current"
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

while IFS=$'\t' read -r status path; do
  [[ -z "$path" ]] && continue
  # Renames report as "R100<TAB>old<TAB>new"; keep the new path.
  if [[ "$status" == R* ]]; then
    path=$(printf '%s' "$path" | awk -F'\t' '{print $NF}')
  fi
  [[ "$status" == D* ]] && continue
  if [[ -f "$path" ]]; then
    mkdir -p "$FILES_DIR/$(dirname -- "$path")"
    cp -- "$path" "$FILES_DIR/$path"
  fi
done < <(git diff --name-status "$BASE_SHA" -- .)

# --- copy explicitly listed context files, if any ---
CONTEXT_FILES_LIST="$BUNDLE_DIR/CONTEXT_FILES.txt"
if [[ -s "$CONTEXT_FILES_LIST" ]]; then
  while IFS= read -r ctx_path; do
    [[ -z "$ctx_path" ]] && continue
    if [[ -f "$ctx_path" ]]; then
      mkdir -p "$FILES_DIR/$(dirname -- "$ctx_path")"
      cp -- "$ctx_path" "$FILES_DIR/$ctx_path"
    else
      echo "warning: context file listed but not found: $ctx_path" >&2
    fi
  done < "$CONTEXT_FILES_LIST"
fi

# --- archive ---
ARCHIVE=".ai-review/review-bundle.tar.gz"
tar -czf "$ARCHIVE" -C .ai-review current

echo "Bundle ready:"
echo "  stage:   $STAGE"
echo "  branch:  $BRANCH"
echo "  base:    $BASE_SHA"
echo "  head:    $HEAD_SHA"
echo "  bundle:  $BUNDLE_DIR"
echo "  archive: $ARCHIVE"
