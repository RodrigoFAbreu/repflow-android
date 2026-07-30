#!/usr/bin/env python3
"""Prototype: review-identity fingerprint for Workflow v2.1 core.

Implements compute_review_content_id() (plan stage) and compute_bundle_id(),
built in direct response to round-6 external review (`OPUS-R6-001`,
`OPUS-R6-002`, `OPUS-R6-008`), round-7's follow-up prototype-correction
review (`PROTO-R7-001` through `PROTO-R7-004`), round-8's second
correction pass (`OPUS-R8-001` through `OPUS-R8-019`), round-9's
integration-focused pass (`GPT-R9-001`, `-002`, `-009`, `-010`, `-014`;
the rest of round 9's findings are lifecycle/state-machine design fixes,
applied in `docs/ai-workflow/WORKFLOW_V2_PLAN.md` rather than here), and
round-10's independent re-verification, which found the identity
algorithm itself finished and correct (all five of its own round-8
regression probes re-run and confirmed fixed by execution) and one
remaining classification-completeness gap: `PLAN_STAGE_EXCLUDED_PATHS`/
`PREFIXES` covered only today's working tree, not the paths this
milestone's own later checkpoints will create, which made plan-approval
durability unevaluable the moment the first implementation-phase file
landed (`OPUS-R10-001`). Both lists are now populated with the concrete
future paths this milestone's registry declares -- see the exclusion
constants below. Per round 10's own assessment, the identity subsystem
should now be treated as **frozen**: no further changes to
`compute_bundle_id()`/`compute_review_content_id()` unless a failing test
forces one; the remaining work is wiring it into commands (WF4a-i), not
refining it.

Round 11 (a manual external reviewer, evaluating a proposed two-stage
local-then-manual-external plan-review protocol alongside the frozen
identity subsystem) made exactly one narrowly-scoped classification
addition, explicitly endorsed as compatible with the freeze
(`GPT-R11-008`, acceptance criterion 13): `PLAN_STAGE_PROTECTED` now also
names the concise plan-review operator guide's final path
(`docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md`), pre-declared before the file
itself exists, the same proactive-classification pattern `OPUS-R10-001`
established. No other change to either function or either classification
list.

Round 7 found the first draft fail-open (unclassified paths silently
ignored instead of raising), non-deterministic (dependent on local
`core.abbrev` and on how the caller spelled the base commit), unable to
prove worktree/commit parity, and reporting an ID that had gone stale
relative to the actual final bundle. Round 8 found that the round-7 fixes
were real but incomplete at the boundaries: `bundle_id`'s self-exclusion was
scoped to one file instead of one field-pattern; the text-based
normalizers collapsed byte-distinct inputs (CRLF/LF, trailing newline,
non-UTF-8 bytes) to the same hash; worktree mode came from the filesystem
instead of from Git's own `core.fileMode` semantics, breaking parity on a
normal Git configuration; a symlinked *directory* bypassed the fail-closed
symlink check because `Path.is_dir()` follows symlinks and was tested
before `is_symlink()`; the commit-source identity entry point never called
the classifier at all; the exclusion list was prefix-matched against exact
paths, silently over-excluding suffixed siblings; gitignored paths were
invisible to classification while a dead, unreachable `.ai-review/`
exclusion entry implied otherwise; exclusion carried no cost or
justification; identity-bearing scalars defaulted instead of being
required; and the protected/excluded sets that determine the manifest were
themselves outside the hashed projection.

This third draft's design changes:
  - `compute_bundle_id()`'s self-reference exclusion is now a single
    field-pattern (`^bundle_id: [0-9a-f]{64}$`), normalized out of *every*
    bundle file, not just `MANIFEST.md` -- resolves `OPUS-R8-001`/`-013`.
    `MANIFEST.md` is now a required bundle file; its absence fails closed.
  - Byte-level normalization throughout `compute_bundle_id()`: split only
    on `b"\n"`, never decode, never use `str.splitlines()` (which also
    treats `\r`, `\x0b`, `\x0c` as line breaks) -- resolves `OPUS-R8-002`.
  - Worktree file mode is derived from Git's own `core.fileMode` semantics
    (executable bit is meaningful only when that config is true) rather
    than raw `os.access()` -- resolves `OPUS-R8-003`.
  - Bundle-entry classification checks `is_symlink()` before `is_dir()`,
    and rejects any non-regular, non-directory entry (FIFOs, sockets) --
    resolves `OPUS-R8-004`.
  - The commit-source identity entry point now calls the same fail-closed
    classifier as the worktree entry point, scoped to `base..commit`
    -- resolves `OPUS-R8-005`.
  - The exclusion list is split into an exact-path set and a
    directory-prefix set (each prefix required to end in `/`, validated at
    import); every entry carries a one-line justification -- resolves
    `OPUS-R8-006`/`-008`.
  - The dead `.ai-review/` prefix entry (unreachable because `.gitignore`
    already hides it from untracked-path enumeration) is removed rather
    than kept for appearance -- resolves `OPUS-R8-007`.
  - `docs/TECHNICAL_DECISIONS.md` moved from excluded to **protected**: a
    technical-decision entry created specifically to satisfy a review
    finding is durable design content the reviewer approving the plan is
    binding, not incidental housekeeping -- resolves `OPUS-R8-008`
    bonus-answer 5.
  - `CHANGED_FILES.txt`'s `generated:` line is found by searching the
    header block for the field, and fails closed if it is missing or
    duplicated, rather than silently no-opping when its position shifts
    -- resolves `OPUS-R8-009`.
  - `work_item_type`/`work_item_id`/`plan_revision` have no defaults; the
    caller must supply them. `work_item_id` is validated against the
    slug grammar -- resolves `OPUS-R8-010`.
  - The protected path set and the exclusion sets are themselves part of
    the hashed `review_content_id` projection -- resolves `OPUS-R8-014`.
  - `PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_*` are frozen (frozenset /
    `MappingProxyType`), never a literal mutable default -- resolves
    `OPUS-R8-015`.
  - Bundle entry keys use `Path.as_posix()`, never the OS path separator
    -- resolves `OPUS-R8-017`.

Round 9 found the round-8 fixes correct as far as they went, but two of
them created new problems at a different boundary, plus two smaller gaps:
  - `bundle_id`'s self-reference exclusion, generalized in round 8 to every
    bundle file, made a *wrong or stale* `bundle_id:`-shaped line in any
    non-manifest file invisible rather than flagged -- confirmed live, in
    this very round's own bundle (see the "Round 8/9 disposition" sections
    of `WORKFLOW_V2_PLAN.md`). Narrowed back to exactly one legal location:
    `MANIFEST.md`'s own field is still stripped; the same pattern appearing
    in *any other* bundle file now raises `ForeignBundleIdFieldError`
    instead of being silently ignored -- resolves `GPT-R9-001`.
  - `compute_bundle_id()`'s and `_snapshot_worktree()`'s mode detection
    used `os.access(path, os.X_OK)`, which reflects the *current process's
    effective* access (user, group, ACLs) rather than the file's own
    stored mode bits -- two reviewers on different filesystems/users could
    derive different logical modes for the same bytes. Both now read the
    owner-execute stat bit directly (`st_mode & 0o100`), matching exactly
    what Git itself consults -- resolves `GPT-R9-009`.
  - Importing this module to verify a bundle could write a `.pyc` file
    inside an extracted bundle's `files/` copy, changing the very
    `bundle_id` being verified. `sys.dont_write_bytecode = True` is now set
    unconditionally at import time -- resolves `GPT-R9-010`.
  - `work_item_type` was accepted as any string; now validated against the
    controlled vocabulary `{"process", "product"}` before hashing --
    resolves `GPT-R9-014`.

Deliberately stdlib-only (no new dependency category; recorded explicitly
in `docs/TECHNICAL_DECISIONS.md`). Not yet wired into any command; this is
a prototype exercised by `workflow_fingerprint_test.py` (hermetic unit
suite) and `workflow_fingerprint_demo_test.py` (explicit real-repository
integration demo).

Path-name policy vs. bundle-content policy (`OPUS-R8-019`): path names
handled via `git ls-files -z`/`git diff -z` are decoded as UTF-8 and will
raise loudly (uncaught `UnicodeDecodeError`) on a non-UTF-8 path -- this
repository's tracked paths are expected to be UTF-8, and a silent
byte-preserving fallback here would hide a genuinely unusual path rather
than surface it. Bundle *file content* is the opposite case -- content
such as `git status --short` output routinely contains non-UTF-8 bytes
that are not a defect -- so `compute_bundle_id()` treats content as opaque
bytes throughout and never decodes it. Two different kinds of data,
deliberately two different policies, stated once here.

Implementation stage (`compute_review_content_id` for `stage="implementation"`)
remains out of scope for this prototype: this milestone has not started
implementation, there is no real changed-file diff to validate a
commit-source diff algorithm against, and building one against a fabricated
example would not be executable evidence. Deferred to WF4a-i, alongside
real implementation-stage fixtures.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from types import MappingProxyType
from typing import Mapping

# A bundle-verification run must never write bytecode into a bundle
# directory it is hashing -- that would change bundle_id as a side effect
# of verifying it. Set unconditionally, not left to the caller's
# environment (resolves GPT-R9-010).
sys.dont_write_bytecode = True


class UnclassifiedPathError(Exception):
    """Raised when a changed or untracked path is neither protected nor
    excluded — fails closed per PROTO-R7-001's requirement that this
    actually be reachable, not merely defined."""


class UnsupportedPathTypeError(Exception):
    """Raised for a protected path or bundle entry whose Git object type
    this prototype does not model (anything but a regular file or a
    symlink for protected paths; anything but a regular file or directory
    for bundle entries) — fails closed per PROTO-R7-007/OPUS-R8-004."""


class AbsentProtectedPathError(Exception):
    """Raised when a plan-stage protected path does not exist. At plan
    stage every protected path is design content the reviewer is meant to
    be reading right now, not a placeholder for work the approved plan has
    yet to produce — a silent `exists: False` tombstone here would let an
    approval bind to content nobody has seen (resolves OPUS-R14-001/-006).
    This does not apply to the (not yet built) implementation-stage
    projection, where a deletion is a real, representable change and a
    tombstone is the correct representation."""


class AmbiguousBaseError(Exception):
    """Raised when a caller-supplied base spelling does not resolve to
    exactly one commit — resolves PROTO-R7-002."""


class InvalidWorkItemIdError(Exception):
    """Raised when a work_item_id does not match the slug grammar
    `^[a-z0-9][a-z0-9_-]{0,63}$` — resolves OPUS-R8-010."""


class InvalidWorkItemTypeError(Exception):
    """Raised when work_item_type is not one of the controlled values
    `{"process", "product"}` — resolves GPT-R9-014."""


class ForeignBundleIdFieldError(Exception):
    """Raised when a bundle file other than `MANIFEST.md` contains a line
    matching the `bundle_id: <64-hex-chars>` contract. Exactly one bundle
    file may ever report the bundle's own identity; a lookalike line
    anywhere else is far more likely a stale or tampered value than
    legitimate content, and round 8's blanket exclusion made exactly that
    case invisible instead of flagged — resolves GPT-R9-001."""


class MissingRequiredBundleFileError(Exception):
    """Raised when a bundle directory is missing a required file (e.g.
    `MANIFEST.md`) — resolves OPUS-R8-001."""


class MalformedBundleIdFieldError(Exception):
    """Raised when a bundle file contains more than one line matching the
    `bundle_id: <64-hex-chars>` contract — resolves OPUS-R8-013."""


class MalformedExclusionConstantError(Exception):
    """Raised at import time if a directory-prefix exclusion entry does
    not end in `/` — resolves OPUS-R8-006."""


class MissingGeneratedTimestampError(Exception):
    """Raised when `CHANGED_FILES.txt`'s header has no `generated:` line
    — resolves OPUS-R8-009 (fail closed instead of silently no-opping)."""


class DuplicateGeneratedTimestampError(Exception):
    """Raised when `CHANGED_FILES.txt`'s header has more than one
    `generated:` line — resolves OPUS-R8-009."""


class PlanRevisionMismatchError(Exception):
    """Raised when the registry JSON's declared `plan_revision` cannot be
    read, or disagrees with the plan document's own title. Round 15
    deferred sourcing `plan_revision` from the registry on the premise
    that no real caller existed yet to source it from anywhere else; by
    round 18 `__main__` was a real caller writing the authoritative
    manifest with a hardcoded, already-stale literal — resolves
    `OPUS-R18-003`."""


class ReviewContentIdNotIdempotentError(Exception):
    """Raised when `review_content_id`, recomputed immediately after
    being written into `MANIFEST.md`, does not match the value just
    written — the same compute-last-and-assert discipline round 8
    established for `bundle_id`, extended to the identifier that actually
    gates approval durability (`OPUS-R18-001`)."""


class BundleIdNotIdempotentError(Exception):
    """Raised when `bundle_id`, recomputed immediately after being
    written into `MANIFEST.md`, does not match the value just written.
    Previously only printed as a boolean; the write path now fails
    closed on this instead of merely reporting it, matching the
    treatment `review_content_id` needed (`OPUS-R18-001`, applied
    symmetrically)."""


class MissingReviewContentIdStatementError(Exception):
    """Raised when `REVIEW_REQUEST.md` states no `review_content_id`
    value at all — resolves `OPUS-R18-005`: a reviewer needs a second,
    checkable copy that does not depend on the one file capable of being
    silently mutated."""


class ReviewContentIdMismatchError(Exception):
    """Raised when `REVIEW_REQUEST.md`'s stated `review_content_id`
    disagrees with `MANIFEST.md`'s (or states more than one disagreeing
    value) — resolves `OPUS-R18-005`."""


WORK_ITEM_ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,63}$")

# D1's controlled vocabulary. "synthetic" (WF8b's dry-run item) is
# deliberately not identity-bearing for the plan-stage fingerprint -- a
# synthetic item never has plan-stage content of its own to fingerprint,
# it borrows the real process item's.
WORK_ITEM_TYPES = frozenset({"process", "product"})


def validate_work_item_id(work_item_id: str) -> None:
    if not WORK_ITEM_ID_RE.match(work_item_id):
        raise InvalidWorkItemIdError(work_item_id)


def validate_work_item_type(work_item_type: str) -> None:
    if work_item_type not in WORK_ITEM_TYPES:
        raise InvalidWorkItemTypeError(work_item_type)


def _owner_executable(st_mode: int) -> bool:
    """Whether the owner-execute stat bit is set, read directly from the
    file's own stored mode bits -- not `os.access()`, which reflects the
    *current process's effective* access (user/group/ACLs) and can differ
    from the file's actual mode across users or filesystems. This is
    exactly the bit Git itself consults when deciding `100644` vs.
    `100755` — resolves GPT-R9-009."""
    return bool(st_mode & 0o100)


def _run(args: list[str], cwd: Path, input_bytes: bytes | None = None) -> str:
    if input_bytes is None:
        result = subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)
        return result.stdout
    result = subprocess.run(args, cwd=cwd, check=True, capture_output=True, input=input_bytes)
    return result.stdout.decode()


def _canonical_json(obj) -> bytes:
    return json.dumps(obj, sort_keys=True, separators=(",", ":")).encode("utf-8")


def resolve_base(repo_root: Path, base: str) -> str:
    """Resolve any base spelling (short SHA, full SHA, branch, tag, `HEAD`)
    to its full, canonical 40-character commit SHA. Two different spellings
    of the same commit must resolve to the same value, and an ambiguous or
    invalid base must fail before any manifest work starts — resolves
    PROTO-R7-002."""
    try:
        out = subprocess.run(
            ["git", "rev-parse", "--verify", f"{base}^{{commit}}"],
            cwd=repo_root, check=True, capture_output=True, text=True,
        ).stdout
    except subprocess.CalledProcessError as exc:
        raise AmbiguousBaseError(
            f"base {base!r} does not resolve to exactly one commit"
        ) from exc
    resolved = out.strip()
    if len(resolved) != 40:
        raise AmbiguousBaseError(f"base {base!r} resolved to unexpected value: {resolved!r}")
    return resolved


def _hash_object(repo_root: Path, rel_path: str) -> str:
    return _run(["git", "hash-object", "--", rel_path], cwd=repo_root).strip()


def _hash_blob_bytes(repo_root: Path, data: bytes) -> str:
    return _run(
        ["git", "hash-object", "--stdin", "-t", "blob"], cwd=repo_root, input_bytes=data
    ).strip()


def _untracked_paths(repo_root: Path) -> set[str]:
    """Untracked, non-gitignored paths. Gitignored paths are categorically
    outside plan-stage content identity (OPUS-R8-007): they are invisible
    here by the same `--exclude-standard` flag that makes the `.gitignore`
    entry classify anything real, and no exclusion-list entry claims to
    cover them."""
    out = _run(["git", "ls-files", "--others", "--exclude-standard", "-z"], cwd=repo_root)
    return {p for p in out.split("\x00") if p}


def _changed_tracked_paths(repo_root: Path, base_full: str) -> set[str]:
    out = _run(["git", "diff", "--name-only", "-z", base_full], cwd=repo_root)
    return {p for p in out.split("\x00") if p}


def _changed_tracked_paths_between(repo_root: Path, base_full: str, target: str) -> set[str]:
    out = _run(["git", "diff", "--name-only", "-z", base_full, target], cwd=repo_root)
    return {p for p in out.split("\x00") if p}


def _core_file_mode_enabled(repo_root: Path) -> bool:
    """Whether Git honours the filesystem executable bit in this repository
    — resolves OPUS-R8-003. Git's own default is true; `--type=bool`
    normalizes any spelling (`true`/`1`/`yes`) to `true`/`false`."""
    result = subprocess.run(
        ["git", "config", "--type=bool", "core.fileMode"],
        cwd=repo_root, capture_output=True, text=True,
    )
    if result.returncode != 0:
        return True
    return result.stdout.strip() == "true"


PLAN_TITLE_REVISION_RE = re.compile(r"\(Revision (\d+)\)")

DEFAULT_REGISTRY_PATH = Path("docs/ai-workflow/registry/workflow-v2-1-core-registry.json")
DEFAULT_PLAN_PATH = Path("docs/ai-workflow/WORKFLOW_V2_PLAN.md")


def load_plan_revision(
    repo_root: Path,
    registry_path: Path = DEFAULT_REGISTRY_PATH,
    plan_path: Path = DEFAULT_PLAN_PATH,
) -> int:
    """Read `plan_revision` from the tracked, protected registry JSON —
    already machine-written and already part of the hashed projection —
    rather than accepting it as a caller-supplied literal that can
    silently drift from the document it identifies (`OPUS-R18-003`).
    Cross-checked against the plan document's own declared
    `(Revision N)` title so a registry/document disagreement fails
    bundle generation instead of binding an approval to the wrong
    revision. `registry_path`/`plan_path` are relative to `repo_root`;
    the defaults are this milestone's own real paths, overridable so
    hermetic tests can exercise this against a scratch repo."""
    registry_full = repo_root / registry_path
    registry = json.loads(registry_full.read_text())
    if "plan_revision" not in registry:
        raise PlanRevisionMismatchError(
            f"{registry_full} has no 'plan_revision' field"
        )
    revision = registry["plan_revision"]
    plan_full = repo_root / plan_path
    title_line = plan_full.read_text().splitlines()[0]
    match = PLAN_TITLE_REVISION_RE.search(title_line)
    if match is None:
        raise PlanRevisionMismatchError(
            f"{plan_full} title has no '(Revision N)' marker: {title_line!r}"
        )
    if int(match.group(1)) != revision:
        raise PlanRevisionMismatchError(
            f"registry {registry_full} declares plan_revision={revision!r}, "
            f"but {plan_full} title declares Revision {match.group(1)}"
        )
    return revision


# ---------------------------------------------------------------------------
# Path classification — resolves PROTO-R7-001, OPUS-R8-006/007/008
# ---------------------------------------------------------------------------

# Explicit, exhaustive protected path set for the plan stage. Frozen so no
# caller can corrupt the shared default by mutating it (OPUS-R8-015).
PLAN_STAGE_PROTECTED: frozenset[str] = frozenset({
    "docs/ai-workflow/WORKFLOW_V2_PLAN.md",
    "docs/ai-workflow/WORKFLOW_V2_AUDIT.md",
    # A technical-decision entry created specifically to satisfy a review
    # finding is durable design content the plan-stage reviewer is binding,
    # not incidental housekeeping -- resolves OPUS-R8-008 bonus-answer 5.
    "docs/TECHNICAL_DECISIONS.md",
    # The plan declares these two JSON files normative (D-Registry, D4b);
    # they must be part of what the reviewer approves, not generated for
    # the first time inside a later approval command -- resolves
    # GPT-R9-003.
    "docs/ai-workflow/registry/workflow-v2-1-core-registry.json",
    "docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json",
})

# Shared justification for the closed set of top-level product
# artifacts named below (OPUS-R18-004): D-Fingerprint's rule 2 covers
# every path a concurrent work item may write, not only the four
# OPUS-R16-001 happened to name, and the same sanctioned scenario
# (Milestone 8 adoption while this item is mid-implementation)
# continues one step further into FIXING_FUNCTIONAL_FINDINGS's writes to
# product code, product tests, and product documentation. Stated once
# here rather than repeated per entry, per the finding's own acceptance
# criterion.
PRODUCT_SCOPE_JUSTIFICATION = (
    "product/repository content outside this process work item's "
    "declared scope -- concurrent work items (product milestones, "
    "functional-finding fixes, other tooling changes) may write here; "
    "fail-closed is preserved for any path outside this named set "
    "(resolves OPUS-R18-004)"
)

# Exact-path exclusions, each with a mandatory one-line justification
# (OPUS-R8-008). Exact match only -- never a prefix -- so a suffixed
# sibling (e.g. "docs/TECHNICAL_DECISIONS.md.orig") fails closed instead of
# silently inheriting the exclusion (OPUS-R8-006).
PLAN_STAGE_EXCLUDED_PATHS: Mapping[str, str] = MappingProxyType({
    ".gitignore":
        "repository housekeeping, not design content",
    ".github/workflows/ci.yml":
        "operational CI wiring for the prototype's test suite -- the design "
        "decision it implements (stdlib-only Python, tested every PR) is "
        "recorded in the protected docs/TECHNICAL_DECISIONS.md; the workflow "
        "file itself also carries unrelated Android build/lint/test steps "
        "that must not stale this milestone's approval",
    "docs/ai-workflow/WORKFLOW_STATE.json":
        "future runtime-mutable per-work-item state (WF1a) -- excluded so "
        "ordinary phase/progress writes never stale a plan approval",
    "docs/ai-workflow/WORKFLOW_CONFIG.json":
        "future runtime-mutable repository-level config (WF1a/WF-Activate) "
        "-- same reasoning as WORKFLOW_STATE.json",
    "CLAUDE.md":
        "WF4a-ii's gate-count depointer is a small mechanical edit that "
        "implements the design, not part of the design being reviewed here",
    "docs/ai-workflow/REVIEW_PROTOCOL.md":
        "WF5's bundle-mechanics updates implement D-Bundle-Manifest, not "
        "design content themselves",
    "docs/ai-workflow/MILESTONE_WORKFLOW.md":
        "WF4a-ii applies the already-reviewed D-States blocks verbatim; the "
        "design is reviewed here, the application of it is not",
    "docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md":
        "the concise two-stage plan-review operator guide documents the "
        "already-reviewed design for operators (D-Plan-Review-Stages), it "
        "is not itself the design -- same reasoning as MILESTONE_WORKFLOW.md/"
        "REVIEW_PROTOCOL.md above. Moved here from PLAN_STAGE_PROTECTED "
        "(resolves OPUS-R14-001/-009): pre-declaring it protected while "
        "unwritten made D-States' 'checkpoints never touch protected "
        "documents' invariance false the moment WF4a-iv actually created "
        "it, since that creation is itself a checkpoint touching a "
        "protected path. Excluding it (like the other operator-facing docs "
        "above) means WF4a-iv can write it without staling the plan "
        "approval, and a plan-stage bundle never needs to tombstone a "
        "not-yet-written protected path in the first place",
    "docs/ACTIVE_MILESTONE.md":
        "product-milestone narrative, explicitly out of scope for this "
        "process work item (CLAUDE.md's own routing) -- excluded, not "
        "unmentioned, so a concurrent product work item's write to it (e.g. "
        "Milestone 8 adoption/functional-review-checklist writes, D-Legacy "
        "phase 2) never blocks this work item's own classification, since "
        "active_work_item_id is a resume-focus pointer, not an execution "
        "lock (D1), and concurrent activity is the normal case (resolves "
        "OPUS-R16-001)",
    "docs/ROADMAP.md":
        "product milestone ordering, same reasoning and same concurrent-"
        "work-item exposure as docs/ACTIVE_MILESTONE.md above (resolves "
        "OPUS-R16-001)",
    # Top-level product docs a concurrent product work item may write
    # (e.g. a functional-finding fix updating domain/UX documentation) --
    # see PRODUCT_SCOPE_JUSTIFICATION above and the matching prefixes in
    # PLAN_STAGE_EXCLUDED_PREFIXES below (resolves OPUS-R18-004).
    "AGENTS.md": PRODUCT_SCOPE_JUSTIFICATION,
    "README.md": PRODUCT_SCOPE_JUSTIFICATION,
    "docs/PROJECT_BRIEF.md": PRODUCT_SCOPE_JUSTIFICATION,
    "docs/DOMAIN_GLOSSARY.md": PRODUCT_SCOPE_JUSTIFICATION,
    "docs/UX_FLOWS.md": PRODUCT_SCOPE_JUSTIFICATION,
})

# Directory-prefix exclusions, each required to end in "/" (validated at
# import below) and each with a justification. Populated this round
# (resolves OPUS-R10-001, which found the previous empty set meant plan-
# approval durability became permanently unevaluable the moment WF0's own
# commit created a single implementation-phase file): every path this
# milestone's own checkpoints are known to create or modify past the plan
# stage is covered here, so classification stays exhaustive across the
# entire registry order, not just against today's working tree. The
# protected JSON files under `docs/ai-workflow/registry/` and
# `docs/ai-workflow/requirements/` are checked first, by exact path, in
# `classify_path` -- a prefix here only ever catches siblings, never
# overrides an exact protected match. **Future work** (WF1b, tied to the
# same generator that emits the registry/mapping JSON): derive this list
# from the registry's own per-checkpoint artifact declarations instead of
# hand-maintaining it, so the two cannot drift.
PLAN_STAGE_EXCLUDED_PREFIXES: Mapping[str, str] = MappingProxyType({
    ".claude/commands/":
        "workflow command files this milestone creates (the bootstrap "
        "command, /approve-review, /accept-milestone) or modifies (the "
        "eight existing v1 commands, per D-States/D-Self-Governance) -- "
        "implementation of the approved design, not the design itself",
    "scripts/":
        "this milestone's own tooling scripts, present (the fingerprint "
        "prototype, reviewed as bundle evidence) and future (WF1a's state "
        "validator and any other workflow script) -- same reasoning as the "
        "prototype's own exclusion, generalized now that this milestone "
        "will create more of them",
    "docs/ai-workflow/requirements/":
        "the mutable execution ledger (WF4b) and any other non-immutable "
        "requirements artifact -- the immutable mapping file itself is "
        "separately protected by exact path, checked first",
    "docs/ai-workflow/registry/":
        "any future non-immutable registry artifact -- the immutable "
        "registry file itself is separately protected by exact path, "
        "checked first",
    "docs/milestones/":
        "product milestone plan/execution artifacts (e.g. "
        "docs/milestones/completed/, written by /accept-milestone for any "
        "product work item, D-Legacy phase 2 included) -- explicitly out "
        "of scope for this process work item, same concurrent-work-item "
        "reasoning as docs/ACTIVE_MILESTONE.md/docs/ROADMAP.md above "
        "(resolves OPUS-R16-001)",
    "docs/ai-workflow/archive/":
        "this work item's own completed-content archival (D1's "
        "process-completion archival), written at or after this work "
        "item's own MILESTONE_COMPLETE -- excluded, not protected, so that "
        "future write is never itself a plan-approval-staling event "
        "(resolves OPUS-R16-001, and closes the loop OPUS-R16-002 opens on "
        "retiming it)",
    # The closed set of top-level product directories a concurrent
    # product work item may write -- app code, its own tests, ADRs,
    # stale agent-context docs, and build/lint config -- per
    # PRODUCT_SCOPE_JUSTIFICATION above (resolves OPUS-R18-004). A path
    # under any *other* directory not named here, or not named in
    # PLAN_STAGE_EXCLUDED_PATHS, still fails closed via
    # UnclassifiedPathError -- this widens the named set, it does not
    # relax the fail-closed default.
    "app/": PRODUCT_SCOPE_JUSTIFICATION,
    "docs/adr/": PRODUCT_SCOPE_JUSTIFICATION,
    "docs/agent-context/": PRODUCT_SCOPE_JUSTIFICATION,
    "gradle/": PRODUCT_SCOPE_JUSTIFICATION,
    "config/": PRODUCT_SCOPE_JUSTIFICATION,
    ".github/": PRODUCT_SCOPE_JUSTIFICATION,
})


def _validate_exclusion_prefixes(prefixes: Mapping[str, str]) -> None:
    for prefix in prefixes:
        if not prefix.endswith("/"):
            raise MalformedExclusionConstantError(prefix)


_validate_exclusion_prefixes(PLAN_STAGE_EXCLUDED_PREFIXES)


def classify_path(
    path: str,
    protected: frozenset[str],
    excluded_paths: Mapping[str, str],
    excluded_prefixes: Mapping[str, str],
) -> str:
    if path in protected:
        return "protected"
    if path in excluded_paths:
        return "excluded"
    if any(path.startswith(prefix) for prefix in excluded_prefixes):
        return "excluded"
    raise UnclassifiedPathError(path)


def assert_all_changed_paths_classified_worktree(
    repo_root: Path,
    base_full: str,
    protected: frozenset[str] = PLAN_STAGE_PROTECTED,
    excluded_paths: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PATHS,
    excluded_prefixes: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PREFIXES,
) -> None:
    """Worktree-source classification gate: every changed tracked path and
    every untracked path must be protected or explicitly excluded, or
    computation stops here. Resolves PROTO-R7-001."""
    changed = _changed_tracked_paths(repo_root, base_full) | _untracked_paths(repo_root)
    for path in sorted(changed):
        classify_path(path, protected, excluded_paths, excluded_prefixes)


def assert_all_changed_paths_classified_commit(
    repo_root: Path,
    base_full: str,
    commit: str,
    protected: frozenset[str] = PLAN_STAGE_PROTECTED,
    excluded_paths: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PATHS,
    excluded_prefixes: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PREFIXES,
) -> None:
    """Commit-source counterpart of `assert_all_changed_paths_classified_worktree`,
    scoped to `base..commit` rather than base..worktree. Previously missing
    entirely, which meant an unclassified file present at the approval
    commit was invisible to the one check meant to catch it — resolves
    OPUS-R8-005."""
    changed = _changed_tracked_paths_between(repo_root, base_full, commit)
    for path in sorted(changed):
        classify_path(path, protected, excluded_paths, excluded_prefixes)


# ---------------------------------------------------------------------------
# review_content_id — plan stage, snapshot-based (resolves PROTO-R7-003/007)
# ---------------------------------------------------------------------------


def _snapshot_worktree(repo_root: Path, rel_path: str) -> dict:
    """Final on-disk state of one protected path: exists / mode / blob.
    Symlinks are hashed as Git would store them — the link target string,
    not the referent file's content. Regular-file mode is derived from
    Git's own `core.fileMode` semantics (OPUS-R8-003): the executable bit
    is meaningful only when that config is true, matching exactly what Git
    itself would record on commit, so worktree/commit parity holds
    regardless of the repository's `core.fileMode` setting."""
    abs_path = repo_root / rel_path
    if abs_path.is_symlink():
        target = os.readlink(abs_path)
        return {"exists": True, "mode": "120000", "blob": _hash_blob_bytes(repo_root, target.encode())}
    if not abs_path.exists():
        return {"exists": False, "mode": None, "blob": None}
    if not abs_path.is_file():
        raise UnsupportedPathTypeError(rel_path)
    executable = _core_file_mode_enabled(repo_root) and _owner_executable(abs_path.stat().st_mode)
    mode = "100755" if executable else "100644"
    return {"exists": True, "mode": mode, "blob": _hash_object(repo_root, rel_path)}


def _snapshot_commit(repo_root: Path, commit: str, rel_path: str) -> dict:
    """Final state of one protected path at a commit, via `git ls-tree` —
    a direct snapshot read, not a diff, so it needs no abbreviation flag
    and no status interpretation."""
    out = subprocess.run(
        ["git", "ls-tree", commit, "--", rel_path],
        cwd=repo_root, check=True, capture_output=True, text=True,
    ).stdout.strip()
    if not out:
        return {"exists": False, "mode": None, "blob": None}
    meta, _, _path = out.partition("\t")
    mode, obj_type, blob = meta.split(" ")
    if obj_type != "blob":
        raise UnsupportedPathTypeError(rel_path)
    return {"exists": True, "mode": mode, "blob": blob}


def compute_review_content_manifest_plan_stage_worktree(
    repo_root: Path, protected: frozenset[str] = PLAN_STAGE_PROTECTED
) -> list[dict]:
    """Fails closed on an absent protected path (resolves
    OPUS-R14-001/-006) -- see `AbsentProtectedPathError`."""
    manifest = []
    for path in sorted(protected):
        entry = _snapshot_worktree(repo_root, path)
        if not entry["exists"]:
            raise AbsentProtectedPathError(path)
        manifest.append({"path": path, **entry})
    return manifest


def compute_review_content_manifest_plan_stage_commit(
    repo_root: Path, commit: str, protected: frozenset[str] = PLAN_STAGE_PROTECTED
) -> list[dict]:
    """Commit-source counterpart; same fail-closed rule (resolves
    OPUS-R14-001/-006) -- a protected path absent at the reviewed commit is
    exactly as invalid as one absent from the worktree."""
    manifest = []
    for path in sorted(protected):
        entry = _snapshot_commit(repo_root, commit, path)
        if not entry["exists"]:
            raise AbsentProtectedPathError(path)
        manifest.append({"path": path, **entry})
    return manifest


def compute_review_content_id_plan_stage(
    repo_root: Path,
    base: str,
    work_item_type: str,
    work_item_id: str,
    plan_revision: int,
    protected: frozenset[str] = PLAN_STAGE_PROTECTED,
    excluded_paths: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PATHS,
    excluded_prefixes: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PREFIXES,
) -> tuple[str, dict]:
    """No identity-bearing scalar has a default (OPUS-R8-010): the caller
    must supply `work_item_type`/`work_item_id`/`plan_revision` explicitly.
    `work_item_id` is validated against the slug grammar. The protected and
    exclusion sets are themselves part of the hashed projection
    (OPUS-R8-014), so editing either changes `review_content_id` even when
    no file content changes. `work_item_type` is validated against the
    controlled vocabulary (GPT-R9-014)."""
    validate_work_item_id(work_item_id)
    validate_work_item_type(work_item_type)
    base_full = resolve_base(repo_root, base)
    assert_all_changed_paths_classified_worktree(
        repo_root, base_full, protected, excluded_paths, excluded_prefixes
    )
    manifest = compute_review_content_manifest_plan_stage_worktree(repo_root, protected)
    projection = {
        "stage": "plan",
        "work_item_type": work_item_type,
        "work_item_id": work_item_id,
        "plan_revision": plan_revision,
        "base_commit": base_full,
        "reviewed_implementation_head": None,
        "review_content_manifest": manifest,
        "protected_paths": sorted(protected),
        "excluded_paths": sorted(excluded_paths),
        "excluded_prefixes": sorted(excluded_prefixes),
    }
    digest = hashlib.sha256(_canonical_json(projection)).hexdigest()
    return digest, projection


def compute_review_content_id_plan_stage_at_commit(
    repo_root: Path,
    base: str,
    commit: str,
    work_item_type: str,
    work_item_id: str,
    plan_revision: int,
    protected: frozenset[str] = PLAN_STAGE_PROTECTED,
    excluded_paths: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PATHS,
    excluded_prefixes: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PREFIXES,
) -> tuple[str, dict]:
    """The commit-source counterpart of `compute_review_content_id_plan_stage`,
    used for post-approval-commit parity verification: same projection
    shape, snapshot read from a commit instead of the working tree, and
    (OPUS-R8-005) the same fail-closed classification precondition, scoped
    to `base..commit`."""
    validate_work_item_id(work_item_id)
    validate_work_item_type(work_item_type)
    base_full = resolve_base(repo_root, base)
    assert_all_changed_paths_classified_commit(
        repo_root, base_full, commit, protected, excluded_paths, excluded_prefixes
    )
    manifest = compute_review_content_manifest_plan_stage_commit(repo_root, commit, protected)
    projection = {
        "stage": "plan",
        "work_item_type": work_item_type,
        "work_item_id": work_item_id,
        "plan_revision": plan_revision,
        "base_commit": base_full,
        "reviewed_implementation_head": None,
        "review_content_manifest": manifest,
        "protected_paths": sorted(protected),
        "excluded_paths": sorted(excluded_paths),
        "excluded_prefixes": sorted(excluded_prefixes),
    }
    digest = hashlib.sha256(_canonical_json(projection)).hexdigest()
    return digest, projection


# ---------------------------------------------------------------------------
# bundle_id — resolves PROTO-R7-004/006/007, OPUS-R8-001/002/004/013/017
# ---------------------------------------------------------------------------

REQUIRED_BUNDLE_FILES: frozenset[str] = frozenset({"MANIFEST.md"})

# Exactly one line of this form may appear in any bundle file: the
# canonical way any file is allowed to report the bundle's own identity
# (OPUS-R8-001/-013). Anything else -- a Markdown list item, an indented
# line inside a fenced block, a table cell -- is simply not this field, and
# is therefore real hashed content, which is the desired behavior: the
# contract is enforced by what the pattern matches, not asserted in prose.
BUNDLE_ID_FIELD_RE = re.compile(rb"^bundle_id: [0-9a-f]{64}$")


def _split_lines_preserve_trailing(content: bytes) -> tuple[list[bytes], bool]:
    """Byte-level split on `b"\\n"` only, preserving whether the content
    ended with a trailing newline. Never decodes, never treats `\\r`,
    `\\x0b`, or `\\x0c` as a line terminator the way `str.splitlines()`
    does — resolves OPUS-R8-002's CRLF/trailing-newline/non-UTF-8 collision
    classes."""
    if not content:
        return [], False
    had_trailing_nl = content.endswith(b"\n")
    body = content[:-1] if had_trailing_nl else content
    return body.split(b"\n"), had_trailing_nl


def _join_lines_preserve_trailing(lines: list[bytes], had_trailing_nl: bool) -> bytes:
    joined = b"\n".join(lines)
    if had_trailing_nl:
        joined += b"\n"
    return joined


def _normalize_changed_files_header(content: bytes) -> bytes:
    """`CHANGED_FILES.txt`'s header block (everything before the first
    blank line) must contain exactly one `generated:` line; that line is
    normalized to a fixed placeholder so bundle regeneration is idempotent.
    Zero or more than one such line fails closed instead of silently
    no-opping — resolves OPUS-R8-009, which found the previous
    fixed-line-index version went silent the moment the header format
    changed shape."""
    lines, had_trailing_nl = _split_lines_preserve_trailing(content)
    header_end = len(lines)
    for i, line in enumerate(lines):
        if line == b"":
            header_end = i
            break
    header = lines[:header_end]
    matches = [i for i, line in enumerate(header) if line.startswith(b"generated:")]
    if not matches:
        raise MissingGeneratedTimestampError(
            "no 'generated:' line found in CHANGED_FILES.txt header block"
        )
    if len(matches) > 1:
        raise DuplicateGeneratedTimestampError(
            f"multiple 'generated:' lines found in CHANGED_FILES.txt header: {matches}"
        )
    lines[matches[0]] = b"generated: <normalized>"
    return _join_lines_preserve_trailing(lines, had_trailing_nl)


MANIFEST_FILENAME = "MANIFEST.md"


def _strip_manifest_self_reference(content: bytes) -> bytes:
    """Normalize out `MANIFEST.md`'s own self-reported identity line —
    the **only** bundle file allowed to carry one (resolves GPT-R9-001,
    narrowing OPUS-R8-001's blanket every-file exclusion back to a single
    schema-defined location). Enforces the field contract from
    OPUS-R8-013: more than one matching line fails closed rather than
    silently stripping all of them."""
    lines, had_trailing_nl = _split_lines_preserve_trailing(content)
    matches = [i for i, line in enumerate(lines) if BUNDLE_ID_FIELD_RE.match(line)]
    if len(matches) > 1:
        raise MalformedBundleIdFieldError(
            f"multiple 'bundle_id: <hex>' lines found ({len(matches)})"
        )
    if not matches:
        return content
    filtered = [line for i, line in enumerate(lines) if i not in matches]
    return _join_lines_preserve_trailing(filtered, had_trailing_nl)


def _reject_foreign_bundle_id_field(rel: str, content: bytes) -> None:
    """Any bundle file *other than* `MANIFEST.md` must not contain a line
    matching the `bundle_id: <64-hex-chars>` contract. Round 8's blanket
    exclusion made a wrong, stale, or tampered value in a narrative file
    (e.g. `TEST_RESULTS.md`) invisible to the identifier instead of
    flagged — confirmed live in round 9's own reviewed bundle. Failing
    closed here means that class of mistake cannot recur silently —
    resolves GPT-R9-001."""
    lines, _ = _split_lines_preserve_trailing(content)
    matches = [i for i, line in enumerate(lines) if BUNDLE_ID_FIELD_RE.match(line)]
    if matches:
        raise ForeignBundleIdFieldError(rel, matches)


def compute_bundle_id(bundle_dir: Path) -> tuple[str, dict]:
    """Hash every file under bundle_dir, keyed by POSIX-style relative path
    (OPUS-R8-017), each entry carrying mode and content hash (PROTO-R7-007).
    Only `MANIFEST.md` may report the bundle's own identity; that one field
    is normalized out of that one file (GPT-R9-001, narrowing
    OPUS-R8-001/-013); the same pattern in any other file fails closed.
    `CHANGED_FILES.txt`'s generated-time line is normalized, failing closed
    if missing/duplicated (OPUS-R8-009). Symlinks are checked *before*
    directories, so a symlinked directory fails closed instead of silently
    bypassing the check the way `Path.is_dir()`-first ordering allowed
    (OPUS-R8-004); any other non-regular, non-directory entry (FIFO,
    socket, device node) fails closed too. Mode is read from the file's own
    stat bits, not effective-access `os.access()` (GPT-R9-009). A bundle
    missing a required file (currently `MANIFEST.md`) fails validation
    rather than returning an identifier for an incomplete bundle
    (OPUS-R8-001)."""
    entries: dict[str, dict] = {}
    for p in sorted(bundle_dir.rglob("*")):
        rel = p.relative_to(bundle_dir).as_posix()
        if p.is_symlink():
            raise UnsupportedPathTypeError(rel)
        if p.is_dir():
            continue
        if not p.is_file():
            raise UnsupportedPathTypeError(rel)
        mode = "100755" if _owner_executable(p.stat().st_mode) else "100644"
        content = p.read_bytes()
        if rel == "CHANGED_FILES.txt":
            content = _normalize_changed_files_header(content)
        if rel == MANIFEST_FILENAME:
            content = _strip_manifest_self_reference(content)
        else:
            _reject_foreign_bundle_id_field(rel, content)
        entries[rel] = {"mode": mode, "sha256": hashlib.sha256(content).hexdigest()}
    missing_required = sorted(REQUIRED_BUNDLE_FILES - entries.keys())
    if missing_required:
        raise MissingRequiredBundleFileError(missing_required)
    digest = hashlib.sha256(_canonical_json(entries)).hexdigest()
    return digest, entries


def render_manifest_md(
    *,
    review_content_id: str,
    protected: frozenset[str],
    excluded_paths: Mapping[str, str],
    excluded_prefixes: Mapping[str, str],
    bundle_id: str | None = None,
) -> str:
    """Render `MANIFEST.md` content. If `bundle_id` is supplied, it is
    written using the exact contract `_strip_bundle_id_field` recognizes
    (`bundle_id: <64-hex-chars>`), so writing it in after computing
    `compute_bundle_id()` never invalidates the value just computed --
    the field is excluded from the hash by construction, not by
    convention."""
    lines = ["# Bundle Manifest", ""]
    if bundle_id is not None:
        lines.append(f"bundle_id: {bundle_id}")
    lines.append(f"review_content_id: {review_content_id}")
    lines.append("")
    lines.append("## Protected paths")
    for path in sorted(protected):
        lines.append(f"- {path}")
    lines.append("")
    lines.append("## Excluded paths (exact match)")
    for path, reason in sorted(excluded_paths.items()):
        lines.append(f"- `{path}` — {reason}")
    lines.append("")
    lines.append("## Excluded prefixes (directories)")
    if excluded_prefixes:
        for path, reason in sorted(excluded_prefixes.items()):
            lines.append(f"- `{path}` — {reason}")
    else:
        lines.append("(none)")
    return "\n".join(lines) + "\n"


_MANIFEST_FIELD_RE = re.compile(r"^(bundle_id|review_content_id): ([0-9a-f]{64})$")


def read_manifest_identifiers(manifest_path: Path) -> dict[str, str]:
    """Read whichever of `bundle_id`/`review_content_id` a `MANIFEST.md`
    currently states, without writing anything -- the read-only
    counterpart callers use to report an existing bundle's recorded
    identifiers alongside a freshly recomputed value (`OPUS-R18-002`)."""
    if not manifest_path.is_file():
        return {}
    fields: dict[str, str] = {}
    for line in manifest_path.read_text().splitlines():
        match = _MANIFEST_FIELD_RE.match(line)
        if match:
            fields[match.group(1)] = match.group(2)
    return fields


_REVIEW_CONTENT_ID_STATEMENT_RE = re.compile(r"^review_content_id: ([0-9a-f]{64})$", re.MULTILINE)


def assert_review_request_states_review_content_id(bundle_dir: Path, review_content_id: str) -> None:
    """`REVIEW_REQUEST.md` must state the same `review_content_id` as
    `MANIFEST.md` -- a second, checkable copy that doesn't depend on the
    one file capable of being silently mutated (`OPUS-R18-002`). This is
    a labelled `review_content_id: <hex>` line, not a `bundle_id:
    <hex>`-shaped one -- `GPT-R9-001`'s one-location rule is specifically
    about that field pattern and is not violated here (`OPUS-R18-005`)."""
    review_request_path = bundle_dir / "REVIEW_REQUEST.md"
    content = review_request_path.read_text() if review_request_path.is_file() else ""
    matches = _REVIEW_CONTENT_ID_STATEMENT_RE.findall(content)
    if not matches:
        raise MissingReviewContentIdStatementError(str(review_request_path))
    if len(set(matches)) > 1:
        raise ReviewContentIdMismatchError(
            f"{review_request_path} states disagreeing review_content_id values: {sorted(set(matches))}"
        )
    stated = matches[0]
    if stated != review_content_id:
        raise ReviewContentIdMismatchError(
            f"{review_request_path} states review_content_id={stated!r}, "
            f"manifest computes {review_content_id!r}"
        )


def write_manifest_with_verified_identifiers(
    repo_root: Path,
    bundle_dir: Path,
    base: str,
    work_item_type: str,
    work_item_id: str,
    plan_revision: int,
    protected: frozenset[str] = PLAN_STAGE_PROTECTED,
    excluded_paths: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PATHS,
    excluded_prefixes: Mapping[str, str] = PLAN_STAGE_EXCLUDED_PREFIXES,
) -> tuple[str, str]:
    """The **only** code path allowed to write `MANIFEST.md` (`OPUS-R18-002`
    — every other entry point, including the CLI's default invocation, is
    read-only). Computes both identifiers last, writes them, then
    recomputes each from the final artifact and asserts equality before
    returning — the `bundle_id` discipline round 8 established, now
    applied symmetrically to `review_content_id` too (`OPUS-R18-001`):
    a protected-path edit landing between the first computation and this
    recompute-and-assert step is caught here rather than silently
    surviving into a written manifest. Also asserts `REVIEW_REQUEST.md`
    already states the same `review_content_id` **before any write
    happens** (`OPUS-R18-005`) — checked first, not last, so a stale
    `REVIEW_REQUEST.md` fails closed without leaving `MANIFEST.md`
    partially rewritten. Returns `(review_content_id, bundle_id)`."""
    manifest_path = bundle_dir / MANIFEST_FILENAME

    digest, _projection = compute_review_content_id_plan_stage(
        repo_root, base, work_item_type=work_item_type, work_item_id=work_item_id,
        plan_revision=plan_revision, protected=protected,
        excluded_paths=excluded_paths, excluded_prefixes=excluded_prefixes,
    )
    assert_review_request_states_review_content_id(bundle_dir, digest)

    manifest_path.write_text(
        render_manifest_md(
            review_content_id=digest, protected=protected,
            excluded_paths=excluded_paths, excluded_prefixes=excluded_prefixes,
        )
    )
    bundle_id, _entries = compute_bundle_id(bundle_dir)
    manifest_path.write_text(
        render_manifest_md(
            review_content_id=digest, protected=protected,
            excluded_paths=excluded_paths, excluded_prefixes=excluded_prefixes,
            bundle_id=bundle_id,
        )
    )

    recomputed_bundle_id, _ = compute_bundle_id(bundle_dir)
    if recomputed_bundle_id != bundle_id:
        raise BundleIdNotIdempotentError(bundle_id, recomputed_bundle_id)

    recomputed_digest, _ = compute_review_content_id_plan_stage(
        repo_root, base, work_item_type=work_item_type, work_item_id=work_item_id,
        plan_revision=plan_revision, protected=protected,
        excluded_paths=excluded_paths, excluded_prefixes=excluded_prefixes,
    )
    if recomputed_digest != digest:
        raise ReviewContentIdNotIdempotentError(digest, recomputed_digest)

    return digest, bundle_id


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(
        description=(
            "Compute this milestone's plan-stage review_content_id/bundle_id. "
            "Read-only by default (OPUS-R18-002): prints both identifiers and "
            "writes nothing. Pass --write-manifest to (re)generate "
            "MANIFEST.md -- the one invocation that mutates the bundle "
            "directory; a reviewer inspecting a bundle should never need it."
        )
    )
    parser.add_argument(
        "base", nargs="?", default="162154d3e5e10eb65e109833acae4b4fb01fc5d6",
        help="base commit/ref (default: this milestone's own base commit)",
    )
    parser.add_argument(
        "--write-manifest", action="store_true",
        help="write .ai-review/current/MANIFEST.md (the only invocation that writes anything)",
    )
    args = parser.parse_args()

    repo_root = Path(
        subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
    )
    plan_revision = load_plan_revision(repo_root)
    digest, projection = compute_review_content_id_plan_stage(
        repo_root, args.base,
        work_item_type="process",
        work_item_id="workflow-v2-1-core",
        plan_revision=plan_revision,
    )
    print("=== compute_review_content_id_plan_stage ===")
    print(f"base_commit (resolved, full): {projection['base_commit']}")
    print(f"plan_revision (from registry JSON, cross-checked against plan title): {plan_revision}")
    print(f"review_content_id: {digest}")
    print(f"protected_paths: {projection['protected_paths']}")
    print(f"excluded_paths: {projection['excluded_paths']}")
    print(f"excluded_prefixes: {projection['excluded_prefixes']}")
    print("review_content_manifest:")
    for entry in projection["review_content_manifest"]:
        print(f"  {entry}")

    bundle_dir = repo_root / ".ai-review" / "current"
    manifest_path = bundle_dir / "MANIFEST.md"
    print()

    if args.write_manifest:
        if not bundle_dir.is_dir():
            raise SystemExit(f"error: no bundle directory at {bundle_dir} -- run scripts/prepare-ai-review.sh first")
        digest, bundle_id = write_manifest_with_verified_identifiers(
            repo_root, bundle_dir, args.base,
            work_item_type="process", work_item_id="workflow-v2-1-core",
            plan_revision=plan_revision,
        )
        print("=== wrote MANIFEST.md ===")
        print(f"wrote: {manifest_path}")
        print(f"review_content_id (write -> recompute -> equal): {digest}")
        print(f"bundle_id (write -> recompute -> equal): {bundle_id}")
        raise SystemExit(0)

    print("=== read-only bundle inspection (no files written) ===")
    if not bundle_dir.is_dir():
        print(f"no bundle directory at {bundle_dir}")
        raise SystemExit(0)
    existing = read_manifest_identifiers(manifest_path)
    if "review_content_id" in existing:
        match = "matches" if existing["review_content_id"] == digest else "DIFFERS -- protected content changed since MANIFEST.md was last written"
        print(f"MANIFEST.md's recorded review_content_id: {existing['review_content_id']} ({match})")
    else:
        print(f"{manifest_path} has no recorded review_content_id yet -- run with --write-manifest first")
    try:
        bundle_id, entries = compute_bundle_id(bundle_dir)
        print(f"bundle_id (recomputed from {bundle_dir}, not written): {bundle_id}")
        print(f"file count: {len(entries)}")
        if "bundle_id" in existing:
            match = "matches" if existing["bundle_id"] == bundle_id else "DIFFERS -- bundle changed since MANIFEST.md was last written"
            print(f"MANIFEST.md's recorded bundle_id: {existing['bundle_id']} ({match})")
    except MissingRequiredBundleFileError:
        print(f"{manifest_path} does not exist yet -- run with --write-manifest first")
    print()
    print("This command never writes to .ai-review/current/ -- safe to re-run "
          "at any time. Verify from an extracted archive copy for a second, "
          "independent check.")
