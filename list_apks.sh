#!/usr/bin/env bash
# Lists all built APKs of the project (veil module), newest first,
# printing their full absolute paths to stdout.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_DIR="$PROJECT_ROOT/veil/app/build/outputs/apk"

if [[ ! -d "$APK_DIR" ]]; then
    echo "No APK output directory found: $APK_DIR" >&2
    echo "Build the project first: cd veil && ./gradlew assembleRelease" >&2
    exit 1
fi

mapfile -d '' apks < <(
    find "$APK_DIR" -type f -name '*.apk' -printf '%T@\t%p\0' \
        | sort -z -t $'\t' -k1,1nr \
        | cut -z -f2-
)

if (( ${#apks[@]} == 0 )); then
    echo "No APK files found in $APK_DIR" >&2
    exit 1
fi

for apk in "${apks[@]}"; do
    echo "$apk"
done
