#!/usr/bin/env python3
"""Checks a release APK before it is published: it is signed with the key the owner's phone
expects, so it installs over the app there without a wipe, and it carries the release's version."""
import os
import re
import subprocess
import sys
from pathlib import Path

# ~/.android/debug.keystore on the owner's machine, alias androiddebugkey.
EXPECTED_CERT_SHA256 = "348959510f08e5bebbd0c57db4c53ae8d641dd90d716932de2439397341fc070"


def build_tool(name: str) -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        local = Path("local.properties")
        match = re.search(r"^sdk\.dir=(.+)$", local.read_text(), re.M) if local.exists() else None
        sdk = match.group(1).strip() if match else None
    if not sdk:
        sys.exit("verify_apk: no Android SDK found")
    tools = sorted((Path(sdk) / "build-tools").glob(f"*/{name}"), key=lambda p: [int(n) if n.isdigit() else 0 for n in re.split(r"[.-]", p.parent.name)])
    if not tools:
        sys.exit(f"verify_apk: no {name} in the Android SDK")
    return str(tools[-1])


def run(*argv: str) -> str:
    return subprocess.run(argv, check=True, capture_output=True, text=True).stdout


def main() -> int:
    apk = sys.argv[1]
    version = os.environ["RELEASE_VERSION"]
    certs = run(build_tool("apksigner"), "verify", "--print-certs", apk)
    found = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", certs)
    if found != [EXPECTED_CERT_SHA256]:
        sys.exit(f"verify_apk: {apk} is signed with {found}, expected {EXPECTED_CERT_SHA256}")
    badging = run(build_tool("aapt2"), "dump", "badging", apk)
    if f"versionName='{version}'" not in badging:
        sys.exit(f"verify_apk: {apk} does not carry versionName {version}")
    if "package: name='com.repflow.app'" not in badging:
        sys.exit(f"verify_apk: {apk} is not com.repflow.app")
    print(f"ok: {apk} is signed with the expected key and carries {version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
