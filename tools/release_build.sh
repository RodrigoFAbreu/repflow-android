#!/bin/sh
# Builds the release APK. Its version comes from RELEASE_VERSION (see app/build.gradle.kts), and it is signed
# with ~/.android/debug.keystore, the key the owner's phone has, so it installs over the app there.
set -eu
: "${RELEASE_VERSION:?is unset}"
./gradlew assembleDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk "dist/repflow-$RELEASE_VERSION.apk"
