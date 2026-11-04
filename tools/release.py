#!/usr/bin/env python3
"""RepFlow's thin CLI over the Workflow Controller's release transaction, which reads everything
from .workflow-controller/policy.json. CI installs the Controller wheel, then calls these:
classify --commit C | build | verify | checksums DIR | publish --commit C | check-title TITLE."""
import argparse
import hashlib
import os
import subprocess
import sys
from pathlib import Path

from controller import conventional_commit, forge, release_txn, repo_policy
from controller.errors import ControllerError

ROOT = Path(__file__).resolve().parent.parent
TAGGER = ("github-actions[bot]", "41898282+github-actions[bot]@users.noreply.github.com")


def context(rev: str = "HEAD") -> release_txn.ReleaseContext:
    policy = repo_policy.read_committed_policy(ROOT, rev)
    if policy is None:
        raise SystemExit(f"release.py: {rev} has no {repo_policy.POLICY_PATH}")
    return release_txn.ReleaseContext(repo_root=ROOT, policy=policy, forge=forge.GhForge(policy.forge_repository))


def head() -> str:
    return subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()


def classify(commit: str) -> int:
    state = release_txn.classify(context(commit), commit)
    lines = "".join(f"{key}={value}\n" for key, value in state.outputs().items())
    sys.stdout.write(lines)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
            handle.write(lines)
    if not state.succeeded:
        print(f"release.py: refused: {state.state}: {state.detail}", file=sys.stderr)
        return 1
    print(f"ok: {state.state}: {state.detail}", file=sys.stderr)
    return 0


def checksums(directory: Path) -> None:
    lines = []
    for path in sorted(directory.iterdir(), key=lambda p: p.name):
        if path.name != "SHA256SUMS" and path.is_file():
            lines.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n")
    (directory / "SHA256SUMS").write_text("".join(lines))


def check_title(title: str) -> None:
    release = context().release
    print(f"ok: {conventional_commit.bump(title, release.change_types)}: {title}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("classify").add_argument("--commit", required=True)
    sub.add_parser("build")
    sub.add_parser("verify")
    sub.add_parser("checksums").add_argument("directory", type=Path)
    sub.add_parser("publish").add_argument("--commit", required=True)
    sub.add_parser("check-title").add_argument("title")
    args = parser.parse_args()
    try:
        if args.command == "classify":
            return classify(args.commit)
        if args.command == "build":
            for path in release_txn.build(context(), head(), os.environ["RELEASE_VERSION"]):
                print(f"ok: built {path.relative_to(ROOT)}")
        elif args.command == "verify":
            for path in release_txn.verify(context(), head(), os.environ["RELEASE_VERSION"]):
                print(f"ok: {path.relative_to(ROOT)} verified")
        elif args.command == "checksums":
            checksums(args.directory)
        elif args.command == "publish":
            outcome = release_txn.publish(context(), head(), args.commit, tagger=TAGGER)
            print(f"ok: {outcome.tag} at {outcome.target}: {outcome.action} ({outcome.url})")
        elif args.command == "check-title":
            check_title(args.title)
    except ControllerError as exc:
        print(f"release.py {args.command}: refused: {exc.code}: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
