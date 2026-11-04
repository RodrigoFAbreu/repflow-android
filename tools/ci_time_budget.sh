#!/bin/sh
# Warns in the run when a pipeline run is over its time budget, so a slow-down is seen and handled.
# Usage: ci_time_budget.sh <minutes> <label>
set -eu
budget=$1
label=$2
started=$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID/attempts/${GITHUB_RUN_ATTEMPT:-1}" --jq .run_started_at)
elapsed=$(( ( $(date +%s) - $(date -d "$started" +%s) ) / 60 ))
echo "$label: $elapsed min so far, budget $budget min" | tee -a "$GITHUB_STEP_SUMMARY"
if [ "$elapsed" -gt "$budget" ]; then
  echo "::warning title=CI over budget::$label took $elapsed min, budget $budget min. Find the cause; more shards alone is not a fix."
fi
