#!/bin/sh
set -eu
cd "$(dirname "$0")"
node ../build.cjs
exec ./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug "$@"
