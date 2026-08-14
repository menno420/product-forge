#!/usr/bin/env bash
# compile-check — prove the :app module's Kotlin COMPILES without the Android SDK.
#
# ============================== PROVENANCE ==============================
# Why added : Slice 18. CI's assemble-app job is the authoritative app-module
#             proof, but it needs the full Android SDK. Working sessions run
#             in containers without one, and the Slice-16 card's "app compiles
#             vs android.jar via the saved pipeline" pipeline was session-local
#             — a mechanism that lives only in a chat is not in the repo. This
#             script is that pipeline, committed: it compiles every app-module
#             .kt against the Robolectric android-all jar (real framework
#             classes, org.json included) + the two pure-JVM module class dirs,
#             with a generated stand-in R (string ids only — the app builds its
#             UI programmatically; grep confirms R.string is the only app-R
#             namespace used). Catches type errors pre-push; it is NOT a build:
#             no aapt, no dex, no manifest processing.
# Usage     : ./compile-check.sh   (from products/phone-controller/android/)
#             Jars cache in ~/.cache/pc-compile-check (override:
#             PC_COMPILE_CHECK_CACHE); Maven mirror override: PC_MAVEN_MIRROR.
# Date      : 2026-08-14 (Slice 18)
# ========================================================================
set -euo pipefail
cd "$(dirname "$0")"

KOTLIN_VERSION="2.0.21" # keep in lockstep with app/build.gradle.kts
ANDROID_ALL="14-robolectric-10818077" # Android 14 framework classes
CACHE="${PC_COMPILE_CHECK_CACHE:-$HOME/.cache/pc-compile-check}"
MAVEN="${PC_MAVEN_MIRROR:-https://repo1.maven.org/maven2}"
mkdir -p "$CACHE"

# The OFFICIAL compiler distribution — self-contained (the embeddable Maven jar
# wants its whole transitive dep tree hand-assembled; the zip needs nothing).
KOTLINC="$CACHE/kotlinc-$KOTLIN_VERSION/bin/kotlinc"
if [ ! -x "$KOTLINC" ]; then
  curl -fsSL "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VERSION/kotlin-compiler-$KOTLIN_VERSION.zip" \
    -o "$CACHE/kotlin-compiler.zip"
  rm -rf "$CACHE/kotlinc-$KOTLIN_VERSION" "$CACHE/kotlinc"
  (cd "$CACHE" && unzip -q kotlin-compiler.zip && mv kotlinc "kotlinc-$KOTLIN_VERSION")
  rm -f "$CACHE/kotlin-compiler.zip"
fi

AJAR="$CACHE/android-all-$ANDROID_ALL.jar"
[ -f "$AJAR" ] || curl -fsSL "$MAVEN/org/robolectric/android-all/$ANDROID_ALL/android-all-$ANDROID_ALL.jar" -o "$AJAR"

# The two pure-JVM modules the app depends on (compiled by their test task, or here).
[ -d capability-core/build/classes/kotlin/main ] || gradle :capability-core:classes -q --no-daemon --console=plain
[ -d hid-core/build/classes/kotlin/main ] || gradle :hid-core:classes -q --no-daemon --console=plain

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

# Stand-in R: real ids come from aapt in CI; for type-checking, stable fakes do.
RSRC="$OUT/rsrc/com/productforge/phonecontroller"
mkdir -p "$RSRC"
python3 - app/src/main/res/values/strings.xml "$RSRC/R.java" <<'PY'
import re, sys
names = sorted(set(re.findall(r'<string name="([A-Za-z0-9_]+)"', open(sys.argv[1]).read())))
with open(sys.argv[2], "w") as f:
    f.write("package com.productforge.phonecontroller;\n")
    f.write("public final class R { public static final class string {\n")
    for i, n in enumerate(names):
        f.write("  public static final int %s = %d;\n" % (n, 0x7F010000 + i))
    f.write("} }\n")
PY

mapfile -t SRCS < <(find app/src/main/kotlin -name '*.kt' | sort)
CP="$AJAR:capability-core/build/classes/kotlin/main:hid-core/build/classes/kotlin/main"

# Real exit code read directly — never $? after a pipe (estate rule).
set +e
"$KOTLINC" -classpath "$CP" -jvm-target 17 -nowarn -d "$OUT/classes" \
  "${SRCS[@]}" "$RSRC/R.java"
rc=$?
set -e
if [ "$rc" -ne 0 ]; then
  echo "compile-check: FAILED (kotlinc exit $rc)"
  exit "$rc"
fi
count=$(find "$OUT/classes" -name '*.class' | wc -l)
echo "compile-check: OK — ${count} classes from ${#SRCS[@]} source files"
