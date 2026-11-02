#!/usr/bin/env bash
# Local/CI preparation only: no installation, upload, tagging or publication.
set -euo pipefail
root="$(cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "$root"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
python3 tools/larpgram/rust-sdk/build.py --release --all-abis
python3 tools/larpgram/rust-sdk/build.py --verify-aar \
    libraries/rustsdk/matrix-rust-sdk.aar --release --all-abis
./gradlew --no-daemon --max-workers=3 '-Dorg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8 -XX:+UseG1GC' \
    --console=plain :app:lintGplayRelease :app:assembleGplayRelease
