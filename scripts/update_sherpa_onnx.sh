#!/usr/bin/env bash
set -euo pipefail

# Resolve the latest upstream master commit and print the Gradle override.
# The app keeps a tested default in app/build.gradle.kts; update it only after
# compiling and running the emulator loop with the new upstream artifact.
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sha="$(curl -fsSL https://api.github.com/repos/k2-fsa/sherpa-onnx/commits/master | sed -n 's/.*"sha": "\([0-9a-f]*\)".*/\1/p' | head -1)"
if [[ -z "$sha" ]]; then
  echo "Unable to resolve sherpa-onnx upstream commit" >&2
  exit 1
fi
short_sha="${sha:0:10}"
echo "Upstream master: $sha"
echo "Run: (cd \"$repo_root/android\" && ./gradlew -PsherpaOnnxVersion=master-${short_sha}-1 :app:assembleDebug)"
