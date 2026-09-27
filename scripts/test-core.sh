#!/usr/bin/env bash
# Zero-Gradle core test runner: compiles :core with javac against the cached
# LuaJ jar and runs the reflective harness. Useful when Gradle/Maven access is
# unavailable; `./gradlew :core:coreTests` is the primary path.
set -euo pipefail
cd "$(dirname "$0")/.."

LUAJ_JAR=$(find "$HOME/.gradle/caches" -name 'luaj-jse-3.0.1.jar' 2>/dev/null | head -1)
if [ -z "$LUAJ_JAR" ]; then
  LUAJ_JAR=$(find "$HOME/.m2" -name 'luaj-jse-*.jar' 2>/dev/null | head -1)
fi
if [ -z "$LUAJ_JAR" ]; then
  echo "luaj-jse-3.0.1.jar not found in ~/.gradle or ~/.m2" >&2
  exit 1
fi

OUT=build/core-tests
rm -rf "$OUT"
mkdir -p "$OUT/main" "$OUT/test"

javac -d "$OUT/main" -cp "$LUAJ_JAR" \
  $(find core/src/main/java -name '*.java')
javac -d "$OUT/test" -cp "$LUAJ_JAR:$OUT/main" \
  $(find core/src/test/java -name '*.java')
java -cp "$LUAJ_JAR:$OUT/main:$OUT/test" org.opencomputers.test.Harness
